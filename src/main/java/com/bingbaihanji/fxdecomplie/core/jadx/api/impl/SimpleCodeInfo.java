package com.bingbaihanji.fxdecomplie.core.jadx.api.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeMetadata;

public class SimpleCodeInfo implements ICodeInfo {

	private final String code;

	public SimpleCodeInfo(String code) {
		this.code = code;
	}

	@Override
	public String getCodeStr() {
		return code;
	}

	@Override
	public ICodeMetadata getCodeMetadata() {
		return ICodeMetadata.EMPTY;
	}

	@Override
	public boolean hasMetadata() {
		return false;
	}

	@Override
	public String toString() {
		return code;
	}
}
