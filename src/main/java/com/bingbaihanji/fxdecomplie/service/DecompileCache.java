package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheScope;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheStore;
import com.bingbaihanji.fxdecomplie.util.cache.LruCache;

import java.util.Optional;

/**
 * L2 反编译源码内存缓存,按字节大小限制(50MB 上限),加权 LRU 淘汰.
 * 实现 {@link CodeCacheStore} 以支持注入和测试替换.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class DecompileCache implements CodeCacheStore {

    private static final int MAX_CACHE_BYTES = 50_000_000; // 50 MB

    /** 缓存键分隔符(null 字符不可能出现在 JVM 内部名称中),用于各组件之间的精确匹配,避免前缀碰撞 */
    private static final String KEY_SEP = "\0";

    private final LruCache<String, String> cache = new LruCache<String, String>(MAX_CACHE_BYTES) {

        @Override
        protected int sizeOf(String key, String value) {
            return value.length() * 2;
        }
    };

    // ─── CodeCacheStore 实现 ───

    /** 构建缓存键,组合工作区标识 类内部名 引擎类型和选项哈希 */
    private static String cacheKey(String workspaceKey, String internalName,
                                   DecompilerTypeEnum engine, String optionsHash) {
        return workspaceKey + KEY_SEP + internalName + KEY_SEP + KEY_SEP
                + engine.name() + KEY_SEP + optionsHash;
    }

    @Override
    public Optional<String> load(CodeCacheKey key) {
        return Optional.ofNullable(cache.get(key.compositeKey()));
    }

    @Override
    public void save(CodeCacheKey key, String source) {
        cache.put(key.compositeKey(), source);
    }

    // ─── 向后兼容方法 ───

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
        switch (scope) {
            case CLASS -> {
                String prefix = key.invalidationPrefix();
                cache.snapshot().keySet().stream()
                        .filter(k -> k.startsWith(prefix))
                        .forEach(cache::remove);
            }
            case WORKSPACE -> {
                String prefix = key.workspacePrefix();
                cache.snapshot().keySet().stream()
                        .filter(k -> k.startsWith(prefix))
                        .forEach(cache::remove);
            }
            case ALL -> cache.evictAll();
        }
    }

    /** 从 L2 内存缓存中获取已反编译的源码 */
    public String get(String workspaceKey, String internalName,
                      DecompilerTypeEnum engine, String optionsHash) {
        return cache.get(cacheKey(workspaceKey, internalName, engine, optionsHash));
    }

    /** 将反编译源码存入 L2 内存缓存 */
    public void put(String workspaceKey, String internalName,
                    DecompilerTypeEnum engine, String optionsHash, String sourceCode) {
        cache.put(cacheKey(workspaceKey, internalName, engine, optionsHash), sourceCode);
    }

    /** 失效指定类的所有缓存条目 */
    public void invalidate(String workspaceKey, String internalName) {
        String prefix = workspaceKey + KEY_SEP + internalName + KEY_SEP + KEY_SEP;
        cache.snapshot().keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .forEach(cache::remove);
    }

    /** 清空所有内存缓存条目 */
    public void clear() {
        cache.evictAll();
    }

    /** @return 当前缓存的条目数量 */
    public int size() {
        return cache.size();
    }
}
