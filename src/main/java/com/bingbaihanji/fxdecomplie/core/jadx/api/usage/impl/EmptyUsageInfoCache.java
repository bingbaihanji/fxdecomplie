package com.bingbaihanji.fxdecomplie.core.jadx.api.usage.impl;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.api.usage.IUsageInfoCache;
import com.bingbaihanji.fxdecomplie.core.jadx.api.usage.IUsageInfoData;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

public class EmptyUsageInfoCache implements IUsageInfoCache {
	@Override
	public @Nullable IUsageInfoData get(RootNode root) {
		return null;
	}

	@Override
	public void set(RootNode root, IUsageInfoData data) {
	}

	@Override
	public void close() throws IOException {
	}
}
