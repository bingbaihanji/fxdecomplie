package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;

/**
 * 结构化反编译结果 — 替代字符串匹配判断。
 *
 * <p>缓存层只缓存 {@link Success}，UI 层将 {@link Failure} 转为本地化字符串。</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public sealed interface DecompileOutcome {

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

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isFailure() {
        return this instanceof Failure;
    }
}
