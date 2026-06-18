package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L2 反编译源码内存缓存。key = workspaceKey + internalName + engine + optionsHash。
 * 引擎切换或反编译选项变更时自动失效。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class DecompileCache {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

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
