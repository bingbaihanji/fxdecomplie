package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.DecompilationMode;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;

public class DecompileModeOverrideAttr implements IJadxAttribute {

	private final DecompilationMode mode;

	public DecompileModeOverrideAttr(DecompilationMode mode) {
		this.mode = mode;
	}

	public DecompilationMode getMode() {
		return mode;
	}

	@Override
	public IJadxAttrType<DecompileModeOverrideAttr> getAttrType() {
		return AType.DECOMPILE_MODE_OVERRIDE;
	}

	@Override
	public String toString() {
		return "DECOMPILE_MODE_OVERRIDE: " + mode;
	}
}
