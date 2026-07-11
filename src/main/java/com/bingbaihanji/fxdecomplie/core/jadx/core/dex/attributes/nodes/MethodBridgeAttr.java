package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.PinnedAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;

public class MethodBridgeAttr extends PinnedAttribute {

	private final MethodNode bridgeMth;

	public MethodBridgeAttr(MethodNode bridgeMth) {
		this.bridgeMth = bridgeMth;
	}

	public MethodNode getBridgeMth() {
		return bridgeMth;
	}

	@Override
	public AType<MethodBridgeAttr> getAttrType() {
		return AType.BRIDGED_BY;
	}

	@Override
	public String toString() {
		return "BRIDGED_BY: " + bridgeMth;
	}
}
