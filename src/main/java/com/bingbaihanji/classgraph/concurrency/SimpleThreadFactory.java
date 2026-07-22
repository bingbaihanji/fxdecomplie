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
package com.bingbaihanji.classgraph.concurrency;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程工厂的简单实现
 *
 * @author Johno Crawford (johno@sulake.com)
 */
public class SimpleThreadFactory implements java.util.concurrent.ThreadFactory {
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
