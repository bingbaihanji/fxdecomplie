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
 * Copyright (c) 2021 Luke Hutchison
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
package com.bingbaihanji.classgraph.reflect;

import com.bingbaihanji.classgraph.metadata.*;
import com.bingbaihanji.classgraph.type.*;
import com.bingbaihanji.classgraph.resource.*;
import com.bingbaihanji.classgraph.classpath.*;
import com.bingbaihanji.classgraph.util.*;
import com.bingbaihanji.classgraph.reflect.*;
import com.bingbaihanji.classgraph.bytecode.*;

import java.lang.reflect.*;
import java.util.concurrent.Callable;

/**
 * 标准反射驱动(必要时使用 {@link AccessibleObject#setAccessible(boolean)} 访问非公开字段)
 */
class StandardDriver extends ReflectionDriver {
    private static Method setAccessibleMethod;
    private static Method trySetAccessibleMethod;
    private static Class<?> accessControllerClass;
    private static Class<?> privilegedActionClass;
    private static Method accessControllerDoPrivileged;

    static {
        // 查找已弃用的方法以消除编译期警告
        // TODO 待此问题修复后切换到 MethodHandles：
        // https://github.com/mojohaus/animal-sniffer/issues/67
        try {
            setAccessibleMethod = AccessibleObject.class.getDeclaredMethod("setAccessible", boolean.class);
        } catch (final Throwable t) {
            // 忽略
        }
        try {
            trySetAccessibleMethod = AccessibleObject.class.getDeclaredMethod("trySetAccessible");
        } catch (final Throwable t) {
            // 忽略
        }
        try {
            accessControllerClass = Class.forName("java.security.AccessController");
            privilegedActionClass = Class.forName("java.security.PrivilegedAction");
            accessControllerDoPrivileged = accessControllerClass.getMethod("doPrivileged", privilegedActionClass);
        } catch (final Throwable t) {
            // 忽略
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    private static boolean tryMakeAccessible(final AccessibleObject obj) {
        if (trySetAccessibleMethod != null) {
            // JDK 9+
            try {
                return (Boolean) trySetAccessibleMethod.invoke(obj);
            } catch (final Throwable e) {
                // 忽略
            }
        }
        if (setAccessibleMethod != null) {
            // JDK 7/8
            try {
                setAccessibleMethod.invoke(obj, true);
                return true;
            } catch (final Throwable e) {
                // 忽略
            }
        }
        return false;
    }

    /**
     * 如果可能，使用反射在 AccessController.doPrivileged(PrivilegedAction) 上下文中调用方法
     * (AccessController 在 JDK 17 中已弃用)
     */
    @SuppressWarnings("unchecked")
    private <T> T doPrivileged(final Callable<T> callable) throws Throwable {
        if (accessControllerDoPrivileged != null) {
            final Object privilegedAction = Proxy.newProxyInstance(privilegedActionClass.getClassLoader(),
                    new Class<?>[]{privilegedActionClass}, new PrivilegedActionInvocationHandler<T>(callable));
            return (T) accessControllerDoPrivileged.invoke(null, privilegedAction);
        } else {
            // 回退到非特权上下文中调用
            return callable.call();
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    public boolean makeAccessible(final Object instance, final AccessibleObject obj) {
        if (isAccessible(instance, obj)) {
            return true;
        }
        try {
            return doPrivileged(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return tryMakeAccessible(obj);
                }
            });
        } catch (final Throwable t) {
            // 穿透到备用方案
            return tryMakeAccessible(obj);
        }
    }

    @Override
    Class<?> findClass(final String className) throws Exception {
        return Class.forName(className);
    }

    @Override
    Method[] getDeclaredMethods(final Class<?> cls) throws Exception {
        return cls.getDeclaredMethods();
    }

    @SuppressWarnings("unchecked")
    @Override
    <T> Constructor<T>[] getDeclaredConstructors(final Class<T> cls) throws Exception {
        return (Constructor<T>[]) cls.getDeclaredConstructors();
    }

    @Override
    Field[] getDeclaredFields(final Class<?> cls) throws Exception {
        return cls.getDeclaredFields();
    }

    @Override
    Object getField(final Object object, final Field field) throws Exception {
        makeAccessible(object, field);
        return field.get(object);
    }

    @Override
    void setField(final Object object, final Field field, final Object value) throws Exception {
        makeAccessible(object, field);
        field.set(object, value);
    }

    @Override
    Object getStaticField(final Field field) throws Exception {
        makeAccessible(null, field);
        return field.get(null);
    }

    @Override
    void setStaticField(final Field field, final Object value) throws Exception {
        makeAccessible(null, field);
        field.set(null, value);
    }

    @Override
    Object invokeMethod(final Object object, final Method method, final Object... args) throws Exception {
        makeAccessible(object, method);
        return method.invoke(object, args);
    }

    @Override
    Object invokeStaticMethod(final Method method, final Object... args) throws Exception {
        makeAccessible(null, method);
        return method.invoke(null, args);
    }

    private class PrivilegedActionInvocationHandler<T> implements InvocationHandler {
        private final Callable<T> callable;

        public PrivilegedActionInvocationHandler(final Callable<T> callable) {
            this.callable = callable;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            return callable.call();
        }
    }
}
