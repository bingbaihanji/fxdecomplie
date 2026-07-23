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
package com.bingbaihanji.classgraph.util;

import com.bingbaihanji.classgraph.util.LogNode;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

/**
 * 一个从键到单例实例的映射允许你按需创建对象单例实例，并根据键值将其添加到 {@link ConcurrentMap} 中
 * 其功能与 {@code concurrentMap.computeIfAbsent(key, key -> newInstance(key))} 相同，但同时也兼容 JDK 7
 *
 * @param <K>
 *            键类型
 * @param <V>
 *            值类型
 * @param <E>
 *            元素类型
 */
public abstract class SingletonMap<K, V, E extends Exception> {
    /** 内部映射 */
    private final ConcurrentMap<K, SingletonHolder<V>> map = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造一个新的单例实例
     *
     * @param key
     *            单例的键
     * @param log
     *            日志
     * @return 单例实例此方法必须返回非 null 值，或者抛出类型为 E 的异常
     * @throws E
     *             如果在实例化新对象实例时出错
     * @throws InterruptedException
     *             如果线程在实例化单例时被中断
     */
    public abstract V newInstance(K key, LogNode log) throws E, InterruptedException;

    /**
     * 检查给定的键是否在映射中，如果在，则返回该键对应的 {@link #newInstance(Object, LogNode)} 的值，
     * 如果另一个线程正在创建新实例，则阻塞等待 {@link #newInstance(Object, LogNode)} 的结果
     *
     * 如果给定的键当前不在映射中，则在映射中为该键存储一个占位符，然后运行该键对应的
     * {@link #newInstance(Object, LogNode)}，将结果存储到占位符中(这会解除其他等待该值的线程的阻塞)，
     * 然后返回新实例
     *
     * @param key
     *            单例的键
     * @param newInstanceFactory
     *            如果非 null，则为创建新实例的工厂；如果为 null，则调用
     *            {@link #newInstance(Object, LogNode)}(这允许按实例覆盖新实例的创建方式)
     * @param log
     *            日志
     * @return 非 null 的单例实例，前提是 {@link #newInstance(Object, LogNode)} 在此次调用或之前的调用中
     *         返回了非 null 实例；否则，如果此次调用或之前的 {@link #newInstance(Object, LogNode)} 调用返回了
     *         null，则抛出 {@link NullPointerException}
     * @throws E
     *             如果 {@link #newInstance(Object, LogNode)} 抛出了异常
     * @throws InterruptedException
     *             如果线程在等待另一个线程实例化单例时被中断
     * @throws NullSingletonException
     *             如果 {@link #newInstance(Object, LogNode)} 返回了 null
     * @throws NewInstanceException
     *             如果 {@link #newInstance(Object, LogNode)} 抛出了异常
     */
    public V get(final K key, final LogNode log, final NewInstanceFactory<V, E> newInstanceFactory)
            throws E, InterruptedException, NullSingletonException, NewInstanceException {
        final SingletonHolder<V> singletonHolder = map.get(key);
        @SuppressWarnings("null")
        V instance = null;
        if (singletonHolder != null) {
            // 此键在映射中已有 SingletonHolder -- 获取其值
            instance = singletonHolder.get();
        } else {
            // 此键在映射中没有 SingletonHolder，需要创建一个
            // (需要处理竞态条件，因此使用 putIfAbsent 调用)
            final SingletonHolder<V> newSingletonHolder = new SingletonHolder<>();
            final SingletonHolder<V> oldSingletonHolder = map.putIfAbsent(key, newSingletonHolder);
            if (oldSingletonHolder != null) {
                // 由于竞态条件，此键在映射中已经存在一个单例 --
                // 返回已存在的单例
                instance = oldSingletonHolder.get();
            } else {
                try {
                    // 创建新实例
                    if (newInstanceFactory != null) {
                        // 调用 NewInstanceFactory
                        instance = newInstanceFactory.newInstance();
                    } else {
                        // 调用被重写的 newInstance 方法
                        instance = newInstance(key, log);
                    }

                } catch (final Throwable t) {
                    // 用新实例初始化 newSingletonHolder
                    // 即使 newInstance() 抛出异常或返回 null，也必须始终调用 .set()，
                    // 因为 .set() 会调用 initialized.countDown()
                    // 否则，调用 .get() 的线程可能会永远等待下去
                    newSingletonHolder.set(instance);
                    throw new NewInstanceException(key, t);
                }
                newSingletonHolder.set(instance);
            }
        }
        if (instance == null) {
            throw new NullSingletonException(key);
        } else {
            return instance;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 检查给定的键是否在映射中，如果在，则返回该键对应的 {@link #newInstance(Object, LogNode)} 的值，
     * 如果另一个线程正在创建新实例，则阻塞等待 {@link #newInstance(Object, LogNode)} 的结果
     *
     * 如果给定的键当前不在映射中，则在映射中为该键存储一个占位符，然后运行该键对应的
     * {@link #newInstance(Object, LogNode)}，将结果存储到占位符中(这会解除其他等待该值的线程的阻塞)，
     * 然后返回新实例
     *
     * @param key
     *            单例的键
     * @param log
     *            日志
     * @return 非 null 的单例实例，前提是 {@link #newInstance(Object, LogNode)} 在此次调用或之前的调用中
     *         返回了非 null 实例；否则，如果此次调用或之前的 {@link #newInstance(Object, LogNode)} 调用返回了
     *         null，则抛出 {@link NullPointerException}
     * @throws E
     *             如果 {@link #newInstance(Object, LogNode)} 抛出了异常
     * @throws InterruptedException
     *             如果线程在等待另一个线程实例化单例时被中断
     * @throws NullSingletonException
     *             如果 {@link #newInstance(Object, LogNode)} 返回了 null
     * @throws NewInstanceException
     *             如果 {@link #newInstance(Object, LogNode)} 抛出了异常
     */
    public V get(final K key, final LogNode log)
            throws E, InterruptedException, NullSingletonException, NewInstanceException {
        return get(key, log, null);
    }

    /**
     * 获取映射中所有有效的单例值
     *
     * @return 映射中的单例值，跳过 newInstance() 抛出异常或返回 null 的值
     * @throws InterruptedException
     *             如果获取值时被中断
     */
    public List<V> values() throws InterruptedException {
        final List<V> entries = new ArrayList<>(map.size());
        for (final Entry<K, SingletonHolder<V>> ent : map.entrySet()) {
            final V entryValue = ent.getValue().get();
            if (entryValue != null) {
                entries.add(entryValue);
            }
        }
        return entries;
    }

    /**
     * 如果映射为空则返回 true
     *
     * @return 如果映射为空则返回 true
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * 获取映射条目
     *
     * @return 映射条目
     * @throws InterruptedException
     *             如果被中断
     */
    public List<Entry<K, V>> entries() throws InterruptedException {
        final List<Entry<K, V>> entries = new ArrayList<>(map.size());
        for (final Entry<K, SingletonHolder<V>> ent : map.entrySet()) {
            entries.add(new SimpleEntry<>(ent.getKey(), ent.getValue().get()));
        }
        return entries;
    }

    /**
     * 移除给定键对应的单例
     *
     * @param key
     *            键
     * @return 映射中原来的单例(如果存在)，否则为 null
     * @throws InterruptedException
     *             如果被中断
     */
    @SuppressWarnings("null")
    public V remove(final K key) throws InterruptedException {
        final SingletonHolder<V> val = map.remove(key);
        return val == null ? null : val.get();
    }

    /** 清空映射 */
    public void clear() {
        map.clear();
    }

    /**
     * 创建新实例
     *
     * @param <V>
     *            实例类型
     */
    @FunctionalInterface
    public interface NewInstanceFactory<V, E extends Exception> {
        /**
         * 创建新实例
         *
         * @return 新实例
         */
        public V newInstance() throws E, InterruptedException;
    }

    /** 当 {@link SingletonMap#newInstance(Object, LogNode)} 返回 null 时抛出 */
    public static class NullSingletonException extends Exception {
        /** serialVersionUID */
        static final long serialVersionUID = 1L;

        /**
         * 构造函数
         *
         * @param <K>
         *            键类型
         * @param key
         *            键
         */
        public <K> NullSingletonException(final K key) {
            super("newInstance returned null for key " + key);
        }
    }

    /** 当 {@link SingletonMap#newInstance(Object, LogNode)} 抛出异常时抛出 */
    public static class NewInstanceException extends Exception {
        /** serialVersionUID */
        static final long serialVersionUID = 1L;

        /**
         * 构造函数
         *
         * @param <K>
         *            键类型
         * @param key
         *            键
         * @param t
         *            被抛出的 Throwable
         */
        public <K> NewInstanceException(final K key, final Throwable t) {
            super("newInstance threw an exception for key " + key + " : " + t, t);
        }
    }

    /**
     * 包装器，允许使用 putIfAbsent() 将对象实例放入 ConcurrentHashMap 中，而无需先初始化该实例，
     * 这样就不需要使用同步锁来包装 putIfAbsent 操作，并且如果键对应的对象已存在于映射中，
     * 也不会浪费初始化工作
     *
     * @param <V>
     *            单例类型
     */
    private static class SingletonHolder<V> {
        /** 单例是否已初始化(如果已初始化，计数将达到 0) */
        private final CountDownLatch initialized = new CountDownLatch(1);
        /** 单例实例 */
        @SuppressWarnings("null")
        private volatile V singleton;

        /**
         * 设置单例值，并将倒计时锁存器减少到 0
         *
         * @param singleton
         *            单例实例
         * @throws IllegalArgumentException
         *             如果此方法被调用多次(表明内部不一致)
         */
        void set(final V singleton) throws IllegalArgumentException {
            if (initialized.getCount() < 1) {
                // 不应发生
                throw new IllegalArgumentException("Singleton already initialized");
            }
            this.singleton = singleton;
            initialized.countDown();
            if (initialized.getCount() != 0) {
                // 不应发生
                throw new IllegalArgumentException("Singleton initialized more than once");
            }
        }

        /**
         * 获取单例值
         *
         * @return 单例值
         * @throws InterruptedException
         *             如果线程在等待设置值时被中断
         */
        V get() throws InterruptedException {
            initialized.await();
            return singleton;
        }
    }
}
