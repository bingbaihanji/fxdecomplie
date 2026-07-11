package com.bingbaihanji.fxdecomplie.core.jadx.api.usage;

import java.io.Closeable;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

public interface IUsageInfoCache extends Closeable {

	@Nullable
	IUsageInfoData get(RootNode root);

	void set(RootNode root, IUsageInfoData data);
}
