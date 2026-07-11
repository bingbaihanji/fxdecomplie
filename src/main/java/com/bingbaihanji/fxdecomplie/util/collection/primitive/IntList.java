package com.bingbaihanji.fxdecomplie.util.collection.primitive;

import java.util.Arrays;
import java.util.List;

/**
 * 基本类型 {@code int} 的列表(类似 {@link List} 但只支持 int 值) 
 * 动态数组实现,支持自动扩容 
 *
 * @author Matt Coley
 */
public class IntList {

    // ---------- 常量 ----------
    /** 默认初始容量 */
    private static final int DEFAULT_CAPACITY = 16;

    // ---------- 字段 ----------
    /** 存储数据的内部数组 */
    private int[] data;

    /** 当前列表中元素数量 */
    private int size;

    // ---------- 构造方法 ----------

    /**
     * 使用默认初始容量(16)构造列表 
     */
    public IntList() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 使用指定初始容量构造列表 
     * 若传入的容量为负数,则使用默认容量 
     *
     * @param initialCapacity 期望的初始容量
     */
    public IntList(int initialCapacity) {
        if (initialCapacity < 0) {
            initialCapacity = DEFAULT_CAPACITY;
        }
        this.data = new int[initialCapacity];
        this.size = 0;
    }

    // ---------- 公共方法 ----------

    /**
     * 在列表末尾添加一个元素 
     *
     * @param value 要添加的 int 值
     */
    public void add(int value) {
        ensureCapacity(size + 1);
        data[size++] = value;
    }

    /**
     * 获取指定索引处的元素 
     *
     * @param index 索引(从 0 开始)
     * @return 该索引处的 int 值
     * @throws IndexOutOfBoundsException 若索引超出有效范围(size 为 0 时 index 必须为 0)
     */
    public int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return data[index];
    }

    /**
     * 将指定索引处的元素替换为新值 
     *
     * @param index 索引(从 0 开始)
     * @param value 要设置的新值
     * @throws IndexOutOfBoundsException 若索引超出有效范围
     */
    public void set(int index, int value) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        data[index] = value;
    }

    /**
     * 删除指定索引处的元素,并返回被删除的值 
     * 删除后,后续元素会向左移动 
     *
     * @param index 索引(从 0 开始)
     * @return 被删除的 int 值
     * @throws IndexOutOfBoundsException 若索引超出有效范围
     */
    public int removeAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        int removed = data[index];
        System.arraycopy(data, index + 1, data, index, size - index - 1);
        size--;
        return removed;
    }

    /**
     * 返回列表中元素的数量 
     *
     * @return 元素个数
     */
    public int size() {
        return size;
    }

    /**
     * 判断列表是否为空(不含任何元素) 
     *
     * @return 若空返回 {@code true},否则 {@code false}
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 清空列表(移除所有元素),容量保持不变 
     */
    public void clear() {
        size = 0;
    }

    /**
     * 返回包含列表所有元素的数组(按顺序) 
     * 返回的数组为独立副本,修改不影响原列表 
     *
     * @return 包含所有元素的 int 数组
     */
    public int[] toArray() {
        return Arrays.copyOf(data, size);
    }

    // ---------- 私有辅助方法 ----------

    /**
     * 确保内部容量至少为指定的最小容量 
     * 若当前容量不足,则扩容(至少翻倍) 
     *
     * @param minCapacity 需要的最小容量
     */
    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int newCapacity = Math.max(minCapacity, data.length * 2);
            data = Arrays.copyOf(data, newCapacity);
        }
    }

    // ---------- Object 覆写 ----------
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntList other)) {
            return false;
        }
        if (size != other.size) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (data[i] != other.data[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(size);
        for (int i = 0; i < size; i++) {
            result = 31 * result + Integer.hashCode(data[i]);
        }
        return result;
    }
}