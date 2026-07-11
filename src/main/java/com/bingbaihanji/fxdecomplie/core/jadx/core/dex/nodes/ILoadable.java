package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.DecodeException;

public interface ILoadable {

	/**
	 * On demand loading
	 */
	void load() throws DecodeException;

	/**
	 * Free resources
	 */
	void unload();
}
