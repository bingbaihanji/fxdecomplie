package com.bingbaihanji.fxdecomplie.util.collection.primitive;

/**
 * 基于 {@link AbstractObjectKeyMap} 实现的 {@code Object} 键到 {@code int} 值的映射(Map) 
 * 采用开放地址法处理哈希冲突,支持自动扩容 
 * 键不允许为 null(所有公共方法均会检查) 
 *
 * @param <K> 键的类型
 * @author Matt Coley
 */
public class Object2IntMap<K> extends AbstractObjectKeyMap<K> {

    // ---------- 字段 ----------
    /** 存储值(与键数组一一对应) */
    private int[] values;

    // ---------- 构造方法 ----------

    /**
     * 使用默认初始容量(16)构造映射 
     */
    public Object2IntMap() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 使用指定初始容量构造映射 
     * 容量会被调整为 2 的幂 
     *
     * @param initialCapacity 期望的初始容量
     */
    public Object2IntMap(int initialCapacity) {
        super(initialCapacity);
        values = new int[keys.length];
    }

    // ---------- 遍历与聚合方法 ----------

    /**
     * 遍历映射中的所有键值对,并对每对执行给定的消费者操作 
     *
     * @param consumer 接收键和值的消费者
     */
    public void forEach(ObjectIntConsumer<K> consumer) {
        boolean[] occupied = this.occupied;
        Object[] keys = this.keys;
        int[] values = this.values;
        int end = keys.length;
        for (int i = 0; i < end; i++) {
            if (occupied[i]) {
                consumer.accept((K) keys[i], values[i]);
            }
        }
    }

    /**
     * 计算映射中所有值的总和 
     *
     * @return 所有值的总和
     */
    public int sum() {
        boolean[] occupied = this.occupied;
        int[] values = this.values;
        int end = keys.length;
        int sum = 0;
        for (int i = 0; i < end; i++) {
            if (occupied[i]) {
                sum += values[i];
            }
        }
        return sum;
    }

    // ---------- 修改操作 ----------

    /**
     * 将指定键与值放入映射中 
     * 若键已存在,则替换旧值并返回旧值 否则插入新键值对并返回 -1 
     *
     * @param key   键(不允许为 null)
     * @param value 值
     * @return 旧值(若键已存在),否则 -1
     * @throws NullPointerException 若键为 null
     */
    public int put(K key, int value) {
        if (key == null) {
            throw new NullPointerException("Null keys not supported");
        }
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
     * 对指定键的值进行增量操作 
     * 若键不存在,则插入新键值对,值为增量值 若已存在,则将原值增加 delta 
     *
     * @param key   键(不允许为 null)
     * @param delta 增量(可为负数)
     * @return 增量后的新值(若键原不存在,则返回 delta)
     * @throws NullPointerException 若键为 null
     */
    public int increment(K key, int delta) {
        if (key == null) {
            throw new NullPointerException("Null keys not supported");
        }
        int idx = findIndex(key);
        if (occupied[idx]) {
            values[idx] += delta;
            return values[idx];
        }
        values[idx] = delta;
        insertKeyAt(idx, key);
        return delta;
    }

    /**
     * 根据键获取值,若键不存在则返回 -1 
     * 若键为 null,直接返回 -1(不抛出异常) 
     *
     * @param key 键
     * @return 关联的值,若不存在则返回 -1
     */
    public int get(K key) {
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
    public int getOrDefault(K key, int defaultValue) {
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
     * @param mapping 计算值的函数(接收键,返回 int)
     * @return 当前键对应的值(现有值或新计算值)
     * @throws NullPointerException 若键为 null
     */
    public int computeIfAbsent(K key, ObjectIntFunction<K> mapping) {
        if (key == null) {
            throw new NullPointerException("Null keys not supported");
        }
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
     * 若键为 null 或不存在,则返回 -1 
     *
     * @param key 要删除的键
     * @return 被删除的值,若键不存在则返回 -1
     */
    public int remove(K key) {
        if (key == null) {
            return -1;
        }
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
        // 注意：clearKeys 已将键数组置 null,但 values 数组未被清空,
        // 但 size 已重置,旧值不会被访问,无需显式填充 0 
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
        Object[] oldKeys = keys;
        int[] oldValues = values;
        boolean[] oldOccupied = occupied;

        allocateTable(newCap);
        values = new int[newCap];

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
        if (!(o instanceof Object2IntMap other)) {
            return false;
        }
        if (size() != other.size()) {
            return false;
        }

        for (int i = 0; i < keys.length; i++) {
            if (occupied[i]) {
                K key = (K) keys[i];
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
                K key = (K) keys[i];
                int value = values[i];
                hash += key.hashCode() ^ Integer.hashCode(value);
            }
        }
        return hash;
    }

    // ---------- 内部函数式接口 ----------

    /**
     * 接收 K 类型键和 int 值的消费者函数式接口 
     *
     * @param <K> 键的类型
     */
    @FunctionalInterface
    public interface ObjectIntConsumer<K> {
        /**
         * 处理一对键值 
         *
         * @param key   键
         * @param value 值
         */
        void accept(K key, int value);
    }

    /**
     * 接收 K 类型键并返回 int 值的映射函数式接口 
     *
     * @param <K> 键的类型
     */
    @FunctionalInterface
    public interface ObjectIntFunction<K> {
        /**
         * 根据键计算新值 
         *
         * @param key 键
         * @return 计算出的 int 值
         */
        int apply(K key);
    }
}