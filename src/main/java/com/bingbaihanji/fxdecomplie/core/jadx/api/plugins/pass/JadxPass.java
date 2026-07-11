package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types.JadxPassType;

/**
 * Jadx 处理通道接口。
 * <p>
 * 所有处理通道（如 {@code PreparePass}、{@code DecompilePass}）
 * 均需实现此接口，以提供通道信息和通道类型。
 */
public interface JadxPass {
    /**
     * 获取该通道的元信息（名称、描述等）。
     *
     * @return 通道信息对象
     */
    JadxPassInfo getInfo();

    /**
     * 获取该通道的类型标识。
     *
     * @return 通道类型
     */
    JadxPassType getPassType();
}
