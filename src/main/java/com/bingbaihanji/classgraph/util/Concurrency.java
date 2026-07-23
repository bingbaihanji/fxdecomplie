/*
 * This file is part of ClassGraph.
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Johno Crawford
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

import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// -------------------------------------------------------------------------------------------------------------

/** 一个可在 try-with-resources 块中使用的 ThreadPoolExecutor */
public class AutoCloseableExecutorService extends ThreadPoolExecutor implements AutoCloseable {
    /** {@link InterruptionChecker} 实例 */
    public final InterruptionChecker interruptionChecker = new InterruptionChecker();

    /**
     * 一个可在 try-with-resources 块中使用的 ThreadPoolExecutor
     *
     * @param numThreads
     *            要分配的线程数
     */
    public AutoCloseableExecutorService(final int numThreads) {
        super(numThreads, numThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
                new SimpleThreadFactory("ClassGraph-worker-", true));
    }

    /**
     * 捕获来自 submit() 和 execute() 的异常，并调用 {@link InterruptionChecker#interrupt()} 中断所有线程
     *
     * @param runnable
     *            要执行的 Runnable
     * @param throwable
     *            抛出的 Throwable
     */
    @Override
    public void afterExecute(final Runnable runnable, final Throwable throwable) {
        super.afterExecute(runnable, throwable);
        if (throwable != null) {
            // 将 throwable 包装为 ExecutionException(execute() 不会自动执行此操作)
            interruptionChecker.setExecutionException(new ExecutionException("Uncaught exception", throwable));
            // 调用了 execute() 并抛出了未捕获的异常或错误
            interruptionChecker.interrupt();
        } else if (/* throwable == null && */ runnable instanceof Future<?>) {
            // 调用了 submit()，因此 throwable 未设置
            try {
                // 此调用不会阻塞，因为执行已经完成
                ((Future<?>) runnable).get();
            } catch (CancellationException | InterruptedException e) {
                // 如果此线程被取消或中断，则中断其他线程
                interruptionChecker.interrupt();
            } catch (final ExecutionException e) {
                // 记录线程抛出的异常
                interruptionChecker.setExecutionException(e);
                // 中断其他线程
                interruptionChecker.interrupt();
            }
        }
    }

    /** 在 close() 中关闭线程池 */
    @Override
    public void close() {
        try {
            // 禁止提交新的任务
            shutdown();
        } catch (final SecurityException e) {
            // 暂时忽略(shutdownNow() 失败时会再次捕获)
        }
        boolean terminated = false;
        try {
            // 等待所有正在运行的任务终止
            terminated = awaitTermination(2500, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            interruptionChecker.interrupt();
        }
        if (!terminated) {
            try {
                // 如果 awaitTermination() 超时，中断所有线程以终止它们
                shutdownNow();
            } catch (final SecurityException e) {
                throw new RuntimeException("Could not shut down ExecutorService -- need "
                        + "java.lang.RuntimePermission(\"modifyThread\"), "
                        + "or the security manager's checkAccess method denies access", e);
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------------------

/**
 * 检查当前线程或共享此 InterruptionChecker 实例的任何其他线程是否已被中断或抛出了异常
 */
class InterruptionChecker {
    /** 当线程被中断时设置为 true */
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    /** 第一个被抛出的 {@link ExecutionException} */
    private final AtomicReference<ExecutionException> thrownExecutionException = //
            new AtomicReference<>();

    /**
     * 获取 {@link ExecutionException} 的根本原因
     *
     * @param throwable
     *            要解包的 Throwable
     * @return 根本原因
     */
    public static Throwable getCause(final Throwable throwable) {
        // 解包可能嵌套的 ExecutionException 以获取根本原因
        Throwable cause = throwable;
        while (cause instanceof ExecutionException) {
            cause = cause.getCause();
        }
        return cause != null ? cause : new ExecutionException("ExecutionException with unknown cause", null);
    }

    /** 中断共享此 InterruptionChecker 的所有线程 */
    public void interrupt() {
        interrupted.set(true);
        Thread.currentThread().interrupt();
    }

    /**
     * 获取工作线程抛出的 {@link ExecutionException}，如果没有则返回 null
     *
     * @return 工作线程抛出的 {@link ExecutionException}，如果没有则返回 null
     */
    public ExecutionException getExecutionException() {
        return thrownExecutionException.get();
    }

    /**
     * 设置工作线程抛出的 {@link ExecutionException}
     *
     * @param executionException
     *            被抛出的执行异常
     */
    public void setExecutionException(final ExecutionException executionException) {
        // 只设置一次执行异常
        if (executionException != null && thrownExecutionException.get() == null) {
            thrownExecutionException.compareAndSet(/* expectedValue = */ null, executionException);
        }
    }

    /**
     * 检查中断状态并返回中断状态
     *
     * @return 如果当前线程或共享此 InterruptionChecker 实例的任何其他线程已被中断或抛出了异常，则返回 true
     */
    public boolean checkAndReturn() {
        // 检查是否有任何线程已被中断
        if (interrupted.get()) {
            // 如果是，则中断当前线程
            interrupt();
            return true;
        }
        // 检查当前线程是否已被中断
        if (Thread.currentThread().isInterrupted()) {
            // 如果是，则中断其他线程
            interrupted.set(true);
            return true;
        }
        return false;
    }

    /**
     * 检查当前线程或共享此 InterruptionChecker 实例的任何其他线程是否已被中断或抛出了异常，
     * 如果是，则抛出 InterruptedException
     *
     * @throws InterruptedException
     *             如果某个线程已被中断
     * @throws ExecutionException
     *             如果某个线程抛出了未捕获的异常
     */
    public void check() throws InterruptedException, ExecutionException {
        // 如果某个线程抛出了未捕获的异常，则重新抛出它
        final ExecutionException executionException = getExecutionException();
        if (executionException != null) {
            throw executionException;
        }
        // 如果当前线程或其他线程已被中断，则抛出 InterruptedException
        if (checkAndReturn()) {
            throw new InterruptedException();
        }
    }
}

// -------------------------------------------------------------------------------------------------------------

/**
 * 线程工厂的简单实现
 *
 * @author Johno Crawford (johno@sulake.com)
 */
class SimpleThreadFactory implements java.util.concurrent.ThreadFactory {
    /** 线程索引计数器，用于分配唯一的线程 ID */
    private static final AtomicInteger threadIdx = new AtomicInteger();
    /** 线程名称前缀 */
    private final String threadNamePrefix;
    /** 是否设置为守护线程模式 */
    private final boolean daemon;

    /**
     * 构造函数
     *
     * @param threadNamePrefix
     *            创建的线程的名称前缀
     * @param daemon
     *            是否创建守护线程？
     */
    SimpleThreadFactory(final String threadNamePrefix, final boolean daemon) {
        this.threadNamePrefix = threadNamePrefix;
        this.daemon = daemon;
    }

    /**
     * 创建新线程
     *
     * @param runnable
     *            要执行的 Runnable
     * @return 创建的线程
     */
    @Override
    public Thread newThread(final Runnable runnable) {
        // 通过反射调用 System.getSecurityManager().getThreadGroup()，因为该方法在 JDK 17 中已被弃用
        ThreadGroup securityManagerThreadGroup = null;
        try {
            final Method getSecurityManager = System.class.getDeclaredMethod("getSecurityManager");
            final Object securityManager = getSecurityManager.invoke(null);
            if (securityManager != null) {
                final Method getThreadGroup = securityManager.getClass().getDeclaredMethod("getThreadGroup");
                securityManagerThreadGroup = (ThreadGroup) getThreadGroup.invoke(securityManager);
            }
        } catch (final Throwable t) {
            // 忽略异常，继续执行
        }
        final Thread thread = new Thread(
                securityManagerThreadGroup != null ? securityManagerThreadGroup
                        : new ThreadGroup("ClassGraph-thread-group"),
                runnable, threadNamePrefix + threadIdx.getAndIncrement());
        thread.setDaemon(daemon);
        return thread;
    }
}

// -------------------------------------------------------------------------------------------------------------

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
abstract class SingletonMap<K, V, E extends Exception> {
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

// -------------------------------------------------------------------------------------------------------------

/**
 * 并行工作队列
 *
 * @param <T>
 *            工作单元类型
 */
class WorkQueue<T> implements AutoCloseable {
    /** 工作单元处理器 */
    private final WorkUnitProcessor<T> workUnitProcessor;

    /** 工作单元队列 */
    private final BlockingQueue<WorkUnitWrapper<T>> workUnits = new LinkedBlockingQueue<>();

    /** 工作线程数量 */
    private final int numWorkers;

    /**
     * 剩余待处理的工作单元数量，加上当前正在处理工作单元的运行中线程数量
     */
    private final AtomicInteger numIncompleteWorkUnits = new AtomicInteger();

    /** 为每个工作线程添加的 Future 对象，用于检测工作线程是否完成 */
    private final ConcurrentLinkedQueue<Future<?>> workerFutures = new ConcurrentLinkedQueue<>();

    /**
     * 共享的 InterruptionChecker，用于检测线程中断和执行异常，并在发生任一情况时关闭所有线程
     */
    private final InterruptionChecker interruptionChecker;

    /** 日志节点 */
    private final LogNode log;

    /**
     * 并行工作队列
     *
     * @param initialWorkUnits
     *            初始工作单元
     * @param workUnitProcessor
     *            工作单元处理器
     * @param numWorkers
     *            工作线程数量
     * @param interruptionChecker
     *            中断检查器
     * @param log
     *            日志
     */
    private WorkQueue(final Collection<T> initialWorkUnits, final WorkUnitProcessor<T> workUnitProcessor,
                      final int numWorkers, final InterruptionChecker interruptionChecker, final LogNode log) {
        this.workUnitProcessor = workUnitProcessor;
        this.numWorkers = numWorkers;
        this.interruptionChecker = interruptionChecker;
        this.log = log;
        addWorkUnits(initialWorkUnits);
    }

    /**
     * 对提供的集合中的元素启动工作队列，阻塞直到所有工作单元完成处理
     *
     * @param <U>
     *            工作队列单元的类型
     * @param elements
     *            要处理的工作队列单元
     * @param executorService
     *            {@link ExecutorService} 实例
     * @param interruptionChecker
     *            中断检查器
     * @param numParallelTasks
     *            并行任务数
     * @param log
     *            日志
     * @param workUnitProcessor
     *            {@link WorkUnitProcessor} 实例
     * @throws InterruptedException
     *             如果工作被中断
     * @throws ExecutionException
     *             如果工作线程抛出未捕获的异常
     */
    public static <U> void runWorkQueue(final Collection<U> elements, final ExecutorService executorService,
                                        final InterruptionChecker interruptionChecker, final int numParallelTasks, final LogNode log,
                                        final WorkUnitProcessor<U> workUnitProcessor) throws InterruptedException, ExecutionException {
        if (elements.isEmpty()) {
            // 无需处理
            return;
        }
        // 当此 try-with-resources 块终止时，调用 WorkQueue#close()，在所有工作线程完成后启动屏障等待
        try (WorkQueue<U> workQueue = new WorkQueue<>(elements, workUnitProcessor, numParallelTasks,
                interruptionChecker, log)) {
            // 启动 (numParallelTasks - 1) 个工作线程(如果 numParallelTasks == 1，则可能启动零个线程)
            workQueue.startWorkers(executorService, numParallelTasks - 1);
            // 同样使用当前线程来完成部分工作，以防止 ExecutorService 中只有一个可用线程，
            // 或者 numParallelTasks 大于 ExecutorService 中可用线程数的情况
            workQueue.runWorkLoop();
        }
    }

    /**
     * 启动带有共享日志的工作线程
     *
     * @param executorService
     *            执行器服务
     * @param numTasks
     *            要启动的工作线程任务数量
     */
    private void startWorkers(final ExecutorService executorService, final int numTasks) {
        for (int i = 0; i < numTasks; i++) {
            workerFutures.add(executorService.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    runWorkLoop();
                    return null;
                }
            }));
        }
    }

    /**
     * 向工作线程发送毒丸信号
     */
    @SuppressWarnings("null")
    private void sendPoisonPills() {
        for (int i = 0; i < numWorkers; i++) {
            workUnits.add(new WorkUnitWrapper<T>(null));
        }
    }

    /**
     * 启动一个工作线程由 startWorkers() 调用，但也应由主线程调用以在该线程上完成部分工作，
     * 防止在 ExecutorService 没有 numParallelTasks 那么多可用线程时发生死锁当此方法返回时，
     * 要么所有工作已经完成，要么此线程或其他某个线程已被中断如果抛出 InterruptedException，
     * 则表示此线程或其他线程被中断了
     *
     * @throws InterruptedException
     *             如果工作线程被中断
     * @throws ExecutionException
     *             如果工作线程抛出未捕获的异常
     */
    private void runWorkLoop() throws InterruptedException, ExecutionException {
        // 从队列中获取下一个工作单元
        for (; ; ) {
            // 处理工作单元
            try {
                // 检查中断状态
                interruptionChecker.check();

                // 获取下一个工作单元
                final WorkUnitWrapper<T> workUnitWrapper = workUnits.take();

                if (workUnitWrapper.workUnit == null) {
                    // 收到毒丸信号
                    break;
                }

                // 处理工作单元(可能抛出 InterruptedException)
                workUnitProcessor.processWorkUnit(workUnitWrapper.workUnit, this, log);

            } catch (InterruptedException | Error e) {
                // 当发生 InterruptedException 或 OutOfMemoryError 时，清空工作队列，发送毒丸信号，并重新抛出
                workUnits.clear();
                numIncompleteWorkUnits.set(0);
                sendPoisonPills();
                throw e;

            } catch (final RuntimeException e) {
                // 当发生未检查异常时，清空工作队列，发送毒丸信号，并抛出 ExecutionException
                workUnits.clear();
                numIncompleteWorkUnits.set(0);
                sendPoisonPills();
                throw new ExecutionException("Worker thread threw unchecked exception", e);

            }
            if (numIncompleteWorkUnits.decrementAndGet() == 0) {
                // 没有更多的工作单元 -- 发送毒丸信号
                sendPoisonPills();
            }
        }
    }

    /**
     * 添加一个工作单元可由工作线程调用，以将更多工作单元添加到队列尾部
     *
     * @param workUnit
     *            工作单元
     * @throws NullPointerException
     *             如果工作单元为 null
     */
    public void addWorkUnit(final T workUnit) {
        if (workUnit == null) {
            throw new NullPointerException("workUnit cannot be null");
        }
        numIncompleteWorkUnits.incrementAndGet();
        workUnits.add(new WorkUnitWrapper<>(workUnit));
    }

    /**
     * 添加多个工作单元可由工作线程调用，以将更多工作单元添加到队列尾部
     *
     * @param workUnits
     *            要添加到队列尾部的工作单元
     * @throws NullPointerException
     *             如果其中任何工作单元为 null
     */
    public void addWorkUnits(final Collection<T> workUnits) {
        for (final T workUnit : workUnits) {
            addWorkUnit(workUnit);
        }
    }

    /**
     * 工作队列的完成屏障应在主线程上 runWorkLoop() 退出后调用(例如通过 try-with-resources)
     *
     * @throws ExecutionException
     *             如果工作线程抛出了未捕获的异常
     */
    @Override
    public void close() throws ExecutionException {
        for (Future<?> future; (future = workerFutures.poll()) != null; ) {
            try {
                // 通过 future.get() 阻塞等待完成，可能会抛出以下异常之一
                future.get();
            } catch (final CancellationException e) {
                if (log != null) {
                    log.log("~", "Worker thread was cancelled");
                }
            } catch (final InterruptedException e) {
                if (log != null) {
                    log.log("~", "Worker thread was interrupted");
                }
                // 中断其他线程
                interruptionChecker.interrupt();
            } catch (final ExecutionException e) {
                interruptionChecker.setExecutionException(e);
                interruptionChecker.interrupt();
            }
        }
    }

    /**
     * 工作单元处理器
     *
     * @param <T>
     *            要处理的工作单元类型
     */
    public interface WorkUnitProcessor<T> {
        /**
         * 处理一个工作单元
         *
         * @param workUnit
         *            工作单元
         * @param workQueue
         *            工作队列
         * @param log
         *            日志
         * @throws InterruptedException
         *             如果工作线程被中断
         */
        void processWorkUnit(T workUnit, WorkQueue<T> workQueue, LogNode log) throws InterruptedException;
    }

    /**
     * 工作单元的包装器(由于 BlockingQueue 不接受 null 值，需要用包装器将 null 值作为毒丸信号发送)
     *
     * @param <T>
     *            泛型类型
     */
    private static class WorkUnitWrapper<T> {
        /** 工作单元 */
        final T workUnit;

        /**
         * 构造函数
         *
         * @param workUnit
         *            工作单元，或 null 表示毒丸信号
         */
        public WorkUnitWrapper(final T workUnit) {
            this.workUnit = workUnit;
        }
    }
}
