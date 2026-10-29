package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheScope;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheStore;
import com.bingbaihanji.fxdecomplie.util.cache.LruCache;

import java.util.Optional;

/**
 * L2 反编译源码内存缓存,按字节大小限制,加权 LRU 淘汰.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class DecompileCache implements CodeCacheStore {

    /** 旧版缓存键分隔符(null 字符不可能出现在 JVM 内部名称中),用于各组件之间的精确匹配,避免前缀碰撞 */
    private static final String KEY_SEP = "\0";

    private final LruCache<String, String> cache;

    public DecompileCache() {
        this(50_000_000);
    }

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
        return Optional.ofNullable(cache.get(key.compositeKey()));
    }

    @Override
    public void save(CodeCacheKey key, String source) {
        if (source != null) {
            cache.put(key.compositeKey(), source);
        }
    }

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
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

    /** @return 当前缓存占用的加权字节数 */
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

    // ─── 向后兼容方法(旧字符串键,待调用方迁移到 CodeCacheStore API 后删除) ───

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
        String prefix = workspaceKey + KEY_SEP + internalName + KEY_SEP + KEY_SEP;
        cache.snapshot().keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .forEach(cache::remove);
    }

    /**
     * @return 当前缓存占用的加权字节数(历史命名)
     * @deprecated 使用 {@link #sizeBytes()} 代替
     */
    @Deprecated(forRemoval = true)
    public int size() {
        return cache.size();
    }
}
