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
package com.bingbaihanji.classgraph.reflect;

import com.bingbaihanji.classgraph.core.ClassGraph;
import com.bingbaihanji.classgraph.core.ClassGraph.CircumventEncapsulationMethod;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;

/** 可供 ClassLoaderHandler 使用的反射工具方法 */
public final class ReflectionUtils {
    /** 要使用的反射驱动 */
    public ReflectionDriver reflectionDriver;
    private Class<?> accessControllerClass;
    private Class<?> privilegedActionClass;
    private Method accessControllerDoPrivileged;

    /** 如果更改了 {@link ClassGraph#CIRCUMVENT_ENCAPSULATION} 的值，请调用此方法 */
    public ReflectionUtils() {
        if (ClassGraph.CIRCUMVENT_ENCAPSULATION == CircumventEncapsulationMethod.NARCISSUS) {
            try {
                reflectionDriver = new NarcissusDriver();
            } catch (final Throwable t) {
                System.err.println("Could not load Narcissus reflection driver: " + t);
                // 回退到标准反射驱动
            }
        }
        if (reflectionDriver == null) {
            reflectionDriver = new StandardDriver();
        }
        try {
            accessControllerClass = reflectionDriver.findClass("java.security.AccessController");
            privilegedActionClass = reflectionDriver.findClass("java.security.PrivilegedAction");
            accessControllerDoPrivileged = reflectionDriver.findMethod(accessControllerClass, null, "doPrivileged",
                    privilegedActionClass);
        } catch (final Throwable t) {
            // 忽略
        }
    }

    /**
     * 获取给定对象类或其任意超类中字段的值如果在尝试读取字段时抛出异常，且 throwException 为 true，
     * 则抛出 IllegalArgumentException 包装原始异常；否则此方法将返回 null
     * 如果传入 null 对象，则返回 null，除非 throwException 为 true，此时抛出 IllegalArgumentException
     *
     * @param throwException
     *            如果为 true，则在无法读取字段值时抛出异常
     * @param obj
     *            对象
     * @param field
     *            字段
     *
     * @return 字段值
     * @throws IllegalArgumentException
     *             如果无法读取字段值
     */
    public Object getFieldVal(final boolean throwException, final Object obj, final Field field)
            throws IllegalArgumentException {
        if (reflectionDriver == null) {
            throw new RuntimeException("Cannot use reflection after ScanResult has been closed");
        }
        if (obj == null || field == null) {
            if (throwException) {
                throw new NullPointerException();
            } else {
                return null;
            }
        }
        try {
            return reflectionDriver.getField(obj, field);
        } catch (final Throwable e) {
            if (throwException) {
                throw new IllegalArgumentException(
                        "Can't read field " + obj.getClass().getName() + "." + field.getName(), e);
            }
        }
        return null;
    }

    /**
     * 获取给定对象类或其任意超类中命名字段的值如果在尝试读取字段时抛出异常，且 throwException 为 true，
     * 则抛出 IllegalArgumentException 包装原始异常；否则此方法将返回 null
     * 如果传入 null 对象，则返回 null，除非 throwException 为 true，此时抛出 IllegalArgumentException
     *
     * @param throwException
     *            如果为 true，则在无法读取字段值时抛出异常
     * @param obj
     *            对象
     * @param fieldName
     *            字段名
     *
     * @return 字段值
     * @throws IllegalArgumentException
     *             如果无法读取字段值
     */
    public Object getFieldVal(final boolean throwException, final Object obj, final String fieldName)
            throws IllegalArgumentException {
        if (reflectionDriver == null) {
            throw new RuntimeException("Cannot use reflection after ScanResult has been closed");
        }
        if (obj == null || fieldName == null) {
            if (throwException) {
                throw new NullPointerException();
            } else {
                return null;
            }
        }
        try {
            return reflectionDriver.getField(obj, reflectionDriver.findInstanceField(obj, fieldName));
        } catch (final Throwable e) {
            if (throwException) {
                throw new IllegalArgumentException("Can't read field " + obj.getClass().getName() + "." + fieldName,
                        e);
            }
        }
        return null;
    }

    /**
     * 获取给定类或其任意超类中命名字段的值如果在尝试读取字段值时抛出异常，且 throwException 为 true，
     * 则抛出 IllegalArgumentException 包装原始异常；否则此方法将返回 null
     * 如果传入 null 类引用，则返回 null，除非 throwException 为 true，此时抛出 IllegalArgumentException
     *
     * @param throwException
     *            如果为 true，则在无法读取字段值时抛出异常
     * @param cls
     *            类
     * @param fieldName
     *            字段名
     *
     * @return 字段值
     * @throws IllegalArgumentException
     *             如果无法读取字段值
     */
    public Object getStaticFieldVal(final boolean throwException, final Class<?> cls, final String fieldName)
            throws IllegalArgumentException {
        if (reflectionDriver == null) {
            throw new RuntimeException("Cannot use reflection after ScanResult has been closed");
        }
        if (cls == null || fieldName == null) {
            if (throwException) {
                throw new NullPointerException();
            } else {
                return null;
            }
        }
        try {
            return reflectionDriver.getStaticField(reflectionDriver.findStaticField(cls, fieldName));
        } catch (final Throwable e) {
            if (throwException) {
                throw new IllegalArgumentException("Can't read field " + cls.getName() + "." + fieldName, e);
            }
        }
        return null;
    }

    /**
     * 调用给定对象或其超类中的命名方法如果在尝试调用方法时抛出异常，且 throwException 为 true，
     * 则抛出 IllegalArgumentException 包装原始异常；否则此方法将返回 null
     * 如果传入 null 对象，则返回 null，除非 throwException 为 true，此时抛出 IllegalArgumentException
     *
     * @param throwException
     *            如果为 true，则在无法读取字段值时抛出异常
     * @param obj
     *            对象
     * @param methodName
     *            方法名
     *
     * @return 方法调用的结果
     * @throws IllegalArgumentException
     *             如果无法调用方法
     */
    public Object invokeMethod(final boolean throwException, final Object obj, final String methodName)
            throws IllegalArgumentException {
        if (reflectionDriver == null) {
            throw new RuntimeException("Cannot use reflection after ScanResult has been closed");
        }
        if (obj == null || methodName == null) {
            if (throwException) {
                throw new IllegalArgumentException("Unexpected null argument");
            } else {
                return null;
            }
        }
        try {
            return reflectionDriver.invokeMethod(obj, reflectionDriver.findInstanceMethod(obj, methodName));
        } catch (final Throwable e) {
            if (throwException) {
                throw new IllegalArgumentException("Method \"" + methodName + "\" could not be invoked", e);
            }
            return null;
        }
    }

    /**
     * 调用给定对象或其超类中的命名方法如果在尝试调用方法时抛出异常，且 throwException 为 true，
     * 则抛出 IllegalArgumentException 包装原始异常；否则此方法将返回 null
     * 如果传入 null 对象，则返回 null，除非 throwException 为 true，此时抛出 IllegalArgumentException
     *
     * @param throwException
     *            失败时是否抛出异常
     * @param obj
     *            对象
     * @param methodName
     *            方法名
     * @param argType
     *            方法参数的类型
     * @param param
     *            调用方法时使用的参数值
     *
     * @return 方法调用的结果
     * @throws IllegalArgumentException
     *             如果无法调用方法
     */
    public Object invokeMethod(final boolean throwException, final Object obj, final String methodName,
                               final Class<?> argType, final Object param) throws IllegalArgumentException {
        if (reflectionDriver == null) {
            throw new RuntimeException("Cannot use reflection after ScanResult has been closed");
        }
        if (obj == null || methodName == null || argType == null) {
            if (throwException) {
                throw new IllegalArgumentException("Unexpected null argument");
            } else {
                return null;
            }
        }
        try {
            return reflectionDriver.invokeMethod(obj, reflectionDriver.findInstanceMethod(obj, methodName, argType),
                    param);
        } catch (final Throwable e) {
            if (throwException) {
                throw new IllegalArgumentException("Method \"" + methodName + "\" could not be invoked", e);
            }
            return null;
        }
    }

    /**
     * 调用命名方法如果在尝试调用方法时抛出异常，且 throwException 为 true，
     * 则抛出 IllegalArgumentException 包装原始异常；否则此方法将返回 null
     * 如果传入 null 类引用，则返回 null，除非 throwException 为 true，此时抛出 IllegalArgumentException
     *
     * @param throwException
     *            失败时是否抛出异常
     * @param cls
     *            类
     * @param methodName
     *            方法名
     *
     * @return 方法调用的结果
     * @throws IllegalArgumentException
     *             如果无法调用方法
     */
    public Object invokeStaticMethod(final boolean throwException, final Class<?> cls, final String methodName)
            throws IllegalArgumentException {
        if (reflectionDriver == null) {
            throw new RuntimeException("Cannot use reflection after ScanResult has been closed");
        }
        if (cls == null || methodName == null) {
            if (throwException) {
                throw new IllegalArgumentException("Unexpected null argument");
            } else {
                return null;
            }
        }
        try {
            return reflectionDriver.invokeStaticMethod(reflectionDriver.findStaticMethod(cls, methodName));
        } catch (final Throwable e) {
            if (throwException) {
                throw new IllegalArgumentException("Method \"" + methodName + "\" could not be invoked", e);
            }
            return null;
        }
    }

    /**
     * 调用命名方法如果在尝试调用方法时抛出异常，且 throwException 为 true，
     * 则抛出 IllegalArgumentException 包装原始异常；否则此方法将返回 null
     * 如果传入 null 类引用，则返回 null，除非 throwException 为 true，此时抛出 IllegalArgumentException
     *
     * @param throwException
     *            失败时是否抛出异常
     * @param cls
     *            类
     * @param methodName
     *            方法名
     * @param argType
     *            方法参数的类型
     * @param param
     *            调用方法时使用的参数值
     *
     * @return 方法调用的结果
     * @throws IllegalArgumentException
     *             如果无法调用方法
     */
    public Object invokeStaticMethod(final boolean throwException, final Class<?> cls, final String methodName,
                                     final Class<?> argType, final Object param) throws IllegalArgumentException {
        if (reflectionDriver == null) {
            throw new RuntimeException("Cannot use reflection after ScanResult has been closed");
        }
        if (cls == null || methodName == null || argType == null) {
            if (throwException) {
                throw new IllegalArgumentException("Unexpected null argument");
            } else {
                return null;
            }
        }
        try {
            return reflectionDriver.invokeStaticMethod(reflectionDriver.findStaticMethod(cls, methodName, argType),
                    param);
        } catch (final Throwable e) {
            if (throwException) {
                throw new IllegalArgumentException("Fethod \"" + methodName + "\" could not be invoked", e);
            }
            return null;
        }
    }

    /**
     * 调用 Class.forName(className)，但如果抛出任何异常则返回 null
     *
     * @param className
     *            要加载的类名
     * @return 请求名称对应的类，如果在尝试加载类时抛出异常则返回 null
     */
    public Class<?> classForNameOrNull(final String className) {
        if (reflectionDriver == null) {
            throw new RuntimeException("Cannot use reflection after ScanResult has been closed");
        }
        try {
            return reflectionDriver.findClass(className);
        } catch (final Throwable e) {
            return null;
        }
    }

    /**
     * 按名称获取方法，但如果抛出任何异常则返回 null
     *
     * @param className
     *            要加载的类名
     * @param staticMethodName
     *            静态方法名
     * @return 请求名称对应的类，如果在尝试加载类时抛出异常则返回 null
     */
    public Method staticMethodForNameOrNull(final String className, final String staticMethodName) {
        if (reflectionDriver == null) {
            throw new RuntimeException("Cannot use reflection after ScanResult has been closed");
        }
        try {
            return reflectionDriver.findStaticMethod(reflectionDriver.findClass(className), staticMethodName);
        } catch (final Throwable e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 如果可能，使用反射在 AccessController.doPrivileged(PrivilegedAction) 上下文中调用方法
     * (AccessController 在 JDK 17 中已弃用)
     */
    @SuppressWarnings("unchecked")
    public <T> T doPrivileged(final Callable<T> callable) throws Throwable {
        if (accessControllerDoPrivileged != null) {
            final Object privilegedAction = Proxy.newProxyInstance(privilegedActionClass.getClassLoader(),
                    new Class<?>[]{privilegedActionClass}, new PrivilegedActionInvocationHandler<T>(callable));
            return (T) accessControllerDoPrivileged.invoke(null, privilegedAction);
        } else {
            // 回退到非特权上下文中调用
            return callable.call();
        }
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
