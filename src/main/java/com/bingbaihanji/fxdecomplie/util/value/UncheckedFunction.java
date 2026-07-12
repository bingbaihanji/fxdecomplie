package com.bingbaihanji.fxdecomplie.util.value;

import java.util.function.Function;

/**
 * 与 {@link Function} 类似，但允许抛出异常
 *
 * @author xDark
 */
@FunctionalInterface
public interface UncheckedFunction<T, R> extends Function<T, R> {
    /**
     * 将受检异常伪装为非受检异常向外抛出
     *
     * @param t
     * 		要抛出的异常
     * @param <X>
     * 		伪装的异常类型
     *
     * @throws X
     * 		始终抛出给定的异常
     */
    public static <X extends Throwable> void propagate(Throwable t) throws X {
        throw (X) t;
    }

    @Override
    default R apply(T t) {
        try {
            return uncheckedApply(t);
        } catch (Throwable th) {
            propagate(th);
            return null;
        }
    }

    /**
     * @param input
     * 		函数输入
     *
     * @return 函数结果
     *
     * @throws Throwable
     * 		任意异常
     */
    R uncheckedApply(T input) throws Throwable;
}
