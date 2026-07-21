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
package com.bingbaihanji.classgraph.classloaderhandler;

import com.bingbaihanji.classgraph.classpath.ClassLoaderFinder;
import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.scanspec.ScanSpec;
import com.bingbaihanji.classgraph.utils.LogNode;

/** 能够从 CxfContainerClassLoader 提取 URL 的 ClassLoaderHandler。 */
class CxfContainerClassLoaderHandler implements ClassLoaderHandler {
    /** 类不可构造。 */
    public CxfContainerClassLoaderHandler() {
    }

    /**
     * 检查此 {@link ClassLoaderHandler} 是否能够处理给定的 {@link ClassLoader}。
     *
     * @param classLoaderClass
     *            {@link ClassLoader} 类或其超类。
     * @param log
     *            日志
     * @return 如果此 {@link ClassLoaderHandler} 能够处理 {@link ClassLoader}，则返回 true。
     */
    @Override public boolean canHandle(final Class<?> classLoaderClass, final LogNode log) {
        return ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass,
                "org.apache.openejb.server.cxf.transport.util.CxfContainerClassLoader");
    }

    /**
     * 查找某个 {@link ClassLoader} 的 {@link ClassLoader} 委托顺序。
     *
     * @param classLoader
     *            要查找委托顺序的 {@link ClassLoader}。
     * @param classLoaderOrder
     *            要更新的 {@link ClassLoaderOrder} 对象。
     * @param log
     *            日志
     */
    @Override public void findClassLoaderOrder(final ClassLoader classLoader, final ClassLoaderOrder classLoaderOrder,
                                            final LogNode log) {
        try {
            classLoaderOrder.delegateTo(
                    Class.forName("org.apache.openejb.server.cxf.transport.util.CxfUtil").getClassLoader(),
                    /* isParent = */ true, log);
        } catch (LinkageError | ClassNotFoundException e) {
            // 忽略
        }
        // tccl = TomcatClassLoader
        classLoaderOrder.delegateTo(
                (ClassLoader) classLoaderOrder.reflectionUtils.invokeMethod(false, classLoader, "tccl"),
                /* isParent = */ false, log);
        // 此类加载器实际上不加载任何类，但将其添加到顺序中可改进日志记录
        classLoaderOrder.add(classLoader, log);
    }

    /**
     * 查找关联 {@link ClassLoader} 的类路径条目。
     *
     * @param classLoader
     *            要查找类路径条目顺序的 {@link ClassLoader}。
     * @param classpathOrder
     *            要更新的 {@link ClasspathOrder} 对象。
     * @param scanSpec
     *            {@link ScanSpec}。
     * @param log
     *            日志。
     */
    @Override public void findClasspathOrder(final ClassLoader classLoader, final ClasspathOrder classpathOrder,
                                          final ScanSpec scanSpec, final LogNode log) {
        // 类加载器本身不执行任何类加载操作，它仅委托给其他类加载器
    }
}
