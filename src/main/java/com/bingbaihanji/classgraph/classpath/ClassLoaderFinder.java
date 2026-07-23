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
package com.bingbaihanji.classgraph.classpath;

import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.LogNode;

import java.util.LinkedHashSet;

/** 用于查找唯一有序类路径元素的类 */
public class ClassLoaderFinder {
    /** 上下文类加载器 */
    private final ClassLoader[] contextClassLoaders;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 用于查找唯一有序类路径元素的类
     *
     * @param ScanConfig  扫描规格，如果没有则可以为 null
     * @param log  日志
     */
    ClassLoaderFinder(final ScanConfig ScanConfig, final ReflectionUtils reflectionUtils, final LogNode log) {
        LinkedHashSet<ClassLoader> classLoadersUnique;
        LogNode classLoadersFoundLog;
        if (ScanConfig.overrideClassLoaders == null) {
            // 未覆盖 ClassLoader

            // 这里有一些关于选择最佳或正确类加载器的建议，但不完整
            // (例如，它没有涵盖父级委托模式)：
            // http://www.javaworld.com/article/2077344/core-java/find-a-way-out-of-the-classloader-maze.html?page=2

            // 获取线程上下文类加载器(这是第一个要尝试的类加载器，因为上下文类加载器
            // 可以按线程粒度设置为覆盖项)
            classLoadersUnique = new LinkedHashSet<>();
            final ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
            if (threadClassLoader != null) {
                classLoadersUnique.add(threadClassLoader);
            }

            // 获取此类的类加载器，通常就是调用 ClassGraph 的类的类加载器
            // (当未提供类加载器时，Class.forName(className) 使用调用者的类加载器)
            final ClassLoader currClassClassLoader = getClass().getClassLoader();
            if (currClassClassLoader != null) {
                classLoadersUnique.add(currClassClassLoader);
            }

            // 获取系统类加载器(如果上面的方法都不起作用，则作为回退)
            final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
            if (systemClassLoader != null) {
                classLoadersUnique.add(systemClassLoader);
            }

            // JDK9+ 中还有一个类加载器，即平台类加载器(用于处理扩展)，
            // 参见：http://openjdk.java.net/jeps/261#Class-loaders
            // 获取它的方法调用是 ClassLoader.getPlatformClassLoader()
            // 然而，由于无法从此类加载器获取 URL，并且它是
            // ClassLoader.getSystemClassLoader() 返回的应用程序类加载器的父级(因此会被
            // 应用程序类加载器委托)，所以没有必要在这里添加它模块会被直接
            // 扫描，因此我们不需要从平台类加载器获取模块路径条目

            // 查找调用栈上类的类加载器，以防有遗漏
            try {
                final Class<?>[] callStack = new CallStackReader(reflectionUtils).getClassContext(log);
                for (int i = callStack.length - 1; i >= 0; --i) {
                    final ClassLoader callerClassLoader = callStack[i].getClassLoader();
                    if (callerClassLoader != null) {
                        classLoadersUnique.add(callerClassLoader);
                    }
                }
            } catch (final IllegalArgumentException e) {
                if (log != null) {
                    log.log("Could not get call stack", e);
                }
            }

            // 将自定义添加的类加载器放在系统/上下文/模块类加载器之后
            if (ScanConfig.addedClassLoaders != null) {
                classLoadersUnique.addAll(ScanConfig.addedClassLoaders);
            }
            classLoadersFoundLog = log == null ? null : log.log("Found ClassLoaders:");

        } else {
            // ClassLoader 已被覆盖
            classLoadersUnique = new LinkedHashSet<>(ScanConfig.overrideClassLoaders);
            classLoadersFoundLog = log == null ? null : log.log("Override ClassLoaders:");
        }

        // 记录所有已识别的 ClassLoader
        if (classLoadersFoundLog != null) {
            for (final ClassLoader classLoader : classLoadersUnique) {
                classLoadersFoundLog.log(classLoader.getClass().getName());
            }
        }

        this.contextClassLoaders = classLoadersUnique.toArray(new ClassLoader[0]);
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 如果该类是、继承或实现了给定名称的类或接口，则返回 true */
    // TODO: 在 ClassGraph 5.x 中将此方法作为 ClassLoaderHandler 接口的默认方法
    public static boolean classIsOrExtendsOrImplements(Class<?> cls, String className) {
        if (cls == null) {
            return false;
        }
        if (cls.getName().equals(className)) {
            return true;
        }
        if (classIsOrExtendsOrImplements(cls.getSuperclass(), className)) {
            return true;
        }
        for (Class<?> iface : cls.getInterfaces()) {
            if (classIsOrExtendsOrImplements(iface, className)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取上下文类加载器
     *
     * @return 上下文类加载器，以及任何不是上下文类加载器祖先的其他类加载器
     */
    public ClassLoader[] getContextClassLoaders() {
        return contextClassLoaders;
    }
}
