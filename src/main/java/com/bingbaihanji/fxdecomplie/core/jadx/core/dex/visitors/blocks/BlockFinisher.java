package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.blocks;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;

public class BlockFinisher extends AbstractVisitor {
    @Override
    public void visit(MethodNode mth) {
        if (mth.isNoCode() || mth.getBasicBlocks().isEmpty()) {
            return;
        }
        if (!mth.contains(AFlag.DISABLE_BLOCKS_LOCK)) {
            mth.finishBasicBlocks();
        }
    }
}
