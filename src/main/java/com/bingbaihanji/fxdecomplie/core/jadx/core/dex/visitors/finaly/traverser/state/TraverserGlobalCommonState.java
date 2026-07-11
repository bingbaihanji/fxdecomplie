package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state;
import com.bingbaihanji.fxdecomplie.util.collection.ArrayMap;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.util.collection.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TraverserGlobalCommonState {
    private final MethodNode mth;
    private final Map<Pair<BlockNode, BlockNode>, List<TraverserActivePathState>> searchedStates;

    public TraverserGlobalCommonState(MethodNode mth) {
        this.mth = mth;
        this.searchedStates = new ArrayMap<>();
    }

    public void addCachedStateFor(BlockNode finallyBlock, BlockNode candidateBlock, List<TraverserActivePathState> state) {
        Pair<BlockNode, BlockNode> blocks = new Pair<>(finallyBlock, candidateBlock);
        searchedStates.put(blocks, state);
    }

    @Nullable
    public List<TraverserActivePathState> getCachedStateFor(BlockNode finallyBlock, BlockNode candidateBlock) {
        Pair<BlockNode, BlockNode> blocks = new Pair<>(finallyBlock, candidateBlock);
        return searchedStates.get(blocks);
    }

    public boolean hasBlocksBeenCached(BlockNode finallyBlock, BlockNode candidateBlock) {
        Pair<BlockNode, BlockNode> blocks = new Pair<>(finallyBlock, candidateBlock);
        return searchedStates.containsKey(blocks);
    }

    public MethodNode getMethodNode() {
        return mth;
    }
}

