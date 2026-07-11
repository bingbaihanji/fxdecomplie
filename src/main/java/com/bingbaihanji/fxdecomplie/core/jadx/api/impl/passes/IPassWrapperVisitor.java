package com.bingbaihanji.fxdecomplie.core.jadx.api.impl.passes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.IDexTreeVisitor;

/**
 * Pass 包装访问器接口
 * <p>
 * 将 {@link JadxPass} 适配为 DEX 树访问器（{@link IDexTreeVisitor}），
 * 使得插件提供的 Pass 能够接入 jadx 的访问器执行流程，
 * 同时可以通过 {@link #getPass()} 获取被包装的原始 Pass
 */
public interface IPassWrapperVisitor extends IDexTreeVisitor {

    /**
     * 获取被包装的 Pass
     *
     * @return 被此访问器包装的 {@link JadxPass} 实例
     */
    JadxPass getPass();
}
