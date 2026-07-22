package com.bingbaihanji.classgraph.utils;

/** 断言工具 */
public final class Assert {
    /**
     * 如果类不是注解，则抛出 {@link IllegalArgumentException}
     *
     * @param clazz
     *            类
     * @throws IllegalArgumentException
     *             如果类不是注解
     */
    public static void isAnnotation(final Class<?> clazz) {
        if (!clazz.isAnnotation()) {
            throw new IllegalArgumentException(clazz + " is not an annotation");
        }
    }

    /**
     * 如果类不是接口，则抛出 {@link IllegalArgumentException}
     *
     * @param clazz
     *            类
     * @throws IllegalArgumentException
     *             如果类不是接口
     */
    public static void isInterface(final Class<?> clazz) {
        if (!clazz.isInterface()) {
            throw new IllegalArgumentException(clazz + " is not an interface");
        }
    }
}
