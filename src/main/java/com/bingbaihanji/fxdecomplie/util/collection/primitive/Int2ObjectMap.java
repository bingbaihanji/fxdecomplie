package com.bingbaihanji.fxdecomplie.util.collection.primitive;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * 基于 {@link AbstractIntKeyMap} 实现的 {@code int} 键到 {@code Object} 值的映射(Map) 
 * 采用开放地址法处理哈希冲突,支持自动扩容 
 * 键不允许为 null,值允许为 null 
 *
 * @param <V> 值的类型
 * @author Matt Coley
 */
public class Int2ObjectMap<V> extends AbstractIntKeyMap {

    // ---------- 字段 ----------
    /** 存储值(与键数组一一对应),元素类型为 Object,实际存储 V 类型 */
    private Object[] values;

    // ---------- 构造方法 ----------

    /**
     * 使用默认初始容量(16)构造映射 
     */
    public Int2ObjectMap() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 使用指定初始容量构造映射 
     * 容量会被调整为 2 的幂 
     *
     * @param initialCapacity 期望的初始容量
     */
    public Int2ObjectMap(int initialCapacity) {
        super(initialCapacity);
        values = new Object[keys.length];
    }

    // ---------- 遍历方法 ----------

    /**
     * 遍历映射中的所有键值对,并对每对执行给定的消费者操作 
     *
     * @param consumer 接收键和值的消费者
     */
    public void forEach(IntObjectConsumer<V> consumer) {
        for (int i = 0; i < keys.length; i++) {
            if (occupied[i]) {
                consumer.accept(keys[i], (V) values[i]);
            }
        }
    }

    // ---------- 修改操作 ----------

    /**
     * 将指定键与值放入映射中 
     * 若键已存在,则替换旧值并返回旧值 否则插入新键值对并返回 null 
     *
     * @param key   键
     * @param value 值(允许为 null)
     * @return 旧值(若键已存在),否则 null
     */
    public V put(int key, V value) {
        int idx = findIndex(key);
        if (occupied[idx]) {
            Object old = values[idx];
            values[idx] = value;
            return (V) old;
        }

        values[idx] = value;
        insertKeyAt(idx, key);
        return null;
    }

    /**
     * 根据键获取值,若键不存在则返回 null 
     *
     * @param key 键
     * @return 关联的值,若不存在则返回 null
     */
    public V get(int key) {
        int idx = findIndex(key);
        return occupied[idx] ? (V) values[idx] : null;
    }

    /**
     * 根据键获取值,若键不存在则返回指定的默认值 
     * 注意：默认值类型为 Object,但返回类型为 V,调用者需确保类型匹配 
     *
     * @param key          键
     * @param defaultValue 默认值(可为 null)
     * @return 关联的值或默认值
     */
    public V getOrDefault(int key, Object defaultValue) {
        int idx = findIndex(key);
        return (V) (occupied[idx] ? values[idx] : defaultValue);
    }

    /**
     * 若键不存在,则使用给定的映射函数计算新值并插入 
     * 若键已存在,则直接返回现有值 
     *
     * @param key     键
     * @param mapping 计算值的函数(接收键,返回新值)
     * @return 当前键对应的值(现有值或新计算值)
     */
    public V computeIfAbsent(int key, IntFunction<? extends V> mapping) {
        int idx = findIndex(key);
        if (occupied[idx]) {
            return (V) values[idx];
        }
        V newValue = mapping.apply(key);
        values[idx] = newValue;
        insertKeyAt(idx, key);
        return newValue;
    }

    /**
     * 删除指定键的键值对 
     *
     * @param key 要删除的键
     * @return 被删除的值,若键不存在则返回 null
     */
    public V remove(int key) {
        int idx = findIndex(key);
        if (!occupied[idx]) {
            return null;
        }
        V old = (V) values[idx];
        removeEntryAt(idx);
        return old;
    }

    /**
     * 清空映射中的所有键值对(重置内部状态) 
     */
    public void clear() {
        clearKeys();
        Arrays.fill(values, null);
    }

    // ---------- 受保护的抽象方法实现 ----------
    @Override
    protected void clearValue(int index) {
        values[index] = null;
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
        Object[] oldValues = values;
        boolean[] oldOccupied = occupied;

        allocateTable(newCap);
        values = new Object[newCap];

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldOccupied[i]) {
                put(oldKeys[i], (V) oldValues[i]);
            }
        }
    }

    // ---------- Object 覆写 ----------
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Int2ObjectMap<?> other)) {
            return false;
        }
        if (size() != other.size()) {
            return false;
        }

        for (int i = 0; i < keys.length; i++) {
            if (!occupied[i]) {
                continue;
            }
            int key = keys[i];
            Object value = values[i];
            Object thatValue = other.get(key);
            if (!Objects.equals(value, thatValue)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (int i = 0; i < keys.length; i++) {
            if (!occupied[i]) {
                continue;
            }
            int key = keys[i];
            Object value = values[i];
            hash += Integer.hashCode(key) ^ Objects.hashCode(value);
        }
        return hash;
    }

    // ---------- 内部函数式接口 ----------

    /**
     * 接收 int 键和 V 类型值的消费者函数式接口 
     *
     * @param <V> 值的类型
     */
    @FunctionalInterface
    public interface IntObjectConsumer<V> {
        /**
         * 处理一对键值 
         *
         * @param key   键
         * @param value 值
         */
        void accept(int key, V value);
    }
}