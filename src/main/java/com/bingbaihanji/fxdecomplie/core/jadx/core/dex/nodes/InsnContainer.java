package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AttrNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight replacement for BlockNode in regions.
 * Use with caution! Some passes still expect BlockNode in method blocks list (mth.getBlockNodes())
 */
public final class InsnContainer extends AttrNode implements IBlock {

    private final List<InsnNode> insns;

    public InsnContainer(InsnNode insn) {
        List<InsnNode> list = new ArrayList<>(1);
        list.add(insn);
        this.insns = list;
    }

    public InsnContainer(List<InsnNode> insns) {
        this.insns = insns;
    }

    @Override
    public List<InsnNode> getInstructions() {
        return insns;
    }

    @Override
    public String baseString() {
        return "IC";
    }

    @Override
    public String toString() {
        return "InsnContainer";
    }
}
