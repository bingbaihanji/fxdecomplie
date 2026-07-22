package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.util.cache.LruCache;

/**
 * L2 反编译源码内存缓存，按字节大小限制(50MB 上限)，加权 LRU 淘汰
 * 缓存键 = workspaceKey + internalName + engine + optionsHash
 * 引擎切换或反编译选项变更时自动失效
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class DecompileCache {

    private static final int MAX_CACHE_BYTES = 50_000_000; // 50 MB
    /** 缓存键分隔符(null 字符不可能出现在 JVM 内部名称中),用于各组件之间的精确匹配,避免前缀碰撞 */
    private static final String KEY_SEP = "\0";
    private final LruCache<String, String> cache = new LruCache<String, String>(MAX_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, String value) {
            // 近似按 char 计数 (Latin-1 或 UTF-16, 最坏情况 2 bytes/char)
            return value.length() * 2;
        }
    };

    /** 构建缓存键,组合工作区标识 类内部名 引擎类型和选项哈希 */
    private static String cacheKey(String workspaceKey, String internalName,
                                   DecompilerTypeEnum engine, String optionsHash) {
        return workspaceKey + KEY_SEP + internalName + KEY_SEP + KEY_SEP
                + engine.name() + KEY_SEP + optionsHash;
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

    /**
     * 失效指定类的所有缓存条目
     * 使用双分隔符避免前缀碰撞(如 "com/Fo" 误匹配 "com/Foo")
     */
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
