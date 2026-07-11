package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IBlock;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;

public interface IRegionVisitor {

	void processBlock(MethodNode mth, IBlock container);

	/**
	 * @return true for traverse sub-blocks, false otherwise.
	 */
	boolean enterRegion(MethodNode mth, IRegion region);

	void leaveRegion(MethodNode mth, IRegion region);
}
