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
package com.bingbaihanji.classgraph.classpath.handler;

import com.bingbaihanji.classgraph.classpath.ClassLoaderFinder;
import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.scan.ScanClassLoader;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.LogNode;

import java.net.URL;

/**
 * 允许以 ScanClassLoader 作为参数调用 overrideClassLoaders，以便嵌套扫描可以共享单个类加载器(#485)
 */
class ClassGraphClassLoaderHandler implements ClassLoaderHandler {
    /** 类不可构造 */
    public ClassGraphClassLoaderHandler() {
    }

    /**
     * 检查此 {@link ClassLoaderHandler} 是否能够处理给定的 {@link ClassLoader}
     *
     * @param classLoaderClass
     *            {@link ClassLoader} 类或其超类之一
     * @param log
     *            日志
     * @return 如果此 {@link ClassLoaderHandler} 能够处理 {@link ClassLoader}，则返回 true
     */
    @Override
    public boolean canHandle(final Class<?> classLoaderClass, final LogNode log) {
        final boolean matches = ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass,
                "com.bingbaihanji.classgraph.core.ScanClassLoader");
        if (matches && log != null) {
            log.log("Sharing a `ScanClassLoader` between multiple nested scans is not advisable, "
                    + "because scan criteria may differ between scans. "
                    + "See: https://github.com/classgraph/classgraph/issues/485");
        }
        return matches;
    }

    /**
     * 查找 {@link ClassLoader} 的 {@link ClassLoader} 委托顺序
     *
     * @param classLoader
     *            要查找其顺序的 {@link ClassLoader}
     * @param classLoaderOrder
     *            要更新的 {@link ClassLoaderOrder} 对象
     * @param log
     *            日志
     */
    @Override
    public void findClassLoaderOrder(final ClassLoader classLoader, final ClassLoaderOrder classLoaderOrder,
                                     final LogNode log) {
        classLoaderOrder.delegateTo(classLoader.getParent(), /* isParent = */ true, log);
        classLoaderOrder.add(classLoader, log);
    }

    /**
     * 查找关联的 {@link ClassLoader} 的类路径条目
     *
     * @param classLoader 要查找其类路径条目顺序的 {@link ClassLoader}
     * @param classpathOrder 要更新的 {@link ClasspathOrder} 对象
     * @param ScanConfig {@link ScanConfig}
     * @param log 日志
     */
    @Override
    public void findClasspathOrder(final ClassLoader classLoader, final ClasspathOrder classpathOrder,
                                   final ScanConfig ScanConfig, final LogNode log) {
        // ScanClassLoader 覆盖了 URLClassLoader，因此我们可以使用与 URLClassLoader 相同的方式获取基本类路径 URL
        // 然而，类加载将优先尝试重用旧的 ScanClassLoader，然后再使用当前扫描中的新 ScanClassLoader 进行加载，
        // 因此以下 URL 将被当前扫描扫描，但只有旧的类加载器失败时，才会从这些 URL 加载类
        for (final URL url : ((ScanClassLoader) classLoader).getURLs()) {
            if (url != null) {
                classpathOrder.addClasspathEntry(url, classLoader, ScanConfig, log);
            }
        }
    }
}
