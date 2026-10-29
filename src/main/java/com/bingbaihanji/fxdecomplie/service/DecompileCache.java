package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
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
 * 不包含键、字符串对象头与缓存结构本身的开销.默认内存预算为 50 MB
 * (常量 {@code DEFAULT_MAX_BYTES}),可通过 {@link #DecompileCache(int)} 覆盖.</p>
 *
 * <p>线程安全性继承自 {@link LruCache}:单个 get/put/remove 操作是同步的;
 * 前缀失效先获取快照再逐个删除,不会因并发写入抛出 ConcurrentModificationException.</p>
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class DecompileCache implements CodeCacheStore {

    /** 默认内存预算:50 MB */
    private static final int DEFAULT_MAX_BYTES = 50_000_000;

    /** 旧版缓存键分隔符(null 字符不可能出现在 JVM 内部名称中),用于各组件之间的精确匹配,避免前缀碰撞 */
    private static final String KEY_SEP = "\0";

    private final LruCache<String, String> cache;

    /** 使用默认 50 MB 内存预算创建缓存 */
    public DecompileCache() {
        this(DEFAULT_MAX_BYTES);
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

    /** 清空所有内存条目 */
    public void clear() {
        cache.evictAll();
    }

    // TODO(Task 4): 接线完成后删除旧字符串键 API
    // ─── 向后兼容方法(旧字符串键) ───

    /** 构建旧版缓存键,组合工作区标识 类内部名 引擎类型和选项哈希 */
    private static String cacheKey(String workspaceKey, String internalName,
                                   DecompilerTypeEnum engine, String optionsHash) {
        return workspaceKey + KEY_SEP + internalName + KEY_SEP + KEY_SEP
                + engine.name() + KEY_SEP + optionsHash;
    }

    /**
     * 从 L2 内存缓存中获取已反编译的源码.
     *
     * @deprecated 使用 {@link #load(CodeCacheKey)} 代替;旧键格式与新键格式互不相通
     */
    @Deprecated(forRemoval = true)
    public String get(String workspaceKey, String internalName,
                      DecompilerTypeEnum engine, String optionsHash) {
        return cache.get(cacheKey(workspaceKey, internalName, engine, optionsHash));
    }

    /**
     * 将反编译源码存入 L2 内存缓存.
     *
     * @deprecated 使用 {@link #save(CodeCacheKey, String)} 代替;旧键格式与新键格式互不相通
     */
    @Deprecated(forRemoval = true)
    public void put(String workspaceKey, String internalName,
                    DecompilerTypeEnum engine, String optionsHash, String sourceCode) {
        cache.put(cacheKey(workspaceKey, internalName, engine, optionsHash), sourceCode);
    }

    /**
     * 失效指定类的所有旧键缓存条目.
     *
     * @deprecated 使用 {@link #invalidate(CodeCacheKey, CodeCacheScope)} 代替
     */
    @Deprecated(forRemoval = true)
    public void invalidate(String workspaceKey, String internalName) {
        removeByPrefix(workspaceKey + KEY_SEP + internalName + KEY_SEP + KEY_SEP);
    }
}
