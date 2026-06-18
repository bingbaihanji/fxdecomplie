package com.bingbihanji.fxdecomplie.cache;

import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * L2 反编译源码内存缓存。key = internalName + engine + optionsHash。
 * 引擎切换或反编译选项变更时自动失效。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class DecompileCache {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String get(String internalName, DecompilerTypeEnum engine, String optionsHash) {
        return cache.get(cacheKey(internalName, engine, optionsHash));
    }

    public void put(String internalName, DecompilerTypeEnum engine, String optionsHash, String sourceCode) {
        cache.put(cacheKey(internalName, engine, optionsHash), sourceCode);
    }

    public void invalidate(String internalName) {
        cache.keySet().removeIf(k -> k.startsWith(internalName + "#"));
    }

    public void clear() { cache.clear(); }

    public int size() { return cache.size(); }

    private static String cacheKey(String internalName, DecompilerTypeEnum engine, String optionsHash) {
        return internalName + "#" + engine.name() + "#" + optionsHash;
    }
}
