package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.LoopInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.Edge;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.util.collection.ArrayMap;
import com.bingbaihanji.fxdecomplie.util.collection.ArraySet;
import com.bingbaihanji.fxdecomplie.util.collection.ListUtils;

import java.util.*;
import java.util.stream.Stream;

/**
 * 表示一个 try-catch 块的属性
 * 封装了 try 块所包含的基础块、异常处理器、以及 try 块之间的嵌套关系
 * 提供了 try 边 ({@link TryEdge})的计算方法，用于确定 try-catch-finally 的控制流离开路径
 */
public class TryCatchBlockAttr implements IJadxAttribute {

    /** try-catch 块的唯一标识 */
    private final int id;
    /** 该 try 块关联的异常处理器列表 */
    private final List<ExceptionHandler> handlers;
    /** 属于该 try 块的基础块列表 */
    private List<BlockNode> blocks;
    /** 外层 try 块 (若当前块嵌套在其它 try 块中) */
    private TryCatchBlockAttr outerTryBlock;
    /** 内层嵌套的 try 块列表 */
    private List<TryCatchBlockAttr> innerTryBlocks = Collections.emptyList();
    /** 标记该 try 块是否已被合并到外层 try 块 */
    private boolean merged = false;
    /** try 块的顶部分割块 (进入 try 体的入口块) */
    private BlockNode topSplitter;

    /**
     * 构造一个 try-catch 块属性
     *
     * @param id       try 块的唯一标识
     * @param handlers 关联的异常处理器列表
     * @param blocks   属于该 try 块的基础块列表
     */
    public TryCatchBlockAttr(int id, List<ExceptionHandler> handlers, List<BlockNode> blocks) {
        this.id = id;
        this.handlers = handlers;
        this.blocks = blocks;

        handlers.forEach(h -> h.setTryBlock(this));
    }

    /**
     * 判断给定的 try 块是否为隐式的或已被合并的
     * 当 try 块已被合并到外层 try 块，或者没有任何异常处理器时返回 true
     *
     * @param tryBlock 待检查的 try-catch 块
     * @return 若该块已合并或无处理器则返回 true
     */
    public static boolean isImplicitOrMerged(TryCatchBlockAttr tryBlock) {
        return tryBlock.isMerged() || tryBlock.getHandlers().isEmpty();
    }

    /**
     * 判断该 try 块是否仅包含一个捕获所有异常的处理器
     *
     * @return 若仅有一个捕获所有异常的处理器则返回 true
     */
    public boolean isAllHandler() {
        return handlers.size() == 1 && handlers.get(0).isCatchAll();
    }

    /**
     * 判断该 try 块是否仅用于抛出异常 (即块内只有 throw、move-exception 或 monitor-exit 指令)
     *
     * @return 若该 try 块仅抛出异常则返回 true
     */
    public boolean isThrowOnly() {
        boolean throwFound = false;
        for (BlockNode block : blocks) {
            List<InsnNode> insns = block.getInstructions();
            if (insns.size() != 1) {
                return false;
            }
            InsnNode insn = insns.get(0);
            switch (insn.getType()) {
                case MOVE_EXCEPTION:
                case MONITOR_EXIT:
                    // 允许的指令
                    break;

                case THROW:
                    throwFound = true;
                    break;

                default:
                    return false;
            }
        }
        return throwFound;
    }

    /**
     * @return try 块的唯一标识
     */
    public int getId() {
        return id;
    }

    /**
     * @return 该 try 块关联的异常处理器列表
     */
    public List<ExceptionHandler> getHandlers() {
        return handlers;
    }

    /**
     * @return 异常处理器的数量
     */
    public int getHandlersCount() {
        return handlers.size();
    }

    /**
     * @return 属于该 try 块的基础块列表
     */
    public List<BlockNode> getBlocks() {
        return blocks;
    }

    /**
     * 设置属于该 try 块的基础块列表
     *
     * @param blocks 基础块列表
     */
    public void setBlocks(List<BlockNode> blocks) {
        this.blocks = blocks;
    }

    /**
     * 清空该 try 块，移除所有基础块并将所有处理器标记为待删除
     */
    public void clear() {
        blocks.clear();
        handlers.forEach(ExceptionHandler::markForRemove);
        handlers.clear();
    }

    /**
     * 从该 try 块中移除指定的基础块
     *
     * @param block 待移除的基础块
     */
    public void removeBlock(BlockNode block) {
        blocks.remove(block);
    }

    /**
     * 从该 try 块中移除指定的异常处理器，并将其标记为待删除
     *
     * @param handler 待移除的异常处理器
     */
    public void removeHandler(ExceptionHandler handler) {
        handlers.remove(handler);
        handler.markForRemove();
    }

    /**
     * @return 内层嵌套的 try 块列表
     */
    public List<TryCatchBlockAttr> getInnerTryBlocks() {
        return innerTryBlocks;
    }

    /**
     * 添加一个内层嵌套的 try 块
     *
     * @param inner 内层 try 块
     */
    public void addInnerTryBlock(TryCatchBlockAttr inner) {
        if (this.innerTryBlocks.isEmpty()) {
            this.innerTryBlocks = new ArrayList<>();
        }
        this.innerTryBlocks.add(inner);
    }

    /**
     * @return 外层 try 块 (若当前块嵌套在其它 try 块中)
     */
    public TryCatchBlockAttr getOuterTryBlock() {
        return outerTryBlock;
    }

    /**
     * 设置外层 try 块
     *
     * @param outerTryBlock 外层 try 块
     */
    public void setOuterTryBlock(TryCatchBlockAttr outerTryBlock) {
        this.outerTryBlock = outerTryBlock;
    }

    /**
     * @return try 块的顶部分割块 (进入 try 体的入口块)
     */
    public BlockNode getTopSplitter() {
        return topSplitter;
    }

    /**
     * 设置 try 块的顶部分割块
     *
     * @param topSplitter 顶部分割块
     */
    public void setTopSplitter(BlockNode topSplitter) {
        this.topSplitter = topSplitter;
    }

    /**
     * @return 该 try 块是否已被合并到外层 try 块
     */
    public boolean isMerged() {
        return merged;
    }

    /**
     * 设置该 try 块是否已被合并到外层 try 块
     *
     * @param merged 是否已合并
     */
    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    /**
     * @return try 块的唯一标识
     */
    public int id() {
        return id;
    }

    /**
     * 计算所有异常处理器对应的 try 边 ({@link TryEdge})
     * 每条边表示从 try 体到某个异常处理器的控制流路径
     *
     * @return 指向异常处理器的 try 边列表
     */
    public List<TryEdge> getHandlerTryEdges() {
        List<ExceptionHandler> mergedHandlers = getMergedHandlers();
        List<TryEdge> edges = new ArrayList<>(mergedHandlers.size());
        for (ExceptionHandler handler : mergedHandlers) {
            BlockNode handlerBlock = handler.getHandlerBlock();
            BlockNode handlerSplitter = handler.getBottomSplitter();
            if (handlerSplitter == null) {
                // 如果找不到底部分割块，可能根本不存在此时假定该 try-catch 的顶部分割块即为异常出口的来源
                List<BlockNode> allChildren = ListUtils.filter(handlerBlock.getPredecessors(), blk -> getBlocks().contains(blk));
                handlerSplitter = BlockUtils.getBottomBlock(allChildren);
                if (handlerSplitter == null) {
                    handlerSplitter = getTopSplitter();
                }
            }
            TryEdge edge = new TryEdge(handlerSplitter, handlerBlock, handler);
            edges.add(edge);
        }
        return edges;
    }

    /**
     * 计算所有非处理器 (fallthrough，即正常离开 try 体)的 try 边
     *
     * @return fallthrough 类型的 try 边列表
     */
    public List<TryEdge> getFallthroughTryEdges() {
        List<TryEdge> edges = new LinkedList<>();
        List<BlockNode> exploredBlocks = new ArrayList<>();
        List<TryCatchBlockAttr> exploredTrys = new LinkedList<>();

        getFallthroughTryEdges(edges, exploredBlocks, exploredTrys);
        return edges;
    }

    /**
     * 计算 fallthrough 类型的 try 边，并将结果累积到给定集合中 (用于递归处理嵌套 try 块)
     *
     * @param edges          用于收集 try 边的列表
     * @param exploredBlocks 已探索过的基础块，避免重复计算
     * @param exploredTrys   已探索过的 try 块，避免无限递归
     */
    public void getFallthroughTryEdges(List<TryEdge> edges, List<BlockNode> exploredBlocks, List<TryCatchBlockAttr> exploredTrys) {
        List<ExceptionHandler> mergedHandlers = getMergedHandlers();
        Set<BlockNode> searchBlocks = new ArraySet<>(getBlocks());
        for (ExceptionHandler handler : mergedHandlers) {
            handler.getBlocks().forEach(searchBlocks::remove);
        }
        BlockNode sourceBlock = BlockUtils.getTopBlock(new ArrayList<>(searchBlocks));
        if (sourceBlock != null) {
            exploredTrys.add(this);
            exploreTryPath(edges, sourceBlock, searchBlocks, exploredBlocks, exploredTrys);
        }
    }

    /**
     * 获取该 try 块的所有 try 边，包括指向异常处理器的边和 fallthrough 边
     *
     * @return 不可修改的 try 边列表
     */
    public List<TryEdge> getTryEdges() {
        List<TryEdge> handlerEdges = getHandlerTryEdges();
        List<TryEdge> fallthroughEdges = getFallthroughTryEdges();
        List<TryEdge> edges = new ArrayList<>(handlerEdges.size() + fallthroughEdges.size());
        edges.addAll(handlerEdges);
        edges.addAll(fallthroughEdges);
        return Collections.unmodifiableList(edges);
    }

    /**
     * 从给定的起始块出发，深度优先探索 try 体的控制流路径，识别各类离开 try 的边 (fallthrough、循环退出、提前退出等)
     *
     * @param edges          用于收集识别出的 try 边的列表
     * @param blk            当前正在探索的基础块
     * @param searchBlocks   属于该 try 体 (不含处理器块)的基础块集合
     * @param exploredBlocks 已探索过的基础块，避免重复计算
     * @param exploredTrys   已探索过的 try 块，避免无限递归
     */
    private void exploreTryPath(List<TryEdge> edges, BlockNode blk, Set<BlockNode> searchBlocks, List<BlockNode> exploredBlocks,
                                List<TryCatchBlockAttr> exploredTrys) {
        for (BlockNode successor : blk.getSuccessors()) {
            // 如果另一分支已经探索过该块，则无需重新计算其出口
            if (exploredBlocks.contains(successor)) {
                continue;
            }

            // 如果这是一个底部分割块，则忽略——我们只关心非处理器的边
            if (successor.contains(AFlag.EXC_BOTTOM_SPLITTER)) {
                continue;
            }

            exploredBlocks.add(successor);

            if (successor.contains(AFlag.LOOP_END)) {
                var loopsAttrList = successor.get(AType.LOOP);
                List<LoopInfo> loops = loopsAttrList.getList();
                List<BlockNode> loopStartBlocks = new LinkedList<>();
                for (LoopInfo loop : loops) {
                    loopStartBlocks.add(loop.getStart());
                    List<Edge> loopEdges = loop.getExitEdges();
                    for (Edge loopEdge : loopEdges) {
                        if (loopEdge.getTarget() == successor) {
                            loopStartBlocks.add(loopEdge.getSource());
                        }
                    }
                }
                boolean includesAllLoopStart = ListUtils.allMatch(loopStartBlocks, exploredBlocks::contains);
                if (!includesAllLoopStart) {
                    edges.add(new TryEdge(blk, successor, TryEdgeType.LOOP_EXIT));
                    continue;
                }
            }

            boolean isPathToAnySearchBlock = false;
            for (BlockNode searchBlock : searchBlocks) {
                if (BlockUtils.isPathExists(successor, searchBlock)) {
                    isPathToAnySearchBlock = true;
                    break;
                }
            }
            if (!searchBlocks.contains(successor) && !isPathToAnySearchBlock) {
                // 该块不包含在此 try 的块列表中这可能是因为它是 try 的一个出口，
                // 或者它是一个通向出口的块 (例如异常处理器)

                // 如果该块 (successor)通向一个出口，那么"所有 try 块加上该块"的底部块
                // 将等于"所有 try 块"的底部块如果该块本身就是一个出口，则要么：
                // - 不存在从所有 try 块到该块的路径，从而使底部块为 null
                // - 存在从所有 try 块到该块的路径，但之后没有更多 try 块，从而使底部块就是该块
                List<BlockNode> allBlocksWithCurrent = new ArrayList<>(getBlocks().size() + 1);
                allBlocksWithCurrent.addAll(getBlocks());
                allBlocksWithCurrent.add(successor);
                BlockNode bottomBlock = BlockUtils.getBottomBlock(allBlocksWithCurrent);

                if (!(bottomBlock == null || bottomBlock == successor)) {
                    // 该块通向一个出口
                    exploreTryPath(edges, successor, searchBlocks, exploredBlocks, exploredTrys);
                    continue;
                }

                BlockNode emptyPathEndOfSuccessor = BlockUtils.followEmptyPath(successor, false, false);

                if (emptyPathEndOfSuccessor.contains(AFlag.EXC_TOP_SPLITTER)) {
                    // 该块是一个进入另一个 try-catch 的出口在这种情况下，下一个 try-catch 处于同一作用域内
                    // 因此，我们会取出该 try 的所有边，并将它们添加到当前 try 的边列表中
                    Set<TryCatchBlockAttr> nestedTrys = new ArraySet<>();
                    List<BlockNode> allSuccessorsOnTryBody = ListUtils.filter(emptyPathEndOfSuccessor.getSuccessors(),
                            potentialTryBlock -> potentialTryBlock.contains(AFlag.TRY_ENTER));
                    for (BlockNode tryBodyEnter : allSuccessorsOnTryBody) {
                        TryCatchBlockAttr nestedTry = tryBodyEnter.get(AType.TRY_BLOCK);
                        if (nestedTry == null) {
                            continue;
                        }

                        // 如果我们已经添加过某个 try 的边，则跳过它以避免无限递归
                        if (exploredTrys.contains(nestedTry)) {
                            continue;
                        }

                        // 不确定为何这些顶部分割块必须相同才能被视为"嵌套" try，但这样似乎是可行的 (？)
                        if (nestedTry.getTopSplitter() != getTopSplitter()) {
                            continue;
                        }

                        nestedTrys.add(nestedTry);
                    }

                    // 仅当存在嵌套内层 try 时才尝试添加如果不存在，则对该边执行常规处理
                    if (!nestedTrys.isEmpty()) {
                        for (TryCatchBlockAttr nestedTry : nestedTrys) {
                            nestedTry.getFallthroughTryEdges(edges, exploredBlocks, exploredTrys);
                        }
                        continue;
                    }
                }

                if (bottomBlock == null) {
                    // 该块是一个在所有 try 块逻辑执行完毕之前就发生的出口
                    edges.add(new TryEdge(blk, successor, TryEdgeType.PREMATURE_EXIT));
                } else if (bottomBlock == successor) {
                    // 该块是一个在所有 try 块逻辑执行完毕之后才发生的出口
                    edges.add(new TryEdge(blk, successor, TryEdgeType.TRUE_FALLTHROUGH));
                } else {
                    // 所有可能的情况都应已被上面的 if / else 及前面的 if 捕获
                    // 如果执行到此处，则对该算法所做的任何修改都必须在执行之前妥善处理所有可能的代码路径
                    throw new JadxRuntimeException(
                            "Unexpected code execution branch taken during try edge resolution: blk="
                                    + blk + ",successor=" + successor);
                }
            } else {
                exploreTryPath(edges, successor, searchBlocks, exploredBlocks, exploredTrys);
            }
        }
    }

    /**
     * 获取合并后的异常处理器列表，包含当前 try 块及其所有内层 try 块的处理器
     *
     * @return 不可修改的合并处理器列表
     */
    public List<ExceptionHandler> getMergedHandlers() {
        boolean hasInnerBlocks = !getInnerTryBlocks().isEmpty();
        List<ExceptionHandler> mergedHandlers;
        if (hasInnerBlocks) {
            // 收集当前块及所有内层块的处理器
            //  (目前有意不使用递归收集)
            mergedHandlers = new ArrayList<>(getHandlers());
            for (TryCatchBlockAttr innerTryBlock : getInnerTryBlocks()) {
                mergedHandlers.addAll(innerTryBlock.getHandlers());
            }
        } else {
            mergedHandlers = getHandlers();
        }
        return Collections.unmodifiableList(mergedHandlers);
    }

    /**
     * 构建 try 边到其目标基础块的映射
     *
     * @return try 边到目标块的映射
     */
    public Map<TryEdge, BlockNode> getEdgeBlockMap() {
        List<TryEdge> edges = getTryEdges();
        Map<TryEdge, BlockNode> blockMap = new ArrayMap<>();
        for (TryEdge edge : edges) {
            blockMap.put(edge, edge.getTarget());
        }
        return blockMap;
    }

    /**
     * 计算该 try 块的执行作用域分组，用于分析 try-catch-finally 的作用域结构
     *
     * @param mth 所属方法节点
     * @return try 边作用域分组映射
     */
    public TryEdgeScopeGroupMap getExecutionScopeGroups(MethodNode mth) {
        Map<TryEdge, BlockNode> handlerBlocks = getEdgeBlockMap();
        TryEdgeScopeGroupMap scopeGroups = new TryEdgeScopeGroupMap(mth, this, handlerBlocks.size());
        scopeGroups.populateFromEdges(handlerBlocks);

        return scopeGroups;
    }

    /**
     * 获取处理器的 fallthrough 分组，即以作用域结束块为键、对应 try 边列表为值的映射
     *
     * @param mth         所属方法节点
     * @param scopeGroups try 边作用域分组映射
     * @return 作用域结束块到 try 边列表的映射
     */
    public Map<BlockNode, List<TryEdge>> getHandlerFallthroughGroups(MethodNode mth, TryEdgeScopeGroupMap scopeGroups) {
        return scopeGroups.getScopeEnds(mth);
    }

    /**
     * 根据 fallthrough 分组，查找可作为 finally 块搜索起点的基础块列表
     *
     * @param mth               所属方法节点
     * @param finallyHandler    finally 异常处理器
     * @param fallthroughGroups fallthrough 分组映射
     * @return 搜索起始基础块列表
     */
    public List<BlockNode> getSearchBlocksFromFallthroughGroups(MethodNode mth, ExceptionHandler finallyHandler,
                                                                Map<BlockNode, List<TryEdge>> fallthroughGroups) {

        List<BlockNode> searchBlocks = new LinkedList<>();
        for (Map.Entry<BlockNode, List<TryEdge>> entry : fallthroughGroups.entrySet()) {
            BlockNode scopeEndBlock = entry.getKey();
            List<TryEdge> sourceHandlers = entry.getValue();

            for (BlockNode scopeEndPredecessor : scopeEndBlock.getPredecessors()) {
                // 添加作用域结束块的所有前驱块，这些前驱块连接到某个处理器的作用域起点
                try (Stream<TryEdge> stream = sourceHandlers.stream()) {
                    Object[] matchedHandlerPaths =
                            stream.filter(handler -> !(handler.isHandlerExit() && handler.getExceptionHandler() == finallyHandler))
                                    .map(handler -> handler.getTarget())
                                    .filter(scopeStart -> BlockUtils.isPathExists(scopeStart, scopeEndPredecessor))
                                    .toArray();
                    if (matchedHandlerPaths.length != 0) {
                        searchBlocks.add(scopeEndPredecessor);
                    }
                }
            }
        }
        return searchBlocks;
    }

    @Override
    public IJadxAttrType<? extends IJadxAttribute> getAttrType() {
        return AType.TRY_BLOCK;
    }

    @Override
    public int hashCode() {
        return handlers.hashCode() + 31 * blocks.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TryCatchBlockAttr other = (TryCatchBlockAttr) obj;
        return id == other.id
                && handlers.equals(other.handlers)
                && blocks.equals(other.blocks);
    }

    @Override
    public String toString() {
        if (merged) {
            return "Merged into " + outerTryBlock;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("TryCatch #").append(id).append(" {").append(Utils.listToString(handlers));
        sb.append(", blocks: (").append(Utils.listToString(blocks)).append(')');
        if (topSplitter != null) {
            sb.append(", top: ").append(topSplitter);
        }
        if (outerTryBlock != null) {
            sb.append(", outer: #").append(outerTryBlock.id);
        }
        if (!innerTryBlocks.isEmpty()) {
            sb.append(", inners: ").append(Utils.listToString(innerTryBlocks, inner -> "#" + inner.id));
        }
        sb.append(" }");
        return sb.toString();
    }
}

