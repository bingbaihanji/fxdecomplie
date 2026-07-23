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

import com.bingbaihanji.classgraph.concurrency.SingletonMap;
import com.bingbaihanji.classgraph.utils.LogNode;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** 反射驱动 */
abstract class ReflectionDriver {
    private static Method isAccessibleMethod;
    private static Method canAccessMethod;

    static {
        // 查找已弃用的方法以消除编译期警告
        // TODO 待此问题修复后切换到 MethodHandles：
        // https://github.com/mojohaus/animal-sniffer/issues/67
        try {
            isAccessibleMethod = AccessibleObject.class.getDeclaredMethod("isAccessible");
        } catch (final Throwable t) {
            // 忽略
        }
        try {
            canAccessMethod = AccessibleObject.class.getDeclaredMethod("canAccess", Object.class);
        } catch (final Throwable t) {
            // 忽略
        }
    }

    private final SingletonMap<Class<?>, ClassMemberCache, Exception> classToClassMemberCache //
            = new SingletonMap<Class<?>, ClassMemberCache, Exception>() {
        @Override
        public ClassMemberCache newInstance(final Class<?> cls, final LogNode log)
                throws Exception, InterruptedException {
            return new ClassMemberCache(cls);
        }
    };

    /**
     * 按名称查找类
     *
     * @param className
     *            类名
     * @return 类引用
     */
    abstract Class<?> findClass(final String className) throws Exception;

    /**
     * 获取类的已声明方法
     *
     * @param cls
     *            类
     * @return 已声明的方法
     */
    abstract Method[] getDeclaredMethods(Class<?> cls) throws Exception;

    /**
     * 获取类的已声明构造方法
     *
     * @param <T>
     *            泛型类型
     * @param cls
     *            类
     * @return 已声明的构造方法
     */
    abstract <T> Constructor<T>[] getDeclaredConstructors(Class<T> cls) throws Exception;

    /**
     * 获取类的已声明字段
     *
     * @param cls
     *            类
     * @return 已声明的字段
     */
    abstract Field[] getDeclaredFields(Class<?> cls) throws Exception;

    /**
     * 获取非静态字段的值，必要时进行装箱
     *
     * @param object
     *            要获取字段值的对象实例
     * @param field
     *            非静态字段
     * @return 字段的值
     */
    abstract Object getField(final Object object, final Field field) throws Exception;

    /**
     * 设置非静态字段的值，必要时进行拆箱
     *
     * @param object
     *            要获取字段值的对象实例
     * @param field
     *            非静态字段
     * @param value
     *            要设置的值
     */
    abstract void setField(final Object object, final Field field, Object value) throws Exception;

    /**
     * 获取静态字段的值，必要时进行装箱
     *
     * @param field
     *            静态字段
     * @return 静态字段的值
     */
    abstract Object getStaticField(final Field field) throws Exception;

    /**
     * 设置静态字段的值，必要时进行拆箱
     *
     * @param field
     *            静态字段
     * @param value
     *            要设置的值
     */
    abstract void setStaticField(final Field field, Object value) throws Exception;

    /**
     * 调用非静态方法，必要时对返回值进行装箱
     *
     * @param object
     *            要调用方法的对象实例
     * @param method
     *            非静态方法
     * @param args
     *            方法参数(如果没有参数则为 {@code new Object[0]})
     * @return 返回值(可能为装箱后的值)
     */
    abstract Object invokeMethod(final Object object, final Method method, final Object... args) throws Exception;

    /**
     * 调用静态方法，必要时对返回值进行装箱
     *
     * @param method
     *            静态方法
     * @param args
     *            方法参数(如果没有参数则为 {@code new Object[0]})
     * @return 返回值(可能为装箱后的值)
     */
    abstract Object invokeStaticMethod(final Method method, final Object... args) throws Exception;

    /**
     * 使字段或方法可访问
     *
     * @param instance
     *            对象实例，静态时可为 null
     * @param fieldOrMethod
     *            字段或方法
     *
     * @return 成功时返回 true
     */
    abstract boolean makeAccessible(final Object instance, final AccessibleObject fieldOrMethod);

    /**
     * 检查字段或方法是否可访问
     *
     * <p>
     * 注意：Narcissus 驱动中此方法被重写为直接返回 true，因为 JNI 可以访问所有内容
     *
     * @param instance
     *            对象实例，静态时可为 null
     * @param fieldOrMethod
     *            字段或方法
     *
     * @return 可访问时返回 true
     */
    boolean isAccessible(final Object instance, final AccessibleObject fieldOrMethod) {
        if (canAccessMethod != null) {
            // JDK 9+：使用 canAccess
            try {
                return (Boolean) canAccessMethod.invoke(fieldOrMethod, instance);
            } catch (final Throwable e) {
                // 忽略
            }
        }
        if (isAccessibleMethod != null) {
            // JDK 7/8：使用 isAccessible(JDK 9+ 中已弃用)
            try {
                return (Boolean) isAccessibleMethod.invoke(fieldOrMethod);
            } catch (final Throwable e) {
                // 忽略
            }
        }
        return false;
    }

    /**
     * 获取类中具有给定字段名的字段
     *
     * @param cls
     *            类
     * @param obj
     *            对象实例，静态字段时可为 null
     * @param fieldName
     *            字段名
     * @return 请求字段名对应的 {@link Field} 对象，如果类中未找到该字段则返回 null
     * @throws Exception
     *             如果找不到字段
     */
    protected Field findField(final Class<?> cls, final Object obj, final String fieldName) throws Exception {
        final Field field = classToClassMemberCache.get(cls, /* log = */ null).fieldNameToField.get(fieldName);
        if (field != null) {
            if (!isAccessible(obj, field)) {
                // 如果字段找到了但不可访问，尝试使其可访问然后返回
                // (可能会在 stderr 上产生反射访问警告)
                makeAccessible(obj, field);
            }
            return field;
        }
        throw new NoSuchFieldException("Could not find field " + cls.getName() + "." + fieldName);
    }

    /**
     * 获取类中具有给定字段名的静态字段
     *
     * @param cls
     *            类
     * @param fieldName
     *            字段名
     * @return 请求字段名对应的 {@link Field} 对象，如果类中未找到该字段则返回 null
     * @throws Exception
     *             如果找不到字段
     */
    protected Field findStaticField(final Class<?> cls, final String fieldName) throws Exception {
        return findField(cls, null, fieldName);
    }

    /**
     * 获取类中具有给定字段名的非静态字段
     *
     * @param obj
     *            对象实例，静态字段时可为 null
     * @param fieldName
     *            字段名
     * @return 请求字段名对应的 {@link Field} 对象，如果类中未找到该字段则返回 null
     * @throws Exception
     *             如果找不到字段
     */
    protected Field findInstanceField(final Object obj, final String fieldName) throws Exception {
        if (obj == null) {
            throw new IllegalArgumentException("obj cannot be null");
        }
        return findField(obj.getClass(), obj, fieldName);
    }

    /**
     * 按名称和参数类型获取方法
     *
     * @param cls
     *            类
     * @param obj
     *            对象实例，静态方法时可为 null
     * @param methodName
     *            方法名
     * @param paramTypes
     *            方法参数的类型对于基本类型参数，请使用例如 Integer.TYPE
     * @return 匹配方法对应的 {@link Method} 对象，如果类中未找到该方法则返回 null
     * @throws Exception
     *             如果找不到方法
     */
    protected Method findMethod(final Class<?> cls, final Object obj, final String methodName,
                                final Class<?>... paramTypes) throws Exception {
        final List<Method> methodsForName = classToClassMemberCache.get(cls, null).methodNameToMethods
                .get(methodName);
        if (methodsForName != null) {
            // 返回第一个签名匹配且已可访问的方法
            boolean found = false;
            for (final Method method : methodsForName) {
                if (Arrays.equals(method.getParameterTypes(), paramTypes)) {
                    found = true;
                    if (isAccessible(obj, method)) {
                        return method;
                    }
                }
            }
            // 如果方法找到了但不可访问，尝试使其可访问然后返回
            // (可能会在 stderr 上产生反射访问警告)
            if (found) {
                for (final Method method : methodsForName) {
                    if (Arrays.equals(method.getParameterTypes(), paramTypes) && makeAccessible(obj, method)) {
                        return method;
                    }
                }
            }
            throw new NoSuchMethodException(
                    "Could not make method accessible: " + cls.getName() + "." + methodName);
        }
        throw new NoSuchMethodException("Could not find method " + cls.getName() + "." + methodName);
    }

    /**
     * 按名称和参数类型获取静态方法
     *
     * @param cls
     *            类
     * @param methodName
     *            方法名
     * @param paramTypes
     *            方法参数的类型对于基本类型参数，请使用例如 Integer.TYPE
     * @return 匹配方法对应的 {@link Method} 对象，如果类中未找到该方法则返回 null
     * @throws Exception
     *             如果找不到方法
     */
    protected Method findStaticMethod(final Class<?> cls, final String methodName, final Class<?>... paramTypes)
            throws Exception {
        return findMethod(cls, null, methodName, paramTypes);
    }

    /**
     * 按名称和参数类型获取非静态方法
     *
     * @param obj
     *            对象实例，静态方法时可为 null
     * @param methodName
     *            方法名
     * @param paramTypes
     *            方法参数的类型对于基本类型参数，请使用例如 Integer.TYPE
     * @return 匹配方法对应的 {@link Method} 对象，如果类中未找到该方法则返回 null
     * @throws Exception
     *             如果找不到方法
     */
    protected Method findInstanceMethod(final Object obj, final String methodName, final Class<?>... paramTypes)
            throws Exception {
        if (obj == null) {
            throw new IllegalArgumentException("obj cannot be null");
        }
        return findMethod(obj.getClass(), obj, methodName, paramTypes);
    }

    /** 缓存类成员 */
    public class ClassMemberCache {
        private final Map<String, List<Method>> methodNameToMethods = new HashMap<>();
        private final Map<String, Field> fieldNameToField = new HashMap<>();

        private ClassMemberCache(final Class<?> cls) throws Exception {
            // 遍历类及其超类，查找用于开始遍历的初始接口
            final Set<Class<?>> visited = new HashSet<>();
            final LinkedList<Class<?>> interfaceQueue = new LinkedList<Class<?>>();
            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                try {
                    // 缓存所有已声明的方法和字段
                    for (final Method m : getDeclaredMethods(c)) {
                        cacheMethod(m);
                    }
                    for (final Field f : getDeclaredFields(c)) {
                        cacheField(f);
                    }
                    // 查找此类或其超类实现的接口和超接口
                    if (c.isInterface() && visited.add(c)) {
                        interfaceQueue.add(c);
                    }
                    for (final Class<?> iface : c.getInterfaces()) {
                        if (visited.add(iface)) {
                            interfaceQueue.add(iface);
                        }
                    }
                } catch (final Exception e) {
                    // 跳过
                }
            }
            // 遍历接口查找默认方法
            while (!interfaceQueue.isEmpty()) {
                final Class<?> iface = interfaceQueue.remove();
                try {
                    for (final Method m : getDeclaredMethods(iface)) {
                        cacheMethod(m);
                    }
                } catch (final Exception e) {
                    // 跳过
                }
                for (final Class<?> superIface : iface.getInterfaces()) {
                    if (visited.add(superIface)) {
                        interfaceQueue.add(superIface);
                    }
                }
            }
        }

        private void cacheMethod(final Method method) {
            List<Method> methodsForName = methodNameToMethods.computeIfAbsent(method.getName(), k -> new ArrayList<>());
            methodsForName.add(method);
        }

        private void cacheField(final Field field) {
            // 仅当映射中不存在时，才存入字段名到字段的映射，这样子类会遮蔽超类中同名的字段
            if (!fieldNameToField.containsKey(field.getName())) {
                fieldNameToField.put(field.getName(), field);
            }
        }
    }
}
