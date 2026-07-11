package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;

public interface IRegionIterativeVisitor {

    /**
     * If return 'true' traversal will be restarted.
     */
    boolean visitRegion(MethodNode mth, IRegion region);
}
