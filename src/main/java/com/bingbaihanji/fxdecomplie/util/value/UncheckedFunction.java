package com.bingbaihanji.fxdecomplie.util.value;

import java.util.function.Function;

/**
 * Its {@link Function} but can throw an exception.
 *
 * @author xDark
 */
@FunctionalInterface
public interface UncheckedFunction<T, R> extends Function<T, R> {
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
     * 		Function input.
     *
     * @return The function result.
     *
     * @throws Throwable
     * 		Whenever.
     */
    R uncheckedApply(T input) throws Throwable;
}
