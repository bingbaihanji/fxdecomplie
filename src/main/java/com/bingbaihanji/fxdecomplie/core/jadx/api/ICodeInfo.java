package com.bingbaihanji.fxdecomplie.core.jadx.api;

import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.SimpleCodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeMetadata;

public interface ICodeInfo {

	ICodeInfo EMPTY = new SimpleCodeInfo("");

	String getCodeStr();

	ICodeMetadata getCodeMetadata();

	boolean hasMetadata();
}
