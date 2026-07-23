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

import com.bingbaihanji.classgraph.classpath.ModulePathInfo;
import com.bingbaihanji.classgraph.classpath.SystemJarFinder;
import com.bingbaihanji.classgraph.metadata.*;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.util.*;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.AccessibleObject;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * 超快速、超轻量级的 Java 类路径和模块路径扫描器通过直接解析 ClassParser 二进制格式(而非反射)来扫描类路径和/或模块路径中的 ClassParser
 *
 * <p>
 * 文档：<a href= "https://github.com/classgraph/classgraph/wiki">
 * https://github.com/classgraph/classgraph/wiki</a>
 */
public class ClassGraph {
    /**
     * 扫描时使用的默认工作线程数此数值在配备 SSD 的较新笔记本电脑上扫描大型类路径时取得了最佳效果
     */
    static final int DEFAULT_NUM_WORKER_THREADS = Math.max(
            // 始终至少使用 2 个线程进行扫描
            2, //
            (int) Math.ceil(
                    // I/O 线程数(上限为 4，因为大多数 I/O 设备的扩展性不会超过此值)
                    Math.min(4.0, Runtime.getRuntime().availableProcessors() * 0.75) +
                            // 扫描线程数(高于可用处理器数，因为某些线程可能会被阻塞)
                            Runtime.getRuntime().availableProcessors() * 1.25) //
    );
    /**
     * 如果在 JDK 16+ 上运行，JDK 会强制执行强封装，如果类加载器未通过公共方法或字段公开类路径，
     * 则 ClassGraph 可能无法从类加载器中读取类路径
     *
     * <p>
     * 要启用此问题的变通方案，请先以任何方式与 ClassGraph 交互之前，将此静态字段设置为
     * {@link CircumventEncapsulationMethod#NARCISSUS}，并在类路径或模块路径上包含
     * <a href="https://github.com/toolfactory/narcissus">Narcissus</a> 库
     *
     * <p>
     * Narcissus 使用 JNI 来绕过封装和字段/方法访问控制Narcissus 使用原生代码库，
     * 目前仅针对 Linux x86/x64、Windows x86/x64 和 Mac OS X x64 编译
     */
    public static CircumventEncapsulationMethod CIRCUMVENT_ENCAPSULATION = CircumventEncapsulationMethod.NONE;
    private final ReflectionUtils reflectionUtils;
    /** 扫描规范 */
    ScanConfig ScanConfig = new ScanConfig();
    /**
     * 如果非 null，则在扫描时记录日志
     */
    private LogNode topLevelLog;

    /** 构造一个 ClassGraph 实例 */
    public ClassGraph() {
        reflectionUtils = new ReflectionUtils();
        // 初始化 ScanResult，如果这是第一次调用 ClassGraph 构造函数
        ScanResult.init(reflectionUtils);
    }

    /**
     * Create a new ClassGraph scanner with default settings.
     *
     * @return a new ClassGraph instance
     */
    public static ClassGraph create() {
        return new ClassGraph();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取 ClassGraph 的版本号
     *
     * @return ClassGraph 版本号，如果无法确定则返回 "unknown"
     */
    public static String getVersion() {
        return VersionFinder.getVersion();
    }

    /**
     * 开启详细日志记录到 System.err
     *
     * @return this(用于方法链式调用)
     */
    public ClassGraph verbose() {
        if (topLevelLog == null) {
            topLevelLog = new LogNode();
        }
        return this;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 如果 verbose 为 true，则开启详细日志记录到 System.err
     *
     * @param verbose
     *            如果为 true，则启用详细日志记录
     * @return this(用于方法链式调用)
     */
    public ClassGraph verbose(final boolean verbose) {
        if (verbose) {
            verbose();
        }
        return this;
    }

    /**
     * Enable scanning of all classes, fields, methods, annotations, and static final field constant
     * initializer values, ignoring all visibility modifiers.
     *
     * <p>
     * Calls {@link #withClassInfo()}, {@link #withFieldInfo()}, {@link #withMethodInfo()},
     * {@link #withAnnotationInfo()}, {@link #withConstantFieldValues()},
     * {@link #withoutClassVisibilityFilter()}, {@link #withoutFieldVisibilityFilter()},
     * and {@link #withoutMethodVisibilityFilter()}.
     *
     * @return this (for method chaining)
     */
    public ClassGraph withAllInfo() {
        withClassInfo();
        withFieldInfo();
        withMethodInfo();
        withAnnotationInfo();
        withConstantFieldValues();
        withoutClassVisibilityFilter();
        withoutFieldVisibilityFilter();
        withoutMethodVisibilityFilter();
        return this;
    }

    /** @deprecated Use {@link #withAllInfo()} instead. */
    @Deprecated
    public ClassGraph enableAllInfo() {
        return withAllInfo();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Enable scanning of classes, producing {@link ClassInfo} objects in the {@link ScanResult}.
     * Implicitly disables {@link #enableMultiReleaseVersions()}.
     *
     * @return this (for method chaining)
     */
    public ClassGraph withClassInfo() {
        ScanConfig.enableClassInfo = true;
        ScanConfig.enableMultiReleaseVersions = false;
        return this;
    }

    /** @deprecated Use {@link #withClassInfo()} instead. */
    @Deprecated
    public ClassGraph enableClassInfo() {
        return withClassInfo();
    }

    /**
     * Ignore class visibility, allowing scanning of private, package-private, and protected classes.
     * By default, only public classes are scanned (automatically calls {@link #withClassInfo()}).
     *
     * @return this (for method chaining)
     */
    public ClassGraph withoutClassVisibilityFilter() {
        withClassInfo();
        ScanConfig.ignoreClassVisibility = true;
        return this;
    }

    /** @deprecated Use {@link #withoutClassVisibilityFilter()} instead. */
    @Deprecated
    public ClassGraph ignoreClassVisibility() {
        return withoutClassVisibilityFilter();
    }

    /**
     * Enable saving of method info during scanning. This info can be obtained via
     * {@link ClassInfo#getMethodInfo()} etc. By default, method info is not scanned
     * (automatically calls {@link #withClassInfo()}).
     *
     * @return this (for method chaining)
     */
    public ClassGraph withMethodInfo() {
        withClassInfo();
        ScanConfig.enableMethodInfo = true;
        return this;
    }

    /** @deprecated Use {@link #withMethodInfo()} instead. */
    @Deprecated
    public ClassGraph enableMethodInfo() {
        return withMethodInfo();
    }

    /**
     * Ignore method visibility, allowing scanning of private, package-private, and protected methods.
     * By default, only public methods are scanned (automatically calls {@link #withClassInfo()} and
     * {@link #withMethodInfo()}).
     *
     * @return this (for method chaining)
     */
    public ClassGraph withoutMethodVisibilityFilter() {
        withClassInfo();
        withMethodInfo();
        ScanConfig.ignoreMethodVisibility = true;
        return this;
    }

    /** @deprecated Use {@link #withoutMethodVisibilityFilter()} instead. */
    @Deprecated
    public ClassGraph ignoreMethodVisibility() {
        return withoutMethodVisibilityFilter();
    }

    /**
     * Enable saving of field info during scanning. This info can be obtained via
     * {@link ClassInfo#getFieldInfo()}. By default, field info is not scanned
     * (automatically calls {@link #withClassInfo()}).
     *
     * @return this (for method chaining)
     */
    public ClassGraph withFieldInfo() {
        withClassInfo();
        ScanConfig.enableFieldInfo = true;
        return this;
    }

    /** @deprecated Use {@link #withFieldInfo()} instead. */
    @Deprecated
    public ClassGraph enableFieldInfo() {
        return withFieldInfo();
    }

    /**
     * Ignore field visibility, allowing scanning of private, package-private, and protected fields.
     * By default, only public fields are scanned (automatically calls {@link #withClassInfo()} and
     * {@link #withFieldInfo()}).
     *
     * @return this (for method chaining)
     */
    public ClassGraph withoutFieldVisibilityFilter() {
        withClassInfo();
        withFieldInfo();
        ScanConfig.ignoreFieldVisibility = true;
        return this;
    }

    /** @deprecated Use {@link #withoutFieldVisibilityFilter()} instead. */
    @Deprecated
    public ClassGraph ignoreFieldVisibility() {
        return withoutFieldVisibilityFilter();
    }

    /**
     * Enable saving of static final field constant initializer values during scanning.
     * By default, constant initializer values are not scanned. If enabled, constant field
     * initializer values can be obtained via {@link FieldInfo#getConstantInitializerValue()}.
     *
     * <p>
     * Automatically calls {@link #withClassInfo()} and {@link #withFieldInfo()}.
     *
     * @return this (for method chaining)
     */
    public ClassGraph withConstantFieldValues() {
        withClassInfo();
        withFieldInfo();
        ScanConfig.enableStaticFinalFieldConstantInitializerValues = true;
        return this;
    }

    /** @deprecated Use {@link #withConstantFieldValues()} instead. */
    @Deprecated
    public ClassGraph enableStaticFinalFieldConstantInitializerValues() {
        return withConstantFieldValues();
    }

    /**
     * Enable saving of annotation info during scanning (including class, field, method, and
     * method parameter annotations). This info can be obtained via
     * {@link ClassInfo#getAnnotationInfo()}, {@link FieldInfo#getAnnotationInfo()},
     * and {@link MethodParam#getAnnotationInfo()}. By default, annotation info is not scanned
     * (automatically calls {@link #withClassInfo()}).
     *
     * @return this (for method chaining)
     */
    public ClassGraph withAnnotationInfo() {
        withClassInfo();
        ScanConfig.enableAnnotationInfo = true;
        return this;
    }

    /** @deprecated Use {@link #withAnnotationInfo()} instead. */
    @Deprecated
    public ClassGraph enableAnnotationInfo() {
        return withAnnotationInfo();
    }

    /**
     * Enable determination of inter-class dependencies, which can be read by calling
     * {@link ClassInfo#getClassDependencies()}, getClassDependencyMap(),
     * or getReverseClassDependencyMap()
     * (automatically calls {@link #withClassInfo()}, {@link #withFieldInfo()},
     * {@link #withMethodInfo()}, {@link #withAnnotationInfo()},
     * {@link #withoutClassVisibilityFilter()}, {@link #withoutFieldVisibilityFilter()},
     * and {@link #withoutMethodVisibilityFilter()}).
     *
     * @return this (for method chaining)
     */
    public ClassGraph withInterClassDependencies() {
        withClassInfo();
        withFieldInfo();
        withMethodInfo();
        withAnnotationInfo();
        withoutClassVisibilityFilter();
        withoutFieldVisibilityFilter();
        withoutMethodVisibilityFilter();
        ScanConfig.enableInterClassDependencies = true;
        return this;
    }

    /** @deprecated Use {@link #withInterClassDependencies()} instead. */
    @Deprecated
    public ClassGraph enableInterClassDependencies() {
        return withInterClassDependencies();
    }

    /**
     * Only scan runtime-visible annotations (ignore runtime-invisible annotations).
     * (Automatically calls {@link #withClassInfo()}.)
     *
     * @return this (for method chaining)
     */
    public ClassGraph withoutRuntimeInvisibleAnnotations() {
        withClassInfo();
        ScanConfig.disableRuntimeInvisibleAnnotations = true;
        return this;
    }

    /** @deprecated Use {@link #withoutRuntimeInvisibleAnnotations()} instead. */
    @Deprecated
    public ClassGraph disableRuntimeInvisibleAnnotations() {
        return withoutRuntimeInvisibleAnnotations();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Disable scanning of jar files.
     *
     * @return this (for method chaining)
     */
    public ClassGraph withoutJarScanning() {
        ScanConfig.scanJars = false;
        return this;
    }

    /** @deprecated Use {@link #withoutJarScanning()} instead. */
    @Deprecated
    public ClassGraph disableJarScanning() {
        return withoutJarScanning();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Disable scanning of nested jar files (jar files within jar files).
     *
     * @return this (for method chaining)
     */
    public ClassGraph withoutNestedJarScanning() {
        ScanConfig.scanNestedJars = false;
        return this;
    }

    /** @deprecated Use {@link #withoutNestedJarScanning()} instead. */
    @Deprecated
    public ClassGraph disableNestedJarScanning() {
        return withoutNestedJarScanning();
    }

    /**
     * Disable scanning of directories.
     *
     * @return this (for method chaining)
     */
    public ClassGraph withoutDirScanning() {
        ScanConfig.scanDirs = false;
        return this;
    }

    /** @deprecated Use {@link #withoutDirScanning()} instead. */
    @Deprecated
    public ClassGraph disableDirScanning() {
        return withoutDirScanning();
    }

    /**
     * Disable scanning of modules.
     *
     * @return this (for method chaining)
     */
    public ClassGraph withoutModuleScanning() {
        ScanConfig.scanModules = false;
        return this;
    }

    /** @deprecated Use {@link #withoutModuleScanning()} instead. */
    @Deprecated
    public ClassGraph disableModuleScanning() {
        return withoutModuleScanning();
    }

    /**
     * Cause ClassGraph to return classes that are not in accepted packages but are directly
     * referenced by classes in accepted packages as superclasses, implemented interfaces, or
     * annotations. (Automatically calls {@link #withClassInfo()}.)
     *
     * @return this (for method chaining)
     */
    public ClassGraph withExternalClasses() {
        withClassInfo();
        ScanConfig.enableExternalClasses = true;
        return this;
    }

    /** @deprecated Use {@link #withExternalClasses()} instead. */
    @Deprecated
    public ClassGraph enableExternalClasses() {
        return withExternalClasses();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Cause classes loaded via {@link ClassInfo#loadClass()} to be initialized after class loading.
     * (The default is not to initialize classes.)
     *
     * @return this (for method chaining)
     */
    public ClassGraph withLoadedClassInitialization() {
        ScanConfig.initializeLoadedClasses = true;
        return this;
    }

    /** @deprecated Use {@link #withLoadedClassInitialization()} instead. */
    @Deprecated
    public ClassGraph initializeLoadedClasses() {
        return withLoadedClassInitialization();
    }

    /**
     * Remove temporary files from the temporary directory immediately after scanning completes,
     * including nested jar files (jar files within jar files that must be extracted during
     * scanning in order to be read). By default, temporary files are removed by the
     * {@link ScanResult} finalizer or on JVM exit.
     *
     * @return this (for method chaining)
     */
    public ClassGraph withTempFileCleanup() {
        ScanConfig.removeTemporaryFilesAfterScan = true;
        return this;
    }

    /** @deprecated Use {@link #withTempFileCleanup()} instead. */
    @Deprecated
    public ClassGraph removeTemporaryFilesAfterScan() {
        return withTempFileCleanup();
    }

    /**
     * 使用自定义路径覆盖自动检测的类路径，路径元素由 File.pathSeparatorChar 分隔
     * 使系统 ClassLoader 和 java.class.path 系统属性被忽略同时使模块不被扫描
     *
     * <p>
     * 如果调用了此方法，则仅扫描提供的类路径，即会导致 ClassLoader 以及 java.class.path
     * 系统属性被忽略
     *
     * @param overrideClasspath
     *            用于扫描的自定义类路径，路径元素由 File.pathSeparatorChar 分隔
     * @return this(用于方法链式调用)
     */
    public ClassGraph overrideClasspath(final String overrideClasspath) {
        if (overrideClasspath.isEmpty()) {
            throw new IllegalArgumentException("Can't override classpath with an empty path");
        }
        for (final String Classpath : JarUtils.smartPathSplit(overrideClasspath, ScanConfig)) {
            ScanConfig.addClasspathOverride(Classpath);
        }
        return this;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 使用自定义路径覆盖自动检测的类路径使系统 ClassLoader 和 java.class.path 系统属性被忽略
     * 同时使模块不被扫描
     *
     * <p>
     * 适用于任何类型的 Iterable，其 toString() 方法解析为类路径元素字符串，例如 String、File 或 Path
     *
     * @param overrideClasspaths
     *            用于扫描的自定义类路径，路径元素由 File.pathSeparatorChar 分隔
     * @return this(用于方法链式调用)
     */
    public ClassGraph overrideClasspath(final Iterable<?> overrideClasspaths) {
        if (!overrideClasspaths.iterator().hasNext()) {
            throw new IllegalArgumentException("Can't override classpath with an empty path");
        }
        for (final Object Classpath : overrideClasspaths) {
            ScanConfig.addClasspathOverride(Classpath);
        }
        return this;
    }

    /**
     * 使用自定义路径覆盖自动检测的类路径使系统 ClassLoader 和 java.class.path 系统属性被忽略
     * 同时使模块不被扫描
     *
     * <p>
     * 适用于任何成员类型的数组，其 toString() 方法解析为类路径元素字符串，例如 String、File 或 Path
     *
     * @param overrideClasspaths
     *            用于扫描的自定义类路径，路径元素由 File.pathSeparatorChar 分隔
     * @return this(用于方法链式调用)
     */
    public ClassGraph overrideClasspath(final Object... overrideClasspaths) {
        if (overrideClasspaths.length == 0) {
            throw new IllegalArgumentException("Can't override classpath with an empty path");
        }
        for (final Object Classpath : overrideClasspaths) {
            ScanConfig.addClasspathOverride(Classpath);
        }
        return this;
    }

    /**
     * 添加类路径元素过滤器提供的 ClasspathFilter 在传入的路径字符串是您想要扫描的路径时应返回 true
     *
     * @param classpathElementFilter
     *            要使用的过滤器函数如果应扫描该类路径元素路径，此函数应返回 true；否则返回 false
     * @return this(用于方法链式调用)
     */
    public ClassGraph filterClasspaths(final ClasspathFilter classpathElementFilter) {
        ScanConfig.filterClasspaths(classpathElementFilter);
        return this;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 添加类路径元素过滤器提供的 ClasspathFilter 在传入的 {@link URL} 是您想要扫描的 URL 时应返回 true
     *
     * @param classpathElementURLFilter
     *            要使用的过滤器函数如果应扫描该类路径元素 {@link URL}，此函数应返回 true；否则返回 false
     * @return this(用于方法链式调用)
     */
    public ClassGraph filterClasspathsByURL(final ClasspathURLFilter classpathElementURLFilter) {
        ScanConfig.filterClasspaths(classpathElementURLFilter);
        return this;
    }

    /**
     * 向要扫描的 ClassLoader 列表中添加一个 ClassLoader
     *
     * <p>
     * 如果同时调用了 {@link #overrideClasspath(String)}，或者在此方法之前调用了
     * {@link #overrideClassLoaders(ClassLoader...)}，则此调用将被忽略
     *
     * @param classLoader
     *            要扫描的额外 ClassLoader
     * @return this(用于方法链式调用)
     */
    public ClassGraph addClassLoader(final ClassLoader classLoader) {
        ScanConfig.addClassLoader(classLoader);
        return this;
    }

    /**
     * 完全覆盖(并忽略)系统 ClassLoader 和 java.class.path 系统属性同时使模块不被扫描
     * 请注意，您可能希望将此方法与 {@link #withoutParentClassLoaders()} 一起使用，
     * 以便仅从您在 `overrideClassLoaders` 参数中指定的类加载器(而非其父类加载器)中提取类路径 URL
     *
     * <p>
     * 如果调用了 {@link #overrideClasspath(String)}，则此调用将被忽略
     *
     * @param overrideClassLoaders
     *            用于替代自动检测的 ClassLoader 进行扫描的 ClassLoader
     * @return this(用于方法链式调用)
     */
    public ClassGraph overrideClassLoaders(final ClassLoader... overrideClassLoaders) {
        ScanConfig.overrideClassLoaders(overrideClassLoaders);
        return this;
    }

    /**
     * Ignore parent class loaders (i.e., only get scan paths from class loaders that are not
     * the parent of another class loader).
     *
     * @return this (for method chaining)
     */
    public ClassGraph withoutParentClassLoaders() {
        ScanConfig.ignoreParentClassLoaders = true;
        return this;
    }

    /** @deprecated Use {@link #withoutParentClassLoaders()} instead. */
    @Deprecated
    public ClassGraph ignoreParentClassLoaders() {
        return withoutParentClassLoaders();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 向要扫描的 ModuleLayer 列表中添加一个 ModuleLayer如果您定义了自己的 ModuleLayer，
     * 但扫描代码并未在该自定义 ModuleLayer 中运行，请使用此方法
     *
     * <p>
     * 如果在此方法之前调用了 {@link #overrideModuleLayers(Object...)}，则此调用将被忽略
     *
     * @param moduleLayer
     *            要扫描的额外 ModuleLayer(参数类型为 {@link Object} 是为了与 JDK 7 和 JDK 8
     *            保持向后兼容，但实际参数应为 ModuleLayer 类型)
     * @return this(用于方法链式调用)
     */
    public ClassGraph addModuleLayer(final Object moduleLayer) {
        ScanConfig.addModuleLayer(moduleLayer);
        return this;
    }

    /**
     * 完全覆盖(并忽略)可见的 ModuleLayer，转而扫描请求的 ModuleLayer
     *
     * <p>
     * 如果调用了 overrideClasspath()，则此调用将被忽略
     *
     * @param overrideModuleLayers
     *            用于替代自动检测的 ModuleLayer 进行扫描的 ModuleLayer
     *            (参数类型为 {@link Object}[] 是为了与 JDK 7 和 JDK 8 保持向后兼容，
     *            但实际参数应为 ModuleLayer[] 类型)
     * @return this(用于方法链式调用)
     */
    public ClassGraph overrideModuleLayers(final Object... overrideModuleLayers) {
        ScanConfig.overrideModuleLayers(overrideModuleLayers);
        return this;
    }

    /**
     * 忽略父模块层(即仅扫描非其他模块层的父模块层的模块层)
     *
     * @return this(用于方法链式调用)
     */
    public ClassGraph ignoreParentModuleLayers() {
        ScanConfig.ignoreParentModuleLayers = true;
        return this;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Scan one or more specific packages and their sub-packages.
     *
     * <p>
     * Note: Automatically calls {@link #withClassInfo()} -- if you only need to scan resources,
     * use {@link #filterPaths(String...)} instead.
     *
     * @param packageNames
     *            The fully-qualified names of packages to scan (using '.' as separator).
     *            Can include glob wildcards ({@code '*'}).
     * @return this (for method chaining)
     */
    public ClassGraph filterPackages(final String... packageNames) {
        withClassInfo();
        for (final String packageName : packageNames) {
            final String packageNameNormalized = Filter.normalizePackageOrClassName(packageName);
            ScanConfig.packageAcceptReject.addToAccept(packageNameNormalized);
            final String path = Filter.packageNameToPath(packageNameNormalized);
            ScanConfig.pathAcceptReject.addToAccept(path + "/");
            if (packageNameNormalized.isEmpty()) {
                ScanConfig.pathAcceptReject.addToAccept("");
            }
            if (!packageNameNormalized.contains("*")) {
                if (packageNameNormalized.isEmpty()) {
                    ScanConfig.packagePrefixAcceptReject.addToAccept("");
                    ScanConfig.pathPrefixAcceptReject.addToAccept("");
                } else {
                    ScanConfig.packagePrefixAcceptReject.addToAccept(packageNameNormalized + ".");
                    ScanConfig.pathPrefixAcceptReject.addToAccept(path + "/");
                }
            }
        }
        return this;
    }

    /** @deprecated Use {@link #filterPackages(String...)} instead. */
    @Deprecated
    public ClassGraph acceptPackages(final String... packageNames) {
        return filterPackages(packageNames);
    }

    /**
     * Please use {@link #filterPackages(String...)} instead.
     *
     * @deprecated Please use {@link #filterPackages(String...)} instead.
     */
    @Deprecated
    public ClassGraph whitelistPackages(final String... packageNames) {
        return filterPackages(packageNames);
    }

    /**
     * Scan one or more specific paths and their subdirectories or nested paths.
     *
     * @param paths
     *            The paths to scan, relative to the package root of the classpath element
     *            (using '/' as separator). Can include glob wildcards ({@code '*'}).
     * @return this (for method chaining)
     */
    public ClassGraph filterPaths(final String... paths) {
        for (final String path : paths) {
            final String pathNormalized = Filter.normalizePath(path);
            final String packageName = Filter.pathToPackageName(pathNormalized);
            ScanConfig.packageAcceptReject.addToAccept(packageName);
            ScanConfig.pathAcceptReject.addToAccept(pathNormalized + "/");
            if (pathNormalized.isEmpty()) {
                ScanConfig.pathAcceptReject.addToAccept("");
            }
            if (!pathNormalized.contains("*")) {
                if (pathNormalized.isEmpty()) {
                    ScanConfig.packagePrefixAcceptReject.addToAccept("");
                    ScanConfig.pathPrefixAcceptReject.addToAccept("");
                } else {
                    ScanConfig.packagePrefixAcceptReject.addToAccept(packageName + ".");
                    ScanConfig.pathPrefixAcceptReject.addToAccept(pathNormalized + "/");
                }
            }
        }
        return this;
    }

    /** @deprecated Use {@link #filterPaths(String...)} instead. */
    @Deprecated
    public ClassGraph acceptPaths(final String... paths) {
        return filterPaths(paths);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Please use {@link #filterPaths(String...)} instead.
     *
     * @deprecated Please use {@link #filterPaths(String...)} instead.
     */
    @Deprecated
    public ClassGraph whitelistPaths(final String... paths) {
        return filterPaths(paths);
    }

    /**
     * 扫描一个或多个特定包，但不会递归扫描子包，除非子包自身也被接受
     *
     * <p>
     * 注意：自动调用 {@link #withClassInfo()}——如果您只需要扫描资源，
     * 请改用 {@link #filterPathsNonRecursive(String...)}
     *
     * <p>
     * 这对于扫描包根("")而不递归扫描 jar、目录或模块中的所有内容特别有用
     *
     * @param packageNames
     *            要扫描的包的全限定名称(使用 '.' 作为分隔符)不能包含 glob 通配符({@code '*'})
     *
     * @return this(用于方法链式调用)
     */
    public ClassGraph acceptPackagesNonRecursive(final String... packageNames) {
        withClassInfo();
        for (final String packageName : packageNames) {
            final String packageNameNormalized = Filter.normalizePackageOrClassName(packageName);
            if (packageNameNormalized.contains("*")) {
                throw new IllegalArgumentException("Cannot use a glob wildcard here: " + packageNameNormalized);
            }
            // 接受包，但不接受子包
            ScanConfig.packageAcceptReject.addToAccept(packageNameNormalized);
            ScanConfig.pathAcceptReject.addToAccept(Filter.packageNameToPath(packageNameNormalized) + "/");
            if (packageNameNormalized.isEmpty()) {
                ScanConfig.pathAcceptReject.addToAccept("");
            }
        }
        return this;
    }

    /**
     * 请改用 {@link #filterPackagesNonRecursive(String...)}
     *
     * @param packageNames
     *            要扫描的包的全限定名称(使用 '.' 作为分隔符)不能包含 glob 通配符({@code '*'})
     * @return this(用于方法链式调用)
     * @deprecated 请改用 {@link #filterPackagesNonRecursive(String...)}
     */
    @Deprecated
    public ClassGraph whitelistPackagesNonRecursive(final String... packageNames) {
        return acceptPackagesNonRecursive(packageNames);
    }

    /**
     * 扫描一个或多个特定路径，但不会递归扫描子目录或嵌套路径，除非它们自身也被接受
     *
     * <p>
     * 这对于扫描包根("")而不递归扫描 jar、目录或模块中的所有内容特别有用
     *
     * @param paths
     *            要扫描的路径，相对于类路径元素的包根(使用 '/' 作为分隔符)不能包含 glob 通配符({@code '*'})
     * @return this(用于方法链式调用)
     */
    public ClassGraph acceptPathsNonRecursive(final String... paths) {
        for (final String path : paths) {
            if (path.contains("*")) {
                throw new IllegalArgumentException("Cannot use a glob wildcard here: " + path);
            }
            final String pathNormalized = Filter.normalizePath(path);
            // 接受路径，但不接受子目录/嵌套路径
            ScanConfig.packageAcceptReject.addToAccept(Filter.pathToPackageName(pathNormalized));
            ScanConfig.pathAcceptReject.addToAccept(pathNormalized + "/");
            if (pathNormalized.isEmpty()) {
                ScanConfig.pathAcceptReject.addToAccept("");
            }
        }
        return this;
    }

    /**
     * 请改用 {@link #filterPathsNonRecursive(String...)}
     *
     * @param paths
     *            要扫描的路径，相对于类路径元素的包根(使用 '/' 作为分隔符)不能包含 glob 通配符({@code '*'})
     * @return this(用于方法链式调用)
     * @deprecated 请改用 {@link #filterPathsNonRecursive(String...)}
     */
    @Deprecated
    public ClassGraph whitelistPathsNonRecursive(final String... paths) {
        return acceptPathsNonRecursive(paths);
    }

    /**
     * Prevent scanning of one or more specific packages and their sub-packages.
     *
     * <p>
     * Note: Automatically calls {@link #withClassInfo()} -- if you only need to scan resources,
     * use {@link #excludePaths(String...)} instead.
     *
     * @param packageNames
     *            The fully-qualified names of packages to reject (using '.' as separator).
     *            Can include glob wildcards ({@code '*'}).
     * @return this (for method chaining)
     */
    public ClassGraph excludePackages(final String... packageNames) {
        withClassInfo();
        for (final String packageName : packageNames) {
            final String packageNameNormalized = Filter.normalizePackageOrClassName(packageName);
            if (packageNameNormalized.isEmpty()) {
                throw new IllegalArgumentException(
                        "Rejecting the root package (\"\") will cause nothing to be scanned");
            }
            ScanConfig.packageAcceptReject.addToReject(packageNameNormalized);
            final String path = Filter.packageNameToPath(packageNameNormalized);
            ScanConfig.pathAcceptReject.addToReject(path + "/");
            if (!packageNameNormalized.contains("*")) {
                ScanConfig.packagePrefixAcceptReject.addToReject(packageNameNormalized + ".");
                ScanConfig.pathPrefixAcceptReject.addToReject(path + "/");
            }
        }
        return this;
    }

    /** @deprecated Use {@link #excludePackages(String...)} instead. */
    @Deprecated
    public ClassGraph rejectPackages(final String... packageNames) {
        return excludePackages(packageNames);
    }

    /**
     * Please use {@link #excludePackages(String...)} instead.
     *
     * @deprecated Please use {@link #excludePackages(String...)} instead.
     */
    @Deprecated
    public ClassGraph blacklistPackages(final String... packageNames) {
        return excludePackages(packageNames);
    }

    /**
     * Prevent scanning of one or more specific paths and their subdirectories/nested paths.
     *
     * @param paths
     *            The paths to reject (using '/' as separator). Can include glob wildcards ({@code '*'}).
     * @return this (for method chaining)
     */
    public ClassGraph excludePaths(final String... paths) {
        for (final String path : paths) {
            final String pathNormalized = Filter.normalizePath(path);
            if (pathNormalized.isEmpty()) {
                throw new IllegalArgumentException(
                        "Rejecting the root package (\"\") will cause nothing to be scanned");
            }
            final String packageName = Filter.pathToPackageName(pathNormalized);
            ScanConfig.packageAcceptReject.addToReject(packageName);
            ScanConfig.pathAcceptReject.addToReject(pathNormalized + "/");
            if (!pathNormalized.contains("*")) {
                ScanConfig.packagePrefixAcceptReject.addToReject(packageName + ".");
                ScanConfig.pathPrefixAcceptReject.addToReject(pathNormalized + "/");
            }
        }
        return this;
    }

    /** @deprecated Use {@link #excludePaths(String...)} instead. */
    @Deprecated
    public ClassGraph rejectPaths(final String... paths) {
        return excludePaths(paths);
    }

    /**
     * Please use {@link #excludePaths(String...)} instead.
     *
     * @deprecated Please use {@link #excludePaths(String...)} instead.
     */
    @Deprecated
    public ClassGraph blacklistPaths(final String... paths) {
        return excludePaths(paths);
    }

    /**
     * Scan one or more specific classes; other classes in the same package are not scanned
     * unless the package itself is also accepted.
     *
     * <p>
     * Note: Automatically calls {@link #withClassInfo()}.
     *
     * @param classNames
     *            The fully-qualified names of classes to scan (using '.' as separator).
     * @return this (for method chaining)
     */
    public ClassGraph filterClasses(final String... classNames) {
        withClassInfo();
        for (final String className : classNames) {
            final String classNameNormalized = Filter.normalizePackageOrClassName(className);
            ScanConfig.classAcceptReject.addToAccept(classNameNormalized);
            ScanConfig.classfilePathAcceptReject
                    .addToAccept(Filter.classNameToClassfilePath(classNameNormalized));
            final String packageName = PackageInfo.getParentPackageName(classNameNormalized);
            ScanConfig.classPackageAcceptReject.addToAccept(packageName);
            ScanConfig.classPackagePathAcceptReject.addToAccept(Filter.packageNameToPath(packageName) + "/");
        }
        return this;
    }

    /** @deprecated Use {@link #filterClasses(String...)} instead. */
    @Deprecated
    public ClassGraph acceptClasses(final String... classNames) {
        return filterClasses(classNames);
    }

    /**
     * Please use {@link #filterClasses(String...)} instead.
     *
     * @deprecated Please use {@link #filterClasses(String...)} instead.
     */
    @Deprecated
    public ClassGraph whitelistClasses(final String... classNames) {
        return filterClasses(classNames);
    }

    /**
     * Explicitly reject one or more specific classes, preventing them from being scanned,
     * even if they are in an accepted package.
     *
     * <p>
     * Note: Automatically calls {@link #withClassInfo()}.
     *
     * @param classNames
     *            The fully-qualified names of classes to reject (using '.' as separator).
     * @return this (for method chaining)
     */
    public ClassGraph excludeClasses(final String... classNames) {
        withClassInfo();
        for (final String className : classNames) {
            final String classNameNormalized = Filter.normalizePackageOrClassName(className);
            ScanConfig.classAcceptReject.addToReject(classNameNormalized);
            ScanConfig.classfilePathAcceptReject
                    .addToReject(Filter.classNameToClassfilePath(classNameNormalized));
        }
        return this;
    }

    /** @deprecated Use {@link #excludeClasses(String...)} instead. */
    @Deprecated
    public ClassGraph rejectClasses(final String... classNames) {
        return excludeClasses(classNames);
    }

    /**
     * Please use {@link #excludeClasses(String...)} instead.
     *
     * @deprecated Please use {@link #excludeClasses(String...)} instead.
     */
    @Deprecated
    public ClassGraph blacklistClasses(final String... classNames) {
        return excludeClasses(classNames);
    }

    /**
     * Accept one or more jar files; this will cause only accepted jar files to be scanned.
     *
     * @param jarLeafNames
     *            The leaf names of jar files that should be scanned (e.g., {@code "mylib.jar"}).
     *            Can include glob wildcards ({@code "mylib-*.jar"}).
     * @return this (for method chaining)
     */
    public ClassGraph filterJars(final String... jarLeafNames) {
        for (final String jarLeafName : jarLeafNames) {
            final String leafName = JarUtils.leafName(jarLeafName);
            if (!leafName.equals(jarLeafName)) {
                throw new IllegalArgumentException("Can only accept jars by leafname: " + jarLeafName);
            }
            ScanConfig.jarAcceptReject.addToAccept(leafName);
        }
        return this;
    }

    /** @deprecated Use {@link #filterJars(String...)} instead. */
    @Deprecated
    public ClassGraph acceptJars(final String... jarLeafNames) {
        return filterJars(jarLeafNames);
    }

    /**
     * Please use {@link #filterJars(String...)} instead.
     *
     * @deprecated Please use {@link #filterJars(String...)} instead.
     */
    @Deprecated
    public ClassGraph whitelistJars(final String... jarLeafNames) {
        return filterJars(jarLeafNames);
    }

    /**
     * Reject one or more jar files, preventing them from being scanned.
     *
     * @param jarLeafNames
     *            The leaf names of jar files that should be rejected (e.g., {@code "badlib.jar"}).
     *            Can include glob wildcards ({@code "badlib-*.jar"}).
     * @return this (for method chaining)
     */
    public ClassGraph excludeJars(final String... jarLeafNames) {
        for (final String jarLeafName : jarLeafNames) {
            final String leafName = JarUtils.leafName(jarLeafName);
            if (!leafName.equals(jarLeafName)) {
                throw new IllegalArgumentException("Can only reject jars by leafname: " + jarLeafName);
            }
            ScanConfig.jarAcceptReject.addToReject(leafName);
        }
        return this;
    }

    /** @deprecated Use {@link #excludeJars(String...)} instead. */
    @Deprecated
    public ClassGraph rejectJars(final String... jarLeafNames) {
        return excludeJars(jarLeafNames);
    }

    /**
     * Please use {@link #excludeJars(String...)} instead.
     *
     * @deprecated Please use {@link #excludeJars(String...)} instead.
     */
    @Deprecated
    public ClassGraph blacklistJars(final String... jarLeafNames) {
        return excludeJars(jarLeafNames);
    }

    /**
     * 将 lib 或 ext jar 添加到接受或拒绝列表
     *
     * @param accept
     *            如果为 true，则添加到接受列表；否则添加到拒绝列表
     * @param jarLeafNames
     *            要接受的 jar 叶子名称
     */
    private void acceptOrRejectLibOrExtJars(final boolean accept, final String... jarLeafNames) {
        if (jarLeafNames.length == 0) {
            // 如果未提供 jar 叶子名称，则接受或拒绝所有 lib 或 ext jar
            for (final String libOrExtJar : SystemJarFinder.getJreLibOrExtJars()) {
                acceptOrRejectLibOrExtJars(accept, JarUtils.leafName(libOrExtJar));
            }
        } else {
            for (final String jarLeafName : jarLeafNames) {
                final String leafName = JarUtils.leafName(jarLeafName);
                if (!leafName.equals(jarLeafName)) {
                    throw new IllegalArgumentException(
                            "Can only " + (accept ? "accept" : "reject") + " jars by leafname: " + jarLeafName);
                }
                if (jarLeafName.contains("*")) {
                    // 将通配符模式与 lib 和 ext 目录中的所有 jar 进行比较
                    final Pattern pattern = Filter.globToPattern(jarLeafName, /* simpleGlob = */ true);
                    boolean found = false;
                    for (final String libOrExtJarPath : SystemJarFinder.getJreLibOrExtJars()) {
                        final String libOrExtJarLeafName = JarUtils.leafName(libOrExtJarPath);
                        if (pattern.matcher(libOrExtJarLeafName).matches()) {
                            // 检查文件名中的 "*" 以防止无限递归(不应发生)
                            if (!libOrExtJarLeafName.contains("*")) {
                                acceptOrRejectLibOrExtJars(accept, libOrExtJarLeafName);
                            }
                            found = true;
                        }
                    }
                    if (!found && topLevelLog != null) {
                        topLevelLog.log("Could not find lib or ext jar matching wildcard: " + jarLeafName);
                    }
                } else {
                    // 无通配符，如果存在则直接接受或拒绝指定的 jar
                    boolean found = false;
                    for (final String libOrExtJarPath : SystemJarFinder.getJreLibOrExtJars()) {
                        final String libOrExtJarLeafName = JarUtils.leafName(libOrExtJarPath);
                        if (jarLeafName.equals(libOrExtJarLeafName)) {
                            if (accept) {
                                ScanConfig.libOrExtJarAcceptReject.addToAccept(jarLeafName);
                            } else {
                                ScanConfig.libOrExtJarAcceptReject.addToReject(jarLeafName);
                            }
                            if (topLevelLog != null) {
                                topLevelLog.log((accept ? "Accepting" : "Rejecting") + " lib or ext jar: "
                                        + libOrExtJarPath);
                            }
                            found = true;
                            break;
                        }
                    }
                    if (!found && topLevelLog != null) {
                        topLevelLog.log("Could not find lib or ext jar: " + jarLeafName);
                    }
                }
            }
        }
    }

    /**
     * Accept one or more jar files in the JRE/JDK "lib/" or "ext/" directory.
     * (By default, these directories are not scanned unless {@link #withSystemJarsAndModules()}
     * is called, in conjunction with the JRE/JDK.)
     *
     * @param jarLeafNames
     *            The leaf names of lib/ext jar files that should be scanned (e.g., {@code "mylib.jar"}).
     *            Can include glob wildcards ({@code '*'}). Note that if this method is called with no
     *            arguments, all JRE/JDK "lib/" or "ext/" jar files will be accepted.
     * @return this (for method chaining)
     */
    public ClassGraph filterLibOrExtJars(final String... jarLeafNames) {
        acceptOrRejectLibOrExtJars(/* accept = */ true, jarLeafNames);
        return this;
    }

    /** @deprecated Use {@link #filterLibOrExtJars(String...)} instead. */
    @Deprecated
    public ClassGraph acceptLibOrExtJars(final String... jarLeafNames) {
        return filterLibOrExtJars(jarLeafNames);
    }

    /**
     * Please use {@link #filterLibOrExtJars(String...)} instead.
     *
     * @deprecated Please use {@link #filterLibOrExtJars(String...)} instead.
     */
    @Deprecated
    public ClassGraph whitelistLibOrExtJars(final String... jarLeafNames) {
        return filterLibOrExtJars(jarLeafNames);
    }

    /**
     * Reject one or more jar files in the JRE/JDK "lib/" or "ext/" directory,
     * preventing them from being scanned.
     *
     * @param jarLeafNames
     *            The leaf names of lib/ext jar files that should not be scanned
     *            (e.g., {@code "jre/lib/badlib.jar"}). Can include glob wildcards ({@code '*'}).
     *            If called with no arguments, all JRE/JDK {@code "lib/"} or {@code "ext/"}
     *            jar files will be rejected.
     * @return this (for method chaining)
     */
    public ClassGraph excludeLibOrExtJars(final String... jarLeafNames) {
        acceptOrRejectLibOrExtJars(/* accept = */ false, jarLeafNames);
        return this;
    }

    /** @deprecated Use {@link #excludeLibOrExtJars(String...)} instead. */
    @Deprecated
    public ClassGraph rejectLibOrExtJars(final String... jarLeafNames) {
        return excludeLibOrExtJars(jarLeafNames);
    }

    /**
     * Please use {@link #excludeLibOrExtJars(String...)} instead.
     *
     * @deprecated Please use {@link #excludeLibOrExtJars(String...)} instead.
     */
    @Deprecated
    public ClassGraph blacklistLibOrExtJars(final String... jarLeafNames) {
        return excludeLibOrExtJars(jarLeafNames);
    }

    /**
     * Accept one or more modules for scanning.
     *
     * @param moduleNames
     *            The names of modules that should be scanned. Can include glob wildcards ({@code '*'}).
     * @return this (for method chaining)
     */
    public ClassGraph filterModules(final String... moduleNames) {
        for (final String moduleName : moduleNames) {
            ScanConfig.moduleAcceptReject.addToAccept(Filter.normalizePackageOrClassName(moduleName));
        }
        return this;
    }

    /** @deprecated Use {@link #filterModules(String...)} instead. */
    @Deprecated
    public ClassGraph acceptModules(final String... moduleNames) {
        return filterModules(moduleNames);
    }

    /**
     * Please use {@link #filterModules(String...)} instead.
     *
     * @deprecated Please use {@link #filterModules(String...)} instead.
     */
    @Deprecated
    public ClassGraph whitelistModules(final String... moduleNames) {
        return filterModules(moduleNames);
    }

    /**
     * Reject one or more modules, preventing them from being scanned.
     *
     * @param moduleNames
     *            The names of modules that should not be scanned. Can include glob wildcards ({@code '*'}).
     * @return this (for method chaining)
     */
    public ClassGraph excludeModules(final String... moduleNames) {
        for (final String moduleName : moduleNames) {
            ScanConfig.moduleAcceptReject.addToReject(Filter.normalizePackageOrClassName(moduleName));
        }
        return this;
    }

    /** @deprecated Use {@link #excludeModules(String...)} instead. */
    @Deprecated
    public ClassGraph rejectModules(final String... moduleNames) {
        return excludeModules(moduleNames);
    }

    /**
     * Please use {@link #excludeModules(String...)} instead.
     *
     * @deprecated Please use {@link #excludeModules(String...)} instead.
     */
    @Deprecated
    public ClassGraph blacklistModules(final String... moduleNames) {
        return excludeModules(moduleNames);
    }

    /**
     * 基于资源路径接受类路径元素仅扫描包含匹配接受条件的资源路径的类路径元素
     *
     * @param resourcePaths
     *            资源路径，其中任何一个必须存在于类路径元素中才能使该类路径元素被扫描
     *            可以包含通配符 glob({@code '*'})
     * @return this(用于方法链式调用)
     */
    public ClassGraph acceptClasspathsContainingResourcePath(final String... resourcePaths) {
        for (final String resourcePath : resourcePaths) {
            final String resourcePathNormalized = Filter.normalizePath(resourcePath);
            ScanConfig.classpathElementResourcePathAcceptReject.addToAccept(resourcePathNormalized);
        }
        return this;
    }

    /**
     * 请改用 {@link #filterClasspathsContainingResourcePath(String...)}
     *
     * @param resourcePaths
     *            资源路径，其中任何一个必须存在于类路径元素中才能使该类路径元素被扫描
     *            可以包含通配符 glob({@code '*'})
     * @return this(用于方法链式调用)
     * @deprecated 请改用 {@link #filterClasspathsContainingResourcePath(String...)}
     */
    @Deprecated
    public ClassGraph whitelistClasspathsContainingResourcePath(final String... resourcePaths) {
        return acceptClasspathsContainingResourcePath(resourcePaths);
    }

    /**
     * 基于资源路径拒绝类路径元素包含匹配拒绝条件的资源路径的类路径元素将不会被扫描
     *
     * @param resourcePaths
     *            资源路径，如果其中任何一个存在于类路径元素中，则该类路径元素将不会被扫描
     *            可以包含通配符 glob({@code '*'})
     * @return this(用于方法链式调用)
     */
    public ClassGraph rejectClasspathsContainingResourcePath(final String... resourcePaths) {
        for (final String resourcePath : resourcePaths) {
            final String resourcePathNormalized = Filter.normalizePath(resourcePath);
            ScanConfig.classpathElementResourcePathAcceptReject.addToReject(resourcePathNormalized);
        }
        return this;
    }

    /**
     * 请改用 {@link #excludeClasspathsContainingResourcePath(String...)}
     *
     * @param resourcePaths
     *            资源路径，如果其中任何一个存在于类路径元素中，则该类路径元素将不会被扫描
     *            可以包含通配符 glob({@code '*'})
     * @return this(用于方法链式调用)
     * @deprecated 请改用 {@link #excludeClasspathsContainingResourcePath(String...)}
     */
    @Deprecated
    public ClassGraph blacklistClasspathsContainingResourcePath(final String... resourcePaths) {
        return rejectClasspathsContainingResourcePath(resourcePaths);
    }

    /**
     * 启用从远程("http:"/"https:")URL(或自定义方案的 URL)获取类路径元素
     * 等效于：
     *
     * <p>
     * {@code new ClassGraph().enableURLScheme("http").enableURLScheme("https");}
     *
     * <p>
     * 默认情况下禁用从 http(s) URL 扫描，因为这可能存在安全漏洞，
     * 因为随后可以使用 {@link ClassInfo#loadClass} 加载下载的 jar 中的类
     *
     * @return this(用于方法链式调用)
     */
    public ClassGraph enableRemoteJarScanning() {
        ScanConfig.enableURLScheme("http");
        ScanConfig.enableURLScheme("https");
        return this;
    }

    /**
     * 启用从具有指定 URL 方案的 {@link URL} 连接获取类路径元素
     * (也适用于任何已定义的自定义 URL 方案，只要它们超过两个字符，以免与 Windows 驱动器号冲突)
     *
     * @param scheme
     *            URL 方案字符串，例如 "resource" 用于自定义的 "resource:" URL 方案
     * @return this(用于方法链式调用)
     */
    public ClassGraph enableURLScheme(final String scheme) {
        ScanConfig.enableURLScheme(scheme);
        return this;
    }

    /**
     * Enable scanning of system packages ({@code "java.*"}, {@code "javax.*"},
     * {@code "javafx.*"}, {@code "jdk.*"}, {@code "oracle.*"}, {@code "sun.*"})
     * -- by default these packages are not scanned for speed.
     *
     * <p>
     * Note: Automatically calls {@link #withClassInfo()}.
     *
     * @return this (for method chaining)
     */
    public ClassGraph withSystemJarsAndModules() {
        withClassInfo();
        ScanConfig.enableSystemJarsAndModules = true;
        return this;
    }

    /** @deprecated Use {@link #withSystemJarsAndModules()} instead. */
    @Deprecated
    public ClassGraph enableSystemJarsAndModules() {
        return withSystemJarsAndModules();
    }

    /**
     * 外部 jar 内被压缩(deflated，即压缩而非存储)的内部(嵌套)jar 在被解压时，
     * 在解压后必须溢出到磁盘而非存储在 RAM 支持的 {@link ByteBuffer} 中以便读取内部 jar 的条目之前，
     * 该内部 jar 的最大大小(请注意，需要将嵌套 jar 解压到 RAM 或磁盘以便读取的情况很少见，
     * 因为通常将一个 jarfile 添加到另一个 jarfile 时会存储(store)内部 jar 而非压缩(deflate)它，
     * 因为压缩 jarfile 通常不会产生进一步的压缩收益如果内部 jar 是存储的而非压缩的，
     * 则其 zip 条目可以直接使用 ClassGraph 自己的 zipfile 中央目录解析器读取，
     * 该解析器可以使用文件切片直接从存储的嵌套 jar 中提取条目)
     *
     * <p>
     * 这也是从 {@code http://} 或 {@code https://} 类路径 {@link URL} 下载 jar 到 RAM 的最大大小
     * 一旦从 {@link URL} 的 {@link InputStream} 读取了这么多字节，则 RAM 内容将溢出到磁盘上的临时文件，
     * 其余内容将下载到临时文件中(这也很少见，因为通常没有 {@code http://} 或 {@code https://} 类路径条目)
     *
     * <p>
     * 默认值：64MB(即尽可能避免写入磁盘)如果上述任一罕见情况发生，
     * 设置较低的 max RAM size 值将减少 ClassGraph 的内存使用
     *
     * @param maxBufferedJarRAMSize
     *            用于压缩的内部 jar 或下载的 jar 的最大 RAM 大小这是每个 jar 的限制，而非整个类路径的限制
     * @return this(用于方法链式调用)
     */
    public ClassGraph setMaxBufferedJarRAMSize(final int maxBufferedJarRAMSize) {
        ScanConfig.maxBufferedJarRAMSize = maxBufferedJarRAMSize;
        return this;
    }

    /**
     * 如果为 true，则使用 {@link MappedByteBuffer} 而非 {@link FileChannel} API 来打开文件，
     * 这对于包含许多大型 jarfile 的大型类路径可能更快，但会占用虚拟内存空间
     * 目前在 Java 24+ 上不可用，因为 Unsafe API 已被弃用
     *
     * @return this(用于方法链式调用)
     */
    public ClassGraph enableMemoryMapping() {
        if (VersionFinder.JAVA_MAJOR_VERSION > 23) {
            // 参见 FileUtils.java
            throw new IllegalArgumentException("enableMemoryMapping() is not supported on Java 24+");
        }
        ScanConfig.enableMemoryMapping = true;
        return this;
    }

    /**
     * 如果为 true，则使用多版本路径前缀提供多版本资源的所有版本，
     * 而非仅提供运行 JVM 会选择的那一个版本隐式禁用 {@link #withClassInfo()} 及其所有依赖功能
     *
     * @return this(用于方法链式调用)
     */
    public ClassGraph enableMultiReleaseVersions() {
        ScanConfig.enableMultiReleaseVersions = true;

        ScanConfig.enableClassInfo = false;
        ScanConfig.ignoreClassVisibility = false;
        ScanConfig.enableMethodInfo = false;
        ScanConfig.ignoreMethodVisibility = false;
        ScanConfig.enableFieldInfo = false;
        ScanConfig.ignoreFieldVisibility = false;
        ScanConfig.enableStaticFinalFieldConstantInitializerValues = false;
        ScanConfig.enableAnnotationInfo = false;
        ScanConfig.enableInterClassDependencies = false;
        ScanConfig.disableRuntimeInvisibleAnnotations = false;
        ScanConfig.enableExternalClasses = false;
        ScanConfig.enableSystemJarsAndModules = false;
        return this;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 通过调用 {@link #verbose()} 启用日志记录，然后将日志记录器设置为"实时日志记录模式"，
     * 在此模式下，日志条目会立即写入 stderr，而非仅在扫描完成后才写入
     * 有助于识别扫描陷入循环、某个扫描步骤耗时远超预期等问题
     *
     * @return this(用于方法链式调用)
     */
    public ClassGraph enableRealtimeLogging() {
        verbose();
        LogNode.logInRealtime(true);
        return this;
    }

    /**
     * 异步扫描类路径，在成功时调用 {@link ScanResultProcessor} 回调，
     * 在失败时调用 {@link FailureHandler} 回调
     *
     * @param executorService
     *            用于调度工作线程任务的自定义 {@link ExecutorService}
     * @param numParallelTasks
     *            在类路径扫描最密集的 CPU 阶段将工作分解为的并行任务数
     *            理想情况下，ExecutorService 至少应具有这么多可用线程
     * @param scanResultProcessor
     *            扫描成功时运行的 {@link ScanResultProcessor} 回调
     * @param failureHandler
     *            扫描失败时运行的 {@link FailureHandler} 回调传入扫描期间抛出的任何 {@link Throwable}
     */
    public void scanAsync(final ExecutorService executorService, final int numParallelTasks,
                          final ScanResultProcessor scanResultProcessor, final FailureHandler failureHandler) {
        if (scanResultProcessor == null) {
            // 如果 scanResultProcessor 为 null，扫描完成后将不会执行任何操作，ScanResult 将直接丢失
            throw new IllegalArgumentException("scanResultProcessor cannot be null");
        }
        if (failureHandler == null) {
            // 下面丢弃了 launchAsyncScan 返回的 Future<ScanObject> 对象的结果，
            // 因此我们强制要求添加 FailureHandler，以免异常被静默吞掉
            throw new IllegalArgumentException("failureHandler cannot be null");
        }
        // 使用 execute() 而非 submit()，因为使用了 ScanResultProcessor 和 FailureHandler
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 调用扫描器，但忽略返回的 ScanResult
                    new Scanner(/* performScan = */ true, ScanConfig, executorService, numParallelTasks,
                            scanResultProcessor, failureHandler, reflectionUtils, topLevelLog).call();
                } catch (final InterruptedException | CancellationException | ExecutionException e) {
                    // 调用失败处理器
                    failureHandler.onFailure(e);
                }
            }
        });
    }

    /**
     * 异步扫描类路径以查找匹配的文件，返回一个 {@code Future<ScanResult>}
     * 您应在 try-with-resources 语句中赋值返回的 {@link ScanResult}，或在使用完毕后手动关闭它
     *
     * @param performScan
     *            如果为 true，则执行扫描如果为 false，则仅获取类路径
     * @param executorService
     *            用于调度工作线程任务的自定义 {@link ExecutorService}
     * @param numParallelTasks
     *            在类路径扫描最密集的 CPU 阶段将工作分解为的并行任务数
     *            理想情况下，ExecutorService 至少应具有这么多可用线程
     * @return 一个 {@code Future<ScanResult>}，当使用 get() 解析后，
     *         返回表示扫描结果的新 {@link ScanResult} 对象
     */
    private Future<ScanResult> scanAsync(final boolean performScan, final ExecutorService executorService,
                                         final int numParallelTasks) {
        try {
            return executorService.submit(new Scanner(performScan, ScanConfig, executorService, numParallelTasks,
                    /* scanResultProcessor = */ null, /* failureHandler = */ null, reflectionUtils, topLevelLog));
        } catch (final InterruptedException e) {
            // 在 Scanner 构造函数执行期间被中断(具体来说是被 getModuleOrder() 中断，
            // 这不太可能实际被中断——但此异常需要被捕获)
            return executorService.submit(new Callable<ScanResult>() {
                @Override
                public ScanResult call() throws Exception {
                    throw e;
                }
            });
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 异步扫描类路径以查找匹配的文件，返回一个 {@code Future<ScanResult>}
     * 您应在 try-with-resources 语句中赋值返回的 {@link ScanResult}，或在使用完毕后手动关闭它
     *
     * @param executorService
     *            用于调度工作线程任务的自定义 {@link ExecutorService}
     * @param numParallelTasks
     *            在类路径扫描最密集的 CPU 阶段将工作分解为的并行任务数
     *            理想情况下，ExecutorService 至少应具有这么多可用线程
     * @return 一个 {@code Future<ScanResult>}，当使用 get() 解析后，
     *         返回表示扫描结果的新 {@link ScanResult} 对象
     */
    public Future<ScanResult> scanAsync(final ExecutorService executorService, final int numParallelTasks) {
        return scanAsync(/* performScan = */ true, executorService, numParallelTasks);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 使用请求的 {@link ExecutorService} 和请求的并行度扫描类路径，
     * 阻塞直到扫描完成您应在 try-with-resources 语句中赋值返回的 {@link ScanResult}，
     * 或在使用完毕后手动关闭它
     *
     * @param executorService
     *            用于调度工作线程任务的自定义 {@link ExecutorService}
     *            此 {@link ExecutorService} 应按 FIFO 顺序启动任务以避免扫描期间死锁，
     *            即确保使用 {@link LinkedBlockingQueue} 作为其任务队列构造 {@link ExecutorService}
     *            (这是 {@link Executors#newFixedThreadPool(int)} 的默认设置)
     * @param numParallelTasks
     *            在类路径扫描最密集的 CPU 阶段将工作分解为的并行任务数
     *            理想情况下，ExecutorService 至少应具有这么多可用线程
     * @return 表示扫描结果的 {@link ScanResult} 对象
     * @throws ScanException
     *             如果任何工作线程抛出未捕获的异常，或扫描被中断
     */
    public ScanResult scan(final ExecutorService executorService, final int numParallelTasks) {
        try {
            // 启动扫描并等待完成

            // 返回 scanResult，然后阻塞等待结果
            final ScanResult scanResult = scanAsync(executorService, numParallelTasks).get();

            //    // 通过序列化然后反序列化 ScanResult 来测试序列化/反序列化
            //    if (ScanConfig.enableClassInfo && ScanConfig.performScan) {
            //        final String scanResultJson = scanResult.toJSON(2);
            //        final ScanResult scanResultFromJson = ScanResult.fromJSON(scanResultJson);
            //        final String scanResultJson2 = scanResult.toJSON(2);
            //        if (!scanResultJson2.equals(scanResultJson)) {
            //            throw new RuntimeException("Serialization mismatch");
            //        }
            //        scanResult = scanResultFromJson;
            //    }

            // 生成的 scanResult 不能为 null，但检查 null 以使 SpotBugs 满意
            if (scanResult == null) {
                throw new NullPointerException();
            }
            return scanResult;

        } catch (final InterruptedException | CancellationException e) {
            throw new ScanException("Scan interrupted", e);
        } catch (final ExecutionException e) {
            throw new ScanException("Uncaught exception during scan", InterruptionChecker.getCause(e));
        }
    }

    /**
     * 使用请求的线程数扫描类路径，阻塞直到扫描完成您应在 try-with-resources 语句中赋值返回的
     * {@link ScanResult}，或在使用完毕后手动关闭它
     *
     * @param numThreads
     *            要启动的工作线程数
     * @return 表示扫描结果的 {@link ScanResult} 对象
     * @throws ScanException
     *             如果任何工作线程抛出未捕获的异常，或扫描被中断
     */
    public ScanResult scan(final int numThreads) {
        try (AutoCloseableExecutorService executorService = new AutoCloseableExecutorService(numThreads)) {
            return scan(executorService, numThreads);
        }
    }

    /**
     * 扫描类路径，阻塞直到扫描完成您应在 try-with-resources 语句中赋值返回的
     * {@link ScanResult}，或在使用完毕后手动关闭它
     *
     * @return 表示扫描结果的 {@link ScanResult} 对象
     * @throws ScanException
     *             如果任何工作线程抛出未捕获的异常，或扫描被中断
     */
    public ScanResult scan() {
        return scan(DEFAULT_NUM_WORKER_THREADS);
    }

    /**
     * 获取可用于确定类路径的 {@link ScanResult}
     *
     * @param executorService
     *            执行器服务
     * @return 表示扫描结果的 {@link ScanResult} 对象(仅可用于确定类路径)
     * @throws ScanException
     *             如果任何工作线程抛出未捕获的异常，或扫描被中断
     */
    ScanResult getClasspathScanResult(final AutoCloseableExecutorService executorService) {
        try {
            final ScanResult scanResult = scanAsync(/* performScan = */ false, executorService,
                    DEFAULT_NUM_WORKER_THREADS).get();

            // 生成的 scanResult 不能为 null，但检查 null 以使 SpotBugs 满意
            if (scanResult == null) {
                throw new NullPointerException();
            }
            return scanResult;

        } catch (final InterruptedException | CancellationException e) {
            throw new ScanException("Scan interrupted", e);
        } catch (final ExecutionException e) {
            throw new ScanException("Uncaught exception during scan", InterruptionChecker.getCause(e));
        }
    }

    /**
     * 返回类路径上所有唯一 File 对象(表示目录或 zip/jarfile)的列表，
     * 按类加载器解析顺序排列不存在为文件或目录的类路径元素不会包含在返回的列表中
     *
     * @return 一个 {@code List<File>}，包含类路径上唯一的目录和 jarfile，按类路径解析顺序排列
     * @throws ScanException
     *             如果任何工作线程抛出未捕获的异常，或扫描被中断
     */
    public List<File> getClasspathFiles() {
        try (AutoCloseableExecutorService executorService = new AutoCloseableExecutorService(
                DEFAULT_NUM_WORKER_THREADS); ScanResult scanResult = getClasspathScanResult(executorService)) {
            return scanResult.getClasspathFiles();
        }
    }

    /**
     * 返回类路径上所有唯一 File 对象(表示目录或 zip/jarfile)的列表，
     * 按类加载器解析顺序排列，以类路径字符串形式返回不存在为文件或目录的类路径元素不会包含在返回的列表中
     * 请注意，返回的字符串仅包含基本文件，不包含包根或 jar 内的嵌套 jar，
     * 因为路径分隔符(':')在 Linux 和 Mac OS X 上与 URL 方案分隔符(也是 ':')冲突
     * 调用 {@link #getClasspathURIs()} 以获取类路径元素和模块的完整 URI
     *
     * @return 一个类路径字符串，包含类路径上唯一的目录和 jarfile，按类路径解析顺序排列
     * @throws ScanException
     *             如果任何工作线程抛出未捕获的异常，或扫描被中断
     */
    public String getClasspath() {
        return JarUtils.pathElementsToPathStr(getClasspathFiles());
    }

    /**
     * 返回所有唯一 {@link URI} 对象(表示目录/jar 类路径元素和模块)的有序列表
     * 表示不存在 jarfile 或目录的类路径元素不会包含在返回的列表中
     *
     * @return 唯一的类路径元素和模块，作为 {@link URI} 对象列表
     * @throws ScanException
     *             如果任何工作线程抛出未捕获的异常，或扫描被中断
     */
    public List<URI> getClasspathURIs() {
        try (AutoCloseableExecutorService executorService = new AutoCloseableExecutorService(
                DEFAULT_NUM_WORKER_THREADS); ScanResult scanResult = getClasspathScanResult(executorService)) {
            return scanResult.getClasspathURIs();
        }
    }

    /**
     * 返回所有唯一 {@link URL} 对象(表示目录/jar 类路径元素和模块)的有序列表
     * 表示不存在 jarfile 或目录的类路径元素，以及位置未知(null)或具有 {@code jrt:} 位置 URI 方案的模块，
     * 不会包含在返回的列表中
     *
     * @return 唯一的类路径元素和模块，作为 {@link URL} 对象列表
     * @throws ScanException
     *             如果任何工作线程抛出未捕获的异常，或扫描被中断
     */
    public List<URL> getClasspathURLs() {
        try (AutoCloseableExecutorService executorService = new AutoCloseableExecutorService(
                DEFAULT_NUM_WORKER_THREADS); ScanResult scanResult = getClasspathScanResult(executorService)) {
            return scanResult.getClasspathURLs();
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 返回所有可见模块的 {@link ModuleRef} 引用
     *
     * @return 所有可见模块的 {@link ModuleRef} 引用列表
     * @throws ScanException
     *             如果任何工作线程抛出未捕获的异常，或扫描被中断
     */
    public List<ModuleRef> getModules() {
        try (AutoCloseableExecutorService executorService = new AutoCloseableExecutorService(
                DEFAULT_NUM_WORKER_THREADS); ScanResult scanResult = getClasspathScanResult(executorService)) {
            return scanResult.getModules();
        }
    }

    /**
     * 获取通过 {@code --module-path}、{@code --add-modules}、
     * {@code --patch-module}、{@code --add-exports}、{@code --add-opens} 和 {@code --add-reads}
     * 在命令行上提供的模块路径信息
     *
     * <p>
     * 请注意，返回的 {@link ModulePathInfo} 对象不包括传统类路径或系统模块的类路径条目
     * 使用 {@link #getModules()} 获取所有可见模块，包括匿名模块、自动模块和系统模块
     *
     * <p>
     * 此外，{@link ModulePathInfo#addExports} 和 {@link ModulePathInfo#addOpens} 不会包含扫描期间
     * 遇到的 jarfile 清单文件中的 {@code Add-Exports} 或 {@code Add-Opens} 条目，
     * 除非您通过调用 {@link ScanResult#getModulePathInfo()} 而非在 {@link ClassGraph#scan()}
     * 之前调用 {@link ClassGraph#getModulePathInfo()} 来获取 {@link ModulePathInfo}
     *
     * @return {@link ModulePathInfo}
     */
    public ModulePathInfo getModulePathInfo() {
        ScanConfig.modulePathInfo.getRuntimeInfo(reflectionUtils);
        return ScanConfig.modulePathInfo;
    }

    /**
     * 用于尝试绕过 JDK 16+ 封装以访问类加载器私有类路径的方法
     */
    public static enum CircumventEncapsulationMethod {
        /**
         * 使用反射 API 和 {@link AccessibleObject#setAccessible(boolean)} 尝试获取对私有类路径字段或方法的访问权限，
         * 以确定类路径
         */
        NONE,

        /**
         * 使用 <a href="https://github.com/toolfactory/narcissus">Narcissus</a> 库尝试获取对私有类加载器
         * 字段或方法的访问权限，以确定类路径
         */
        NARCISSUS,
    }

    /**
     * 添加类路径元素过滤器includeClasspath 方法在传入的路径字符串是您想要扫描的路径时应返回 true
     */
    @FunctionalInterface
    public interface ClasspathFilter {
        /**
         * 是否将给定的类路径元素包含在扫描中
         *
         * @param classpathElementPathStr
         *            类路径元素的路径字符串，已标准化为路径分隔符为 '/'
         *            这通常是一个文件路径，但也可能是 URL，或者可能是嵌套 jar 的路径，
         *            其中路径按 Java 约定使用 '!' 分隔如果类路径中存在 "jar:" 和/或 "file:" 前缀，
         *            则这些前缀将已从开头剥离
         * @return 如果传入的路径字符串是您想要扫描的路径，则返回 true
         */
        boolean includeClasspath(String classpathElementPathStr);
    }

    /**
     * 添加类路径元素 URL 过滤器includeClasspath 方法在传入的 {@link URL}
     * 对应于您想要扫描的类路径元素时应返回 true
     */
    @FunctionalInterface
    public interface ClasspathURLFilter {
        /**
         * 是否将给定的类路径元素包含在扫描中
         *
         * @param classpathElementURL
         *            类路径元素的 {@link URL}
         * @return 如果您想要扫描该 {@link URL}，则返回 true
         */
        boolean includeClasspath(URL classpathElementURL);
    }

    /** 用于处理成功异步扫描结果的回调 */
    @FunctionalInterface
    public interface ScanResultProcessor {
        /**
         * 在扫描完成后处理异步扫描的结果
         *
         * @param scanResult
         *            要处理的 {@link ScanResult}
         */
        void processScanResult(ScanResult scanResult);
    }

    /** 用于处理异步扫描期间失败的回调 */
    @FunctionalInterface
    public interface FailureHandler {
        /**
         * 在异步扫描期间扫描失败时调用
         *
         * @param throwable
         *            扫描期间抛出的 {@link Throwable}
         */
        void onFailure(Throwable throwable);
    }
}
