package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.ICatch;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.ITry;

import static com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.impl.InputUtils.formatOffset;

public class TryData implements ITry {

	private final int startOffset;
	private final int endOffset;
	private final ICatch catchHandler;

	public TryData(int startOffset, int endOffset, ICatch catchHandler) {
		this.startOffset = startOffset;
		this.endOffset = endOffset;
		this.catchHandler = catchHandler;
	}

	@Override
	public ICatch getCatch() {
		return catchHandler;
	}

	@Override
	public int getStartOffset() {
		return startOffset;
	}

	@Override
	public int getEndOffset() {
		return endOffset;
	}

	@Override
	public String toString() {
		return "Try{" + formatOffset(startOffset) + " - " + formatOffset(endOffset) + ": " + catchHandler + '}';
	}
}
