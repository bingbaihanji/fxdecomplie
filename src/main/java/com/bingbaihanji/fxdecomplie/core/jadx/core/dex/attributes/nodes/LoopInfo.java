package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.Edge;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 描述控制流图中一个循环结构的信息
 * <p>
 * 包含循环的起始块、结束块以及构成循环体的所有基本块集合，
 * 同时维护循环的编号与父循环引用，用于表示嵌套循环关系
 * </p>
 */
public class LoopInfo {

    /** 循环的起始块（循环头） */
    private final BlockNode start;
    /** 循环的结束块（回边的源块） */
    private final BlockNode end;
    /** 构成循环体的所有基本块集合 */
    private final Set<BlockNode> loopBlocks;

    /** 循环编号 */
    private int id;
    /** 父循环（用于表示嵌套循环关系） */
    private LoopInfo parentLoop;

    /**
     * 构造一个循环信息对象
     *
     * @param start      循环起始块
     * @param end        循环结束块
     * @param loopBlocks 循环体包含的所有基本块
     */
    public LoopInfo(BlockNode start, BlockNode end, Set<BlockNode> loopBlocks) {
        this.start = start;
        this.end = end;
        this.loopBlocks = loopBlocks;
    }

    /**
     * 获取循环起始块
     *
     * @return 循环起始块
     */
    public BlockNode getStart() {
        return start;
    }

    /**
     * 获取循环结束块
     *
     * @return 循环结束块
     */
    public BlockNode getEnd() {
        return end;
    }

    /**
     * 获取循环体包含的所有基本块
     *
     * @return 循环块集合
     */
    public Set<BlockNode> getLoopBlocks() {
        return loopBlocks;
    }

    /**
     * 返回退出边的源块<br>
     * 退出节点属于循环（包含在 {@code loopBlocks} 中）
     *
     * @return 退出边源块集合
     */
    public Set<BlockNode> getExitNodes() {
        Set<BlockNode> nodes = new HashSet<>();
        Set<BlockNode> blocks = getLoopBlocks();
        for (BlockNode block : blocks) {
            // 退出：后继节点不属于本循环（不要改用 getCleanSuccessors）
            for (BlockNode s : block.getSuccessors()) {
                if (!blocks.contains(s) && !s.contains(AType.EXC_HANDLER)) {
                    nodes.add(block);
                }
            }
        }
        return nodes;
    }

    /**
     * 返回循环的退出边
     *
     * @return 退出边列表
     */
    public List<Edge> getExitEdges() {
        List<Edge> edges = new ArrayList<>();
        Set<BlockNode> blocks = getLoopBlocks();
        for (BlockNode block : blocks) {
            for (BlockNode s : block.getSuccessors()) { // 不使用 clean successors，以便包含循环回边
                if (!blocks.contains(s) && !BlockUtils.isExceptionHandlerPath(s)) {
                    edges.add(new Edge(block, s));
                }
            }
        }
        return edges;
    }

    /**
     * 获取循环的前置头块（pre-header）
     *
     * @return 前置头块
     */
    public BlockNode getPreHeader() {
        return BlockUtils.selectOther(end, start.getPredecessors());
    }

    /**
     * 获取循环编号
     *
     * @return 循环编号
     */
    public int getId() {
        return id;
    }

    /**
     * 设置循环编号
     *
     * @param id 循环编号
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * 获取父循环
     *
     * @return 父循环，若无则为 null
     */
    public LoopInfo getParentLoop() {
        return parentLoop;
    }

    /**
     * 设置父循环
     *
     * @param parentLoop 父循环
     */
    public void setParentLoop(LoopInfo parentLoop) {
        this.parentLoop = parentLoop;
    }

    /**
     * 判断指定循环是否为当前循环的祖先循环
     *
     * @param searchLoop 待查找的循环
     * @return 若 searchLoop 是当前循环的某一级父循环则返回 true，否则返回 false
     */
    public boolean hasParent(LoopInfo searchLoop) {
        LoopInfo parent = parentLoop;
        while (true) {
            if (parent == null) {
                return false;
            }
            if (parent == searchLoop) {
                return true;
            }
            parent = parent.getParentLoop();
        }
    }

    /**
     * 返回循环的字符串表示
     */
    @Override
    public String toString() {
        return "LOOP:" + id + ": " + start + "->" + end;
    }
}
