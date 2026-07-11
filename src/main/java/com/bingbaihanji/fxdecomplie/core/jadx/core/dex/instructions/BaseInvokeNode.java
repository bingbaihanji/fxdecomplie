package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import org.jetbrains.annotations.Nullable;

public abstract class BaseInvokeNode extends InsnNode {
    public BaseInvokeNode(InsnType type, int argsCount) {
        super(type, argsCount);
    }

    public abstract MethodInfo getCallMth();

    @Nullable
    public abstract InsnArg getInstanceArg();

    public abstract boolean isStaticCall();

    /**
     * Return offset to match method args from {@link #getCallMth()}
     */
    public abstract int getFirstArgOffset();
}
