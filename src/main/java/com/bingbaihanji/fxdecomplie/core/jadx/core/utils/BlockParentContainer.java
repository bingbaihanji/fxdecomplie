package com.bingbaihanji.fxdecomplie.core.jadx.core.utils;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IBlock;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IContainer;

import java.util.Objects;

public class BlockParentContainer {

    private final IContainer parent;
    private final IBlock block;

    public BlockParentContainer(IContainer parent, IBlock block) {
        this.parent = Objects.requireNonNull(parent);
        this.block = Objects.requireNonNull(block);
    }

    public IBlock getBlock() {
        return block;
    }

    public IContainer getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return "BlockParentContainer{" + block + ", parent=" + parent + '}';
    }
}
