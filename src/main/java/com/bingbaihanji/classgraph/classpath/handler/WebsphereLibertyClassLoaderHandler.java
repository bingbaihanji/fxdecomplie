/*
 * This file is part of ClassGraph.
 *
 * Author: R. Kempees
 *
 * With contributions from @cpierceworld (#414)
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 R. Kempees (contributed to the ClassGraph project)
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
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * WebsphereLibertyClassLoaderHandler
 *
 * <p>
 * 用于在 com.bingbaihanji.classgraph.core 中支持 WAS Liberty Profile 类加载
 *
 * @author R. Kempees
 */
class WebsphereLibertyClassLoaderHandler implements ClassLoaderHandler {
    /** {@code "com.ibm.ws.classloading.internal."} */
    private static final String PKG_PREFIX = "com.ibm.ws.classloading.internal.";

    /** {@code "com.ibm.ws.classloading.internal.AppClassLoader"} */
    private static final String IBM_APP_CLASS_LOADER = PKG_PREFIX + "AppClassLoader";

    /** {@code "com.ibm.ws.classloading.internal.ThreadContextClassLoader"} */
    private static final String IBM_THREAD_CONTEXT_CLASS_LOADER = PKG_PREFIX + "ThreadContextClassLoader";

    /** 类不可构造 */
    public WebsphereLibertyClassLoaderHandler() {
    }

    /**
     * 从 containerClassLoader 对象获取路径
     *
     * <p>
     * 传入的对象应为 "com.ibm.ws.classloading.internal.ContainerClassLoader" 的实例
     * <p>
     * 将尝试使用 "getContainerURLs" 方法重新获取类路径
     *
     * @param containerClassLoader
     *            containerClassLoader 对象
     * @return 路径对象的集合，类型为 {@link URL} 或 {@link String}
     */
    private static Collection<Object> getPaths(final Object containerClassLoader,
                                               final ReflectionUtils reflectionUtils) {
        if (containerClassLoader == null) {
            return Collections.emptyList();
        }

        // 期望这是
        // "com.ibm.ws.classloading.internal.ContainerClassLoader$UniversalContainer" 的实例
        // 调用 "getContainerURLs" 以获取其容器的类路径
        Collection<Object> urls = callGetUrls(containerClassLoader, "getContainerURLs", reflectionUtils);
        if (urls != null && !urls.isEmpty()) {
            return urls;
        }

        // "getContainerURLs" 不起作用，尝试获取容器对象……
        final Object container = reflectionUtils.getFieldVal(false, containerClassLoader, "container");
        if (container == null) {
            return Collections.emptyList();
        }

        // 应为 "com.ibm.wsspi.adaptable.module.Container" 的实例
        // 调用 "getURLs" 以获取其类路径
        urls = callGetUrls(container, "getURLs", reflectionUtils);
        if (urls != null && !urls.isEmpty()) {
            return urls;
        }

        // "getURLs" 不起作用，回退到之前的 "delegate" 内省逻辑
        final Object delegate = reflectionUtils.getFieldVal(false, container, "delegate");
        if (delegate == null) {
            return Collections.emptyList();
        }

        final String path = (String) reflectionUtils.getFieldVal(false, delegate, "path");
        if (path != null && path.length() > 0) {
            return Collections.singletonList((Object) path);
        }

        final Object base = reflectionUtils.getFieldVal(false, delegate, "base");
        if (base == null) {
            // 放弃
            return Collections.emptyList();
        }

        final Object archiveFile = reflectionUtils.getFieldVal(false, base, "archiveFile");
        if (archiveFile != null) {
            final File file = (File) archiveFile;
            return Collections.singletonList((Object) file.getAbsolutePath());
        }
        return Collections.emptyList();
    }

    /**
     * 调用 "getURLs" 方法的工具，展平"集合的集合"并忽略
     * "UnsupportedOperationException"
     *
     * 所有 "getURLs" 方法最终都会调用 "com.ibm.wsspi.adaptable.module.Container#getURLs()"
     *
     * https://www.ibm.com/support/knowledgecenter/SSEQTP_liberty/com.ibm.websphere.javadoc.liberty.doc
     * /com.ibm.websphere.appserver.spi.artifact_1.2-javadoc
     * /com/ibm/wsspi/adaptable/module/Container.html?view=embed#getURLs() "表示所有为此容器贡献内容的
     * 磁盘位置的 URL 集合"
     */
    @SuppressWarnings("unchecked")
    private static Collection<Object> callGetUrls(final Object container, final String methodName,
                                                  final ReflectionUtils reflectionUtils) {
        if (container != null) {
            try {
                final Collection<Object> results = (Collection<Object>) reflectionUtils.invokeMethod(false,
                        container, methodName);
                if (results != null && !results.isEmpty()) {
                    final Collection<Object> allUrls = new HashSet<>();
                    for (final Object result : results) {
                        if (result instanceof Collection) {
                            // SmartClassPath 返回 URL 集合的集合
                            for (final Object url : ((Collection<Object>) result)) {
                                if (url != null) {
                                    allUrls.add(url);
                                }
                            }
                        } else if (result != null) {
                            allUrls.add(result);
                        }
                    }
                    return allUrls;
                }
            } catch (final UnsupportedOperationException e) {
                /* 忽略 */
            }
        }
        return Collections.emptyList();
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
        return ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass, IBM_APP_CLASS_LOADER)
                || ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass,
                IBM_THREAD_CONTEXT_CLASS_LOADER);
    }

    /**
     * 查找某个 {@link ClassLoader} 的 {@link ClassLoader} 委托顺序
     *
     * @param classLoader
     *            要查找委托顺序的 {@link ClassLoader}
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
     * 查找关联 {@link ClassLoader} 的类路径条目
     *
     * @param classLoader
     *            要查找类路径条目顺序的 {@link ClassLoader}
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
        Object smartClassPath;
        final Object appLoader = classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "appLoader");
        if (appLoader != null) {
            smartClassPath = classpathOrder.reflectionUtils.getFieldVal(false, appLoader, "smartClassPath");
        } else {
            smartClassPath = classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "smartClassPath");
        }
        if (smartClassPath != null) {
            // "com.ibm.ws.classloading.internal.ContainerClassLoader$SmartClassPath"
            // 接口指定了 "getClassPath" 来返回构成其路径的所有 URL
            final Collection<Object> paths = callGetUrls(smartClassPath, "getClassPath",
                    classpathOrder.reflectionUtils);
            if (!paths.isEmpty()) {
                for (final Object path : paths) {
                    classpathOrder.addClasspathEntry(path, classLoader, ScanConfig, log);
                }
            } else {
                // "getClassPath" 不起作用……回退到遍历 "classPath" 元素
                @SuppressWarnings("unchecked") final List<Object> classPathElements = (List<Object>) classpathOrder.reflectionUtils
                        .getFieldVal(false, smartClassPath, "classPath");
                if (classPathElements != null && !classPathElements.isEmpty()) {
                    for (final Object Classpath : classPathElements) {
                        final Collection<Object> subPaths = getPaths(Classpath,
                                classpathOrder.reflectionUtils);
                        for (final Object path : subPaths) {
                            classpathOrder.addClasspathEntry(path, classLoader, ScanConfig, log);
                        }
                    }
                }
            }
        }
    }
}
