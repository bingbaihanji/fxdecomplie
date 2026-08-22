package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;

/**
 * 结构化反编译结果 — 替代字符串匹配判断.
 *
 * <p>缓存层只缓存 {@link Success},UI 层将 {@link Failure} 转为本地化字符串.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public sealed interface DecompileOutcome {

    /**
     * 将反编译器输出的源码字符串分类为结构化结果.
     *
     * <p>替代 {@code DecompilerRunner.isFailureOutput()} 等字符串匹配方法.
     * 引擎返回的源码文本若匹配已知的失败模式,则返回 {@link Failure};
     * 否则返回 {@link Success}.</p>
     *
     * @param source 反编译器输出的源码字符串
     * @param engine 使用的反编译引擎
     * @return 结构化结果
     */
    public static DecompileOutcome classify(String source, DecompilerTypeEnum engine) {
        if (source == null || source.isBlank()) {
            return new Failure(engine, FailureKind.ENGINE_ERROR, "空输出");
        }
        // 瞬时失败
        String lower = source.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("decompiler is busy") || lower.contains("反编译器正忙")) {
            return new Failure(engine, FailureKind.BUSY, "反编译器正忙");
        }
        if (lower.contains("decompilation timed out") || lower.contains("反编译超时")) {
            return new Failure(engine, FailureKind.TIMEOUT, "反编译超时");
        }
        // 永久失败
        String trimmed = source.trim();
        String kind = detectFailureKind(trimmed);
        if (kind != null) {
            return new Failure(engine, FailureKind.ENGINE_ERROR, kind);
        }
        // 未匹配到失败模式 → 成功
        return new Success(source);
    }

    /** 检测引擎级失败前缀 */
    private static String detectFailureKind(String trimmed) {
        String[] patterns = {
                "// Vineflower Error:", "// CFR Error:", "// Procyon Error:",
                "// JD-Core Error:", "// JD-Core decompile failed",
                "// jadx Error:", "// jadx decompile failed",
                "// CFR decompile failed", "// Procyon decompile failed",
                "// Vineflower decompile failed",
                "// Decompile failed", "// 反编译失败"
        };
        for (String p : patterns) {
            if (trimmed.startsWith(p)) {
                return p;
            }
        }
        return null;
    }

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isFailure() {
        return this instanceof Failure;
    }

    // ─── 静态工厂:从现有字符串结果分类 ───

    /** 反编译成功 */
    record Success(String source, CodeMetadata metadata) implements DecompileOutcome {

        public Success(String source) {
            this(source, null);
        }
    }

    /** 反编译失败 */
    record Failure(DecompilerTypeEnum engine, FailureKind kind,
                   String message, Throwable cause) implements DecompileOutcome {

        public Failure(DecompilerTypeEnum engine, FailureKind kind, String message) {
            this(engine, kind, message, null);
        }

        /** 生成便于日志的结构化诊断字符串 */
        public String diagnostic() {
            return String.format("engine=%s kind=%s message=%s", engine, kind, message);
        }
    }
}
