package com.bingbaihanji.fxdecomplie.util.collection.primitive;

/**
 * 基于 {@link AbstractObjectKeyMap} 实现的 {@code Object} 键到 {@code long} 值的映射(Map) 
 * 采用开放地址法处理哈希冲突,支持自动扩容 
 * 键不允许为 null(除 get/getOrDefault/remove 外均会抛出异常,这些方法返回默认值) 
 *
 * @param <K> 键的类型
 * @author Matt Coley
 */
public class Object2LongMap<K> extends AbstractObjectKeyMap<K> {

    // ---------- 字段 ----------
    /** 存储值(与键数组一一对应) */
    private long[] values;

    // ---------- 构造方法 ----------

    /**
     * 使用默认初始容量(16)构造映射 
     */
    public Object2LongMap() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 使用指定初始容量构造映射 
     * 容量会被调整为 2 的幂 
     *
     * @param initialCapacity 期望的初始容量
     */
    public Object2LongMap(int initialCapacity) {
        super(initialCapacity);
        values = new long[keys.length];
    }

    // ---------- 遍历方法 ----------

    /**
     * 遍历映射中的所有键值对,并对每对执行给定的消费者操作 
     *
     * @param consumer 接收键和值的消费者
     */
    public void forEach(ObjectLongConsumer<K> consumer) {
        for (int i = 0; i < keys.length; i++) {
            if (occupied[i]) {
                consumer.accept((K) keys[i], values[i]);
            }
        }
    }

    // ---------- 修改操作 ----------

    /**
     * 将指定键与值放入映射中 
     * 若键已存在,则替换旧值并返回旧值；否则插入新键值对并返回 -1 
     *
     * @param key   键(不允许为 null)
     * @param value 值
     * @return 旧值(若键已存在),否则 -1
     * @throws NullPointerException 若键为 null
     */
    public long put(K key, long value) {
        if (key == null) {
            throw new NullPointerException("Null keys not supported");
        }
        int idx = findIndex(key);
        if (occupied[idx]) {
            long old = values[idx];
            values[idx] = value;
            return old;
        }
        values[idx] = value;
        insertKeyAt(idx, key);
        return -1;
    }

    /**
     * 根据键获取值,若键不存在则返回 -1 
     * 若键为 null,直接返回 -1(不抛出异常) 
     *
     * @param key 键
     * @return 关联的值,若不存在则返回 -1
     */
    public long get(K key) {
        if (key == null) {
            return -1;
        }
        int idx = findIndex(key);
        return occupied[idx] ? values[idx] : -1;
    }

    /**
     * 根据键获取值,若键不存在则返回指定的默认值 
     * 若键为 null,返回默认值 
     *
     * @param key          键
     * @param defaultValue 默认值
     * @return 关联的值或默认值
     */
    public long getOrDefault(K key, long defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        int idx = findIndex(key);
        return occupied[idx] ? values[idx] : defaultValue;
    }

    /**
     * 若键不存在,则使用给定的映射函数计算新值并插入 
     * 若键已存在,则直接返回现有值 
     *
     * @param key     键(不允许为 null)
     * @param mapping 计算值的函数(接收键,返回 long)
     * @return 当前键对应的值(现有值或新计算值)
     * @throws NullPointerException 若键为 null
     */
    public long computeIfAbsent(K key, ObjectLongFunction<K> mapping) {
        if (key == null) {
            throw new NullPointerException("Null keys not supported");
        }
        int idx = findIndex(key);
        if (occupied[idx]) {
            return values[idx];
        }
        long newValue = mapping.apply(key);
        values[idx] = newValue;
        insertKeyAt(idx, key);
        return newValue;
    }

    /**
     * 删除指定键的键值对 
     * 若键为 null 或不存在,则返回 -1 
     *
     * @param key 要删除的键
     * @return 被删除的值,若键不存在则返回 -1
     */
    public long remove(K key) {
        if (key == null) {
            return -1;
        }
        int idx = findIndex(key);
        if (!occupied[idx]) {
            return -1;
        }
        long old = values[idx];
        removeEntryAt(idx);
        return old;
    }

    /**
     * 清空映射中的所有键值对(重置内部状态) 
     * 注意：values 数组未被显式清零,但 size 已重置,旧值不会被访问 
     */
    public void clear() {
        clearKeys();
    }

    // ---------- 受保护的抽象方法实现 ----------
    @Override
    protected void clearValue(int index) {
        values[index] = 0L;
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
        Object[] oldKeys = keys;
        long[] oldValues = values;
        boolean[] oldOccupied = occupied;

        allocateTable(newCap);
        values = new long[newCap];

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldOccupied[i]) {
                put((K) oldKeys[i], oldValues[i]);
            }
        }
    }

    // ---------- Object 覆写 ----------
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Object2LongMap other)) {
            return false;
        }
        if (size() != other.size()) {
            return false;
        }

        for (int i = 0; i < keys.length; i++) {
            if (occupied[i]) {
                K key = (K) keys[i];
                long value = values[i];
                long otherValue = other.get(key);
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
                K key = (K) keys[i];
                long value = values[i];
                hash += key.hashCode() ^ Long.hashCode(value);
            }
        }
        return hash;
    }

    // ---------- 内部函数式接口 ----------

    /**
     * 接收 K 类型键和 long 值的消费者函数式接口 
     *
     * @param <K> 键的类型
     */
    @FunctionalInterface
    public interface ObjectLongConsumer<K> {
        /**
         * 处理一对键值 
         *
         * @param key   键
         * @param value 值
         */
        void accept(K key, long value);
    }

    /**
     * 接收 K 类型键并返回 long 值的映射函数式接口 
     *
     * @param <K> 键的类型
     */
    @FunctionalInterface
    public interface ObjectLongFunction<K> {
        /**
         * 根据键计算新值 
         *
         * @param key 键
         * @return 计算出的 long 值
         */
        long apply(K key);
    }
}