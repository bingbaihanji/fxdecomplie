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
    private final Map<String, String> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    /** 缓存键分隔符，用于各组件之间的精确匹配，避免前缀碰撞 */
    private static final char KEY_SEP = '#';

    private static String cacheKey(String workspaceKey, String internalName,
                                   DecompilerTypeEnum engine, String optionsHash) {
        return workspaceKey + KEY_SEP + internalName + KEY_SEP + KEY_SEP
                + engine.name() + KEY_SEP + optionsHash;
    }

    public String get(String workspaceKey, String internalName,
                      DecompilerTypeEnum engine, String optionsHash) {
        return cache.get(cacheKey(workspaceKey, internalName, engine, optionsHash));
    }

    public void put(String workspaceKey, String internalName,
                    DecompilerTypeEnum engine, String optionsHash, String sourceCode) {
        cache.put(cacheKey(workspaceKey, internalName, engine, optionsHash), sourceCode);
    }

    /**
     * 失效指定类的所有缓存条目。
     * 使用双分隔符避免前缀碰撞（如 "com/Fo" 误匹配 "com/Foo"）。
     */
    public void invalidate(String workspaceKey, String internalName) {
        String prefix = workspaceKey + KEY_SEP + internalName + KEY_SEP + KEY_SEP;
        synchronized (cache) {
            cache.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
