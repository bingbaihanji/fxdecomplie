package com.bingbaihanji.fxdecomplie.util.collection;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public record Pair<A, B>(A first, B second) {

    /**
     * 静态工厂方法,用于创建对偶组
     * @param first 第一个元素
     * @param second 第二个元素
     * @param <A> 第一个元素的类型
     * @param <B> 第二个元素的类型
     * @return 包含给定元素的对偶组
     */
    public static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<>(first, second);
    }

    private static String valueToString(Object value) {
        if (value == null) {
            return "null";
        }

        Class<?> clazz = value.getClass();

        // 处理数组(包含基本类型数组)
        if (clazz.isArray()) {
            if (clazz == int[].class) {
                return Arrays.toString((int[]) value);
            }
            if (clazz == long[].class) {
                return Arrays.toString((long[]) value);
            }
            if (clazz == double[].class) {
                return Arrays.toString((double[]) value);
            }
            if (clazz == float[].class) {
                return Arrays.toString((float[]) value);
            }
            if (clazz == boolean[].class) {
                return Arrays.toString((boolean[]) value);
            }
            if (clazz == byte[].class) {
                return Arrays.toString((byte[]) value);
            }
            if (clazz == short[].class) {
                return Arrays.toString((short[]) value);
            }
            if (clazz == char[].class) {
                return Arrays.toString((char[]) value);
            }

            return Arrays.deepToString((Object[]) value);
        }

        return String.valueOf(value);
    }

    /**
     * 返回一个新对偶组,仅替换第一个元素
     * @param first 新的第一个元素
     * @return 新对偶组
     */
    public Pair<A, B> withFirst(A first) {
        return new Pair<>(first, this.second);
    }

    /**
     * 返回一个新对偶组,仅替换第二个元素
     * @param second 新的第二个元素
     * @return 新对偶组
     */
    public Pair<A, B> withSecond(B second) {
        return new Pair<>(this.first, second);
    }

    /**
     * 对第一个元素应用映射函数,返回新对偶组
     * @param mapper 映射函数
     * @param <A2> 新第一个元素的类型
     * @return 映射后的对偶组
     */
    public <A2> Pair<A2, B> mapFirst(Function<? super A, ? extends A2> mapper) {
        return new Pair<>(mapper.apply(first), second);
    }

    /**
     * 对第二个元素应用映射函数,返回新对偶组
     * @param mapper 映射函数
     * @param <B2> 新第二个元素的类型
     * @return 映射后的对偶组
     */
    public <B2> Pair<A, B2> mapSecond(Function<? super B, ? extends B2> mapper) {
        return new Pair<>(first, mapper.apply(second));
    }

    /**
     * 同时对两个元素应用映射函数,返回新对偶组
     * @param firstMapper 第一个元素的映射函数
     * @param secondMapper 第二个元素的映射函数
     * @param <A2> 新第一个元素的类型
     * @param <B2> 新第二个元素的类型
     * @return 全部映射后的对偶组
     */
    public <A2, B2> Pair<A2, B2> mapAll(Function<? super A, ? extends A2> firstMapper,
                                        Function<? super B, ? extends B2> secondMapper) {
        return new Pair<>(firstMapper.apply(first), secondMapper.apply(second));
    }

    /**
     * 将对偶组转换为包含两个元素的列表 列表的顺序为 [first, second]
     * @return 包含两个元素的不可变列表
     */
    public List<Object> toList() {
        return List.of(first, second);
    }

    /**
     * 将对偶组转换为流
     * @return 包含两个元素的流
     */
    public Stream<Object> stream() {
        return Stream.of(first, second);
    }

    /**
     * 交换两个元素的位置,返回新对偶组
     * @return 交换后的对偶组 (second, first)
     */
    public Pair<B, A> swap() {
        return new Pair<>(second, first);
    }

    /**
     * 对对偶组应用一个二元函数,返回结果
     * @param function 接收两个参数并返回结果的函数
     * @param <R> 返回类型
     * @return 函数应用的结果
     */
    public <R> R apply(PairFunction<? super A, ? super B, ? extends R> function) {
        return function.apply(first, second);
    }

    @Override
    public String toString() {
        return "Pair[" + "first=" + valueToString(first) + ", second=" + valueToString(second) + ']';
    }

    /**
     * 二元函数接口,用于 {@link Pair#apply(PairFunction)}
     *
     * @param <A> 第一个参数类型
     * @param <B> 第二个参数类型
     * @param <R> 返回值类型
     */
    @FunctionalInterface
    public interface PairFunction<A, B, R> {

        R apply(A a, B b);

    }

}