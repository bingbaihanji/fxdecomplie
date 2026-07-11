package com.bingbaihanji.fxdecomplie.util.collection.primitive;

import com.bingbaihanji.fxdecomplie.util.NumberUtil;

import java.util.Arrays;
import java.util.Objects;

/**
 * 通用对象键映射(Map)实现的抽象骨架 
 * 采用开放地址法(线性探测)处理哈希冲突,并自动扩容 
 * 键允许为 null(通过 containsKey 方法会返回 false,实际插入需子类支持) 
 *
 * @param <K> 键的类型
 * @author Matt Coley
 */
abstract class AbstractObjectKeyMap<K> {

    // ---------- 常量 ----------
    /** 默认初始容量(必须为 2 的幂) */
    protected static final int DEFAULT_CAPACITY = 16;

    /** 负载因子,用于计算扩容阈值 */
    protected static final float LOAD_FACTOR = 0.75f;

    // ---------- 字段 ----------
    /** 存储键的数组(元素类型为 Object) */
    protected Object[] keys;

    /** 标记对应位置是否已被占用 */
    protected boolean[] occupied;

    /** 当前映射中键值对的数量 */
    protected int size;

    /** 触发扩容的阈值(容量 × 负载因子) */
    protected int threshold;

    // ---------- 构造方法 ----------

    /** 使用默认初始容量构造映射  */
    protected AbstractObjectKeyMap() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 指定初始容量的构造方法 
     * 容量会被调整为不小于给定值且为 2 的幂 
     *
     * @param initialCapacity 期望的初始容量
     */
    protected AbstractObjectKeyMap(int initialCapacity) {
        // 保证至少为 2,并调整为 2 的幂
        allocateTable(Math.max(2, NumberUtil.nextPowerOfTwo(initialCapacity)));
    }

    // ---------- 公共查询方法 ----------

    /**
     * 判断映射中是否包含指定的键 
     * 如果键为 null,直接返回 false 
     *
     * @param key 要检查的键
     * @return 若存在返回 {@code true},否则 {@code false}
     */
    public final boolean containsKey(K key) {
        return key != null && occupied[findIndex(key)];
    }

    /**
     * 返回映射中键值对的数量 
     *
     * @return 元素个数
     */
    public final int size() {
        return size;
    }

    /**
     * 判断映射是否为空(不含任何键值对) 
     *
     * @return 若空返回 {@code true},否则 {@code false}
     */
    public final boolean isEmpty() {
        return size == 0;
    }

    // ---------- 受保护的内部方法 ----------

    /**
     * 分配指定容量的内部存储空间 
     * 该方法会重置 size 并更新阈值 
     *
     * @param capacity 新的容量(必须为 2 的幂)
     */
    protected final void allocateTable(int capacity) {
        keys = new Object[capacity];
        occupied = new boolean[capacity];
        size = 0;
        threshold = (int) (capacity * LOAD_FACTOR);
    }

    /**
     * 清空所有键(逻辑清除),重置 size,并将键数组置为 null,占用标记置为 false 
     */
    protected final void clearKeys() {
        size = 0;
        Arrays.fill(keys, null);
        Arrays.fill(occupied, false);
    }

    /**
     * 查找给定键在哈希表中的存储位置 
     * 若键存在,返回其实际索引；若不存在,返回第一个可用的空位索引 
     * 调用者需通过 occupied 判断返回值是否为有效键 
     *
     * @param key 要查找的键(非 null)
     * @return 键所在的索引(可能为空位)
     */
    protected final int findIndex(K key) {
        int len = keys.length;
        int idx = hash(key) & (len - 1);
        while (occupied[idx]) {
            if (Objects.equals(keys[idx], key)) {
                return idx;
            }
            idx = (idx + 1) & (len - 1);
        }
        return idx;
    }

    /**
     * 在指定索引位置插入键(该位置必须为空位) 
     * 插入后会增加 size,并在超过阈值时触发扩容 
     *
     * @param index 目标索引(由 findIndex 返回的空位)
     * @param key   要插入的键
     */
    protected final void insertKeyAt(int index, K key) {
        keys[index] = key;
        occupied[index] = true;
        size++;
        if (size > threshold) {
            resize();
        }
    }

    /**
     * 删除指定索引位置的键值对(该位置必须被占用) 
     * 删除后调整后续元素以维持探测链的完整性 
     *
     * @param index 要删除的索引
     */
    protected final void removeEntryAt(int index) {
        size--;
        closeDeletion(index);
    }

    /**
     * 计算键的哈希值(扰动函数),用于减少冲突 
     *
     * @param key 键对象(非 null)
     * @return 扰动后的哈希值
     */
    protected int hash(Object key) {
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    // ---------- 私有辅助方法 ----------

    /**
     * 删除操作后的“闭合删除”处理 
     * 从被删除位置开始向后扫描,将后续冲突元素前移,并清空最后一个被移动的位置 
     *
     * @param deletedIndex 被删除元素的起始索引
     */
    private void closeDeletion(int deletedIndex) {
        int index = deletedIndex;
        int len = keys.length;

        // 从被删除位置的下一个位置开始,遍历所有连续占用的槽位
        for (int next = (index + 1) & (len - 1); occupied[next]; next = (next + 1) & (len - 1)) {
            int slot = hash(keys[next]) & (len - 1);
            // 判断当前 next 元素是否应当前移到 index 位置(即它位于 index 和 next 之间)
            if ((next < slot && (slot <= index || index <= next)) ||
                    (slot <= index && index <= next)) {
                // 将 next 元素移动到 index,并标记占位
                keys[index] = keys[next];
                occupied[index] = true;
                moveValue(next, index); // 子类实现值(value)的迁移
                index = next;           // 继续处理被挖空的位置
            }
        }

        // 最终 index 位置即为需要清空的位置
        keys[index] = null;
        occupied[index] = false;
        clearValue(index); // 子类清理对应的值
    }

    // ---------- 抽象方法(由子类实现) ----------

    /**
     * 清空指定索引位置的值(例如将关联的 value 置为 null 或默认值) 
     *
     * @param index 索引位置
     */
    protected abstract void clearValue(int index);

    /**
     * 将值从源索引迁移到目标索引(在元素前移时调用) 
     *
     * @param from 源索引
     * @param to   目标索引
     */
    protected abstract void moveValue(int from, int to);

    /**
     * 扩容操作,将容量扩至原来的两倍(通常为 2 的幂),并重新散列所有元素 
     */
    protected abstract void resize();

    // ---------- Object 覆写 ----------
    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();
}