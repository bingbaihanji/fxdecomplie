package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.shrink;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;

final class WrapInfo {
    private final InsnNode insn;
    private final RegisterArg arg;

    WrapInfo(InsnNode assignInsn, RegisterArg arg) {
        this.insn = assignInsn;
        this.arg = arg;
    }

    InsnNode getInsn() {
        return insn;
    }

    RegisterArg getArg() {
        return arg;
    }

    @Override
    public String toString() {
        return "WrapInfo: " + arg + " -> " + insn;
    }
}
