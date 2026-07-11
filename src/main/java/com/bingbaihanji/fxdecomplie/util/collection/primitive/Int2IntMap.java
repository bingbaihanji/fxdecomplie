package com.bingbaihanji.fxdecomplie.util.collection.primitive;

import java.util.Arrays;

/**
 * 基于 {@link AbstractIntKeyMap} 实现的 {@code int} 键到 {@code int} 值的映射(Map) 
 * 采用开放地址法处理哈希冲突,支持自动扩容 
 * 键不允许为 null(基本类型),默认值返回 -1 或自定义默认值 
 *
 * @author Matt Coley
 */
public class Int2IntMap extends AbstractIntKeyMap {

    // ---------- 字段 ----------
    /** 存储值(与键数组一一对应) */
    private int[] values;

    // ---------- 构造方法 ----------

    /**
     * 使用默认初始容量(16)构造映射 
     */
    public Int2IntMap() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 使用指定初始容量构造映射 
     * 容量会被调整为 2 的幂 
     *
     * @param initialCapacity 期望的初始容量
     */
    public Int2IntMap(int initialCapacity) {
        super(initialCapacity);
        values = new int[keys.length];
    }

    // ---------- 遍历方法 ----------

    /**
     * 遍历映射中的所有键值对,并对每对执行给定的消费者操作 
     *
     * @param consumer 接收键和值的消费者
     */
    public void forEach(IntIntConsumer consumer) {
        for (int i = 0; i < keys.length; i++) {
            if (occupied[i]) {
                consumer.accept(keys[i], values[i]);
            }
        }
    }

    // ---------- 修改操作 ----------

    /**
     * 对指定键的值进行增量操作 
     * 若键不存在,则视为当前值为 0 
     *
     * @param key    要增量的键
     * @param amount 增量值(可为负数)
     * @return 增量后的新值
     */
    public int increment(int key, int amount) {
        int newValue = getOrDefault(key, 0) + amount;
        put(key, newValue);
        return newValue;
    }

    /**
     * 将指定键与值放入映射中 
     * 若键已存在,则替换旧值并返回旧值 否则插入新键值对并返回 -1 
     *
     * @param key   键
     * @param value 值
     * @return 旧值(若键已存在),否则 -1
     */
    public int put(int key, int value) {
        int idx = findIndex(key);
        if (occupied[idx]) {
            int old = values[idx];
            values[idx] = value;
            return old;
        }

        values[idx] = value;
        insertKeyAt(idx, key);
        return -1;
    }

    /**
     * 根据键获取值,若键不存在则返回 -1 
     *
     * @param key 键
     * @return 关联的值,若不存在则返回 -1
     */
    public int get(int key) {
        int idx = findIndex(key);
        return occupied[idx] ? values[idx] : -1;
    }

    /**
     * 根据键获取值,若键不存在则返回指定的默认值 
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 关联的值或默认值
     */
    public int getOrDefault(int key, int defaultValue) {
        int idx = findIndex(key);
        return occupied[idx] ? values[idx] : defaultValue;
    }

    /**
     * 若键不存在,则使用给定的映射函数计算新值并插入 
     * 若键已存在,则直接返回现有值 
     *
     * @param key     键
     * @param mapping 计算值的函数(接收键,返回新值)
     * @return 当前键对应的值(现有值或新计算值)
     */
    public int computeIfAbsent(int key, IntIntFunction mapping) {
        int idx = findIndex(key);
        if (occupied[idx]) {
            return values[idx];
        }
        int newValue = mapping.apply(key);
        values[idx] = newValue;
        insertKeyAt(idx, key);
        return newValue;
    }

    /**
     * 删除指定键的键值对 
     *
     * @param key 要删除的键
     * @return 被删除的值,若键不存在则返回 -1
     */
    public int remove(int key) {
        int idx = findIndex(key);
        if (!occupied[idx]) {
            return -1;
        }
        int old = values[idx];
        removeEntryAt(idx);
        return old;
    }

    /**
     * 清空映射中的所有键值对(重置内部状态) 
     */
    public void clear() {
        clearKeys();
        Arrays.fill(values, 0);
    }

    // ---------- 受保护的抽象方法实现 ----------
    @Override
    protected void clearValue(int index) {
        values[index] = 0;
    }

    @Override
    protected void moveValue(int from, int to) {
        values[to] = values[from];
    }

    /**
     * 扩容至当前容量的 2 倍,并重新散列所有键值对 
     */
    @Override
    protected void resize() {
        int newCap = keys.length * 2;
        int[] oldKeys = keys;
        int[] oldValues = values;
        boolean[] oldOccupied = occupied;

        allocateTable(newCap);
        values = new int[newCap];

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldOccupied[i]) {
                put(oldKeys[i], oldValues[i]);
            }
        }
    }

    // ---------- Object 覆写 ----------
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Int2IntMap other)) {
            return false;
        }
        if (size != other.size) {
            return false;
        }

        for (int i = 0; i < keys.length; i++) {
            if (occupied[i]) {
                int key = keys[i];
                int value = values[i];
                int otherValue = other.get(key);
                if (otherValue != value) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (int i = 0; i < keys.length; i++) {
            if (occupied[i]) {
                int key = keys[i];
                int value = values[i];
                hash += Integer.hashCode(key) ^ Integer.hashCode(value);
            }
        }
        return hash;
    }

    // ---------- 内部函数式接口 ----------

    /**
     * 接收 int 键和 int 值的消费者函数式接口 
     */
    @FunctionalInterface
    public interface IntIntConsumer {
        /**
         * 处理一对键值 
         *
         * @param key   键
         * @param value 值
         */
        void accept(int key, int value);
    }

    /**
     * 接收 int 键并返回 int 值的映射函数式接口 
     */
    @FunctionalInterface
    public interface IntIntFunction {
        /**
         * 根据键计算新值 
         *
         * @param key 键
         * @return 计算出的值
         */
        int apply(int key);
    }
}