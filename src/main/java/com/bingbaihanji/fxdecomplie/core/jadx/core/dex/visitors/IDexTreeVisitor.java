package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

/**
 * Visitor interface for traverse dex tree
 */
public interface IDexTreeVisitor {

	/**
	 * Visitor short id
	 */
	String getName();

	/**
	 * Called after loading dex tree, but before visitor traversal.
	 */
	void init(RootNode root) throws JadxException;

	/**
	 * Visit class
	 *
	 * @return false for disable child methods and inner classes traversal
	 */
	boolean visit(ClassNode cls) throws JadxException;

	/**
	 * Visit method
	 */
	void visit(MethodNode mth) throws JadxException;
}
