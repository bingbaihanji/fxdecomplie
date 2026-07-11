package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

public interface JadxPreparePass extends JadxPass {
	JadxPassType TYPE = new JadxPassType("PreparePass");

	void init(RootNode root);

	@Override
	default JadxPassType getPassType() {
		return TYPE;
	}
}
