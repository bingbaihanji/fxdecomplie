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
package com.bingbaihanji.classgraph.concurrency;

import com.bingbaihanji.classgraph.utils.LogNode;

import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并行工作队列
 *
 * @param <T>
 *            工作单元类型
 */
public class WorkQueue<T> implements AutoCloseable {
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
