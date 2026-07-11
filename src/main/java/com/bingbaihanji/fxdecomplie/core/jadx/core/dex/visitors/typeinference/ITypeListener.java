package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface ITypeListener {

    /**
     * Listener function - triggered on type update
     *
     * @param updateInfo    store all allowed type updates
     * @param arg           apply suggested type for this arg
     * @param candidateType suggest new type
     */
    @Nullable
    TypeUpdateResult update(TypeUpdateInfo updateInfo, InsnNode insn, InsnArg arg, @NotNull ArgType candidateType);
}
