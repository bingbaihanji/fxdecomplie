package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.loops;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;

public final class ForEachLoop extends LoopType {
    private final InsnNode varArgInsn;
    private final InsnNode iterableArgInsn;

    public ForEachLoop(RegisterArg varArg, InsnArg iterableArg) {
        // store for-each args in fake instructions to
        // save code semantics and allow args manipulations like args inlining
        varArgInsn = new InsnNode(InsnType.REGION_ARG, 0);
        varArgInsn.add(AFlag.DONT_INLINE);
        varArgInsn.setResult(varArg.duplicate());

        iterableArgInsn = new InsnNode(InsnType.REGION_ARG, 1);
        iterableArgInsn.add(AFlag.DONT_INLINE);
        iterableArgInsn.addArg(iterableArg.duplicate());

        // will be declared at codegen
        getVarArg().getSVar().getCodeVar().setDeclared(true);
    }

    public void injectFakeInsns(LoopRegion loopRegion) {
        loopRegion.getInfo().getPreHeader().getInstructions().add(iterableArgInsn);
        loopRegion.getHeader().getInstructions().add(0, varArgInsn);
    }

    public RegisterArg getVarArg() {
        return varArgInsn.getResult();
    }

    public InsnArg getIterableArg() {
        return iterableArgInsn.getArg(0);
    }
}
