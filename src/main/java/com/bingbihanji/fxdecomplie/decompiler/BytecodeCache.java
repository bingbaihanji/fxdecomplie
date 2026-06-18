package com.bingbihanji.fxdecomplie.decompiler;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 全局字节码缓存。在打开 JAR/ZIP 时预加载 class 字节码，供反编译器解析类型依赖时查找。
 * 最大 5000 条目，30 分钟过期，软引用值以允许 GC 回收。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BytecodeCache {

    /** 有界字节码缓存，internalName → byte[]，软引用值，最大 5000 条目，30 分钟 TTL */
    private static final Cache<String, byte[]> CACHE = Caffeine.newBuilder()
            .maximumSize(5_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .softValues()
            .build();

    private BytecodeCache() {
        throw new AssertionError("utility class");
    }

    public static void put(String internalName, byte[] bytes) {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(bytes, "bytes");
        CACHE.put(internalName, bytes);
    }

    public static byte[] get(String internalName) {
        Objects.requireNonNull(internalName, "internalName");
        return CACHE.getIfPresent(internalName);
    }

    public static boolean contains(String internalName) {
        return CACHE.getIfPresent(internalName) != null;
    }

    public static void clear() {
        CACHE.invalidateAll();
    }

    /** 遍历所有缓存条目（供继承分析等模块使用） */
    public static void forEach(BiConsumer<String, byte[]> action) {
        CACHE.asMap().forEach(action);
    }

    /** @return 缓存条目近似数 */
    public static long size() {
        return CACHE.estimatedSize();
    }
}
