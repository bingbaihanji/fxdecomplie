package com.bingbaihanji.fxdecomplie.core.jadx.core.utils;

import java.util.BitSet;

/**
 * 不可变的空 {@link BitSet} 实现
 * <p>
 * 该类提供了一个永远为空的 BitSet 单例 ({@link #EMPTY})
 * 所有查询方法均返回空集合的预期结果 ({@link #cardinality()} 返回 0 
 * {@link #isEmpty()} 返回 {@code true} 等)，
 * 而所有修改方法 ({@code set} {@code and} {@code or} {@code xor} {@code andNot})
 * 均抛出 {@link UnsupportedOperationException}，确保不可变性
 * </p>
 * <p>
 * 主要用于避免在不需要位集合的场景中分配空的 BitSet 对象，
 * 通过共享单例来减少内存开销
 * </p>
 */
public final class EmptyBitSet extends BitSet {

    /** 空的不可变 BitSet 单例，可在任何需要空 BitSet 的场景中复用 */
    public static final BitSet EMPTY = new EmptyBitSet();
    private static final long serialVersionUID = -1194884945157778639L;

    /**
     * 构造一个空的不可变 BitSet
     */
    public EmptyBitSet() {
        super(0);
    }

    /**
     * 返回基数，始终为 0
     *
     * @return 0
     */
    @Override
    public int cardinality() {
        return 0;
    }

    /**
     * 判断是否为空，始终为 {@code true}
     *
     * @return {@code true}
     */
    @Override
    public boolean isEmpty() {
        return true;
    }

    /**
     * 查找从指定索引开始的下一个被设置为 {@code true} 的位索引
     * 由于此 BitSet 始终为空，始终返回 -1
     *
     * @param fromIndex 起始搜索索引 (包含)
     * @return -1，表示没有设置为 {@code true} 的位
     */
    @Override
    public int nextSetBit(int fromIndex) {
        return -1;
    }

    /**
     * 返回此 BitSet 的"逻辑大小"，始终为 0
     *
     * @return 0
     */
    @Override
    public int length() {
        return 0;
    }

    /**
     * 返回此 BitSet 实际使用的空间位数，始终为 0
     *
     * @return 0
     */
    @Override
    public int size() {
        return 0;
    }

    /**
     * 不支持的操作——此 BitSet 不可变
     *
     * @param bitIndex 位索引
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void set(int bitIndex) {
        throw new UnsupportedOperationException();
    }

    /**
     * 不支持的操作——此 BitSet 不可变
     *
     * @param bitIndex 位索引
     * @param value    位值
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void set(int bitIndex, boolean value) {
        throw new UnsupportedOperationException();
    }

    /**
     * 不支持的操作——此 BitSet 不可变
     *
     * @param fromIndex 起始索引 (包含)
     * @param toIndex   结束索引 (不包含)
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void set(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    /**
     * 不支持的操作——此 BitSet 不可变
     *
     * @param fromIndex 起始索引 (包含)
     * @param toIndex   结束索引 (不包含)
     * @param value     位值
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void set(int fromIndex, int toIndex, boolean value) {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取指定索引处的位值，始终返回 {@code false}
     *
     * @param bitIndex 位索引
     * @return {@code false}
     */
    @Override
    public boolean get(int bitIndex) {
        return false;
    }

    /**
     * 获取指定范围的子 BitSet，始终返回空单例
     *
     * @param fromIndex 起始索引 (包含)
     * @param toIndex   结束索引 (不包含)
     * @return {@link #EMPTY} 单例
     */
    @Override
    public BitSet get(int fromIndex, int toIndex) {
        return EMPTY;
    }

    /**
     * 不支持的操作——此 BitSet 不可变
     *
     * @param set 另一个 BitSet
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void and(BitSet set) {
        throw new UnsupportedOperationException();
    }

    /**
     * 不支持的操作——此 BitSet 不可变
     *
     * @param set 另一个 BitSet
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void or(BitSet set) {
        throw new UnsupportedOperationException();
    }

    /**
     * 不支持的操作——此 BitSet 不可变
     *
     * @param set 另一个 BitSet
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void xor(BitSet set) {
        throw new UnsupportedOperationException();
    }

    /**
     * 不支持的操作——此 BitSet 不可变
     *
     * @param set 另一个 BitSet
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void andNot(BitSet set) {
        throw new UnsupportedOperationException();
    }
}
