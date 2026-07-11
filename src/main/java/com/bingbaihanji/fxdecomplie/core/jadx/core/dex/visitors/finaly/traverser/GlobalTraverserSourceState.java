package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;

import java.util.Set;

/**
 * A state used by the traverser controller for storing information regarding an entire path to
 * take during traversal. This should be static amongst all states following the same path.
 */
public final class GlobalTraverserSourceState {

    private final Set<BlockNode> containedBlocks;

    public GlobalTraverserSourceState(Set<BlockNode> containedBlocks) {
        this.containedBlocks = containedBlocks;
    }

    public boolean isBlockContained(BlockNode block) {
        return containedBlocks.contains(block);
    }

    public Set<BlockNode> getContainedBlocks() {
        return containedBlocks;
    }
}
