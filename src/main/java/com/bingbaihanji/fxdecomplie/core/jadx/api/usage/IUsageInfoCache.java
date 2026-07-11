package com.bingbaihanji.fxdecomplie.core.jadx.api.usage;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;

public interface IUsageInfoCache extends Closeable {

    @Nullable
    IUsageInfoData get(RootNode root);

    void set(RootNode root, IUsageInfoData data);
}
