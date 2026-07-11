package com.bingbaihanji.fxdecomplie.core.jadx.core.utils;

/**
 * 简单的不可变二元组容器，存放两个同类型的值。
 * <p>
 * 该类是不可变的（两个字段均为 final），正确实现了
 * {@link #equals(Object)}、{@link #hashCode()} 和 {@link #toString()}，
 * 适合用作 Map 的键或集合中的元素。
 * </p>
 *
 * @param <T> 两个元素的类型
 */
public class Pair<T> {

    /** 第一个元素 */
    private final T first;
    /** 第二个元素 */
    private final T second;

    /**
     * 使用给定的两个值构造一个 Pair。
     *
     * @param first  第一个元素
     * @param second 第二个元素
     */
    public Pair(T first, T second) {
        this.first = first;
        this.second = second;
    }

    /**
     * 获取第一个元素。
     *
     * @return 第一个元素
     */
    public T getFirst() {
        return first;
    }

    /**
     * 获取第二个元素。
     *
     * @return 第二个元素
     */
    public T getSecond() {
        return second;
    }

    /**
     * 基于两个元素的值进行相等性比较。
     *
     * @param o 要比较的对象
     * @return 如果两个元素均相等则返回 {@code true}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pair)) {
            return false;
        }
        Pair<?> other = (Pair<?>) o;
        return first.equals(other.first) && second.equals(other.second);
    }

    /**
     * 基于两个元素的值计算哈希码。
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return first.hashCode() + 31 * second.hashCode();
    }

    /**
     * 返回形如 {@code (first, second)} 的字符串表示。
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return "(" + first + ", " + second + ')';
    }
}
