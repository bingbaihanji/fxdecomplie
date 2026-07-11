package com.bingbaihanji.fxdecomplie.core.jadx.core.utils.blocks;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.EmptyBitSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * 基于 {@link BitSet} 实现的 {@link BlockNode} 块集合
 * <p>
 * 每个块通过其在方法基本块列表中的位置（{@link BlockNode#getPos()}）映射到位图的一位，
 * 从而以极低的内存开销完成集合的增删查、交集、并集等操作
 */
public class BlockSet implements Iterable<BlockNode> {

    /** 所属方法节点 */
    private final MethodNode mth;
    /** 底层位图，每一位对应一个基本块的位置 */
    private final BitSet bs;

    /**
     * 构造块集合
     *
     * @param mth 所属方法节点，用于确定位图大小和进行位置到块的映射
     */
    public BlockSet(MethodNode mth) {
        this.mth = mth;
        this.bs = new BitSet(mth.getBasicBlocks().size());
    }

    /**
     * 创建一个空的块集合
     *
     * @param mth 所属方法节点
     * @return 新建的空块集合
     */
    public static BlockSet empty(MethodNode mth) {
        return new BlockSet(mth);
    }

    /**
     * 根据给定的块集合创建并填充块集合
     *
     * @param mth    所属方法节点
     * @param blocks 初始包含的块集合
     * @return 包含指定块的新块集合
     */
    public static BlockSet from(MethodNode mth, Collection<BlockNode> blocks) {
        BlockSet newBS = new BlockSet(mth);
        newBS.addAll(blocks);
        return newBS;
    }

    /**
     * 判断集合是否包含指定块
     *
     * @param block 待检查的块
     * @return 若包含则返回 true
     */
    public boolean contains(BlockNode block) {
        return bs.get(block.getPos());
    }

    /**
     * 向集合中添加一个块
     *
     * @param block 待添加的块
     */
    public void add(BlockNode block) {
        bs.set(block.getPos());
    }

    /**
     * 批量添加块集合
     *
     * @param blocks 待添加的块集合
     */
    public void addAll(Collection<BlockNode> blocks) {
        blocks.forEach(this::add);
    }

    /**
     * 合并另一个块集合（并集操作）
     *
     * @param otherBlockSet 另一个块集合
     */
    public void addAll(BlockSet otherBlockSet) {
        bs.or(otherBlockSet.bs);
    }

    /**
     * 从集合中移除一个块
     *
     * @param block 待移除的块
     */
    public void remove(BlockNode block) {
        bs.clear(block.getPos());
    }

    /**
     * 批量移除块集合
     *
     * @param blocks 待移除的块集合
     */
    public void remove(Collection<BlockNode> blocks) {
        blocks.forEach(this::remove);
    }

    /**
     * 添加块并返回添加前的存在状态
     *
     * @param block 待添加的块
     * @return 添加前集合是否已包含该块（true 表示原本已存在）
     */
    public boolean addChecked(BlockNode block) {
        int id = block.getPos();
        boolean state = bs.get(id);
        bs.set(id);
        return state;
    }

    /**
     * 判断集合是否包含指定列表中的所有块
     *
     * @param blocks 待检查的块列表
     * @return 若全部包含则返回 true
     */
    public boolean containsAll(List<BlockNode> blocks) {
        for (BlockNode block : blocks) {
            if (!contains(block)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断集合是否与指定列表存在交集
     *
     * @param blocks 待检查的块列表
     * @return 若存在至少一个共同块则返回 true
     */
    public boolean intersects(List<BlockNode> blocks) {
        for (BlockNode block : blocks) {
            if (contains(block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算当前集合与指定块列表的交集
     *
     * @param blocks 参与求交的块列表
     * @return 由共同块组成的新块集合
     */
    public BlockSet intersect(List<BlockNode> blocks) {
        BlockSet input = from(mth, blocks);
        BlockSet result = new BlockSet(mth);
        BitSet resultBS = result.bs;
        resultBS.or(this.bs);
        resultBS.and(input.bs);
        return result;
    }

    /**
     * 判断集合是否为空
     *
     * @return 若不包含任何块则返回 true
     */
    public boolean isEmpty() {
        return bs.isEmpty();
    }

    /**
     * 返回集合中块的数量
     *
     * @return 块的个数
     */
    public int size() {
        return bs.cardinality();
    }

    /**
     * 清空集合中的所有块
     */
    public void remove() {
        bs.clear();
    }

    /**
     * 当集合恰好只包含一个块时返回该块
     *
     * @return 唯一的块 若块数量不为 1 则返回 null
     */
    public @Nullable BlockNode getOne() {
        if (bs.cardinality() == 1) {
            return mth.getBasicBlocks().get(bs.nextSetBit(0));
        }
        return null;
    }

    /**
     * 返回集合中的第一个块（按位置顺序）
     *
     * @return 位置最靠前的块
     */
    public BlockNode getFirst() {
        return mth.getBasicBlocks().get(bs.nextSetBit(0));
    }

    @Override
    public void forEach(Consumer<? super BlockNode> consumer) {
        if (bs.isEmpty()) {
            return;
        }
        List<BlockNode> blocks = mth.getBasicBlocks();
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            consumer.accept(blocks.get(i));
        }
    }

    @Override
    public @NotNull Iterator<BlockNode> iterator() {
        return new BlockSetIterator(bs, size(), mth.getBasicBlocks());
    }

    @Override
    public Spliterator<BlockNode> spliterator() {
        int size = size();
        BlockSetIterator iterator = new BlockSetIterator(bs, size, mth.getBasicBlocks());
        return Spliterators.spliterator(iterator, size, Spliterator.ORDERED | Spliterator.DISTINCT);
    }

    /**
     * 将集合转换为块列表（按位置顺序）
     *
     * @return 包含所有块的列表 集合为空时返回空列表
     */
    public List<BlockNode> toList() {
        if (bs == null || bs == EmptyBitSet.EMPTY) {
            return Collections.emptyList();
        }
        int size = bs.cardinality();
        if (size == 0) {
            return Collections.emptyList();
        }
        List<BlockNode> mthBlocks = mth.getBasicBlocks();
        List<BlockNode> blocks = new ArrayList<>(size);
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            blocks.add(mthBlocks.get(i));
        }
        return blocks;
    }

    @Override
    public String toString() {
        return toList().toString();
    }

    /**
     * {@link BlockSet} 的迭代器实现，按位图中置位的位置顺序遍历对应的块
     */
    private static final class BlockSetIterator implements Iterator<BlockNode> {
        /** 底层位图 */
        private final BitSet bs;
        /** 集合中块的总数量 */
        private final int size;
        /** 位置到块的映射列表（方法的基本块列表） */
        private final List<BlockNode> blocks;

        /** 已返回元素的计数游标 */
        private int cursor;
        /** 下一次查找置位位的起始索引 */
        private int start;

        public BlockSetIterator(BitSet bs, int size, List<BlockNode> blocks) {
            this.bs = bs;
            this.size = size;
            this.blocks = blocks;
        }

        @Override
        public boolean hasNext() {
            return cursor != size;
        }

        @Override
        public BlockNode next() {
            int pos = bs.nextSetBit(start);
            if (pos == -1) {
                throw new NoSuchElementException();
            }
            start = pos + 1;
            cursor++;
            return blocks.get(pos);
        }
    }
}
