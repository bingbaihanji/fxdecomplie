package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.maker;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.EdgeInsnAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.LoopInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.IfNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.SwitchInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnContainer;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.Region;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.blocks.BlockSet;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxOverflowException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils.getNextBlock;

/**
 * 区域构建器
 * <p>
 * 负责将方法的基本块（{@link BlockNode}）转换为结构化的区域（{@link Region}）树，
 * 作为反编译流程中控制流重建的核心组件内部委托 {@link IfRegionMaker}、
 * {@link LoopRegionMaker}、{@link SwitchRegionMaker}、{@link SynchronizedRegionMaker}
 * 等子构建器处理各类控制流结构
 * </p>
 */
public class RegionMaker {
    private final MethodNode mth;
    private final RegionStack stack;

    private final IfRegionMaker ifMaker;
    private final LoopRegionMaker loopMaker;

    private final BlockSet processedBlocks;
    private final int regionsLimit;

    private int regionsCount;

    /**
     * 构造区域构建器
     *
     * @param mth 当前正在处理的方法节点
     */
    public RegionMaker(MethodNode mth) {
        this.mth = mth;
        this.stack = new RegionStack(mth);
        this.ifMaker = new IfRegionMaker(mth, this);
        this.loopMaker = new LoopRegionMaker(mth, this, ifMaker);
        this.processedBlocks = BlockSet.empty(mth);
        this.regionsLimit = mth.getBasicBlocks().size() * 400;
    }

    /**
     * 从方法入口块开始构建方法级区域
     *
     * @return 方法对应的顶层 {@link Region}
     */
    public Region makeMthRegion() {
        return makeRegion(mth.getEnterBlock());
    }

    /**
     * 从指定的基本块开始构建区域
     * <p>
     * 如果起始块已在退出集合中，则插入边界指令并返回 如果起始块已被处理过，
     * 则标记为代码重复（指令会在反编译输出中被复制），并允许继续处理
     * </p>
     *
     * @param startBlock 区域构建的起始基本块
     * @return 构建完成的 {@link Region}
     */
    Region makeRegion(BlockNode startBlock) {
        Objects.requireNonNull(startBlock);
        Region region = new Region(stack.peekRegion());
        if (stack.containsExit(startBlock)) {
            insertEdgeInsns(region, startBlock);
            return region;
        }
        if (processedBlocks.addChecked(startBlock)) {
            // 将块添加到多个区域（在反编译代码中复制指令），并允许继续处理
            if (!startBlock.contains(AFlag.DUPLICATED)) {
                mth.addWarnComment("Code duplicated, block: " + startBlock + ' ' + startBlock.getAttributesString());
                startBlock.add(AFlag.DUPLICATED);
            }
        }
        BlockNode next = startBlock;
        while (next != null) {
            next = traverse(region, next);
            regionsCount++;
            if (regionsCount > regionsLimit) {
                throw new JadxOverflowException("Regions count limit reached at block " + startBlock);
            }
        }
        return region;
    }

    /**
     * 从指定块开始递归遍历所有基本块，直到遇到退出集合中的块为止
     *
     * @param r     当前区域
     * @param block 当前正在遍历的基本块
     * @return 下一个待处理的基本块，如果遍历结束则返回 {@code null}
     */
    private @Nullable BlockNode traverse(Region r, BlockNode block) {
        if (block.contains(AFlag.MTH_EXIT_BLOCK)) {
            return null;
        }
        BlockNode next = null;
        boolean processed = false;

        List<LoopInfo> loops = block.getAll(AType.LOOP);
        int loopCount = loops.size();
        if (loopCount != 0 && block.contains(AFlag.LOOP_START)) {
            if (loopCount == 1) {
                next = loopMaker.process(r, loops.get(0), stack);
                processed = true;
            } else {
                for (LoopInfo loop : loops) {
                    if (loop.getStart() == block) {
                        next = loopMaker.process(r, loop, stack);
                        processed = true;
                        break;
                    }
                }
            }
        }

        InsnNode insn = BlockUtils.getLastInsn(block);
        if (!processed && insn != null) {
            switch (insn.getType()) {
                case IF:
                    next = ifMaker.process(r, block, (IfNode) insn, stack);
                    processed = true;
                    break;

                case SWITCH:
                    SwitchRegionMaker switchMaker = new SwitchRegionMaker(mth, this);
                    next = switchMaker.process(r, block, (SwitchInsn) insn, stack);
                    processed = true;
                    break;

                case MONITOR_ENTER:
                    SynchronizedRegionMaker syncMaker = new SynchronizedRegionMaker(mth, this);
                    next = syncMaker.process(r, block, insn, stack);
                    processed = true;
                    break;
            }
        }
        if (!processed) {
            r.add(block);
            next = getNextBlock(block);
        }
        if (next != null && !stack.containsExit(block) && !stack.containsExit(next)) {
            return next;
        }
        return null;
    }

    /**
     * 向区域中插入边界指令（如 {@code break}、{@code continue}）
     * <p>
     * 当遍历到达退出块时，将块上携带的边界指令按类型（先 break 后 continue）
     * 插入到区域中，以保证控制流语义正确
     * </p>
     *
     * @param region    目标区域
     * @param exitBlock 退出基本块
     */
    private void insertEdgeInsns(Region region, BlockNode exitBlock) {
        List<EdgeInsnAttr> edgeInsns = exitBlock.getAll(AType.EDGE_INSN);
        if (edgeInsns.isEmpty()) {
            return;
        }
        List<InsnNode> insns = new ArrayList<>(edgeInsns.size());
        addOneInsnOfType(insns, edgeInsns, InsnType.BREAK);
        addOneInsnOfType(insns, edgeInsns, InsnType.CONTINUE);
        region.add(new InsnContainer(insns));
    }

    /**
     * 从边界指令列表中提取指定类型的指令并添加到结果列表中（每种类型最多添加一条）
     *
     * @param insns      结果指令列表
     * @param edgeInsns  边界指令属性列表
     * @param insnType   要提取的指令类型
     */
    private void addOneInsnOfType(List<InsnNode> insns, List<EdgeInsnAttr> edgeInsns, InsnType insnType) {
        for (EdgeInsnAttr edgeInsn : edgeInsns) {
            InsnNode insn = edgeInsn.getInsn();
            if (insn.getType() == insnType) {
                insns.add(insn);
                return;
            }
        }
    }

    /**
     * 获取当前的区域栈
     *
     * @return 区域栈实例
     */
    RegionStack getStack() {
        return stack;
    }

    /**
     * 判断指定基本块是否已被处理过
     *
     * @param block 待检查的基本块
     * @return 如果该块已被处理返回 {@code true}
     */
    boolean isProcessed(BlockNode block) {
        return processedBlocks.contains(block);
    }

    /**
     * 清除指定基本块的已处理状态，使其可被重新处理
     *
     * @param block 待清除处理状态的基本块
     */
    void clearBlockProcessedState(BlockNode block) {
        processedBlocks.remove(block);
    }
}
