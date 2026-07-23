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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 检查当前线程或共享此 InterruptionChecker 实例的任何其他线程是否已被中断或抛出了异常
 */
public class InterruptionChecker {
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
