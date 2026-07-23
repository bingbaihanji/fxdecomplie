/*
 * This file is part of ClassGraph.
 *
 * Author: @mcollovati
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 @mcollovati, contributed to the ClassGraph project
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
import com.bingbaihanji.classgraph.scanspec.ScanSpec;
import com.bingbaihanji.classgraph.utils.LogNode;

import java.io.IOError;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

/**
 * 从 Quarkus ClassLoader 提取类路径条目
 */
class QuarkusClassLoaderHandler implements ClassLoaderHandler {
    // Quarkus 1.2 之前的类加载器
    private static final String RUNTIME_CLASSLOADER = "io.quarkus.runner.RuntimeClassLoader";

    // Quarkus 1.3 起的类加载器
    private static final String QUARKUS_CLASSLOADER = "io.quarkus.bootstrap.classloading.QuarkusClassLoader";

    // Quarkus 1.13 起的类加载器
    private static final String RUNNER_CLASSLOADER = "io.quarkus.bootstrap.runner.RunnerClassLoader";

    // Quarkus 3.11 之前的类路径元素
    private static final Map<String, String> PRE_311_RESOURCE_BASED_ELEMENTS;

    static {
        final Map<String, String> hlp = new HashMap<>();
        hlp.put("io.quarkus.bootstrap.classloading.JarClassPathElement", "file");
        hlp.put("io.quarkus.bootstrap.classloading.DirectoryClassPathElement", "root");
        PRE_311_RESOURCE_BASED_ELEMENTS = Collections.unmodifiableMap(hlp);
    }

    /**
     * 类不可构造
     */
    public QuarkusClassLoaderHandler() {
    }

    private static void findClasspathOrderForQuarkusClassloader(final ClassLoader classLoader,
                                                                final ClasspathOrder classpathOrder, final ScanSpec scanSpec, final LogNode log) {

        final Collection<Object> elements = findQuarkusClassLoaderElements(classLoader, classpathOrder);

        for (final Object element : elements) {
            final String elementClassName = element.getClass().getName();
            final String fieldName = PRE_311_RESOURCE_BASED_ELEMENTS.get(elementClassName);
            if (fieldName != null) {
                classpathOrder.addClasspathEntry(
                        classpathOrder.reflectionUtils.getFieldVal(false, element, fieldName), classLoader,
                        scanSpec, log);
            } else {
                final Object rootPath = classpathOrder.reflectionUtils.invokeMethod(false, element, "getRoot");
                if (rootPath instanceof Path) {
                    classpathOrder.addClasspathEntry(rootPath, classLoader, scanSpec, log);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> findQuarkusClassLoaderElements(final ClassLoader classLoader,
                                                                     final ClasspathOrder classpathOrder) {
        Collection<Object> elements = (Collection<Object>) classpathOrder.reflectionUtils.getFieldVal(false,
                classLoader, "elements");
        if (elements == null) {
            elements = new ArrayList<>();
            // Since 3.16.x
            for (final String fieldName : new String[]{"normalPriorityElements", "lesserPriorityElements"}) {
                final Collection<Object> fieldVal = (Collection<Object>) classpathOrder.reflectionUtils
                        .getFieldVal(false, classLoader, fieldName);
                if (fieldVal == null) {
                    continue;
                }
                elements.addAll(fieldVal);
            }
        }
        return elements;
    }

    @SuppressWarnings("unchecked")
    private static void findClasspathOrderForRuntimeClassloader(final ClassLoader classLoader,
                                                                final ClasspathOrder classpathOrder, final ScanSpec scanSpec, final LogNode log) {
        final Collection<Path> applicationClassDirectories = (Collection<Path>) classpathOrder.reflectionUtils
                .getFieldVal(false, classLoader, "applicationClassDirectories");
        if (applicationClassDirectories != null) {
            for (final Path path : applicationClassDirectories) {
                try {
                    final URI uri = path.toUri();
                    classpathOrder.addClasspathEntryObject(uri, classLoader, scanSpec, log);
                } catch (IOError | SecurityException e) {
                    if (log != null) {
                        log.log("Could not convert path to URI: " + path);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void findClasspathOrderForRunnerClassloader(final ClassLoader classLoader,
                                                               final ClasspathOrder classpathOrder, final ScanSpec scanSpec, final LogNode log) {
        for (final Object[] elementArray : ((Map<String, Object[]>) classpathOrder.reflectionUtils
                .getFieldVal(false, classLoader, "resourceDirectoryMap")).values()) {
            for (final Object element : elementArray) {
                final String elementClassName = element.getClass().getName();
                if ("io.quarkus.bootstrap.runner.JarResource".equals(elementClassName)) {
                    classpathOrder.addClasspathEntry(
                            classpathOrder.reflectionUtils.getFieldVal(false, element, "jarPath"), classLoader,
                            scanSpec, log);
                }
            }
        }
    }

    /**
     * 能否处理
     *
     * @param classLoaderClass
     *            类加载器类
     * @param log
     *            日志
     * @return 如果 classLoaderClass 是 Quarkus RuntimeClassloader 或 QuarkusClassloader，则返回 true
     */
    @Override
    public boolean canHandle(final Class<?> classLoaderClass, final LogNode log) {
        return ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass, RUNTIME_CLASSLOADER)
                || ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass, QUARKUS_CLASSLOADER)
                || ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass, RUNNER_CLASSLOADER);
    }

    /**
     * 查找类加载器顺序
     *
     * @param classLoader
     *            类加载器
     * @param classLoaderOrder
     *            类加载器顺序
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
     * @param scanSpec
     *            {@link ScanSpec}
     * @param log
     *            日志
     */
    @Override
    public void findClasspathOrder(final ClassLoader classLoader, final ClasspathOrder classpathOrder,
                                   final ScanSpec scanSpec, final LogNode log) {

        final String classLoaderName = classLoader.getClass().getName();
        if (RUNTIME_CLASSLOADER.equals(classLoaderName)) {
            findClasspathOrderForRuntimeClassloader(classLoader, classpathOrder, scanSpec, log);
        } else if (QUARKUS_CLASSLOADER.equals(classLoaderName)) {
            findClasspathOrderForQuarkusClassloader(classLoader, classpathOrder, scanSpec, log);
        } else if (RUNNER_CLASSLOADER.equals(classLoaderName)) {
            findClasspathOrderForRunnerClassloader(classLoader, classpathOrder, scanSpec, log);
        }
    }
}
