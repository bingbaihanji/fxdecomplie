package com.bingbihanji.fxdecomplie.decompiler;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 反编译引擎工厂。每个引擎类型单例缓存。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class DecompilerFactory {

    /** 引擎单例缓存，按类型索引 */
    private static final ConcurrentHashMap<DecompilerTypeEnum, Decompiler> CACHE = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(DecompilerFactory.class);

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

    /** 清理所有引擎实例，单个引擎清理失败不影响其余引擎 */
    public static void cleanup() {
        CACHE.values().forEach(engine -> {
            try {
                engine.cleanup();
            } catch (Exception e) {
                logger.warn("Failed to cleanup decompiler engine: {}", engine.getType(), e);
            }
        });
        CACHE.clear();
    }
}
