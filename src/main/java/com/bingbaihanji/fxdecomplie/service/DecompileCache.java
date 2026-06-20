package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * L2 反编译源码内存缓存key = workspaceKey + internalName + engine + optionsHash
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

    private static String cacheKey(String workspaceKey, String internalName,
                                   DecompilerTypeEnum engine, String optionsHash) {
        return workspaceKey + "#" + internalName + "#" + engine.name() + "#" + optionsHash;
    }

    public String get(String workspaceKey, String internalName,
                      DecompilerTypeEnum engine, String optionsHash) {
        return cache.get(cacheKey(workspaceKey, internalName, engine, optionsHash));
    }

    public void put(String workspaceKey, String internalName,
                    DecompilerTypeEnum engine, String optionsHash, String sourceCode) {
        cache.put(cacheKey(workspaceKey, internalName, engine, optionsHash), sourceCode);
    }

    public void invalidate(String workspaceKey, String internalName) {
        cache.keySet().removeIf(k -> k.startsWith(workspaceKey + "#" + internalName + "#"));
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
