package com.bingbaihanji.fxdecomplie.decompiler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反编译引擎工厂每个引擎类型单例缓存
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class DecompilerFactory {

    /** 引擎单例缓存,按类型索引 */
    private static final ConcurrentHashMap<DecompilerTypeEnum, Decompiler> CACHE = new ConcurrentHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(DecompilerFactory.class);

    private DecompilerFactory() {
        throw new AssertionError("utility class");
    }

    /**
     * 获取指定类型的反编译引擎实例(单例缓存)
     * @param type 引擎类型
     * @return 引擎实例
     */
    public static Decompiler getDecompiler(DecompilerTypeEnum type) {
        Objects.requireNonNull(type, "type");
        return CACHE.computeIfAbsent(type, t -> {
            log.info("创建反编译引擎实例: {}", t);
            Decompiler engine = switch (t) {
                case PROCYON -> new ProcyonDecompiler();
                case CFR -> new CfrDecompiler();
                case VINEFLOWER -> new VineflowerDecompiler();
                case JD -> new JdDecompiler();
            };
            engine.initialize();
            log.info("反编译引擎初始化完成: {} ({})", t, engine.getName());
            return engine;
        });
    }

    /** 清理所有引擎实例,单个引擎清理失败不影响其余引擎 */
    public static void cleanup() {
        CACHE.values().forEach(engine -> {
            try {
                engine.cleanup();
            } catch (Exception e) {
                log.warn("清理反编译引擎失败: {}", engine.getType(), e);
            }
        });
        CACHE.clear();
    }
}
