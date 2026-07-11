package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;

public interface ITypeConstraint {

	List<SSAVar> getRelatedVars();

	boolean check(TypeSearchState state);
}
