package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.CentralityState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockPathTraverserHandler;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.handlers.PredecessorBlockPathTraverserHandler;
import org.jetbrains.annotations.Nullable;

public final class NoBlockTraverserState extends TraverserState implements ISourceBlockState {

    private final BlockNode sourceBlock;
    private final CentralityState centralityState;

    public NoBlockTraverserState(TraverserActivePathState state, CentralityState centralityState, BlockNode sourceBlock) {
        super(state);
        this.sourceBlock = sourceBlock;
        this.centralityState = centralityState;
    }

    public static TraverserStateFactory<NoBlockTraverserState> getFactory(CentralityState centralityState, BlockNode sourceBlock) {
        return new NoBlockStateFactory(centralityState, sourceBlock);
    }

    @Override
    public @Nullable AbstractBlockPathTraverserHandler getNextHandler() {
        return new PredecessorBlockPathTraverserHandler<>(this);
    }

    @Override
    public ComparisonState getCompareState() {
        return ComparisonState.NOT_READY;
    }

    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    protected @Nullable CentralityState getUnderlyingCentralityState() {
        return centralityState;
    }

    @Override
    protected @Nullable TraverserBlockInfo getUnderlyingBlockInsnInfo() {
        return null;
    }

    @Override
    public BlockNode getSourceBlock() {
        return sourceBlock;
    }

    @Override
    protected TraverserState duplicateInternalState(TraverserActivePathState comparatorState) {
        CentralityState dCentralityState = centralityState.duplicate();
        return new NoBlockTraverserState(comparatorState, dCentralityState, sourceBlock);
    }

    private static class NoBlockStateFactory extends TraverserStateFactory<NoBlockTraverserState> {
        private final CentralityState centralityState;
        private final BlockNode sourceBlock;

        public NoBlockStateFactory(CentralityState centralityState, BlockNode sourceBlock) {
            this.centralityState = centralityState;
            this.sourceBlock = sourceBlock;
        }

        @Override
        public NoBlockTraverserState generateInternalState(TraverserActivePathState state) {
            return new NoBlockTraverserState(state, centralityState, sourceBlock);
        }
    }
}
