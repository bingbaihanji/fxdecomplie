package com.bingbihanji.fxdecomplie.decompiler;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局字节码缓存。在打开 JAR/ZIP 时预加载 class 字节码，供反编译器解析类型依赖时查找。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BytecodeCache {

    private BytecodeCache() {
        throw new AssertionError("utility class");
    }

    /** 全局字节码缓存（internalName → byte[]） */
    private static final ConcurrentHashMap<String, byte[]> CACHE = new ConcurrentHashMap<>();

    /**
     * 缓存字节码。
     * @param internalName 内部类型名（如 "com/example/Main"）
     * @param bytes        字节码
     */
    public static void put(String internalName, byte[] bytes) {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(bytes, "bytes");
        CACHE.put(internalName, bytes);
    }

    /**
     * 获取缓存的字节码。
     * @param internalName 内部类型名
     * @return 字节码，可能为 null
     */
    public static byte[] get(String internalName) {
        Objects.requireNonNull(internalName, "internalName");
        return CACHE.get(internalName);
    }

    /** @return 是否已缓存指定类型 */
    public static boolean contains(String internalName) {
        return CACHE.containsKey(internalName);
    }

    /** 清空缓存 */
    public static void clear() {
        CACHE.clear();
    }

    /** 遍历所有缓存条目（供继承分析等模块使用） */
    public static void forEach(java.util.function.BiConsumer<String, byte[]> action) {
        CACHE.forEach(action);
    }

    /** @return 缓存条目数 */
    public static int size() {
        return CACHE.size();
    }
}
