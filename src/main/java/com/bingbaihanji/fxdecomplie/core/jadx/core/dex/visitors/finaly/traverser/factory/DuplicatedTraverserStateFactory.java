package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.factory;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

public final class DuplicatedTraverserStateFactory<T extends TraverserState> extends TraverserStateFactory<T> {
	private final T baseState;

	public DuplicatedTraverserStateFactory(T baseState) {
		this.baseState = baseState;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T generateInternalState(TraverserActivePathState state) {
		Class<? extends T> baseStateClass = (Class<? extends T>) baseState.getClass();
		TraverserState duplicated = baseState.duplicate(state);
		if (!baseStateClass.isInstance(duplicated)) {
			throw new JadxRuntimeException(
					"A state of class " + baseState.getClass() + " has duplicated to produce a class of " + duplicated.getClass());
		}
		return baseStateClass.cast(duplicated);
	}

}
