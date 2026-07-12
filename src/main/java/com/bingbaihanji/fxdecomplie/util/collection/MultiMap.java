package com.bingbaihanji.fxdecomplie.util.collection;


import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 简单的多值映射(一个键关联一组值)
 *
 * @author xDark
 */
public final class MultiMap<K, V, C extends Collection<V>> {
    private final Map<K, C> backing;
    private final Function<K, ? extends C> collectionFunction;

    /**
     * @param backing
     * 		底层 Map
     * @param collectionSupplier
     * 		值集合的供给函数
     */
    private MultiMap(Map<K, C> backing, Supplier<? extends C> collectionSupplier) {
        this.backing = backing;
        this.collectionFunction = _ -> collectionSupplier.get();
    }

    /**
     * 创建一个多值映射
     *
     * @param map
     * 		底层 Map
     * @param collectionSupplier
     * 		用于创建值集合的供给函数
     * @param <K>
     * 		键类型
     * @param <V>
     * 		值类型
     * @param <C>
     * 		值集合类型
     *
     * @return 新建的多值映射
     */

    public static <K, V, C extends Collection<V>> MultiMap<K, V, C> from(Map<K, C> map, Supplier<? extends C> collectionSupplier) {
        return new MultiMap<>(map, collectionSupplier);
    }

    /**
     * @return 映射中所有值的总数量
     */
    public int size() {
        return backing.values()
                .stream()
                .mapToInt(Collection::size)
                .sum();
    }

    /**
     * @return 映射为空时返回 {@code true}
     */
    public boolean isEmpty() {
        return backing.values()
                .stream()
                .noneMatch(Collection::isEmpty);
    }

    /**
     * @param key
     * 		要检查的键
     *
     * @return 映射包含该键时返回 {@code true}
     */
    public boolean containsKey(K key) {
        return backing.containsKey(key);
    }

    /**
     * @param value
     * 		要检查的值
     *
     * @return 映射包含该值时返回 {@code true}
     */
    public boolean containsValue(V value) {
        return backing.values()
                .stream()
                .anyMatch(c -> c.contains(value));
    }

    /**
     * @param key
     * 		要获取值集合的键
     *
     * @return 该键对应的值集合(不存在时会创建)
     */

    public C get(K key) {
        return backing.computeIfAbsent(key, collectionFunction);
    }

    /**
     * @param key
     * 		要获取值集合的键
     *
     * @return 该键对应的值集合;若不存在则返回空列表
     */
    @SuppressWarnings("unchecked")

    public Collection<V> getIfPresent(K key) {
        return ((Map<K, Collection<V>>) backing).getOrDefault(key, Collections.emptyList());
    }

    /**
     * @param key
     * 		要获取值集合的键
     *
     * @return 该键对应的值集合;若不存在则返回 {@code defaultValue}
     */
    public C getOrDefault(K key, C defaultValue) {
        return backing.getOrDefault(key, defaultValue);
    }

    /**
     * 向映射中放入一个值
     *
     * @param key
     * 		键
     * @param value
     * 		值
     *
     * @return 值被成功加入集合时返回 {@code true}
     */
    public boolean put(K key, V value) {
        return get(key).add(value);
    }

    /**
     * 向映射中放入一组值
     *
     * @param key
     * 		键
     * @param values
     * 		值集合
     *
     * @return 有任意值被加入集合时返回 {@code true}
     */
    public boolean putAll(K key, Collection<? extends V> values) {
        return get(key).addAll(values);
    }

    /**
     * 移除一个键值对
     *
     * @param key
     * 		键
     * @param value
     * 		值
     *
     * @return 键值对被成功移除时返回 {@code true}
     */
    public boolean remove(K key, V value) {
        C collection = backing.get(key);
        if (collection != null && collection.remove(value) && collection.isEmpty()) {
            backing.remove(key);
            return true;
        }
        return false;
    }

    /**
     * 移除一个键对应的整组值
     *
     * @param key
     * 		要移除的键
     *
     * @return 被移除的值集合;若不存在则返回空列表
     */

    public Collection<V> remove(K key) {
        Collection<V> collection = backing.remove(key);
        if (collection == null) {
            return Collections.emptyList();
        }
        return collection;
    }

    /**
     * 清空映射
     */
    public void clear() {
        backing.clear();
    }

    /**
     * @return 所有键
     */

    public Set<K> keySet() {
        return backing.keySet();
    }

    /**
     * @return 所有值
     */

    public Stream<V> values() {
        return backing.values()
                .stream()
                .flatMap(Collection::stream);
    }

    /**
     * @return Map 的 entry 集合
     */

    public Set<Map.Entry<K, C>> entrySet() {
        return backing.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MultiMap<?, ?, ?> multiMap)) {
            return false;
        }
        return backing.equals(multiMap.backing);
    }

    @Override
    public int hashCode() {
        return backing.hashCode();
    }
}
