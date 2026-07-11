package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.handlers;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.TraverserException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState;

import java.util.List;

public abstract class AbstractActivePathTraverserHandler extends AbstractBlockTraverserHandler {

    private final TraverserActivePathState comparatorState;

    public AbstractActivePathTraverserHandler(TraverserActivePathState comparatorState) {
        this.comparatorState = comparatorState;
    }

    protected abstract List<TraverserActivePathState> handle() throws TraverserException;

    public final List<TraverserActivePathState> process() throws TraverserException {
        return handle();
    }

    public final TraverserActivePathState getComparator() {
        return comparatorState;
    }
}
