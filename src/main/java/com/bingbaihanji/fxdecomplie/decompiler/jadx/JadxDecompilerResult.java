package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import org.jetbrains.annotations.Nullable;

/**
 * jadx 结构化反编译结果
 *
 * @param source     反编译源码（成功时非空）
 * @param status     结果状态
 * @param diagnostic 诊断信息（失败时非空）
 * @author bingbaihanji
 */
public record JadxDecompilerResult(
        @Nullable String source,
        JadxResultStatus status,
        @Nullable JadxDiagnostic diagnostic
) {
    /** 是否反编译成功 */
    public boolean isSuccess() {
        return status == JadxResultStatus.OK && source != null && !source.isBlank();
    }
}
