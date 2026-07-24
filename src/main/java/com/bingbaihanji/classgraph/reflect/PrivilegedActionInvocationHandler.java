package com.bingbaihanji.classgraph.reflect;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * 特权操作调用处理器 — 用于在 AccessController.doPrivileged 上下文中执行操作。
 *
 * <p>从 {@code StandardDriver} 和 {@code ReflectionUtils} 中提取，
 * 消除重复代码。</p>
 *
 * @param <T> 操作的返回类型
 */
public final class PrivilegedActionInvocationHandler<T> implements InvocationHandler {

    private final Callable<T> callable;

    /**
     * 构造函数。
     *
     * @param callable 要在特权上下文中执行的操作
     */
    public PrivilegedActionInvocationHandler(final Callable<T> callable) {
        this.callable = callable;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args)
            throws Throwable {
        return callable.call();
    }
}
