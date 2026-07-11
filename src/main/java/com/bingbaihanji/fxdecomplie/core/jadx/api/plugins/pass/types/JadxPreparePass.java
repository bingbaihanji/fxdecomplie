package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

/**
 * 准备处理通道接口。
 * <p>
 * 在反编译流程开始前执行，用于对根节点进行预处理和初始化准备工作。
 */
public interface JadxPreparePass extends JadxPass {
    JadxPassType TYPE = new JadxPassType("PreparePass");

    /**
     * 初始化通道，传入根节点以获取全局上下文。
     *
     * @param root 根节点
     */
    void init(RootNode root);

    @Override
    default JadxPassType getPassType() {
        return TYPE;
    }
}
