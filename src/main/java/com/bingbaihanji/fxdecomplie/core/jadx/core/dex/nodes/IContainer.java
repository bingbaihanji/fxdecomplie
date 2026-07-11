package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.RegionGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.IAttributeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.CodegenException;

public interface IContainer extends IAttributeNode {

	/**
	 * Unique id for use in 'toString()' method
	 */
	String baseString();

	/**
	 * Dispatch to needed generate method in RegionGen
	 */
	default void generate(RegionGen regionGen, ICodeWriter code) throws CodegenException {
		throw new CodegenException("Code generate not implemented for container: " + getClass().getSimpleName());
	}
}
