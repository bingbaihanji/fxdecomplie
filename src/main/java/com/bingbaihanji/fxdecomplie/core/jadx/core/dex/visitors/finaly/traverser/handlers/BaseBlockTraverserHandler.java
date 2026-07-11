package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.handlers;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.visitors.ImplicitInsnBlockTraverserVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.visitors.PathEndBlockTraverserVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

import java.util.concurrent.atomic.AtomicReference;

public class BaseBlockTraverserHandler extends AbstractBlockPathTraverserHandler {

    public BaseBlockTraverserHandler(TraverserState initialState) {
        super(initialState);
    }

    public BaseBlockTraverserHandler(AtomicReference<TraverserState> initialStateRef) {
        super(initialStateRef);
    }

    @Override
    protected void handle() {
        TraverserBlockInfo blockInsnInfo = getState().getBlockInsnInfo();
        if (blockInsnInfo == null) {
            throw new JadxRuntimeException("Expected to find block info within " + getClass().getSimpleName());
        }
        TraverserActivePathState comparator = getState().getComparatorState();
        AtomicReference<TraverserState> stateRef = comparator.getReferenceForState(getState());
        if (stateRef == null) {
            throw new JadxRuntimeException("Orphaned traverser state");
        }
        BlockNode block = blockInsnInfo.getBlock();
        ImplicitInsnBlockTraverserVisitor implicitVisitor = new ImplicitInsnBlockTraverserVisitor(getState());
        TraverserState stateAfterImplicit = implicitVisitor.visit(block);
        PathEndBlockTraverserVisitor pathEndVisitor = new PathEndBlockTraverserVisitor(stateAfterImplicit);
        TraverserState nextState = pathEndVisitor.visit(block);

        stateRef.set(nextState);
    }
}
