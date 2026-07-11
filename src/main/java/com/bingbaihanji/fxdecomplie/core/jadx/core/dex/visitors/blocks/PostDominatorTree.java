package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.blocks;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.EmptyBitSet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class PostDominatorTree {

    public static void compute(MethodNode mth) {
        if (!mth.contains(AFlag.COMPUTE_POST_DOM)) {
            return;
        }
        try {
            int mthBlocksCount = mth.getBasicBlocks().size();
            List<BlockNode> sorted = new ArrayList<>(mthBlocksCount);
            BlockUtils.visitReverseDFS(mth, sorted::add);
            // temporary set block positions to match reverse sorted order
            // save old positions for later remapping
            int blocksCount = sorted.size();
            int[] posMapping = new int[mthBlocksCount];
            for (int i = 0; i < blocksCount; i++) {
                posMapping[i] = sorted.get(i).getPos();
            }
            BlockNode.updateBlockPositions(sorted);

            BlockNode[] postDoms = DominatorTree.build(sorted, BlockNode::getSuccessors);
            BlockNode firstBlock = sorted.get(0);
            firstBlock.setPostDoms(EmptyBitSet.EMPTY);
            firstBlock.setIPostDom(null);
            for (int i = 1; i < blocksCount; i++) {
                BlockNode block = sorted.get(i);
                BlockNode iPostDom = postDoms[i];
                block.setIPostDom(iPostDom);
                BitSet postDomBS = DominatorTree.collectDoms(postDoms, iPostDom);
                block.setPostDoms(postDomBS);
            }
            for (int i = 1; i < blocksCount; i++) {
                BlockNode block = sorted.get(i);
                BitSet bs = new BitSet(blocksCount);
                block.getPostDoms().stream().forEach(n -> bs.set(posMapping[n]));
                bs.clear(posMapping[i]);
                block.setPostDoms(bs);
            }
            // check for missing blocks in 'sorted' list
            // can be caused by infinite loops
            int blocksDelta = mthBlocksCount - blocksCount;
            if (blocksDelta != 0) {
                int insnsCount = 0;
                for (BlockNode block : mth.getBasicBlocks()) {
                    if (block.getPostDoms() == null) {
                        block.setPostDoms(EmptyBitSet.EMPTY);
                        block.setIPostDom(null);
                        insnsCount += block.getInstructions().size();
                    }
                }
                mth.addInfoComment("Infinite loop detected, blocks: " + blocksDelta + ", insns: " + insnsCount);
            }
        } catch (StackOverflowError | Exception e) {
            // show error as a warning because this info not always used
            mth.addWarnComment("Failed to build post-dominance tree", e);
        } finally {
            // revert block positions change
            mth.updateBlockPositions();
        }
    }
}
