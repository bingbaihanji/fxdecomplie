package com.bingbaihanji.fxdecomplie.core.jadx.core.clsp;

import java.util.Collections;
import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.AccessFlags;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IMethodDetails;

/**
 * Method details build from MethodInfo.
 * Note: some fields have unknown values.
 */
public class SimpleMethodDetails implements IMethodDetails {

	private final MethodInfo methodInfo;

	public SimpleMethodDetails(MethodInfo methodInfo) {
		this.methodInfo = methodInfo;
	}

	@Override
	public MethodInfo getMethodInfo() {
		return methodInfo;
	}

	@Override
	public ArgType getReturnType() {
		return methodInfo.getReturnType();
	}

	@Override
	public List<ArgType> getArgTypes() {
		return methodInfo.getArgumentsTypes();
	}

	@Override
	public List<ArgType> getTypeParameters() {
		return Collections.emptyList();
	}

	@Override
	public List<ArgType> getThrows() {
		return Collections.emptyList();
	}

	@Override
	public boolean isVarArg() {
		return false;
	}

	@Override
	public int getRawAccessFlags() {
		return AccessFlags.PUBLIC;
	}

	@Override
	public String toAttrString() {
		return IMethodDetails.super.toAttrString() + " (s)";
	}

	@Override
	public String toString() {
		return "SimpleMethodDetails{" + methodInfo + '}';
	}
}
