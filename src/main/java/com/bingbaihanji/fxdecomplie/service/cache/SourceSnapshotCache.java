package com.bingbaihanji.fxdecomplie.service.cache;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.service.CacheLimits;
import com.bingbaihanji.fxdecomplie.service.CacheStats;
import com.bingbaihanji.fxdecomplie.util.cache.LruCache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局全文搜索快照缓存 — 保存重命名后的显示源码.
 *
 * <p>键为 workspaceId + 引擎 + 选项 hash + 工作区代数；值不可变. 工作区代数用于拒绝
 * 重命名/反混淆期间构造的陈旧快照写回. 预算按源码字符数 × 2 估算，全局 LRU 淘汰.</p>
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
public final class SourceSnapshotCache {

    private static final String STATS_NAME = "source-snapshots";

    private final LruCache<String, Map<String, String>> cache;

    private final ConcurrentHashMap<String, AtomicLong> generations = new ConcurrentHashMap<>();

    public SourceSnapshotCache() {
        this((int) CacheLimits.SNAPSHOT_BYTES);
    }

    public SourceSnapshotCache(int maxBytes) {
        this.cache = new LruCache<String, Map<String, String>>(maxBytes) {

            @Override
            protected int sizeOf(String key, Map<String, String> value) {
                long bytes = 0L;
                for (Map.Entry<String, String> entry : value.entrySet()) {
                    bytes += (entry.getKey().length() + entry.getValue().length()) * 2L;
                }
                return (int) Math.min(Integer.MAX_VALUE, bytes);
            }
        };
    }

    /** 当前工作区代数；每次失效递增 */
    public long generation(Workspace workspace) {
        return generationFor(CacheKeys.workspaceId(workspace));
    }

    /** 读取当前代数下的快照 */
    public Map<String, String> get(Workspace workspace, DecompilerTypeEnum engine,
                                   Map<String, String> options) {
        return get(workspace, engine, options, generation(workspace));
    }

    /** 读取指定代数下的快照；代数已变化时返回 null */
    public Map<String, String> get(Workspace workspace, DecompilerTypeEnum engine,
                                   Map<String, String> options, long expectedGeneration) {
        if (generation(workspace) != expectedGeneration) {
            return null;
        }
        return cache.get(key(workspace, engine, options, expectedGeneration));
    }

    /** 仅当代数未变时发布快照；返回是否发布 */
    public boolean putIfCurrent(Workspace workspace, DecompilerTypeEnum engine,
                                Map<String, String> options, long expectedGeneration,
                                Map<String, String> snapshot) {
        if (snapshot == null || generation(workspace) != expectedGeneration) {
            return false;
        }
        Map<String, String> immutable = Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
        cache.put(key(workspace, engine, options, expectedGeneration), immutable);
        return true;
    }

    /** 失效一个工作区的全部快照并递增其代数 */
    public void invalidateWorkspace(Workspace workspace) {
        String workspaceId = CacheKeys.workspaceId(workspace);
        generationRef(workspaceId).incrementAndGet();
        String prefix = workspaceId + "|";
        cache.snapshot().keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .forEach(cache::remove);
    }

    /** 失效全部快照；递增所有已知工作区代数，使在途写回失效 */
    public void invalidateAll() {
        generations.values().forEach(AtomicLong::incrementAndGet);
        cache.evictAll();
    }

    /** 清空快照但不改变代数（应用关闭时使用） */
    public void clear() {
        cache.evictAll();
    }

    public CacheStats stats() {
        return new CacheStats(STATS_NAME, cache.snapshot().size(), cache.size(),
                cache.hitCount(), cache.missCount());
    }

    private long generationFor(String workspaceId) {
        return generationRef(workspaceId).get();
    }

    private AtomicLong generationRef(String workspaceId) {
        return generations.computeIfAbsent(workspaceId, ignored -> new AtomicLong());
    }

    private String key(Workspace workspace, DecompilerTypeEnum engine,
                       Map<String, String> options, long generation) {
        return CacheKeys.workspaceId(workspace) + "|" + engine.name() + "|"
                + CacheKeys.optionsHash(engine, options) + "|" + generation;
    }
}
