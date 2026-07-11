package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AttrNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.LoopInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.EmptyBitSet;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils.lockList;

/**
 * 基本块节点，表示控制流图中的一个基本块。
 * <p>
 * 包含指令列表、前驱/后继节点关系以及支配树相关信息（支配节点、后支配节点、支配边界等）。
 * </p>
 */
public final class BlockNode extends AttrNode implements IBlock, Comparable<BlockNode> {

    /**
     * 常量标识符，用于唯一标识此块节点
     */
    private final int cid;
    /**
     * 方法字节码中的起始偏移量
     */
    private final int startOffset;
    /**
     * 基本块内包含的指令列表
     */
    private final List<InsnNode> instructions = new ArrayList<>(2);
    /**
     * 在块列表中的位置（便于使用 BitSet 进行位操作）
     */
    private int pos;
    /**
     * 前驱基本块列表（控制流中能到达当前块的块）
     */
    private List<BlockNode> predecessors = new ArrayList<>(1);

    /**
     * 后继基本块列表（控制流中从当前块出发能到达的块）
     */
    private List<BlockNode> successors = new ArrayList<>(1);

    /**
     * 清理后的后继节点列表（排除异常处理器和循环回边目标的副本）
     */
    private List<BlockNode> cleanSuccessors;

    /**
     * 所有支配节点的位集合，不包含自身
     */
    private BitSet doms = EmptyBitSet.EMPTY;

    /**
     * 后支配节点的位集合，不包含自身
     */
    private BitSet postDoms = EmptyBitSet.EMPTY;

    /**
     * 支配边界（Dominator Frontier）
     */
    private BitSet domFrontier;

    /**
     * 直接支配节点（Immediate Dominator）
     */
    private BlockNode idom;

    /**
     * 直接后支配节点（Immediate Post Dominator）
     */
    private BlockNode iPostDom;

    /**
     * 当前块所支配的节点集合
     */
    private List<BlockNode> dominatesOn = new ArrayList<>(3);

    /**
     * 构造一个基本块节点。
     *
     * @param cid    常量标识符
     * @param pos    在块列表中的位置
     * @param offset 方法字节码中的起始偏移量
     */
    public BlockNode(int cid, int pos, int offset) {
        this.cid = cid;
        this.pos = pos;
        this.startOffset = offset;
    }

    /**
     * 批量更新块列表中每个块的位置索引，使其与在列表中的下标一致。
     *
     * @param blocks 待更新位置的块列表
     */
    public static void updateBlockPositions(List<BlockNode> blocks) {
        int count = blocks.size();
        for (int i = 0; i < count; i++) {
            blocks.get(i).setPos(i);
        }
    }

    /**
     * 返回所有非异常处理器且非循环回边目标的后继节点。
     *
     * @param block 待处理的基本块
     * @return 清理后的后继节点列表
     */
    private static List<BlockNode> cleanSuccessors(BlockNode block) {
        List<BlockNode> sucList = block.getSuccessors();
        if (sucList.isEmpty()) {
            return sucList;
        }
        List<BlockNode> toRemove = new ArrayList<>(sucList.size());
        for (BlockNode b : sucList) {
            if (BlockUtils.isExceptionHandlerPath(b)) {
                toRemove.add(b);
            }
        }
        if (block.contains(AFlag.LOOP_END)) {
            List<LoopInfo> loops = block.getAll(AType.LOOP);
            for (LoopInfo loop : loops) {
                toRemove.add(loop.getStart());
            }
        }
        if (toRemove.isEmpty()) {
            return sucList;
        }
        List<BlockNode> result = new ArrayList<>(sucList);
        result.removeAll(toRemove);
        return result;
    }

    /**
     * 获取常量标识符。
     *
     * @return 常量标识符
     */
    public int getCId() {
        return cid;
    }

    /**
     * 已过时，请使用 {@link #getPos()}。
     */
    @Deprecated
    public int getId() {
        return pos;
    }

    /**
     * 获取块在列表中的位置。
     *
     * @return 位置索引
     */
    public int getPos() {
        return pos;
    }

    /**
     * 设置块在列表中的位置。
     *
     * @param id 新的位置索引
     */
    void setPos(int id) {
        this.pos = id;
    }

    /**
     * 获取前驱节点列表。
     *
     * @return 前驱节点列表
     */
    public List<BlockNode> getPredecessors() {
        return predecessors;
    }

    /**
     * 获取后继节点列表。
     *
     * @return 后继节点列表
     */
    public List<BlockNode> getSuccessors() {
        return successors;
    }

    /**
     * 获取清理后的后继节点列表（排除异常处理器和循环回边目标）。
     *
     * @return 清理后的后继节点列表
     */
    public List<BlockNode> getCleanSuccessors() {
        return this.cleanSuccessors;
    }

    /**
     * 重新计算并更新当前块的清理后后继节点列表。
     */
    public void updateCleanSuccessors() {
        cleanSuccessors = cleanSuccessors(this);
    }

    /**
     * 锁定当前块，将各类节点列表转换为不可变列表以防止后续修改。
     * <p>
     * 若支配边界未设置则抛出异常。
     * </p>
     */
    public void lock() {
        try {
            List<BlockNode> successorsList = successors;
            successors = lockList(successorsList);
            cleanSuccessors = successorsList == cleanSuccessors ? this.successors : lockList(cleanSuccessors);
            predecessors = lockList(predecessors);
            dominatesOn = lockList(dominatesOn);
            if (domFrontier == null) {
                throw new JadxRuntimeException("Dominance frontier not set for block: " + this);
            }
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to lock block: " + this, e);
        }
    }

    /**
     * 获取基本块内的指令列表。
     *
     * @return 指令列表
     */
    @Override
    public List<InsnNode> getInstructions() {
        return instructions;
    }

    /**
     * 获取块在方法字节码中的起始偏移量。
     *
     * @return 起始偏移量
     */
    public int getStartOffset() {
        return startOffset;
    }

    /**
     * 检查指定块是否支配当前节点。
     *
     * @param block 待检查的块
     * @return 若 {@code block} 支配当前节点则返回 {@code true}
     */
    public boolean isDominator(BlockNode block) {
        return doms.get(block.getPos());
    }

    /**
     * 获取当前节点的支配节点集合（不包含自身）。
     *
     * @return 支配节点的位集合
     */
    public BitSet getDoms() {
        return doms;
    }

    /**
     * 设置支配节点集合。
     *
     * @param doms 支配节点的位集合
     */
    public void setDoms(BitSet doms) {
        this.doms = doms;
    }

    /**
     * 获取后支配节点集合。
     *
     * @return 后支配节点的位集合
     */
    public BitSet getPostDoms() {
        return postDoms;
    }

    /**
     * 设置后支配节点集合。
     *
     * @param postDoms 后支配节点的位集合
     */
    public void setPostDoms(BitSet postDoms) {
        this.postDoms = postDoms;
    }

    /**
     * 获取支配边界。
     *
     * @return 支配边界的位集合
     */
    public BitSet getDomFrontier() {
        return domFrontier;
    }

    /**
     * 设置支配边界。
     *
     * @param domFrontier 支配边界的位集合
     */
    public void setDomFrontier(BitSet domFrontier) {
        this.domFrontier = domFrontier;
    }

    /**
     * 获取直接支配节点。
     *
     * @return 直接支配节点
     */
    public BlockNode getIDom() {
        return idom;
    }

    /**
     * 设置直接支配节点。
     *
     * @param idom 直接支配节点
     */
    public void setIDom(BlockNode idom) {
        this.idom = idom;
    }

    /**
     * 获取直接后支配节点。
     *
     * @return 直接后支配节点
     */
    public BlockNode getIPostDom() {
        return iPostDom;
    }

    /**
     * 设置直接后支配节点。
     *
     * @param iPostDom 直接后支配节点
     */
    public void setIPostDom(BlockNode iPostDom) {
        this.iPostDom = iPostDom;
    }

    /**
     * 获取当前块所直接支配的节点集合。
     *
     * @return 被支配的节点列表
     */
    public List<BlockNode> getDominatesOn() {
        return dominatesOn;
    }

    /**
     * 向当前块的直接支配节点集合中添加一个块。
     *
     * @param block 被当前块直接支配的块
     */
    public void addDominatesOn(BlockNode block) {
        dominatesOn.add(block);
    }

    /**
     * 判断当前块是否为合成块。
     *
     * @return 若为合成块则返回 {@code true}
     */
    public boolean isSynthetic() {
        return contains(AFlag.SYNTHETIC);
    }

    /**
     * 判断当前块是否为返回块。
     *
     * @return 若为返回块则返回 {@code true}
     */
    public boolean isReturnBlock() {
        return contains(AFlag.RETURN);
    }

    /**
     * 判断当前块是否为方法出口块。
     *
     * @return 若为方法出口块则返回 {@code true}
     */
    public boolean isMthExitBlock() {
        return contains(AFlag.MTH_EXIT_BLOCK);
    }

    /**
     * 判断当前块是否不包含任何指令。
     *
     * @return 若无指令则返回 {@code true}
     */
    public boolean isEmpty() {
        return instructions.isEmpty();
    }

    @Override
    public int hashCode() {
        return cid;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BlockNode)) {
            return false;
        }
        BlockNode other = (BlockNode) obj;
        return cid == other.cid;
    }

    @Override
    public int compareTo(@NotNull BlockNode o) {
        return Integer.compare(cid, o.cid);
    }

    @Override
    public String baseString() {
        return Integer.toString(cid);
    }

    @Override
    public String toString() {
        return "B:" + cid + ':' + InsnUtils.formatOffset(startOffset);
    }
}
