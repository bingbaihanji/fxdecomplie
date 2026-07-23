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

import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.LogNode;

/**
 * 回退 ClassLoaderHandler尝试从一系列可能的方法和字段名中获取类路径
 */
class FallbackClassLoaderHandler implements ClassLoaderHandler {
    /** 类不可构造 */
    public FallbackClassLoaderHandler() {
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
        // 这是回退处理器，它可以处理任何情况
        return true;
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
     * @param classLoader
     *            要查找其类路径条目顺序的 {@link ClassLoader}
     * @param classpathOrder
     *            要更新的 {@link ClasspathOrder} 对象
     * @param ScanConfig
     *            {@link ScanConfig}
     * @param log
     *            日志
     */
    @Override
    public void findClasspathOrder(final ClassLoader classLoader, final ClasspathOrder classpathOrder,
                                   final ScanConfig ScanConfig, final LogNode log) {
        boolean valid = false;
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getClassPath"), classLoader,
                ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getClasspath"), classLoader,
                ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "classpath"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "classPath"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "cp"), classLoader, ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "classpath"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "classPath"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "cp"), classLoader, ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getPath"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getPaths"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "path"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "paths"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "paths"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "paths"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getDir"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getDirs"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "dir"), classLoader, ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "dirs"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "dir"), classLoader, ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "dirs"), classLoader, ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getFile"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getFiles"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "file"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "files"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "file"), classLoader, ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "files"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getJar"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getJars"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "jar"), classLoader, ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "jars"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "jar"), classLoader, ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "jars"), classLoader, ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getURL"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getURLs"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getUrl"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getUrls"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "url"), classLoader, ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "urls"), classLoader, ScanConfig,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "url"), classLoader, ScanConfig, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "urls"), classLoader, ScanConfig, log);
        if (log != null) {
            log.log("FallbackClassLoaderHandler " + (valid ? "found" : "did not find")
                    + " classpath entries in unknown ClassLoader " + classLoader);
        }
    }
}
