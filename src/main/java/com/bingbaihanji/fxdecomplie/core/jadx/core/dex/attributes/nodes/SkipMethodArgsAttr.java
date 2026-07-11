package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.PinnedAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

public class SkipMethodArgsAttr extends PinnedAttribute {

    private final BitSet skipArgs;

    private SkipMethodArgsAttr(MethodNode mth) {
        this.skipArgs = new BitSet(mth.methodInfo().getArgsCount());
    }

    public static void skipArg(MethodNode mth, RegisterArg arg) {
        int argNum = Utils.indexInListByRef(mth.getArgRegs(), arg);
        if (argNum == -1) {
            throw new JadxRuntimeException("Arg not found: " + arg);
        }
        skipArg(mth, argNum);
    }

    public static void skipArg(MethodNode mth, int argNum) {
        SkipMethodArgsAttr attr = mth.get(AType.SKIP_MTH_ARGS);
        if (attr == null) {
            attr = new SkipMethodArgsAttr(mth);
            mth.addAttr(attr);
        }
        attr.skip(argNum);
    }

    public static boolean isSkip(@Nullable MethodNode mth, int argNum) {
        if (mth == null) {
            return false;
        }
        if (argNum == 0 && mth.contains(AFlag.SKIP_FIRST_ARG)) {
            return true;
        }
        SkipMethodArgsAttr attr = mth.get(AType.SKIP_MTH_ARGS);
        if (attr == null) {
            return false;
        }
        return attr.isSkip(argNum);
    }

    public void skip(int argNum) {
        skipArgs.set(argNum);
    }

    public boolean isSkip(int argNum) {
        return skipArgs.get(argNum);
    }

    public int getSkipCount() {
        return skipArgs.cardinality();
    }

    @Override
    public AType<SkipMethodArgsAttr> getAttrType() {
        return AType.SKIP_MTH_ARGS;
    }

    @Override
    public String toString() {
        return "SKIP_MTH_ARGS: " + skipArgs;
    }
}
