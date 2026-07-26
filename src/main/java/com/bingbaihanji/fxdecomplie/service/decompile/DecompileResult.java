package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompileOutcome;

/**
 * 反编译结果 — 包含 requestId 用于过期丢弃，generation 用于抵消竞态。
 */
public record DecompileResult(
        DecompileOutcome outcome,
        String requestId,
        long generation,
        long elapsedMs) {

    public boolean isSuccess() {
        return outcome != null && outcome.isSuccess();
    }
}
