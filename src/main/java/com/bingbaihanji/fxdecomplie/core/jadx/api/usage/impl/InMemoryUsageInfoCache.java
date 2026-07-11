package com.bingbaihanji.fxdecomplie.core.jadx.api.usage.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.usage.IUsageInfoCache;
import com.bingbaihanji.fxdecomplie.core.jadx.api.usage.IUsageInfoData;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import org.jetbrains.annotations.Nullable;

public class InMemoryUsageInfoCache implements IUsageInfoCache {

    private IUsageInfoData data;

    /**
     * `data` field tied to root node instance, keep hash to reset cache on change
     */
    private int rootNodeHash;

    @Override
    public @Nullable IUsageInfoData get(RootNode root) {
        return rootNodeHash == root.hashCode() ? data : null;
    }

    @Override
    public void set(RootNode root, IUsageInfoData data) {
        this.rootNodeHash = root.hashCode();
        this.data = data;
    }

    @Override
    public void close() {
        this.rootNodeHash = 0;
        this.data = null;
    }
}
