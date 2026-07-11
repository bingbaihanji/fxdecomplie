package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions;
import com.bingbaihanji.fxdecomplie.util.collection.ArraySet;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.SimpleCodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.InsnGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.MethodGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.loops.LoopRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.CodegenException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class CheckRegions extends AbstractVisitor {
    private static final Logger LOG = LoggerFactory.getLogger(CheckRegions.class);

    private static String getBlockInsnStr(MethodNode mth, IBlock block) {
        ICodeWriter code = new SimpleCodeWriter();
        code.incIndent();
        code.newLine();
        MethodGen mg = MethodGen.getFallbackMethodGen(mth);
        InsnGen ig = new InsnGen(mg, true);
        for (InsnNode insn : block.getInstructions()) {
            try {
                ig.makeInsn(insn, code);
            } catch (CodegenException e) {
                // ignore
            }
        }
        code.newLine();
        return code.getCodeStr();
    }

    @Override
    public void visit(MethodNode mth) throws JadxException {
        if (mth.isNoCode()
                || mth.getRegion() == null
                || mth.getBasicBlocks().isEmpty()
                || mth.contains(AType.JADX_ERROR)) {
            return;
        }

        // check if all blocks included in regions
        Set<BlockNode> blocksInRegions = new ArraySet<>();
        DepthRegionTraversal.traverse(mth, new AbstractRegionVisitor() {
            @Override
            public void processBlock(MethodNode mth, IBlock container) {
                if (!(container instanceof BlockNode)) {
                    return;
                }
                BlockNode block = (BlockNode) container;
                if (blocksInRegions.add(block)) {
                    return;
                }
                if (false
                        && LOG.isDebugEnabled()
                        && !block.contains(AFlag.RETURN)
                        && !block.contains(AFlag.REMOVE)
                        && !block.contains(AFlag.SYNTHETIC)
                        && !block.getInstructions().isEmpty()) {
                    LOG.debug("Duplicated block: {} - {}", mth, block);
                }
            }
        });
        if (mth.getBasicBlocks().size() != blocksInRegions.size()) {
            for (BlockNode block : mth.getBasicBlocks()) {
                if (!blocksInRegions.contains(block)
                        && !block.getInstructions().isEmpty()
                        && !block.contains(AFlag.ADDED_TO_REGION)
                        && !block.contains(AFlag.DONT_GENERATE)
                        && !block.contains(AFlag.REMOVE)) {
                    String blockCode = getBlockInsnStr(mth, block).replace("*/", "*\\/");
                    mth.addWarn("Code restructure failed: missing block: " + block + ", code lost:" + blockCode);
                }
            }
        }

        DepthRegionTraversal.traverse(mth, new AbstractRegionVisitor() {
            @Override
            public boolean enterRegion(MethodNode mth, IRegion region) {
                if (region instanceof LoopRegion) {
                    // check loop conditions
                    BlockNode loopHeader = ((LoopRegion) region).getHeader();
                    if (loopHeader != null && !loopHeader.contains(AFlag.ALLOW_MULTIPLE_INSNS_LOOP_COND)
                            && loopHeader.getInstructions().size() != 1) {
                        mth.addWarn("Incorrect condition in loop: " + loopHeader);
                    }
                }
                return true;
            }
        });
    }
}

