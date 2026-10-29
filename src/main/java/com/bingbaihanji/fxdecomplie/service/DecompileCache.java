package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheScope;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheStore;
import com.bingbaihanji.fxdecomplie.util.cache.LruCache;

import java.util.Objects;
import java.util.Optional;

/**
 * L2 反编译源码内存缓存,按字节大小限制,加权 LRU 淘汰.
 *
 * <p>{@link #sizeBytes()} 返回的是估算值:按 {@code value.length() * 2} 计算,
 * 不包含键、字符串对象头与缓存结构本身的开销.默认内存预算为
 * {@link CacheLimits#L2_BYTES},可通过 {@link #DecompileCache(int)} 覆盖.</p>
 *
 * <p>线程安全性继承自 {@link LruCache}:单个 get/put/remove 操作是同步的;
 * 前缀失效先获取快照再逐个删除,不会因并发写入抛出 ConcurrentModificationException.</p>
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class DecompileCache implements CodeCacheStore {

    private final LruCache<String, String> cache;

    /** 使用 {@link CacheLimits#L2_BYTES} 内存预算创建缓存 */
    public DecompileCache() {
        this((int) CacheLimits.L2_BYTES);
    }

    /**
     * @param maxBytes 内存预算,以加权字节数计(每个条目按 value.length() * 2),必须大于 0
     */
    public DecompileCache(int maxBytes) {
        this.cache = new LruCache<String, String>(maxBytes) {
            @Override
            protected int sizeOf(String key, String value) {
                return value.length() * 2;
            }
        };
    }

    @Override
    public Optional<String> load(CodeCacheKey key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(cache.get(key.compositeKey()));
    }

    /** 忽略 null 或空白源码,与 L3 的写入契约保持一致 */
    @Override
    public void save(CodeCacheKey key, String source) {
        Objects.requireNonNull(key, "key");
        if (source != null && !source.isBlank()) {
            cache.put(key.compositeKey(), source);
        }
    }

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(scope, "scope");
        switch (scope) {
            case CLASS -> removeByPrefix(key.invalidationPrefix());
            case WORKSPACE -> removeByPrefix(key.workspacePrefix());
            case ALL -> cache.evictAll();
        }
    }

    private void removeByPrefix(String prefix) {
        cache.snapshot().keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .forEach(cache::remove);
    }

    /** @return 当前缓存占用的加权字节数估算值(value.length() * 2,不含键和对象头开销) */
    public int sizeBytes() {
        return cache.size();
    }

    /** @return 命中次数 */
    public int hitCount() {
        return cache.hitCount();
    }

    /** @return 未命中次数 */
    public int missCount() {
        return cache.missCount();
    }

    /** @return 当前条目数（设置页统计，非热路径） */
    public int entryCount() {
        return cache.snapshot().size();
    }

    /** 清空所有内存条目 */
    public void clear() {
        cache.evictAll();
    }

    @Override
    public void clearAll() {
        clear();
    }
}
