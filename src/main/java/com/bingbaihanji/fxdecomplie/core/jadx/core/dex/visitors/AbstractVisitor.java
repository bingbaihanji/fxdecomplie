package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

public abstract class AbstractVisitor implements IDexTreeVisitor {

	@Override
	public void init(RootNode root) throws JadxException {
		// no op implementation
	}

	@Override
	public boolean visit(ClassNode cls) throws JadxException {
		// no op implementation
		return true;
	}

	@Override
	public void visit(MethodNode mth) throws JadxException {
		// no op implementation
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public String toString() {
		return getName();
	}
}
