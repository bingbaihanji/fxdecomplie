/*
 * This file is part of ClassGraph.
 *
 * Author: Johno Crawford (johno@sulake.com)
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Johno Crawford
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

import java.util.concurrent.*;

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
