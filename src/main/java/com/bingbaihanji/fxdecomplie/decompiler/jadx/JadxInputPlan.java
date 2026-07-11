package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.ICodeLoader;

/**
 * 已构建的 jadx 输入集合。
 */
public record JadxInputPlan(
        ICodeLoader codeLoader,
        String targetType,
        int totalClasses,
        int dependencyClasses
) {
}
