package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IContainer;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import org.jetbrains.annotations.Nullable;

public interface IRegionPartialVisitor<R> {
    /**
     * Visit all containers in region until stopped
     *
     * @return non-null value to stop visiting
     */
    @Nullable
    R visit(MethodNode mth, IContainer container);
}
