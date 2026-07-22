/*
 * This file is part of ClassGraph.
 *
 * Author: Michael J. Simons
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
package com.bingbaihanji.classgraph.classloaderhandler;

import com.bingbaihanji.classgraph.classpath.ClassLoaderFinder;
import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.scanspec.ScanSpec;
import com.bingbaihanji.classgraph.utils.LogNode;

/**
 * 此处理器使用
 * {@link com.bingbaihanji.classgraph.classloaderhandler.ClassLoaderHandler.DelegationOrder#PARENT_LAST} 来支持
 * Spring Boot devtools 的 <code>RestartClassLoader</code><code>RestartClassLoader</code> 为指定的
 * URL(这些都是在开发过程中应更改的)提供父级最后加载因此，该类加载器的处理器也必须以
 * <code>PARENT_LAST</code> 顺序进行委托
 */
class SpringBootRestartClassLoaderHandler implements ClassLoaderHandler {
    /** 类不可构造 */
    public SpringBootRestartClassLoaderHandler() {
    }

    /**
     * 检查此 {@link ClassLoaderHandler} 是否能够处理给定的 {@link ClassLoader}
     *
     * @param classLoaderClass
     *            {@link ClassLoader} 类或其超类
     * @param log
     *            日志
     * @return 如果此 {@link ClassLoaderHandler} 能够处理 {@link ClassLoader}，则返回 true
     */
    @Override
    public boolean canHandle(final Class<?> classLoaderClass, final LogNode log) {
        return ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass,
                "org.springframework.boot.devtools.restart.classloader.RestartClassLoader");
    }

    /**
     * 查找某个 {@link ClassLoader} 的 {@link ClassLoader} 委托顺序
     *
     * @param classLoader  要查找委托顺序的 {@link ClassLoader}
     * @param classLoaderOrder  要更新的 {@link ClassLoaderOrder} 对象
     * @param log 日志
     */
    @Override
    public void findClassLoaderOrder(final ClassLoader classLoader, final ClassLoaderOrder classLoaderOrder,
                                     final LogNode log) {
        // Restart 类加载器是父级最后类加载器，因此首先将 Restart 类加载器本身添加到类加载器顺序中
        new ParentLastDelegationOrderTestClassLoaderHandler().findClassLoaderOrder(classLoader, classLoaderOrder, log);
    }

    /**
     * 查找关联 {@link ClassLoader} 的类路径条目
     *
     * Spring Boot 的 RestartClassLoader 位于父级类加载器之前，并监视一组给定的目录是否有更改
     * 虽然这些类可以直接从父级类加载器访问，但在通过 Spring Boot Developer Tools 完全关闭之前，
     * 它们应始终通过 RestartClassLoader 的直接访问来加载
     *
     * RestartClassLoader 仅遮蔽项目类和可配置的附加目录，因此它本身需要以最后顺序访问父级
     *
     * 参见：<a href="https://github.com/classgraph/classgraph/issues/267">#267</a>、
     * <a href="https://github.com/classgraph/classgraph/issues/268">#268</a>
     *
     * @param classLoader
     *            要查找类路径条目顺序的 {@link ClassLoader}
     * @param classpathOrder
     *            要更新的 {@link ClasspathOrder} 对象
     * @param scanSpec
     *            {@link ScanSpec}
     * @param log
     *            日志
     */
    @Override
    public void findClasspathOrder(final ClassLoader classLoader, final ClasspathOrder classpathOrder,
                                   final ScanSpec scanSpec, final LogNode log) {
        // Restart 类加载器本身不存储任何 URL
    }
}