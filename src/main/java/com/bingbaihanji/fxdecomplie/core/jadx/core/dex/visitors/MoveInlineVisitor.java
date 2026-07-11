package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.RegDebugInfoAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.shrink.CodeShrinkVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ssa.SSATransform;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnRemover;

import java.util.ArrayList;

@JadxVisitor(
        name = "MoveInlineVisitor",
        desc = "Inline redundant move instructions",
        runAfter = SSATransform.class,
        runBefore = CodeShrinkVisitor.class
)
public class MoveInlineVisitor extends AbstractVisitor {
    public static void moveInline(MethodNode mth) {
        InsnRemover remover = new InsnRemover(mth);
        for (BlockNode block : mth.getBasicBlocks()) {
            remover.setBlock(block);
            for (InsnNode insn : block.getInstructions()) {
                if (insn.getType() != InsnType.MOVE) {
                    continue;
                }
                if (processMove(mth, insn)) {
                    remover.addAndUnbind(insn);
                }
            }
            remover.perform();
        }
    }

    private static boolean processMove(MethodNode mth, InsnNode move) {
        RegisterArg resultArg = move.getResult();
        InsnArg moveArg = move.getArg(0);
        if (resultArg.sameRegAndSVar(moveArg)) {
            return true;
        }
        if (moveArg.isRegister()) {
            RegisterArg moveReg = (RegisterArg) moveArg;
            if (moveReg.getSVar().isAssignInPhi()) {
                // don't mix already merged variables
                return false;
            }
        }
        SSAVar ssaVar = resultArg.getSVar();
        if (ssaVar.getUseList().isEmpty()) {
            // unused result
            return true;
        }

        if (ssaVar.isUsedInPhi()) {
            return false;
            // TODO: review conditions of 'up' move inline (test TestMoveInline)
            // return deleteMove(mth, move);
        }
        RegDebugInfoAttr debugInfo = moveArg.get(AType.REG_DEBUG_INFO);
        for (RegisterArg useArg : ssaVar.getUseList()) {
            InsnNode useInsn = useArg.getParentInsn();
            if (useInsn == null) {
                return false;
            }
            if (debugInfo == null) {
                RegDebugInfoAttr debugInfoAttr = useArg.get(AType.REG_DEBUG_INFO);
                if (debugInfoAttr != null) {
                    debugInfo = debugInfoAttr;
                }
            }
        }

        // all checks passed, execute inline
        for (RegisterArg useArg : new ArrayList<>(ssaVar.getUseList())) {
            InsnNode useInsn = useArg.getParentInsn();
            if (useInsn == null) {
                continue;
            }
            InsnArg replaceArg;
            if (moveArg.isRegister()) {
                replaceArg = ((RegisterArg) moveArg).duplicate(useArg.getInitType());
            } else {
                replaceArg = moveArg.duplicate();
            }
            useInsn.inheritMetadata(move);
            replaceArg.copyAttributesFrom(useArg);
            if (debugInfo != null) {
                replaceArg.addAttr(debugInfo);
            }
            if (!useInsn.replaceArg(useArg, replaceArg)) {
                mth.addWarnComment("Failed to replace arg in insn: " + useInsn);
            }
        }
        return true;
    }

    private static boolean deleteMove(MethodNode mth, InsnNode move) {
        InsnArg moveArg = move.getArg(0);
        if (!moveArg.isRegister()) {
            return false;
        }
        RegisterArg moveReg = (RegisterArg) moveArg;
        SSAVar ssaVar = moveReg.getSVar();
        if (ssaVar.getUseCount() != 1 || ssaVar.isUsedInPhi()) {
            return false;
        }
        RegisterArg assignArg = ssaVar.getAssign();
        InsnNode parentInsn = assignArg.getParentInsn();
        if (parentInsn == null) {
            return false;
        }
        if (parentInsn.getSourceLine() != move.getSourceLine()
                || moveArg.contains(AType.REG_DEBUG_INFO)) {
            // preserve debug info
            return false;
        }
        // set move result into parent insn result
        InsnRemover.unbindAllArgs(mth, move);
        InsnRemover.unbindResult(mth, parentInsn);

        RegisterArg resArg = parentInsn.getResult();
        RegisterArg newResArg = move.getResult().duplicate(resArg.getInitType());
        newResArg.copyAttributesFrom(resArg);
        parentInsn.setResult(newResArg);
        return true;
    }

    @Override
    public void visit(MethodNode mth) {
        if (mth.isNoCode()) {
            return;
        }
        moveInline(mth);
    }
}
