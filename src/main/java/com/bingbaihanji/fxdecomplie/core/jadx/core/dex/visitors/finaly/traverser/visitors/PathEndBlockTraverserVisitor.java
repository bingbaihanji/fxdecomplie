package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.CentralityState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.AwaitingInsnCompareTraverserState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.NoBlockTraverserState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserState;

import java.util.List;
import java.util.ListIterator;

public final class PathEndBlockTraverserVisitor extends AbstractBlockTraverserVisitor {

    public PathEndBlockTraverserVisitor(TraverserState state) {
        super(state);
    }

    public static boolean isInstructionPathEnd(InsnNode insn) {
        InsnType type = insn.getType();

        switch (type) {
            case RETURN:
            case THROW:
                return true;
            default:
                return false;
        }
    }

    @Override
    public TraverserState visit(BlockNode block) {
        CentralityState centralityState = getState().getCentralityState();
        TraverserBlockInfo insnInfo = getState().getBlockInsnInfo();
        if (!centralityState.getAllowsCentral()) {
            return new AwaitingInsnCompareTraverserState(getComparator(), centralityState, insnInfo);
        }
        List<InsnNode> insns = insnInfo.getInsnsSlice();
        ListIterator<InsnNode> insnsIterator = insns.listIterator(insns.size());

        // The number of instructions that have been identified as "path end" instructions.
        int bottomDelta = 0;
        while (insnsIterator.hasPrevious()) {
            InsnNode insn = insnsIterator.previous();

            // Check if we should ignore the instruction due to it being a "path end" instruction.
            if (isInstructionPathEnd(insn)) {
                // This instruction is a path end instruction - this instruction causes the handler to exit. Here,
                // we will check the argument
                // of the path end instruction. If the instruction is a THROW or RETURN, this will indicate the
                // argument, so long as it exists
                // and is a register argument, which is operated upon before exiting this scope. Thus, we will mark
                // this argument as an allowable
                // path end instruction so long as an instruction returns this argument.
                //
                // Example:
                // CONST_STR r2 = "return this string" <-- A path end instruction since it sets an arg which is used
                // by path end insn
                // RETURN r2 <-- A path end instruction

                if (insn.getArgsCount() != 0) {
                    InsnArg handlerExitArg = insn.getArg(0);
                    // Returned values from instructions can only be register args so we check that the input to the
                    // path end insn is a register arg
                    if (handlerExitArg instanceof RegisterArg) {
                        centralityState.addAllowableOutput((RegisterArg) handlerExitArg);
                    }
                }

                bottomDelta++;
                // If this instruction is not a path end instruction, check if it sets or invokes a value which is
                // used by a path end instruction.
            } else if (centralityState.hasAllowableOutput(insn)) {
                bottomDelta++;
                centralityState.addAllowableOutputs(insn);
            } else {
                break;
            }
        }

        insnInfo.setBottomOffset(insnInfo.getBottomOffset() + bottomDelta);

        BlockNode sourceBlock = insnInfo.getBlock();
        boolean noInstructionsLeft = insnInfo.getBottomOffset() >= sourceBlock.getInstructions().size();
        if (noInstructionsLeft) {
            // Mark the state to request finding predecessors to search for duplicate instructions for
            return new NoBlockTraverserState(getComparator(), centralityState, sourceBlock);
        } else {
            // Mark the current state to await comparing of instructions
            return new AwaitingInsnCompareTraverserState(getComparator(), centralityState, insnInfo);
        }
    }
}
