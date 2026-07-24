package com.bingbaihanji.classgraph.scan;

import java.util.function.Supplier;

/**
 * 扫描日志接口 — 将扫描过程中的日志输出与具体日志实现解耦。
 *
 * <p>通过此接口，ClassGraph 的扫描代码不再直接依赖 SLF4J 或其他日志框架。
 * 默认实现委托给 {@code LogNode}（ClassGraph 现有日志机制）。</p>
 *
 * <p>使用示例：
 * <pre>{@code
 * ScanLogger logger = ScanLogger.loggingTo(logNode);
 * logger.debug("Scanning {} classpath elements", count);
 * logger.warn("Skipping duplicate class: {}", className);
 * }</pre>
 */
@FunctionalInterface
public interface ScanLogger {

    /** 记录一条日志消息 */
    void log(Level level, String message, Object... args);

    // ─── 便捷方法 ───

    default void debug(String message, Object... args) {
        log(Level.DEBUG, message, args);
    }

    default void info(String message, Object... args) {
        log(Level.INFO, message, args);
    }

    default void warn(String message, Object... args) {
        log(Level.WARN, message, args);
    }

    default void error(String message, Object... args) {
        log(Level.ERROR, message, args);
    }

    /** 延迟求值的 DEBUG 日志（仅当 DEBUG 级别启用时才求值） */
    default void debugLazy(Supplier<String> messageSupplier) {
        if (isEnabled(Level.DEBUG)) {
            log(Level.DEBUG, messageSupplier.get());
        }
    }

    default boolean isEnabled(Level level) {
        return level.ordinal() >= Level.INFO.ordinal();
    }

    // ─── 工厂方法 ───

    /** 创建委托给 LogNode 的日志记录器 */
    static ScanLogger loggingTo(com.bingbaihanji.classgraph.util.LogNode logNode) {
        return (level, message, args) -> {
            if (logNode != null) {
                String formatted = formatMessage(message, args);
                logNode.log(formatted);
            }
        };
    }

    /** 创建空操作的日志记录器（Null Object 模式） */
    static ScanLogger noOp() {
        return (level, message, args) -> {};
    }

    /** 创建委托给 SLF4J 的日志记录器 */
    static ScanLogger slf4j(org.slf4j.Logger slf4jLogger) {
        return (level, message, args) -> {
            if (slf4jLogger == null) return;
            String formatted = formatMessage(message, args);
            switch (level) {
                case DEBUG -> slf4jLogger.debug(formatted);
                case INFO  -> slf4jLogger.info(formatted);
                case WARN  -> slf4jLogger.warn(formatted);
                case ERROR -> slf4jLogger.error(formatted);
            }
        };
    }

    // ─── 辅助方法 ───

    private static String formatMessage(String template, Object... args) {
        if (args == null || args.length == 0) {
            return template;
        }
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{}", String.valueOf(args[i]));
        }
        return result;
    }

    /** 日志级别 */
    enum Level {
        DEBUG, INFO, WARN, ERROR
    }
}
