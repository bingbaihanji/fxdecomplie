/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.bingbaihanji.classgraph.resource;

/**
 * 一个可回收对象实例的 AutoCloseable 包装器通过在 try-with-resources 语句中调用
 * {@link Pool#acquireRecycleOnClose()} 获取，使得当 try 块退出时，获取的实例被回收
 *
 * @param <T>
 *            要回收的类型
 * @param <E>
 *            获取可回收项时可能抛出的异常类型
 */
public class RecycleOnClose<T, E extends Exception> implements AutoCloseable {
    /** 回收器 */
    private final Pool<T, E> Pool;

    /** 实例 */
    private final T instance;

    /**
     * 获取或分配一个实例
     *
     * @param Pool
     *            {@link Pool}
     * @param instance
     *            通过调用回收器的 {@link Pool#acquire()} 获取的对象实例
     * @throws IllegalArgumentException
     *             如果 {@link Pool#newInstance()} 返回了 null
     */
    RecycleOnClose(final Pool<T, E> Pool, final T instance) {
        this.Pool = Pool;
        this.instance = instance;
    }

    /**
     * 获取对象实例
     *
     * @return 对象实例
     */
    public T get() {
        return instance;
    }

    /** 回收一个实例如果该实例实现了 {@link Resettable}，则调用 {@link Resettable#reset()} */
    @Override
    public void close() {
        Pool.recycle(instance);
    }
}