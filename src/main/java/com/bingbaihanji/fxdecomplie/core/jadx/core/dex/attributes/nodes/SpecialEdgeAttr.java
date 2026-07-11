package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AttrList;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;

public class SpecialEdgeAttr implements IJadxAttribute {

	public enum SpecialEdgeType {
		BACK_EDGE,
		CROSS_EDGE
	}

	private final SpecialEdgeType type;
	private final BlockNode start;
	private final BlockNode end;

	public SpecialEdgeAttr(SpecialEdgeType type, BlockNode start, BlockNode end) {
		this.type = type;
		this.start = start;
		this.end = end;
	}

	public SpecialEdgeType getType() {
		return type;
	}

	public BlockNode getStart() {
		return start;
	}

	public BlockNode getEnd() {
		return end;
	}

	@Override
	public AType<AttrList<SpecialEdgeAttr>> getAttrType() {
		return AType.SPECIAL_EDGE;
	}

	@Override
	public String toString() {
		return type + ": " + start + " -> " + end;
	}
}
