package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.IndexInsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import org.jetbrains.annotations.Nullable;

/**
 * Allow ignoring down casts (return arg type instead cast type)
 * Such casts will be removed later.
 */
public final class TypeBoundCheckCastAssign implements ITypeBoundDynamic {
    private final RootNode root;
    private final IndexInsnNode insn;

    public TypeBoundCheckCastAssign(RootNode root, IndexInsnNode insn) {
        this.root = root;
        this.insn = insn;
    }

    @Override
    public BoundEnum getBound() {
        return BoundEnum.ASSIGN;
    }

    @Override
    public ArgType getType(TypeUpdateInfo updateInfo) {
        return getReturnType(updateInfo.getType(insn.getArg(0)));
    }

    @Override
    public ArgType getType() {
        return getReturnType(insn.getArg(0).getType());
    }

    private ArgType getReturnType(ArgType argType) {
        ArgType castType = insn.getIndexAsType();
        TypeCompareEnum result = root.getTypeCompare().compareTypes(argType, castType);
        return result.isNarrow() ? argType : castType;
    }

    @Override
    public @Nullable RegisterArg getArg() {
        return insn.getResult();
    }

    public IndexInsnNode getInsn() {
        return insn;
    }

    @Override
    public String toString() {
        return "CHECK_CAST_ASSIGN{(" + insn.getIndex() + ") " + insn.getArg(0).getType() + "}";
    }
}
