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
 * Copyright (c) 2019 Luke Hutchison, with significant contributions from Davy De Durpel
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
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.FileUtils;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.File;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;

/**
 * 从 JBoss ClassLoader 提取类路径条目参见：
 *
 * <p>
 * https://github.com/jboss-modules/jboss-modules/blob/master/src/main/java/org/jboss/modules/ModuleClassLoader.java
 */
class JBossClassLoaderHandler implements ClassLoaderHandler {
    /** 类不可构造 */
    public JBossClassLoaderHandler() {
    }

    /**
     * 处理资源加载器
     *
     * @param resourceLoader
     *            资源加载器
     * @param classLoader
     *            类加载器
     * @param classpathOrderOut
     *            类路径顺序
     * @param ScanConfig
     *            扫描规范
     * @param log
     *            日志
     */
    private static void handleResourceLoader(final Object resourceLoader, final ClassLoader classLoader,
                                             final ClasspathOrder classpathOrderOut, final ScanConfig ScanConfig, final LogNode log) {
        if (resourceLoader == null) {
            return;
        }
        // PathResourceLoader 有 root 字段，它是一个 Path 对象
        final Object root = classpathOrderOut.reflectionUtils.getFieldVal(false, resourceLoader, "root");

        classpathOrderOut.addClasspathEntry(loadJarPathFromClassicVFS(root, classpathOrderOut), classLoader,
                ScanConfig, log);
        classpathOrderOut.addClasspathEntry(loadJarPathFromNewVFS(root, classpathOrderOut), classLoader, ScanConfig,
                log);
        classpathOrderOut.addClasspathEntry(
                classpathOrderOut.reflectionUtils.getFieldVal(false, resourceLoader, "fileOfJar"), classLoader,
                ScanConfig, log);
    }

    /**
     * 使用 JBoss VFS 机制从给定的根对象返回 JAR 文件的绝对路径这适用于包含以下更改的 JBoss/Wildfly 版本：
     * <a href="https://issues.redhat.com/browse/WFLY-18544">WFLY-18544</a>
     * <a href="https://issues.redhat.com/browse/JBEAP-25879">JBEAP-25879</a>
     * <a href="https://issues.redhat.com/browse/JBEAP-25677">JBEAP-25677</a>
     *
     * @param root
     *            用于获取 JAR 路径的根对象
     * @param classpathOrderOut
     *            用于更新类路径顺序的 ClasspathOrder 对象
     * @return JAR 文件的 {@link File}，如果找不到路径则返回 null
     */
    private static File loadJarPathFromNewVFS(final Object root, final ClasspathOrder classpathOrderOut) {
        if (root == null) {
            return null;
        }
        final Class<?> jbossVFS = getJBossVFSAccess(root);
        if (jbossVFS == null) {
            return null;
        }
        // 尝试查找根的挂载点类型为 org.jboss.vfs.VFS.Mount
        final Object mount = classpathOrderOut.reflectionUtils.invokeStaticMethod(false, jbossVFS, "getMount",
                root.getClass(), root);
        if (mount == null) {
            return null;
        }
        // 尝试访问挂载点的 fileSystem类型为 org.jboss.vfs.spi.FileSystem
        final Object fileSystem = classpathOrderOut.reflectionUtils.invokeMethod(false, mount, "getFileSystem");
        if (fileSystem == null) {
            return null;
        }
        // 现在访问挂载源，即用于创建挂载的文件
        final File mountSource = (File) classpathOrderOut.reflectionUtils.invokeMethod(false, fileSystem,
                "getMountSource");
        if (mountSource == null) {
            return null;
        }
        // mountSource 的绝对路径应为"物理".jar 文件
        return mountSource;
    }

    /**
     * 获取 JBoss VFS 类的访问权限如果根对象来自 org.jboss.vfs，则首先从根对象的类加载器尝试加载 VFS
     * 如果根对象不是来自 org.jboss.vfs，则尝试从当前线程类加载器加载 VFS
     * 从当前线程上下文加载 VFS 可能没有必要，因为这意味着根对象不是来自 org.jboss.vfs，
     * VFS 在这里没有帮助……但作为防御性方法，我们仍在此处尝试获取 VFS 访问权限
     *
     * @param root
     *            JBoss VFS 的根 VirtualFile用于通过根的类加载器加载 VFS不能为 null
     * @return 表示 JBoss VFS 类的 Class 对象，如果找不到则返回 null
     */
    private static Class<?> getJBossVFSAccess(final Object root) {
        Class<?> jbossVFS = null;
        // 我们需要访问 org.jboss.vfs 的 'VFS' 类
        try {
            if (root.getClass().getName().contains("org.jboss.vfs")) {
                // 首先，尝试根对象的类加载器由于根对象来自 org.jboss.vfs，
                // 我们很可能可以从此类加载器获取 org.jboss.vfs.VFS 的访问权限
                final ClassLoader vfsRootClassloader = root.getClass().getClassLoader();
                jbossVFS = loadJBossVFS(vfsRootClassloader);
            } else {
                // 对于非 org.jboss.vfs 对象，使用当前线程
                jbossVFS = loadJBossVFS(Thread.currentThread().getContextClassLoader());
            }
        } catch (final ClassNotFoundException e) {
            try {
                // 由于上一个方法失败，尝试从当前线程的类加载器加载 JBoss VFS 访问权限
                // 如果上一个方法已经使用了当前线程的类加载器，它将再次失败……
                jbossVFS = loadJBossVFS(Thread.currentThread().getContextClassLoader());
            } catch (final ClassNotFoundException e1) {
                // 吞掉异常如果没有 VFS 存在，我们无法做任何事情……
            }
        }
        return jbossVFS;
    }

    private static Class<?> loadJBossVFS(final ClassLoader classLoader) throws ClassNotFoundException {
        return Class.forName("org.jboss.vfs.VFS", true, classLoader);
    }

    /**
     * 使用"经典"VFS 读取机制从给定的根对象返回 JAR 文件的绝对路径
     * 这适用于以下更改之前的 JBoss/Wildfly 版本：
     * <a href="https://issues.redhat.com/browse/WFLY-18544">WFLY-18544</a>
     * <a href="https://issues.redhat.com/browse/JBEAP-25879">JBEAP-25879</a>
     * <a href="https://issues.redhat.com/browse/JBEAP-25677">JBEAP-25677</a>
     *
     * @param root
     *            用于获取 JAR 路径的根对象
     * @param classpathOrderOut
     *            用于更新类路径顺序的 ClasspathOrder 对象
     * @return JAR 文件的 {@link File} 或 {@link Path}，如果找不到 VFS 路径则返回 null
     */
    private static Object loadJarPathFromClassicVFS(final Object root, final ClasspathOrder classpathOrderOut) {
        if (root == null) {
            return null;
        }
        // 类型 VirtualFile
        final File physicalFile = (File) classpathOrderOut.reflectionUtils.invokeMethod(false, root,
                "getPhysicalFile");
        if (physicalFile != null) {
            final String name = (String) classpathOrderOut.reflectionUtils.invokeMethod(false, root, "getName");
            if (name != null) {
                // getParentFile() 移除"contents"目录
                final File file = new File(physicalFile.getParentFile(), name);
                if (FileUtils.canRead(file)) {
                    return file;
                } else {
                    // 这是一个解压的 JAR 或类路径目录
                    return physicalFile;
                }
            } else {
                return physicalFile;
            }
        } else {
            final String path = (String) classpathOrderOut.reflectionUtils.invokeMethod(false, root, "getPathName");
            if (path != null) {
                return path;
            }
            return root;
        }
    }

    /**
     * 处理模块
     *
     * @param module
     *            模块
     * @param visitedModules
     *            已访问的模块
     * @param classLoader
     *            类加载器
     * @param classpathOrderOut
     *            类路径顺序
     * @param ScanConfig
     *            扫描规范
     * @param log
     *            日志
     */
    private static void handleRealModule(final Object module, final Set<Object> visitedModules,
                                         final ClassLoader classLoader, final ClasspathOrder classpathOrderOut, final ScanConfig ScanConfig,
                                         final LogNode log) {
        if (!visitedModules.add(module)) {
            // 避免多次从同一模块提取路径
            return;
        }
        ClassLoader moduleLoader = (ClassLoader) classpathOrderOut.reflectionUtils.invokeMethod(false, module,
                "getClassLoader");
        if (moduleLoader == null) {
            moduleLoader = classLoader;
        }
        // 类型 VFSResourceLoader[]
        final Object vfsResourceLoaders = classpathOrderOut.reflectionUtils.invokeMethod(false, moduleLoader,
                "getResourceLoaders");
        if (vfsResourceLoaders != null) {
            for (int i = 0, n = Array.getLength(vfsResourceLoaders); i < n; i++) {
                // 类型：JarFileResourceLoader(用于 JAR)、VFSResourceLoader(用于解压的 JAR)、
                // PathResourceLoader(用于资源目录)或 NativeLibraryResourceLoader
                // (用于通常不存在的原生库"lib/"目录，这些目录与它们可能从中提取的 JAR 文件相邻)
                final Object resourceLoader = Array.get(vfsResourceLoaders, i);
                // 可以完全跳过 NativeLibraryResourceLoader 实例，但测试它们的存在似乎只增加了约 3% 的总扫描时间
                // if (!resourceLoader.getClass().getSimpleName().equals("NativeLibraryResourceLoader")) {
                handleResourceLoader(resourceLoader, moduleLoader, classpathOrderOut, ScanConfig, log);
                //}
            }
        }
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
                "org.jboss.modules.ModuleClassLoader");
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
        final Object module = classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getModule");
        final Object callerModuleLoader = classpathOrder.reflectionUtils.invokeMethod(false, module,
                "getCallerModuleLoader");
        final Set<Object> visitedModules = new HashSet<>();
        @SuppressWarnings("unchecked") final Map<Object, Object> moduleMap = (Map<Object, Object>) classpathOrder.reflectionUtils
                .getFieldVal(false, callerModuleLoader, "moduleMap");
        final Set<Entry<Object, Object>> moduleMapEntries = moduleMap != null ? moduleMap.entrySet()
                : Collections.<Entry<Object, Object>>emptySet();
        for (final Entry<Object, Object> ent : moduleMapEntries) {
            // 类型 FutureModule
            final Object val = ent.getValue();
            // 类型 Module
            final Object realModule = classpathOrder.reflectionUtils.invokeMethod(false, val, "getModule");
            handleRealModule(realModule, visitedModules, classLoader, classpathOrder, ScanConfig, log);
        }
        // 类型 Map<String, List<LocalLoader>>
        @SuppressWarnings("unchecked") final Map<String, List<?>> pathsMap = (Map<String, List<?>>) classpathOrder.reflectionUtils
                .invokeMethod(false, module, "getPaths");
        for (final Entry<String, List<?>> ent : pathsMap.entrySet()) {
            for (final Object /* ModuleClassLoader$1 */ localLoader : ent.getValue()) {
                // 类型 ModuleClassLoader(外部类)
                final Object moduleClassLoader = classpathOrder.reflectionUtils.getFieldVal(false, localLoader,
                        "this$0");
                // 类型 Module
                final Object realModule = classpathOrder.reflectionUtils.getFieldVal(false, moduleClassLoader,
                        "module");
                handleRealModule(realModule, visitedModules, classLoader, classpathOrder, ScanConfig, log);
            }
        }
    }
}
