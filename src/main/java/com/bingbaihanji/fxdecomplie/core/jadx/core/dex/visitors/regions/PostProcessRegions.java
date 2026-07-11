package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions;

import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.EdgeInsnAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IContainer;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnContainer;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.Region;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.SwitchRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.loops.LoopRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.maker.SwitchRegionMaker;

public final class PostProcessRegions extends AbstractRegionVisitor {
	private static final IRegionVisitor INSTANCE = new PostProcessRegions();

	static void process(MethodNode mth) {
		DepthRegionTraversal.traverse(mth, INSTANCE);
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

	private PostProcessRegions() {
		// singleton
	}
}
