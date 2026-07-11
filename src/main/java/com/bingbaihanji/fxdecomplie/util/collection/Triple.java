package com.bingbaihanji.fxdecomplie.util.collection;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 通用三元组,包含三个元素 该类是一个不可变的记录(record),默认提供了构造器、访问器、equals、hashCode和toString方法
 * 此外,还添加了常用的实用方法,如转换、映射和聚合操作
 *
 * @param <A> 第一个元素的类型
 * @param <B> 第二个元素的类型
 * @param <C> 第三个元素的类型
 */
public record Triple<A, B, C>(A first, B second, C third) {

    /**
     * 创建一个包含指定元素的三元组
     * @param first 第一个元素
     * @param second 第二个元素
     * @param third 第三个元素
     * @param <A> 第一个元素的类型
     * @param <B> 第二个元素的类型
     * @param <C> 第三个元素的类型
     * @return 包含指定元素的三元组
     */
    public static <A, B, C> Triple<A, B, C> of(A first, B second, C third) {
        return new Triple<>(first, second, third);
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
     * 返回一个新三元组,仅替换第一个元素
     * @param first 新的第一个元素
     * @return 新三元组
     */
    public Triple<A, B, C> withFirst(A first) {
        return new Triple<>(first, this.second, this.third);
    }

    /**
     * 返回一个新三元组,仅替换第二个元素
     * @param second 新的第二个元素
     * @return 新三元组
     */
    public Triple<A, B, C> withSecond(B second) {
        return new Triple<>(this.first, second, this.third);
    }

    /**
     * 返回一个新三元组,仅替换第三个元素
     * @param third 新的第三个元素
     * @return 新三元组
     */
    public Triple<A, B, C> withThird(C third) {
        return new Triple<>(this.first, this.second, third);
    }

    /**
     * 对第一个元素应用映射函数,返回新三元组
     * @param mapper 映射函数
     * @param <A2> 新第一个元素的类型
     * @return 映射后的三元组
     */
    public <A2> Triple<A2, B, C> mapFirst(Function<? super A, ? extends A2> mapper) {
        return new Triple<>(mapper.apply(first), second, third);
    }

    /**
     * 对第二个元素应用映射函数,返回新三元组
     * @param mapper 映射函数
     * @param <B2> 新第二个元素的类型
     * @return 映射后的三元组
     */
    public <B2> Triple<A, B2, C> mapSecond(Function<? super B, ? extends B2> mapper) {
        return new Triple<>(first, mapper.apply(second), third);
    }

    /**
     * 对第三个元素应用映射函数,返回新三元组
     * @param mapper 映射函数
     * @param <C2> 新第三个元素的类型
     * @return 映射后的三元组
     */
    public <C2> Triple<A, B, C2> mapThird(Function<? super C, ? extends C2> mapper) {
        return new Triple<>(first, second, mapper.apply(third));
    }

    /**
     * 同时对三个元素应用映射函数,返回新三元组
     * @param firstMapper 第一个元素的映射函数
     * @param secondMapper 第二个元素的映射函数
     * @param thirdMapper 第三个元素的映射函数
     * @param <A2> 新第一个元素的类型
     * @param <B2> 新第二个元素的类型
     * @param <C2> 新第三个元素的类型
     * @return 全部映射后的三元组
     */
    public <A2, B2, C2> Triple<A2, B2, C2> mapAll(Function<? super A, ? extends A2> firstMapper,
                                                  Function<? super B, ? extends B2> secondMapper, Function<? super C, ? extends C2> thirdMapper) {
        return new Triple<>(firstMapper.apply(first), secondMapper.apply(second), thirdMapper.apply(third));
    }

    /**
     * 将三元组转换为包含三个元素的列表 列表的顺序为 [first, second, third]
     * @return 包含三个元素的不可变列表
     */
    public List<Object> toList() {
        return List.of(first, second, third);
    }

    /**
     * 将三元组转换为流
     * @return 包含三个元素的流
     */
    public Stream<Object> stream() {
        return Stream.of(first, second, third);
    }

    /**
     * 对三元组应用一个三元函数,返回结果
     * @param function 接收三个参数并返回结果的函数
     * @param <R> 返回类型
     * @return 函数应用的结果
     */
    public <R> R apply(TripleFunction<? super A, ? super B, ? super C, ? extends R> function) {
        return function.apply(first, second, third);
    }

    @Override
    public String toString() {
        return "Triple[" + "first=" + valueToString(first) + ", second=" + valueToString(second) + ", third="
                + valueToString(third) + ']';
    }

    /**
     * 三元函数接口,用于 {@link Triple#apply(TripleFunction)}
     *
     * @param <A> 第一个参数类型
     * @param <B> 第二个参数类型
     * @param <C> 第三个参数类型
     * @param <R> 返回值类型
     */
    @FunctionalInterface
    public interface TripleFunction<A, B, C, R> {

        R apply(A a, B b, C c);

    }
}