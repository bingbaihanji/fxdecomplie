package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types.JadxPassType;

public interface JadxPass {
	JadxPassInfo getInfo();

	JadxPassType getPassType();
}
