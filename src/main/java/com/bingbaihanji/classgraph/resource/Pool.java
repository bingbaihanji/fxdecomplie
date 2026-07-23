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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 用于类型 T 的实例回收器，其中实例化该类型可能抛出受检异常 E
 *
 * @param <T>
 *            要回收的类型
 * @param <E>
 *            获取要回收类型的实例时可能抛出的异常，如果没有则为 {@link RuntimeException}
 */
public abstract class Pool<T, E extends Exception> implements AutoCloseable {
    /** 已分配的实例 */
    private final Set<T> usedInstances = Collections.newSetFromMap(new ConcurrentHashMap<T, Boolean>());

    /** 已分配但尚未使用的实例 */
    private final Queue<T> unusedInstances = new ConcurrentLinkedQueue<>();

    /**
     * 创建一个新实例此方法应返回一个非 null 的类型 T 实例，或者抛出一个类型为 E 的异常
     *
     * @return 新实例
     * @throws E
     *             如果在实例化期间抛出了类型为 E 的异常
     */
    public abstract T newInstance() throws E;

    /**
     * 获取一个类型 T 的对象实例，如果可能则重用之前回收的实例，如果没有当前未使用的实例，则分配一个新实例
     *
     * @return 一个新的或回收的对象实例
     * @throws E
     *             如果 {@link #newInstance()} 抛出了类型为 E 的异常
     * @throws NullPointerException
     *             如果 {@link #newInstance()} 返回了 null
     */
    public T acquire() throws E {
        final T instance;
        final T recycledInstance = unusedInstances.poll();
        if (recycledInstance == null) {
            // 分配一个新实例——可能抛出类型为 E 的异常
            final T newInstance = newInstance();
            if (newInstance == null) {
                throw new NullPointerException("Failed to allocate a new recyclable instance");
            }
            instance = newInstance;
        } else {
            // 重用未使用的实例
            instance = recycledInstance;
        }
        usedInstances.add(instance);
        return instance;
    }

    /**
     * 获取一个围绕对象实例的可回收包装器，可用于在 try-with-resources 块结束时回收对象实例
     *
     * @return 一个新的或回收的对象实例
     * @throws E
     *             如果在尝试分配新对象实例时出现问题
     */
    public RecycleOnClose<T, E> acquireRecycleOnClose() throws E {
        return new RecycleOnClose<>(this, acquire());
    }

    /**
     * 回收一个对象，供后续调用 {@link #acquire()} 重用如果该对象是 {@link Resettable} 的实例，则在回收之前会对其调用
     * {@link Resettable#reset()}
     *
     * @param instance
     *            要回收的实例
     * @throws IllegalArgumentException
     *             如果该对象实例最初并非从此 {@link Pool} 获取
     */
    public final void recycle(final T instance) {
        if (instance != null) {
            if (!usedInstances.remove(instance)) {
                throw new IllegalArgumentException("Tried to recycle an instance that was not in use");
            }
            if (instance instanceof Resettable) {
                ((Resettable) instance).reset();
            }
            if (!unusedInstances.add(instance)) {
                throw new IllegalArgumentException("Tried to recycle an instance twice");
            }
        }
    }

    /**
     * 释放所有未使用的实例对任何实现了 {@link AutoCloseable} 的未使用实例调用
     * {@link AutoCloseable#close()}
     *
     * <p>
     * 在调用此 close 方法后，{@link Pool} 仍可继续用于获取新实例，并且此 close 方法将来可再次调用，
     * 即调用此方法的效果就是简单地清空回收器中未使用的实例，并关闭任何 {@link AutoCloseable} 实例
     */
    @Override
    public void close() {
        for (T unusedInstance; (unusedInstance = unusedInstances.poll()) != null; ) {
            if (unusedInstance instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) unusedInstance).close();
                } catch (final Exception e) {
                    // 忽略
                }
            }
        }
    }

    /**
     * 强制关闭此 {@link Pool}，将所有已获取但尚未回收的实例强制移动到未使用实例列表中，然后调用
     * {@link #close()} 关闭任何 {@link AutoCloseable} 实例并丢弃所有实例
     */
    public void forceClose() {
        // 以线程安全的方式将所有元素从 usedInstances 移动到 unusedInstances
        for (final T usedInstance : new ArrayList<>(usedInstances)) {
            if (usedInstances.remove(usedInstance)) {
                unusedInstances.add(usedInstance);
            }
        }
        close();
    }
}
