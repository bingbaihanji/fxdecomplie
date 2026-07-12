package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import org.jetbrains.annotations.Nullable;

/**
 * jadx 反编译诊断信息
 *
 * @param status   结果状态
 * @param message  诊断消息
 * @param className 关联类名（可为 null）
 * @param elapsedMs 反编译耗时（毫秒）
 * @author bingbaihanji
 */
public record JadxDiagnostic(
        JadxResultStatus status,
        String message,
        @Nullable String className,
        long elapsedMs
) {
    public JadxDiagnostic {
        message = message == null ? "" : message;
    }
}
