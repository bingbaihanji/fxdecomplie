package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.EdgeInsnAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.Region;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.SwitchRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.loops.LoopRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.maker.SwitchRegionMaker;

import java.util.List;

public final class PostProcessRegions extends AbstractRegionVisitor {
    private static final IRegionVisitor INSTANCE = new PostProcessRegions();

    private PostProcessRegions() {
        // singleton
    }

    static void process(MethodNode mth) {
        DepthRegionTraversal.traverse(mth, INSTANCE);
    }

    /**
     * Insert insn block from edge insn attribute.
     */
    private static void insertEdgeInsn(Region region) {
        List<IContainer> subBlocks = region.getSubBlocks();
        if (subBlocks.isEmpty()) {
            return;
        }
        IContainer last = subBlocks.get(subBlocks.size() - 1);
        List<EdgeInsnAttr> edgeInsnAttrs = last.getAll(AType.EDGE_INSN);
        if (edgeInsnAttrs.isEmpty()) {
            return;
        }
        EdgeInsnAttr insnAttr = edgeInsnAttrs.get(0);
        if (!insnAttr.getStart().equals(last)) {
            return;
        }
        if (last instanceof BlockNode) {
            BlockNode block = (BlockNode) last;
            if (block.getInstructions().isEmpty()) {
                block.getInstructions().add(insnAttr.getInsn());
                return;
            }
        }
        region.add(new InsnContainer(insnAttr.getInsn()));
    }

    @Override
    public void leaveRegion(MethodNode mth, IRegion region) {
        if (region instanceof LoopRegion) {
            // merge conditions in loops
            LoopRegion loop = (LoopRegion) region;
            loop.mergePreCondition();
        } else if (region instanceof SwitchRegion) {
            SwitchRegionMaker.insertBreaks(mth, (SwitchRegion) region);
        } else if (region instanceof Region) {
            insertEdgeInsn((Region) region);
        }
    }
}
