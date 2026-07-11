package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.blocks;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.ExcSplitCrossAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.TmpEdgeAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.NamedArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.CatchAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExcHandlerAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExceptionHandler;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.TryCatchBlockAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeCompare;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnRemover;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.blocks.BlockSet;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.util.collection.ArrayMap;
import com.bingbaihanji.fxdecomplie.util.collection.ArraySet;
import com.bingbaihanji.fxdecomplie.util.collection.ListUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 块异常处理器，负责处理方法的 try-catch 块结构
 * 主要职责包括：解析异常处理器、构建 try-catch 块关系、
 * 包裹 try 块并连接到对应的处理器块
 */
public class BlockExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(BlockExceptionHandler.class);

    /**
     * 处理方法的异常处理器，构建 try-catch 块结构
     * 如果方法没有异常处理器则直接返回 false
     */
    public static boolean process(MethodNode mth) {
        if (mth.isNoExceptionHandlers()) {
            return false;
        }
        BlockProcessor.updateCleanSuccessors(mth);
        DominatorTree.computeDominanceFrontier(mth);

        processCatchAttr(mth);
        initExcHandlers(mth);

        List<TryCatchBlockAttr> tryBlocks = prepareTryBlocks(mth);
        connectExcHandlers(mth, tryBlocks);
        mth.addAttr(AType.TRY_BLOCKS_LIST, tryBlocks);
        mth.getBasicBlocks().forEach(BlockNode::updateCleanSuccessors);

        for (ExceptionHandler eh : mth.getExceptionHandlers()) {
            removeMonitorExitFromExcHandler(mth, eh);
        }
        BlockProcessor.removeMarkedBlocks(mth);

        BlockSet sorted = new BlockSet(mth);
        BlockUtils.visitDFS(mth, sorted::add);
        removeUnusedExcHandlers(mth, tryBlocks, sorted);
        return true;
    }

    /**
     * 用顶部/底部分割块包裹 try 块，并将其连接到处理器块
     * 有时 try 块本身可能就是处理器块，应在包裹前先进行连接
     * 使用队列来推迟处理那些尚未准备好包裹的 try 块
     */
    private static void connectExcHandlers(MethodNode mth, List<TryCatchBlockAttr> tryBlocks) {
        if (tryBlocks.isEmpty()) {
            return;
        }
        int limit = tryBlocks.size() * 3;
        int count = 0;
        Deque<TryCatchBlockAttr> queue = new ArrayDeque<>(tryBlocks);
        while (!queue.isEmpty()) {
            TryCatchBlockAttr tryBlock = queue.removeFirst();
            boolean complete = wrapBlocksWithTryCatch(mth, tryBlock);
            if (!complete) {
                queue.addLast(tryBlock); // 放回队列末尾
            }
            if (count++ > limit) {
                throw new JadxRuntimeException("Try blocks wrapping queue limit reached! Please report as an issue!");
            }
        }
    }

    /**
     * 处理 catch 属性：移除不应该有 catch 的指令上的 EXC_CATCH 属性，
     * 并将指令级别一致的 catch 属性提升到块级别
     */
    private static void processCatchAttr(MethodNode mth) {
        for (BlockNode block : mth.getBasicBlocks()) {
            for (InsnNode insn : block.getInstructions()) {
                if (insn.contains(AType.EXC_CATCH) && !insn.canThrowException()) {
                    insn.remove(AType.EXC_CATCH);
                }
            }
        }
        // 如果块中所有指令都具有相同的 'catch' 属性 -> 将该属性添加到整个块上
        for (BlockNode block : mth.getBasicBlocks()) {
            CatchAttr commonCatchAttr = getCommonCatchAttr(block);
            if (commonCatchAttr != null) {
                block.addAttr(commonCatchAttr);
                for (InsnNode insn : block.getInstructions()) {
                    if (insn.contains(AFlag.TRY_ENTER)) {
                        block.add(AFlag.TRY_ENTER);
                    }
                    if (insn.contains(AFlag.TRY_LEAVE)) {
                        block.add(AFlag.TRY_LEAVE);
                    }
                }
            }
        }
    }

    @Nullable
    private static CatchAttr getCommonCatchAttr(BlockNode block) {
        CatchAttr commonCatchAttr = null;
        for (InsnNode insn : block.getInstructions()) {
            CatchAttr catchAttr = insn.get(AType.EXC_CATCH);
            if (catchAttr != null) {
                if (commonCatchAttr == null) {
                    commonCatchAttr = catchAttr;
                    continue;
                }
                if (!commonCatchAttr.equals(catchAttr)) {
                    return null;
                }
            }
        }
        return commonCatchAttr;
    }

    /**
     * 初始化异常处理器，提取 excHandlerAttr 属性并创建对应的 ExceptionHandler
     * 按支配关系收集处理器覆盖的块 若处理器已有前驱块，则创建空处理器块
     */
    @SuppressWarnings("ForLoopReplaceableByForEach")
    private static void initExcHandlers(MethodNode mth) {
        List<BlockNode> blocks = mth.getBasicBlocks();
        int blocksCount = blocks.size();
        for (int i = 0; i < blocksCount; i++) { // 会向列表末尾添加新块
            BlockNode block = blocks.get(i);
            InsnNode firstInsn = BlockUtils.getFirstInsn(block);
            if (firstInsn == null) {
                continue;
            }
            ExcHandlerAttr excHandlerAttr = firstInsn.get(AType.EXC_HANDLER);
            if (excHandlerAttr == null) {
                continue;
            }
            firstInsn.remove(AType.EXC_HANDLER);
            removeTmpConnection(block);

            ExceptionHandler excHandler = excHandlerAttr.getHandler();
            if (block.getPredecessors().isEmpty()) {
                excHandler.setHandlerBlock(block);
                block.addAttr(excHandlerAttr);
                excHandler.addBlock(block);
                BlockUtils.collectBlocksDominatedByWithExcHandlers(mth, block, block)
                        .forEach(excHandler::addBlock);
            } else {
                // 忽略已连接的处理器 -> 使 catch 为空
                BlockNode emptyHandlerBlock = BlockSplitter.startNewBlock(mth, block.getStartOffset());
                emptyHandlerBlock.add(AFlag.SYNTHETIC);
                emptyHandlerBlock.addAttr(excHandlerAttr);
                BlockSplitter.connect(emptyHandlerBlock, block);
                excHandler.setHandlerBlock(emptyHandlerBlock);
                excHandler.addBlock(emptyHandlerBlock);
            }
            fixMoveExceptionInsn(block, excHandlerAttr);
        }
    }

    private static void removeTmpConnection(BlockNode block) {
        TmpEdgeAttr tmpEdgeAttr = block.get(AType.TMP_EDGE);
        if (tmpEdgeAttr != null) {
            // 移除临时连接
            BlockSplitter.removeConnection(tmpEdgeAttr.getBlock(), block);
            block.remove(AType.TMP_EDGE);
        }
    }

    /**
     * 准备 try-catch 块列表：收集 catch 属性中的异常处理器，按处理器分组块，
     * 合并或标记内外层关系，并进行多重 catch 检查和排序
     */
    private static List<TryCatchBlockAttr> prepareTryBlocks(MethodNode mth) {
        Map<ExceptionHandler, List<BlockNode>> blocksByHandler = new ArrayMap<>();
        for (BlockNode block : mth.getBasicBlocks()) {
            CatchAttr catchAttr = block.get(AType.EXC_CATCH);
            if (catchAttr != null) {
                for (ExceptionHandler eh : catchAttr.getHandlers()) {
                    blocksByHandler
                            .computeIfAbsent(eh, c -> new ArrayList<>())
                            .add(block);
                }
            }
        }
        if (false) {
            LOG.debug("Input exception handlers:");
            blocksByHandler.forEach((eh, blocks) -> LOG.debug(" {}, throw blocks: {}, handler blocks: {}", eh, blocks, eh.getBlocks()));
        }
        if (blocksByHandler.isEmpty()) {
            // 没有 catch 块 -> 移除所有处理器
            mth.getExceptionHandlers().forEach(eh -> removeExcHandler(mth, eh));
        } else {
            // 移除在 catch 属性中没有块的处理器
            blocksByHandler.forEach((eh, blocks) -> {
                if (blocks.isEmpty()) {
                    removeExcHandler(mth, eh);
                }
            });
        }
        BlockSplitter.detachMarkedBlocks(mth);
        mth.clearExceptionHandlers();
        if (mth.isNoExceptionHandlers()) {
            return Collections.emptyList();
        }

        blocksByHandler.forEach((eh, blocks) -> {
            // 移除来自同一处理器的 catch 块
            blocks.removeAll(eh.getBlocks());
        });

        List<TryCatchBlockAttr> tryBlocks = new ArrayList<>();
        blocksByHandler.forEach((eh, blocks) -> {
            List<ExceptionHandler> handlers = new ArrayList<>(1);
            handlers.add(eh);
            tryBlocks.add(new TryCatchBlockAttr(tryBlocks.size(), handlers, blocks));
        });
        if (tryBlocks.size() > 1) {
            // 合并或标记为外层/内层
            while (true) {
                boolean restart = combineTryCatchBlocks(tryBlocks);
                if (!restart) {
                    break;
                }
            }
        }
        checkForMultiCatch(mth, tryBlocks);
        clearTryBlocks(mth, tryBlocks);
        sortHandlers(mth, tryBlocks);

        if (false) {
            LOG.debug("Result try-catch blocks:");
            tryBlocks.forEach(tryBlock -> LOG.debug(" {}", tryBlock));
        }
        return tryBlocks;
    }

    private static void clearTryBlocks(MethodNode mth, List<TryCatchBlockAttr> tryBlocks) {
        tryBlocks.forEach(tc -> tc.getBlocks().removeIf(b -> b.contains(AFlag.REMOVE)));
        tryBlocks.removeIf(tb -> tb.getBlocks().isEmpty() || tb.getHandlers().isEmpty());
        mth.clearExceptionHandlers();
        BlockSplitter.detachMarkedBlocks(mth);
    }

    private static boolean combineTryCatchBlocks(List<TryCatchBlockAttr> tryBlocks) {
        for (TryCatchBlockAttr outerTryBlock : tryBlocks) {
            for (TryCatchBlockAttr innerTryBlock : tryBlocks) {
                if (outerTryBlock == innerTryBlock || innerTryBlock.getOuterTryBlock() != null) {
                    continue;
                }
                if (checkTryCatchRelation(tryBlocks, outerTryBlock, innerTryBlock)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean checkTryCatchRelation(List<TryCatchBlockAttr> tryBlocks,
                                                 TryCatchBlockAttr outerTryBlock, TryCatchBlockAttr innerTryBlock) {
        if (outerTryBlock.getBlocks().equals(innerTryBlock.getBlocks())) {
            // 相同的 try 块 -> 合并处理器
            List<ExceptionHandler> handlers = Utils.concatDistinct(outerTryBlock.getHandlers(), innerTryBlock.getHandlers());
            tryBlocks.add(new TryCatchBlockAttr(tryBlocks.size(), handlers, outerTryBlock.getBlocks()));
            tryBlocks.remove(outerTryBlock);
            tryBlocks.remove(innerTryBlock);
            return true;
        }

        Set<BlockNode> handlerBlocks = innerTryBlock.getHandlers().stream()
                .flatMap(eh -> eh.getBlocks().stream())
                .collect(Collectors.toSet());
        boolean catchInHandler = handlerBlocks.stream().anyMatch(isHandlersIntersects(outerTryBlock));
        boolean catchInTry = innerTryBlock.getBlocks().stream().anyMatch(isHandlersIntersects(outerTryBlock));
        boolean blocksOutsideHandler = outerTryBlock.getBlocks().stream().anyMatch(b -> !handlerBlocks.contains(b));

        if (catchInHandler && (catchInTry || blocksOutsideHandler)) {
            // 转换为内层
            List<BlockNode> mergedBlocks = Utils.concatDistinct(outerTryBlock.getBlocks(), innerTryBlock.getBlocks());
            innerTryBlock.getHandlers().removeAll(outerTryBlock.getHandlers());
            innerTryBlock.setOuterTryBlock(outerTryBlock);
            outerTryBlock.addInnerTryBlock(innerTryBlock);
            outerTryBlock.setBlocks(mergedBlocks);
            return false;
        }
        Set<ExceptionHandler> innerHandlerSet = new ArraySet<>(innerTryBlock.getHandlers());
        if (innerHandlerSet.containsAll(outerTryBlock.getHandlers())) {
            // 合并
            List<BlockNode> mergedBlocks = Utils.concatDistinct(outerTryBlock.getBlocks(), innerTryBlock.getBlocks());
            List<ExceptionHandler> handlers = Utils.concatDistinct(outerTryBlock.getHandlers(), innerTryBlock.getHandlers());
            tryBlocks.add(new TryCatchBlockAttr(tryBlocks.size(), handlers, mergedBlocks));
            tryBlocks.remove(outerTryBlock);
            tryBlocks.remove(innerTryBlock);
            return true;
        }
        return false;
    }

    @NotNull
    private static Predicate<BlockNode> isHandlersIntersects(TryCatchBlockAttr outerTryBlock) {
        return block -> {
            CatchAttr catchAttr = block.get(AType.EXC_CATCH);
            return catchAttr != null && Objects.equals(catchAttr.getHandlers(), outerTryBlock.getHandlers());
        };
    }

    private static void removeExcHandler(MethodNode mth, ExceptionHandler excHandler) {
        excHandler.markForRemove();
        BlockSplitter.removeConnection(mth.getEnterBlock(), excHandler.getHandlerBlock());
    }

    /**
     * 用顶部和底部分割块包裹 try 块，并建立与处理器块之间的连接
     * 搜索 try 块的顶部和底部块，必要时进行块分割和边重定向
     */
    private static boolean wrapBlocksWithTryCatch(MethodNode mth, TryCatchBlockAttr tryCatchBlock) {
        List<BlockNode> blocks = tryCatchBlock.getBlocks();
        BlockNode top = searchTopBlock(mth, blocks);
        if (top.getPredecessors().isEmpty() && top != mth.getEnterBlock()) {
            return false;
        }
        BlockNode bottom = searchBottomBlock(mth, blocks);
        BlockNode splitReturn;
        if (bottom != null && bottom.isReturnBlock()) {
            if (false) {
                LOG.debug("TryCatch #{} bottom block ({}) is return, split", tryCatchBlock.id(), bottom);
            }
            splitReturn = bottom;
            bottom = BlockSplitter.blockSplitTop(mth, bottom);
            bottom.add(AFlag.SYNTHETIC);
        } else {
            splitReturn = null;
        }
        if (false) {
            LOG.debug("TryCatch #{} split: top {}, bottom: {}", tryCatchBlock.id(), top, bottom);
        }
        BlockNode topSplitterBlock = getTopSplitterBlock(mth, top);
        topSplitterBlock.add(AFlag.EXC_TOP_SPLITTER);
        topSplitterBlock.add(AFlag.SYNTHETIC);

        int totalHandlerBlocks = tryCatchBlock.getHandlers().stream().mapToInt(eh -> eh.getBlocks().size()).sum();

        BlockNode bottomSplitterBlock;
        if (bottom == null || totalHandlerBlocks == 0) {
            bottomSplitterBlock = null;
        } else {
            BlockNode existBottomSplitter = BlockUtils.getBlockWithFlag(bottom.getSuccessors(), AFlag.EXC_BOTTOM_SPLITTER);
            bottomSplitterBlock = existBottomSplitter != null ? existBottomSplitter : BlockSplitter.startNewBlock(mth, -1);
            bottomSplitterBlock.add(AFlag.EXC_BOTTOM_SPLITTER);
            bottomSplitterBlock.add(AFlag.SYNTHETIC);
            BlockSplitter.connect(bottom, bottomSplitterBlock);
            if (splitReturn != null) {
                // 将处理器重定向到 return 块而非合成分割块，以避免自循环
                BlockSet bottomPreds = BlockSet.from(mth, bottom.getPredecessors());
                for (ExceptionHandler handler : tryCatchBlock.getHandlers()) {
                    if (bottomPreds.intersects(handler.getBlocks())) {
                        BlockNode lastBlock = bottomPreds.intersect(handler.getBlocks()).getOne();
                        if (lastBlock != null) {
                            BlockSplitter.replaceConnection(lastBlock, bottom, splitReturn);
                        }
                    }
                }
            }
        }

        if (false) {
            LOG.debug("TryCatch #{} result splitters: top {}, bottom: {}",
                    tryCatchBlock.id(), topSplitterBlock, bottomSplitterBlock);
        }
        connectSplittersAndHandlers(tryCatchBlock, topSplitterBlock, bottomSplitterBlock);

        // 此时，由于插入了新的底部块，指向原始底部块的交叉边有可能被转变为回边
        // 这会引发问题，因为回边通常意味着循环，但这里并非循环为修复这一点，
        // 那些同时存在从底部块出发路径的底部块前驱，会被重写为指向原始路径交叉点
        // (即合成块之前的位置)
        if (bottom != null && bottom.contains(AType.EXC_SPLIT_CROSS)) {
            List<BlockNode> convertBlocks = new ArrayList<>();
            for (BlockNode b : bottom.getPredecessors()) {
                if (BlockUtils.isAnyPathExists(bottom, b)) {
                    convertBlocks.add(b);
                }
            }
            for (BlockNode b : convertBlocks) {
                // 连接不能在第一个循环中替换，因为那样会修改前驱列表
                BlockSplitter.replaceConnection(b, bottom, bottom.get(AType.EXC_SPLIT_CROSS).getOriginalPathCross());
            }
        }

        for (BlockNode block : blocks) {
            TryCatchBlockAttr currentTCBAttr = block.get(AType.TRY_BLOCK);
            if (currentTCBAttr == null || currentTCBAttr.getInnerTryBlocks().contains(tryCatchBlock)) {
                block.addAttr(tryCatchBlock);
            }
        }
        tryCatchBlock.setTopSplitter(topSplitterBlock);

        topSplitterBlock.updateCleanSuccessors();
        if (bottomSplitterBlock != null) {
            bottomSplitterBlock.updateCleanSuccessors();
        }
        return true;
    }

    private static BlockNode getTopSplitterBlock(MethodNode mth, BlockNode top) {
        if (top == mth.getEnterBlock()) {
            BlockNode fixedTop = mth.getEnterBlock().getSuccessors().get(0);
            return BlockSplitter.blockSplitTop(mth, fixedTop);
        }
        BlockNode existPredTopSplitter = BlockUtils.getBlockWithFlag(top.getPredecessors(), AFlag.EXC_TOP_SPLITTER);
        if (existPredTopSplitter != null) {
            return existPredTopSplitter;
        }
        // 尝试复用顶部块下方空的简单路径上已存在的分割块
        if (top.getCleanSuccessors().size() == 1 && top.getInstructions().isEmpty()) {
            BlockNode otherTopSplitter = BlockUtils.getBlockWithFlag(top.getCleanSuccessors(), AFlag.EXC_TOP_SPLITTER);
            if (otherTopSplitter != null && otherTopSplitter.getPredecessors().size() == 1) {
                return otherTopSplitter;
            }
        }
        return BlockSplitter.blockSplitTop(mth, top);
    }

    private static BlockNode searchTopBlock(MethodNode mth, List<BlockNode> blocks) {
        BlockNode top = BlockUtils.getTopBlock(blocks);
        if (top != null) {
            return adjustTopBlock(top);
        }
        BlockNode topDom = BlockUtils.getCommonDominator(mth, blocks);
        if (topDom != null) {
            // 支配节点总是返回上一级块 如果 blocks 已包含该支配节点，则改用其后继块
            if (topDom.getSuccessors().size() == 1) {
                BlockNode upBlock = topDom.getSuccessors().get(0);
                if (blocks.contains(upBlock)) {
                    return upBlock;
                }
            }
            return adjustTopBlock(topDom);
        }
        throw new JadxRuntimeException("Failed to find top block for try-catch from: " + blocks);
    }

    private static BlockNode adjustTopBlock(BlockNode topBlock) {
        if (topBlock.getSuccessors().size() == 1 && !topBlock.contains(AType.EXC_CATCH)) {
            // 顶部块可能被 blocks 列表中包含的其他异常处理器抬升，此处尝试撤销该抬升
            return topBlock.getSuccessors().get(0);
        }
        return topBlock;
    }

    /**
     * 搜索 try 块的底部块：先在输入集合内部搜索公共后支配块，
     * 未找到则在外部搜索公共交叉块，并仅在输入集合范围内进行分割
     */
    @Nullable
    private static BlockNode searchBottomBlock(MethodNode mth, List<BlockNode> blocks) {
        // 在输入集合内部搜索公共后支配块
        BlockNode bottom = BlockUtils.getBottomBlock(blocks);
        if (bottom != null) {
            return bottom;
        }
        // 未找到 -> 这些块没有相同的支配节点
        // 尝试在输入集合外部搜索公共交叉块
        // 注意：退出节点不需要底部块 (不存在从它们流出的数据流)
        BlockNode pathCross = BlockUtils.getPathCross(mth, blocks);
        if (pathCross == null) {
            return null;
        }
        List<BlockNode> preds = new ArrayList<>(pathCross.getPredecessors());
        preds.removeAll(blocks);
        List<BlockNode> outsidePredecessors = preds.stream()
                .filter(p -> !BlockUtils.atLeastOnePathExists(blocks, p))
                .collect(Collectors.toList());
        // 如果没有前驱，或者每个前驱都在集合外部 (这意味着插入新的合成块不起任何作用)，
        // 则直接返回已有的路径交叉块
        if (outsidePredecessors.isEmpty() || outsidePredecessors.size() == pathCross.getPredecessors().size()) {
            return pathCross;
        }
        // 部分前驱位于输入集合路径之外 -> 仅为输入集合分割该块
        BlockNode splitCross = BlockSplitter.blockSplitTop(mth, pathCross);
        splitCross.add(AFlag.SYNTHETIC);
        splitCross.addAttr(new ExcSplitCrossAttr(pathCross));
        for (BlockNode outsidePredecessor : outsidePredecessors) {
            // 将前驱重新指回原始的底部分割块
            BlockSplitter.replaceConnection(outsidePredecessor, splitCross, pathCross);
        }
        return splitCross;
    }

    private static void connectSplittersAndHandlers(TryCatchBlockAttr tryCatchBlock, BlockNode topSplitterBlock,
                                                    @Nullable BlockNode bottomSplitterBlock) {
        for (ExceptionHandler handler : tryCatchBlock.getHandlers()) {
            BlockNode handlerBlock = handler.getHandlerBlock();
            BlockSplitter.connect(topSplitterBlock, handlerBlock);
            if (bottomSplitterBlock != null) {
                BlockSplitter.connect(bottomSplitterBlock, handlerBlock);
            }
        }
        TryCatchBlockAttr outerTryBlock = tryCatchBlock.getOuterTryBlock();
        if (outerTryBlock != null) {
            connectSplittersAndHandlers(outerTryBlock, topSplitterBlock, bottomSplitterBlock);
        }
    }

    private static void fixMoveExceptionInsn(BlockNode block, ExcHandlerAttr excHandlerAttr) {
        ExceptionHandler excHandler = excHandlerAttr.getHandler();
        ArgType argType = excHandler.getArgType();
        InsnNode me = BlockUtils.getLastInsn(block);
        if (me != null && me.getType() == InsnType.MOVE_EXCEPTION) {
            // 为 'move-exception' 操作设置正确的类型
            RegisterArg resArg = InsnArg.reg(me.getResult().getRegNum(), argType);
            resArg.copyAttributesFrom(me);
            me.setResult(resArg);
            me.add(AFlag.DONT_INLINE);
            resArg.add(AFlag.CUSTOM_DECLARE);
            excHandler.setArg(resArg);
            me.addAttr(excHandlerAttr);
            return;
        }
        // 处理器参数未被使用
        excHandler.setArg(new NamedArg("unused", argType));
    }

    private static void removeMonitorExitFromExcHandler(MethodNode mth, ExceptionHandler excHandler) {
        for (BlockNode excBlock : excHandler.getBlocks()) {
            InsnRemover remover = new InsnRemover(mth, excBlock);
            for (InsnNode insn : excBlock.getInstructions()) {
                if (insn.getType() == InsnType.MONITOR_ENTER) {
                    break;
                }
                if (insn.getType() == InsnType.MONITOR_EXIT) {
                    remover.addAndUnbind(insn);
                }
            }
            remover.perform();
        }
    }

    private static void checkForMultiCatch(MethodNode mth, List<TryCatchBlockAttr> tryBlocks) {
        boolean merged = false;
        for (TryCatchBlockAttr tryBlock : tryBlocks) {
            if (mergeMultiCatch(mth, tryBlock)) {
                merged = true;
            }
        }
        if (merged) {
            BlockSplitter.detachMarkedBlocks(mth);
            mth.clearExceptionHandlers();
        }
    }

    /**
     * 尝试将多个 catch 处理器合并为一个多重 catch (multi-catch)
     * 条件：每个处理器只有当前异常块，且所有处理器共享同一个后继块和同一个结果寄存器
     */
    private static boolean mergeMultiCatch(MethodNode mth, TryCatchBlockAttr tryCatch) {
        if (tryCatch.getHandlers().size() < 2) {
            return false;
        }
        for (ExceptionHandler handler : tryCatch.getHandlers()) {
            if (handler.getBlocks().size() != 1) {
                return false;
            }
            BlockNode block = handler.getHandlerBlock();
            if (block.getInstructions().size() != 1
                    || !BlockUtils.checkLastInsnType(block, InsnType.MOVE_EXCEPTION)) {
                return false;
            }
        }
        List<BlockNode> handlerBlocks = ListUtils.map(tryCatch.getHandlers(), ExceptionHandler::getHandlerBlock);
        List<BlockNode> successorBlocks = handlerBlocks.stream()
                .flatMap(h -> h.getSuccessors().stream())
                .distinct()
                .collect(Collectors.toList());
        if (successorBlocks.size() != 1) {
            return false;
        }
        BlockNode successorBlock = successorBlocks.get(0);
        if (!ListUtils.unorderedEquals(successorBlock.getPredecessors(), handlerBlocks)) {
            return false;
        }
        List<RegisterArg> regs = tryCatch.getHandlers().stream()
                .map(h -> Objects.requireNonNull(BlockUtils.getLastInsn(h.getHandlerBlock())).getResult())
                .distinct()
                .collect(Collectors.toList());
        if (regs.size() != 1) {
            return false;
        }

        // 确认合并，只保留第一个处理器，移除其余处理器
        ExceptionHandler resultHandler = tryCatch.getHandlers().get(0);
        tryCatch.getHandlers().removeIf(handler -> {
            if (handler == resultHandler) {
                return false;
            }
            resultHandler.addCatchTypes(mth, handler.getCatchTypes());
            handler.markForRemove();
            return true;
        });
        return true;
    }

    /**
     * 按类型层次对异常处理器和 catch 类型进行排序，catch-all 处理器排在最后
     * 使用类型比较器的逆序排列，冲突时按类名排序
     */
    private static void sortHandlers(MethodNode mth, List<TryCatchBlockAttr> tryBlocks) {
        TypeCompare typeCompare = mth.root().getTypeCompare();
        Comparator<ArgType> comparator = typeCompare.getReversedComparator();
        for (TryCatchBlockAttr tryBlock : tryBlocks) {
            for (ExceptionHandler handler : tryBlock.getHandlers()) {
                handler.getCatchTypes().sort((first, second) -> compareByTypeAndName(comparator, first, second));
            }
            tryBlock.getHandlers().sort((first, second) -> {
                if (first.equals(second)) {
                    throw new JadxRuntimeException("Same handlers in try block: " + tryBlock);
                }
                if (first.isCatchAll()) {
                    return 1;
                }
                if (second.isCatchAll()) {
                    return -1;
                }
                return compareByTypeAndName(comparator,
                        ListUtils.first(first.getCatchTypes()), ListUtils.first(second.getCatchTypes()));
            });
        }
    }

    @SuppressWarnings("ComparatorResultComparison")
    private static int compareByTypeAndName(Comparator<ArgType> comparator, ClassInfo first, ClassInfo second) {
        int r = comparator.compare(first.getType(), second.getType());
        if (r == -2) {
            // 冲突时按名称排序
            return first.compareTo(second);
        }
        return r;
    }

    /**
     * 移除在连接过程中未被使用的异常处理器
     * 先检查这些块是否不可达
     */
    private static void removeUnusedExcHandlers(MethodNode mth, List<TryCatchBlockAttr> tryBlocks, BlockSet blocks) {
        for (ExceptionHandler eh : mth.getExceptionHandlers()) {
            boolean notProcessed = true;
            BlockNode handlerBlock = eh.getHandlerBlock();
            if (handlerBlock == null || blocks.contains(handlerBlock)) {
                continue;
            }
            for (TryCatchBlockAttr tcb : tryBlocks) {
                if (tcb.getHandlers().contains(eh)) {
                    notProcessed = false;
                    break;
                }
            }
            if (notProcessed) {
                BlockProcessor.removeUnreachableBlock(handlerBlock, mth);
            }
        }
    }
}

