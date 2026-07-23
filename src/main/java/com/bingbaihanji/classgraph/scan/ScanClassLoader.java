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
package com.bingbaihanji.classgraph.scan;

import com.bingbaihanji.classgraph.classpath.ModuleClasspath;
import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.resource.Resource;
import com.bingbaihanji.classgraph.resource.ResourceList;
import com.bingbaihanji.classgraph.util.JarUtils;
import com.bingbaihanji.classgraph.util.VersionFinder;
import com.bingbaihanji.classgraph.util.VersionFinder.OperatingSystem;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.*;

/** 扫描过程中 ClassGraph 所发现类的 {@link ClassLoader} */
public class ScanClassLoader extends ClassLoader {

    /** 扫描结果 */
    private final ScanResult scanResult;

    /** 是否初始化已加载的类 */
    private final boolean initializeLoadedClasses;
    /** 由类路径上的 URL 组成的 {@link URLClassLoader} */
    private final ClassLoader classpathClassLoader;
    /** 尝试委托到的环境类加载器的有序集合 */
    private Set<ClassLoader> environmentClassLoaderDelegationOrder;
    /** 任何覆盖类加载器 */
    private List<ClassLoader> overrideClassLoaders;
    /** 尝试委托到的覆盖或添加的类加载器的有序集合 */
    private Set<ClassLoader> addedClassLoaderDelegationOrder;

    /**
     * 构造函数
     *
     * @param scanResult
     *            扫描结果
     */
    ScanClassLoader(final ScanResult scanResult) {
        super(null);
        registerAsParallelCapable();

        this.scanResult = scanResult;
        final ScanConfig ScanConfig = scanResult.ScanConfig;
        initializeLoadedClasses = ScanConfig.initializeLoadedClasses;

        final boolean classpathOverridden = ScanConfig.overrideClasspath != null
                && !ScanConfig.overrideClasspath.isEmpty();
        final boolean classloadersOverridden = ScanConfig.overrideClassLoaders != null
                && !ScanConfig.overrideClassLoaders.isEmpty();
        final boolean clasloadersAdded = ScanConfig.addedClassLoaders != null
                && !ScanConfig.addedClassLoaders.isEmpty();

        // 仅当类路径和/或类加载器未被覆盖时才尝试环境类加载器
        if (!classpathOverridden && !classloadersOverridden) {
            // 首先尝试 null 类加载器(这将默认使用引导类加载器)
            environmentClassLoaderDelegationOrder = new LinkedHashSet<>();
            environmentClassLoaderDelegationOrder.add(null);

            // 尝试环境类加载器
            final ClassLoader[] envClassLoaderOrder = scanResult.getClassLoaderOrderRespectingParentDelegation();
            if (envClassLoaderOrder != null) {
                // 尝试环境类加载器
                environmentClassLoaderDelegationOrder.addAll(Arrays.asList(envClassLoaderOrder));
            }
        }

        // 从类路径上的 URL 创建类加载器
        final List<URL> classpathURLs = scanResult.getClasspathURLs();
        classpathClassLoader = classpathURLs.isEmpty() ? null
                : new URLClassLoader(classpathURLs.toArray(new URL[0]));

        // 如果类加载器被覆盖，仅使用覆盖类加载器，如果找不到类则失败
        overrideClassLoaders = classloadersOverridden ? ScanConfig.overrideClassLoaders : null;

        // 如果类路径被覆盖但类加载器未被覆盖，则尝试从类路径 URL 加载类作为覆盖类加载器，
        // 如果找不到类则失败
        //
        // 注意：如果 ScanResult 已关闭，某些类路径 URL 可能无效(例如，在极少数情况下，
        // 内部 jar 必须提取到磁盘上的临时文件中)
        if (overrideClassLoaders == null && classpathOverridden && classpathClassLoader != null) {
            overrideClassLoaders = Collections.singletonList(classpathClassLoader);
        }

        // 如果添加了类加载器，尝试通过这些类加载器加载
        if (clasloadersAdded) {
            addedClassLoaderDelegationOrder = new LinkedHashSet<>();
            addedClassLoaderDelegationOrder.addAll(ScanConfig.addedClassLoaders);
            // 移除重复项
            if (environmentClassLoaderDelegationOrder != null) {
                addedClassLoaderDelegationOrder.removeAll(environmentClassLoaderDelegationOrder);
            }
        }
    }

    /* (non-Javadoc)
     * @see java.lang.ClassLoader#findClass(java.lang.String)
     */
    @Override
    protected Class<?> findClass(final String className)
            throws ClassNotFoundException, LinkageError, SecurityException {
        // 首先委托给外部嵌套的 ScanClassLoader(如果存在)(#485)
        final ScanClassLoader delegateScanClassLoader = scanResult.classpathFinder
                .getDelegateScanClassLoader();
        LinkageError linkageError = null;
        if (delegateScanClassLoader != null) {
            try {
                return Class.forName(className, initializeLoadedClasses, delegateScanClassLoader);
            } catch (final ClassNotFoundException e) {
                // 忽略
            } catch (final LinkageError e) {
                linkageError = e;
            }
        }

        // 如果设置了 overrideClassLoaders，仅使用覆盖加载器
        if (overrideClassLoaders != null) {
            for (final ClassLoader overrideClassLoader : overrideClassLoaders) {
                try {
                    return Class.forName(className, initializeLoadedClasses, overrideClassLoader);
                } catch (final ClassNotFoundException e) {
                    // 忽略
                } catch (final LinkageError e) {
                    if (linkageError == null) {
                        linkageError = e;
                    }
                }
            }
        }

        // 首先尝试环境类加载器，因为这是通常的默认行为
        if (overrideClassLoaders == null && environmentClassLoaderDelegationOrder != null
                && !environmentClassLoaderDelegationOrder.isEmpty()) {
            for (final ClassLoader envClassLoader : environmentClassLoaderDelegationOrder) {
                try {
                    return Class.forName(className, initializeLoadedClasses, envClassLoader);
                } catch (final ClassNotFoundException e) {
                    // 忽略
                } catch (final LinkageError e) {
                    if (linkageError == null) {
                        linkageError = e;
                    }
                }
            }
        }

        // 尝试获取指定类的 ClassInfo，然后从 ClassInfo 获取 ClassLoader
        // 即使 ScanResult 已关闭，这应该仍然有效，因为 ScanResult#close() 会保留
        // classNameToClassInfo 映射不变但即便如此，仅在所有上述尝试均失败后才尝试此方法，
        // 以避免在 ScanResult 关闭后访问 ClassInfo 对象(#399)
        ClassLoader classInfoClassLoader = null;
        final ClassInfo classInfo = scanResult.classNameToClassInfo == null ? null
                : scanResult.classNameToClassInfo.get(className);
        if (classInfo != null) {
            classInfoClassLoader = classInfo.classLoader;
            // 尝试使用获取类文件的类路径元素对应的特定类加载器，
            // 只要它尚未被尝试过
            if (classInfoClassLoader != null && (environmentClassLoaderDelegationOrder == null
                    || !environmentClassLoaderDelegationOrder.contains(classInfoClassLoader))) {
                try {
                    return Class.forName(className, initializeLoadedClasses, classInfoClassLoader);
                } catch (final ClassNotFoundException e) {
                    // 忽略
                } catch (final LinkageError e) {
                    if (linkageError == null) {
                        linkageError = e;
                    }
                }
            }

            // 如果类来自模块，且环境类加载器无法加载它，那么它很可能是一个非公共类，
            // ClassGraph 是通过在直接读取导出包中的资源时忽略类可见性来发现它的
            // 强制 ClassGraph 遵守 JPMS 封装规则，拒绝加载上下文/系统类加载器
            // 无法加载的模块类(正常情况下上面应该抛出 SecurityException，但这里是为了完整性)
            if (classInfo.Classpath instanceof ModuleClasspath && !classInfo.isPublic()) {
                throw new ClassNotFoundException("ClassParser for class " + className + " was found in a module, "
                        + "but the context and system classloaders could not load the class, probably because "
                        + "the class is not public.");
            }
        }

        // 尝试从类路径 URL 加载
        if (overrideClassLoaders == null && classpathClassLoader != null) {
            try {
                return Class.forName(className, initializeLoadedClasses, classpathClassLoader);
            } catch (final ClassNotFoundException e) {
                // 忽略
            } catch (final LinkageError e) {
                if (linkageError == null) {
                    linkageError = e;
                }
            }
        }

        // 尝试任何已添加的类加载器
        if (addedClassLoaderDelegationOrder != null && !addedClassLoaderDelegationOrder.isEmpty()) {
            for (final ClassLoader additionalClassLoader : addedClassLoaderDelegationOrder) {
                if (additionalClassLoader != classInfoClassLoader) {
                    try {
                        return Class.forName(className, initializeLoadedClasses, additionalClassLoader);
                    } catch (final ClassNotFoundException e) {
                        // 忽略
                    } catch (final LinkageError e) {
                        if (linkageError == null) {
                            linkageError = e;
                        }
                    }
                }
            }
        }

        // 作为最后的尝试，如果上述所有努力都失败了，尝试将类文件作为资源获取，
        // 并从资源内容定义类这应在尝试环境类加载之后执行，
        // 以避免类由环境类加载器和直接手动类加载混合加载而导致类兼容性问题
        // 仅在万不得已时才访问 ScanResult(获取资源)，以便尽可能在
        // ScanResult 关闭后再加载链接的类否则，如果在 ScanResult 关闭前加载类，
        // 然后关闭 ScanResult，再尝试访问 ScanResult 中类型尚未加载的字段，
        // 可能会触发 ScanResult 在关闭后被访问的异常(#399)
        final ResourceList classfileResources = scanResult
                .resources().getResourcesWithPath(JarUtils.classNameToClassfilePath(className));
        if (classfileResources != null) {
            for (final Resource resource : classfileResources) {
                // 遍历资源(仅尝试加载列表中的第一个资源)
                // 加载资源内容，并从中定义类
                try (Resource resourceToClose = resource) {
                    // TODO: 是否需要通过反射尝试 java.lang.invoke.MethodHandles.Lookup.defineClass
                    // (它在 JDK 9 中已实现)，如果以下方法失败？
                    // 参见：https://bugs.openjdk.java.net/browse/JDK-8202999
                    return defineClass(className, resourceToClose.read(), (ProtectionDomain) null);
                } catch (final IOException e) {
                    throw new ClassNotFoundException("Could not load ClassParser for class " + className + " : " + e);
                } catch (final LinkageError e) {
                    if (linkageError == null) {
                        linkageError = e;
                    }
                }
            }
        }

        if (linkageError != null) {
            if (VersionFinder.OS == OperatingSystem.Windows) {
                // LinkageError 表示找到了类文件，但无法加载该类
                // 通过巧妙的方式检测 Windows 文件系统上存在两个大小写不敏感的同名类文件
                // 的情况(#494)
                final String msg = linkageError.getMessage();
                if (msg != null) {
                    final String wrongName = "(wrong name: ";
                    final int wrongNameIdx = msg.indexOf(wrongName);
                    if (wrongNameIdx > -1) {
                        final String theWrongName = msg.substring(wrongNameIdx + wrongName.length(),
                                msg.length() - 1);
                        if (theWrongName.replace('/', '.').equalsIgnoreCase(className)) {
                            throw new LinkageError("You appear to have two classfiles with the same "
                                    + "case-insensitive name in the same directory on a case-insensitive "
                                    + "filesystem -- this is not allowed on Windows, and therefore your "
                                    + "code is not portable. Class name: " + className, linkageError);
                        }
                    }
                }
            }
            throw linkageError;
        }

        throw new ClassNotFoundException("Could not find or load ClassParser for class " + className);
    }

    /**
     * 获取类路径 URL
     *
     * @return 由此 {@link ClassLoader} 处理的 {@link ScanResult} 中的类路径 URL
     */
    public URL[] getURLs() {
        return scanResult.getClasspathURLs().toArray(new URL[0]);
    }

    /* (non-Javadoc)
     * @see java.lang.ClassLoader#getResource(java.lang.String)
     */
    @Override
    public URL getResource(final String path) {
        // 此顺序应与 findClass(String) 中的顺序一致

        // 尝试从环境类加载器加载资源
        if (!environmentClassLoaderDelegationOrder.isEmpty()) {
            for (final ClassLoader envClassLoader : environmentClassLoaderDelegationOrder) {
                final URL resource = envClassLoader.getResource(path);
                if (resource != null) {
                    return resource;
                }
            }
        }

        // 尝试从覆盖或添加的类加载器加载资源
        if (!addedClassLoaderDelegationOrder.isEmpty()) {
            for (final ClassLoader additionalClassLoader : addedClassLoaderDelegationOrder) {
                final URL resource = additionalClassLoader.getResource(path);
                if (resource != null) {
                    return resource;
                }
            }
        }

        // 如果上述尝试均失败，则尝试从 ScanResult 获取资源
        // 如果 ScanResult 已关闭，这将抛出异常(#399)
        final ResourceList resourceList = scanResult.resources().getResourcesWithPath(path);
        if (resourceList == null || resourceList.isEmpty()) {
            return super.getResource(path);
        } else {
            return resourceList.get(0).getURL();
        }
    }

    /* (non-Javadoc)
     * @see java.lang.ClassLoader#getResources(java.lang.String)
     */
    @Override
    public Enumeration<URL> getResources(final String path) throws IOException {
        // 此顺序应与 findClass(String) 中的顺序一致

        // 尝试从环境类加载器加载资源
        if (!environmentClassLoaderDelegationOrder.isEmpty()) {
            for (final ClassLoader envClassLoader : environmentClassLoaderDelegationOrder) {
                final Enumeration<URL> resources = envClassLoader.getResources(path);
                if (resources != null && resources.hasMoreElements()) {
                    return resources;
                }
            }
        }

        // 尝试从覆盖或添加的类加载器加载资源
        if (!addedClassLoaderDelegationOrder.isEmpty()) {
            for (final ClassLoader additionalClassLoader : addedClassLoaderDelegationOrder) {
                final Enumeration<URL> resources = additionalClassLoader.getResources(path);
                if (resources != null && resources.hasMoreElements()) {
                    return resources;
                }
            }
        }

        // 如果上述尝试均失败，则尝试从 ScanResult 获取资源
        // 如果 ScanResult 已关闭，这将抛出异常(#399)
        final ResourceList resourceList = scanResult.resources().getResourcesWithPath(path);
        if (resourceList == null || resourceList.isEmpty()) {
            return Collections.emptyEnumeration();
        } else {
            return new Enumeration<URL>() {
                /** 索引 */
                int idx;

                @Override
                public boolean hasMoreElements() {
                    return idx < resourceList.size();
                }

                @Override
                public URL nextElement() {
                    return resourceList.get(idx++).getURL();
                }
            };
        }
    }

    /* (non-Javadoc)
     * @see java.lang.ClassLoader#getResourceAsStream(java.lang.String)
     */
    @Override
    public InputStream getResourceAsStream(final String path) {
        // 此顺序应与 findClass(String) 中的顺序一致

        // 尝试从环境类加载器打开资源
        if (!environmentClassLoaderDelegationOrder.isEmpty()) {
            for (final ClassLoader envClassLoader : environmentClassLoaderDelegationOrder) {
                final InputStream inputStream = envClassLoader.getResourceAsStream(path);
                if (inputStream != null) {
                    return inputStream;
                }
            }
        }

        // 尝试从覆盖或添加的类加载器打开资源
        if (!addedClassLoaderDelegationOrder.isEmpty()) {
            for (final ClassLoader additionalClassLoader : addedClassLoaderDelegationOrder) {
                final InputStream inputStream = additionalClassLoader.getResourceAsStream(path);
                if (inputStream != null) {
                    return inputStream;
                }
            }
        }

        // 如果上述尝试均失败，则尝试从 ScanResult 打开资源
        // 如果 ScanResult 已关闭，这将抛出异常(#399)
        final ResourceList resourceList = scanResult.resources().getResourcesWithPath(path);
        if (resourceList == null || resourceList.isEmpty()) {
            return super.getResourceAsStream(path);
        } else {
            try {
                return resourceList.get(0).open();
            } catch (final IOException e) {
                return null;
            }
        }
    }
}
