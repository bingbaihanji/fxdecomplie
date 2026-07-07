package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * L2 反编译源码内存缓存,缓存键 = workspaceKey + internalName + engine + optionsHash
 * 引擎切换或反编译选项变更时自动失效
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class DecompileCache {

    private static final int MAX_CACHE_SIZE = 1_000;
    /** 缓存键分隔符,用于各组件之间的精确匹配,避免前缀碰撞 */
    private static final char KEY_SEP = '#';
    private final Map<String, String> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    /** 构建缓存键,组合工作区标识、类内部名、引擎类型和选项哈希 */
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
     * 使用双分隔符避免前缀碰撞（如 "com/Fo" 误匹配 "com/Foo"）
     */
    public void invalidate(String workspaceKey, String internalName) {
        String prefix = workspaceKey + KEY_SEP + internalName + KEY_SEP + KEY_SEP;
        synchronized (cache) {
            cache.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    /** 清空所有内存缓存条目 */
    public void clear() {
        cache.clear();
    }

    /** @return 当前缓存的条目数量 */
    public int size() {
        return cache.size();
    }
}
