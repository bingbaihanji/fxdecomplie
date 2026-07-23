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
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.LogNode;

import java.util.SortedSet;

/**
 * 处理 Plexus ClassWorlds ClassRealm ClassLoader
 *
 * @author lukehutch
 */
class PlexusClassWorldsClassRealmClassLoaderHandler implements ClassLoaderHandler {
    /** 类不可构造 */
    public PlexusClassWorldsClassRealmClassLoaderHandler() {
    }

    /**
     * 检查此类加载器是否使用父级优先策略
     *
     * @param classRealmInstance
     *            ClassRealm 实例
     * @return 如果类加载器使用父级优先策略则返回 true
     */
    private static boolean isParentFirstStrategy(final ClassLoader classRealmInstance,
                                                 final ReflectionUtils reflectionUtils) {
        final Object strategy = reflectionUtils.getFieldVal(false, classRealmInstance, "strategy");
        if (strategy != null) {
            final String strategyClassName = strategy.getClass().getName();
            if ("org.codehaus.plexus.classworlds.strategy.SelfFirstStrategy".equals(strategyClassName)
                    || "org.codehaus.plexus.classworlds.strategy.OsgiBundleStrategy".equals(strategyClassName)) {
                // 策略为自身优先
                return false;
            }
        }
        // 策略为 org.codehaus.plexus.classworlds.strategy.ParentFirstStrategy(或未能找到策略)
        return true;
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
                "org.codehaus.plexus.classworlds.realm.ClassRealm");
    }

    /**
     * 查找某个 {@link ClassLoader} 的 {@link ClassLoader} 委托顺序
     *
     * @param classRealm
     *            要查找委托顺序的 {@link ClassLoader}
     * @param classLoaderOrder
     *            要更新的 {@link ClassLoaderOrder} 对象
     * @param log
     *            日志
     */
    @Override
    public void findClassLoaderOrder(final ClassLoader classRealm, final ClassLoaderOrder classLoaderOrder,
                                     final LogNode log) {
        // 来自 ClassRealm#loadClassFromImport(String) -> getImportClassLoader(String)
        final Object foreignImports = classLoaderOrder.reflectionUtils.getFieldVal(false, classRealm,
                "foreignImports");
        if (foreignImports != null) {
            @SuppressWarnings("unchecked") final SortedSet<Object> foreignImportEntries = (SortedSet<Object>) foreignImports;
            for (final Object entry : foreignImportEntries) {
                final ClassLoader foreignImportClassLoader = (ClassLoader) classLoaderOrder.reflectionUtils
                        .invokeMethod(false, entry, "getClassLoader");
                // 将外部导入类加载器视为父级类加载器
                classLoaderOrder.delegateTo(foreignImportClassLoader, /* isParent = */ true, log);
            }
        }

        // 获取委托顺序 -- 不同的策略有不同的委托顺序
        final boolean isParentFirst = isParentFirstStrategy(classRealm, classLoaderOrder.reflectionUtils);

        // 来自 ClassRealm#loadClassFromSelf(String) -> findLoadedClass(String)(自身优先策略)
        if (!isParentFirst) {
            // 在父级之前添加自身
            classLoaderOrder.add(classRealm, log);
        }

        // 来自 ClassRealm#loadClassFromParent -- 注意：我们忽略了 parentImports，它用于在决定是否调用父级类加载器
        // 之前过滤类名(因此 ClassGraph 将能够按名称加载未从父级类加载器导入的类)
        final ClassLoader parentClassLoader = (ClassLoader) classLoaderOrder.reflectionUtils.invokeMethod(false,
                classRealm, "getParentClassLoader");
        classLoaderOrder.delegateTo(parentClassLoader, /* isParent = */ true, log);
        classLoaderOrder.delegateTo(classRealm.getParent(), /* isParent = */ true, log);

        // 来自 ClassRealm#loadClassFromSelf(String) -> findLoadedClass(String)(父级优先策略)
        if (isParentFirst) {
            // 在父级之后添加自身
            classLoaderOrder.add(classRealm, log);
        }
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
        // ClassRealm 继承 URLClassLoader
        new URLClassLoaderHandler().findClasspathOrder(classLoader, classpathOrder, ScanConfig, log);
    }
}
