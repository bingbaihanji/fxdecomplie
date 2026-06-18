package com.bingbihanji.fxdecomplie.decompiler;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 反编译引擎工厂。每个引擎类型单例缓存。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class DecompilerFactory {

    /** 引擎单例缓存，按类型索引 */
    private static final ConcurrentHashMap<DecompilerTypeEnum, Decompiler> CACHE = new ConcurrentHashMap<>();

    private DecompilerFactory() {
        throw new AssertionError("utility class");
    }

    /**
     * 获取指定类型的反编译引擎实例（单例缓存）。
     * @param type 引擎类型
     * @return 引擎实例
     */
    public static Decompiler getDecompiler(DecompilerTypeEnum type) {
        return CACHE.computeIfAbsent(type, t -> {
            Decompiler engine = switch (t) {
                case PROCYON -> new ProcyonDecompiler();
                case CFR -> new CfrDecompiler();
                case VINEFLOWER -> new VineflowerDecompiler();
                case JD -> new JdDecompiler();
                default -> throw new IllegalStateException("Unknown engine: " + t);
            };
            engine.initialize();
            return engine;
        });
    }

    /** 清理所有引擎实例 */
    public static void cleanup() {
        CACHE.values().forEach(Decompiler::cleanup);
        CACHE.clear();
    }
}
