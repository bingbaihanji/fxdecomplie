package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.factory;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserState;

public abstract class TraverserStateFactory<T extends TraverserState> {

    protected abstract T generateInternalState(TraverserActivePathState state);

    public final T generateState(TraverserActivePathState state) {
        return generateInternalState(state);
    }
}
