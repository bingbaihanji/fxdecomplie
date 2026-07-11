package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

import java.util.ArrayList;
import java.util.List;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils.isEmpty;

@JadxVisitor(
        name = "CheckCode",
        desc = "Check and remove bad or incorrect code"
)
public class CheckCode extends AbstractVisitor {

    @Override
    public void visit(MethodNode mth) throws JadxException {
        MethodInfo mthInfo = mth.methodInfo();
        if (mthInfo.getArgumentsTypes().size() > 255) {
            // java spec don't allow more than 255 args
            if (canRemoveMethod(mth)) {
                mth.ignoreMethod();
            } else {
                // TODO: convert args to array
            }
        }
        checkInstructions(mth);
    }

    private boolean canRemoveMethod(MethodNode mth) {
        if (mth.getUseIn().isEmpty()) {
            return true;
        }
        InsnNode[] insns = mth.getInstructions();
        if (insns.length == 0) {
            return true;
        }
        for (InsnNode insn : insns) {
            if (insn != null && insn.getType() != InsnType.NOP) {
                if (insn.getType() == InsnType.RETURN && insn.getArgsCount() == 0) {
                    // ignore void return
                } else {
                    // found useful instruction
                    return false;
                }
            }
        }
        return true;
    }

    public void checkInstructions(MethodNode mth) {
        if (isEmpty(mth.getInstructions())) {
            return;
        }
        int regsCount = mth.getRegsCount();
        List<RegisterArg> list = new ArrayList<>();
        for (InsnNode insnNode : mth.getInstructions()) {
            if (insnNode == null) {
                continue;
            }
            list.clear();
            RegisterArg resultArg = insnNode.getResult();
            if (resultArg != null) {
                list.add(resultArg);
            }
            insnNode.getRegisterArgs(list);
            for (RegisterArg arg : list) {
                int regNum = arg.getRegNum();
                if (regNum < 0) {
                    throw new JadxRuntimeException("Incorrect negative register number in instruction: " + insnNode);
                }
                if (regNum >= regsCount) {
                    throw new JadxRuntimeException("Incorrect register number in instruction: " + insnNode
                            + ", expected to be less than " + regsCount);
                }
            }
        }
    }
}
