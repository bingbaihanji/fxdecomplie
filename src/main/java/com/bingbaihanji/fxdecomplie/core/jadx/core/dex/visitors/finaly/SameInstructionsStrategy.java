package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;

public abstract class SameInstructionsStrategy {

	public abstract boolean sameInsns(InsnNode dupInsn, InsnNode fInsn);

	public abstract boolean isSameArgs(InsnArg dupArg, InsnArg fArg);
}
