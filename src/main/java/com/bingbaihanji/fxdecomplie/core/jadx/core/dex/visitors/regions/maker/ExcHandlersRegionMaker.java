package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.maker;
import com.bingbaihanji.fxdecomplie.util.collection.ArraySet;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.Region;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExcHandlerAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExceptionHandler;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.TryCatchBlockAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.RegionUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ExcHandlersRegionMaker {
    private final MethodNode mth;
    private final RegionMaker regionMaker;

    public ExcHandlersRegionMaker(MethodNode mth, RegionMaker regionMaker) {
        this.mth = mth;
        this.regionMaker = regionMaker;
    }

    public void process() {
        if (mth.isNoExceptionHandlers()) {
            return;
        }
        IRegion excOutBlock = collectHandlerRegions();
        if (excOutBlock != null) {
            mth.getRegion().add(excOutBlock);
        }
    }

    private @Nullable IRegion collectHandlerRegions() {
        List<TryCatchBlockAttr> tcs = mth.getAll(AType.TRY_BLOCKS_LIST);
        for (TryCatchBlockAttr tc : tcs) {
            List<BlockNode> blocks = new ArrayList<>(tc.getHandlersCount());
            Set<BlockNode> splitters = new ArraySet<>();
            for (ExceptionHandler handler : tc.getHandlers()) {
                BlockNode handlerBlock = handler.getHandlerBlock();
                if (handlerBlock != null) {
                    blocks.add(handlerBlock);
                    splitters.add(BlockUtils.getTopSplitterForHandler(handlerBlock));
                } else {
                    mth.addDebugComment("No exception handler block: " + handler);
                }
            }
            Set<BlockNode> exits = new ArraySet<>();
            for (BlockNode splitter : splitters) {
                for (BlockNode handler : blocks) {
                    if (handler.contains(AFlag.REMOVE)) {
                        continue;
                    }
                    List<BlockNode> s = splitter.getSuccessors();
                    if (s.isEmpty()) {
                        mth.addDebugComment("No successors for splitter: " + splitter);
                        continue;
                    }
                    BlockNode ss = s.get(0);
                    BlockNode cross = BlockUtils.getPathCross(mth, ss, handler);
                    if (cross != null && cross != ss && cross != handler) {
                        exits.add(cross);
                    }
                }
            }
            for (ExceptionHandler handler : tc.getHandlers()) {
                processExcHandler(handler, exits);
            }
        }
        return processHandlersOutBlocks(tcs);
    }

    /**
     * Search handlers successor blocks aren't included in any region.
     */
    private @Nullable IRegion processHandlersOutBlocks(List<TryCatchBlockAttr> tcs) {
        Set<IBlock> allRegionBlocks = new ArraySet<>();
        RegionUtils.getAllRegionBlocks(mth.getRegion(), allRegionBlocks);

        Set<IBlock> successorBlocks = new ArraySet<>();
        for (TryCatchBlockAttr tc : tcs) {
            for (ExceptionHandler handler : tc.getHandlers()) {
                IContainer region = handler.getHandlerRegion();
                if (region != null) {
                    IBlock lastBlock = RegionUtils.getLastBlock(region);
                    if (lastBlock instanceof BlockNode) {
                        successorBlocks.addAll(((BlockNode) lastBlock).getSuccessors());
                    }
                    RegionUtils.getAllRegionBlocks(region, allRegionBlocks);
                }
            }
        }
        successorBlocks.removeAll(allRegionBlocks);
        if (successorBlocks.isEmpty()) {
            return null;
        }
        RegionStack stack = regionMaker.getStack();
        Region excOutRegion = new Region(mth.getRegion());
        for (IBlock block : successorBlocks) {
            if (block instanceof BlockNode) {
                stack.clear();
                stack.push(excOutRegion);
                excOutRegion.add(regionMaker.makeRegion((BlockNode) block));
            }
        }
        return excOutRegion;
    }

    private void processExcHandler(ExceptionHandler handler, Set<BlockNode> exits) {
        BlockNode start = handler.getHandlerBlock();
        if (start == null) {
            return;
        }
        RegionStack stack = regionMaker.getStack().clear();
        BlockNode dom;
        if (handler.isFinally()) {
            dom = BlockUtils.getTopSplitterForHandler(start);
        } else {
            dom = start;
            stack.addExits(exits);
        }
        if (dom.contains(AFlag.REMOVE)) {
            return;
        }
        List<BlockNode> handlerExits = new ArrayList<>();

        BlockNode handlerOutBlock = BlockUtils.getTryAndHandlerCrossBlock(mth, handler);
        if (handlerOutBlock != null) {
            // ensure frontier's other predecessors comes from try end
            handlerExits.add(handlerOutBlock);
        } else {
            // fallback to simple frontier
            BitSet domFrontier = dom.getDomFrontier();
            handlerExits.addAll(BlockUtils.bitSetToBlocks(mth, domFrontier));
        }

        boolean inLoop = mth.getLoopForBlock(start) != null;
        for (BlockNode exit : handlerExits) {
            if ((!inLoop || BlockUtils.isPathExists(start, exit))
                    && RegionUtils.isRegionContainsBlock(mth.getRegion(), exit)) {
                stack.addExit(exit);
            }
        }
        handler.setHandlerRegion(regionMaker.makeRegion(start));

        ExcHandlerAttr excHandlerAttr = start.get(AType.EXC_HANDLER);
        if (excHandlerAttr == null) {
            mth.addWarn("Missing exception handler attribute for start block: " + start);
        } else {
            handler.getHandlerRegion().addAttr(excHandlerAttr);
        }
    }
}

