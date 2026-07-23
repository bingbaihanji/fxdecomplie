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

import com.bingbaihanji.classgraph.metadata.*;
import com.bingbaihanji.classgraph.classpath.*;
import com.bingbaihanji.classgraph.resource.*;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.scan.Filter;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.*;

import java.io.Closeable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 扫描的结果你应该将 ScanResult 赋值在 try-with-resources 块中，
 * 或者在使用完扫描结果后手动关闭它
 */
public final class ScanResult implements Closeable {
    /** 如果为 true，则表示 ScanResult#staticInit() 已运行 */
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    /** 当前的序列化格式 */
    private static final String CURRENT_SERIALIZATION_FORMAT = "10";
    /**
     * 对未关闭的 ScanResult 对象的 WeakReference 集合使用 WeakReference 以避免阻止垃圾回收(Bug #233)
     */
    private static Set<WeakReference<ScanResult>> nonClosedWeakReferences = Collections
            .newSetFromMap(new ConcurrentHashMap<WeakReference<ScanResult>, Boolean>());
    /**
     * {@link #getResourcesWithPath(String)} 被调用的次数
     */
    private final AtomicInteger getResourcesWithPathCallCount = new AtomicInteger();
    /** 如果为 true，则表示此 ScanResult 已被关闭 */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /** 顶层日志 */
    private final LogNode topLevelLog;
    /** 此 ScanResult 的 {@link WeakReference} */
    private final WeakReference<ScanResult> weakReference;
    protected ReflectionUtils reflectionUtils;
    /** 从类名到 {@link ClassInfo} 的映射 */
    Map<String, ClassInfo> classNameToClassInfo;
    /** {@link ClasspathFinder} */
    ClasspathFinder classpathFinder;
    /** 扫描规格 */
    ScanConfig ScanConfig;
    /** 扫描期间跳过的文件数(读取或解析失败的 class 文件) */
    int skippedFileCount;
    /** 原始类路径元素的顺序 */
    private List<String> rawClasspathEltOrderStrs;
    /**
     * 类路径元素的顺序，在内层 jar 被提取到临时文件等操作之后
     */
    private List<Classpath> classpathOrder;
    /** 在被接受的包中找到的所有文件的列表 */
    private ResourceList allAcceptedResourcesCached;
    /**
     * 从路径(相对于包根)到具有匹配路径的 {@link Resource} 元素列表的映射
     */
    private Map<String, ResourceList> pathToAcceptedResourcesCached;

    // -------------------------------------------------------------------------------------------------------------
    /** 从包名到 {@link PackageInfo} 的映射 */
    private Map<String, PackageInfo> packageNameToPackageInfo;
    /** 从类名到 {@link ClassInfo} 的映射 */
    private Map<String, ModuleInfo> moduleNameToModuleInfo;
    /**
     * 扫描期间记录时间戳的文件、目录和 jar 文件资源，以及它们在扫描时的时间戳
     * 对于 jar 文件，时间戳表示 jar 中所有文件的时间戳如果此 ScanResult 对象是调用
     * ClassGraph#getUniqueClasspathsAsync() 的结果，则可能为 null
     */
    private Map<File, Long> fileToLastModified;

    // -------------------------------------------------------------------------------------------------------------
    /** 一个自定义的 ClassLoader，可以加载扫描期间找到的类 */
    private ScanClassLoader ScanClassLoader;
    /** 嵌套 jar 处理器实例 */
    private JarReader JarReader;

    /**
     * 扫描的结果确保在调用构造函数后调用 complete()
     *
     * @param ScanConfig
     *            扫描规格
     * @param classpathOrder
     *            类路径顺序
     * @param rawClasspathEltOrderStrs
     *            原始类路径元素顺序
     * @param classpathFinder
     *            {@link ClasspathFinder}
     * @param classNameToClassInfo
     *            从类名到类信息的映射
     * @param packageNameToPackageInfo
     *            从包名到包信息的映射
     * @param moduleNameToModuleInfo
     *            从模块名到模块信息的映射
     * @param fileToLastModified
     *            从文件到最后修改时间的映射
     * @param JarReader
     *            嵌套 jar 处理器
     * @param topLevelLog
     *            顶层日志
     */
    public ScanResult(final ScanConfig ScanConfig, final List<Classpath> classpathOrder,
                      final List<String> rawClasspathEltOrderStrs, final ClasspathFinder classpathFinder,
                      final Map<String, ClassInfo> classNameToClassInfo,
                      final Map<String, PackageInfo> packageNameToPackageInfo,
                      final Map<String, ModuleInfo> moduleNameToModuleInfo, final Map<File, Long> fileToLastModified,
                      final JarReader JarReader, final LogNode topLevelLog) {
        this.ScanConfig = ScanConfig;
        this.rawClasspathEltOrderStrs = rawClasspathEltOrderStrs;
        this.classpathOrder = classpathOrder;
        this.classpathFinder = classpathFinder;
        this.fileToLastModified = fileToLastModified;
        this.classNameToClassInfo = classNameToClassInfo;
        this.packageNameToPackageInfo = packageNameToPackageInfo;
        this.moduleNameToModuleInfo = moduleNameToModuleInfo;
        this.JarReader = JarReader;
        this.reflectionUtils = JarReader.reflectionUtils;
        this.topLevelLog = topLevelLog;
        this.skippedFileCount = 0;

        if (classNameToClassInfo != null) {
            indexResourcesAndClassInfo(topLevelLog);
        }

        if (classNameToClassInfo != null) {
            // 处理 @Repeatable 注解
            final Set<String> allRepeatableAnnotationNames = new HashSet<>();
            for (final ClassInfo classInfo : classNameToClassInfo.values()) {
                if (classInfo.isAnnotation() && classInfo.annotationInfo != null) {
                    final AnnotationInfo repeatableMetaAnnotation = classInfo.annotationInfo
                            .get("java.lang.annotation.Repeatable");
                    if (repeatableMetaAnnotation != null) {
                        final AnnotationParameterValueList vals = repeatableMetaAnnotation.getParameterValues();
                        if (!vals.isEmpty()) {
                            final Object val = vals.getValue("value");
                            if (val instanceof AnnotationClassRef) {
                                final AnnotationClassRef classRef = (AnnotationClassRef) val;
                                final String repeatableAnnotationName = classRef.getName();
                                if (repeatableAnnotationName != null) {
                                    allRepeatableAnnotationNames.add(repeatableAnnotationName);
                                }
                            }
                        }
                    }
                }
            }
            if (!allRepeatableAnnotationNames.isEmpty()) {
                for (final ClassInfo classInfo : classNameToClassInfo.values()) {
                    classInfo.handleRepeatableAnnotations(allRepeatableAnnotationNames);
                }
            }
        }

        // 定义一个新的 ClassLoader，可以加载扫描期间找到的类
        this.ScanClassLoader = new ScanClassLoader(this);

        // 为关机钩子提供此 ScanResult 的弱引用
        this.weakReference = new WeakReference<>(this);
        nonClosedWeakReferences.add(this.weakReference);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 关机钩子初始化代码

    /**
     * 为 ClassGraphWorkspaceAdapter 提供的简化构造函数
     * 从预构建的 classInfo 映射创建 ScanResult，无需完整的扫描基础设施
     *
     * @param classNameToClassInfo
     *            从类名到 ClassInfo 的映射
     */
    public ScanResult(final Map<String, ClassInfo> classNameToClassInfo) {
        this.ScanConfig = new ScanConfig();
        // 启用简化扫描所需的类信息功能
        this.ScanConfig.enableClassInfo = true;
        this.ScanConfig.enableFieldInfo = true;
        this.ScanConfig.enableMethodInfo = true;
        this.ScanConfig.enableAnnotationInfo = true;
        this.rawClasspathEltOrderStrs = Collections.emptyList();
        this.classpathOrder = Collections.emptyList();
        this.classpathFinder = null;
        this.classNameToClassInfo = classNameToClassInfo != null
                ? classNameToClassInfo : Collections.emptyMap();
        this.packageNameToPackageInfo = Collections.emptyMap();
        this.moduleNameToModuleInfo = Collections.emptyMap();
        this.fileToLastModified = Collections.emptyMap();
        this.JarReader = null;
        this.skippedFileCount = 0;
        this.reflectionUtils = null;
        this.topLevelLog = null;
        this.weakReference = new WeakReference<>(this);
        // 为每个 ClassInfo 设置 scanResult
        for (final ClassInfo ci : this.classNameToClassInfo.values()) {
            ci.setScanResult(this);
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 构造函数

    /**
     * 静态初始化(预热类加载)，在 ClassGraph 类初始化时调用
     */
    static void init(final ReflectionUtils reflectionUtils) {
        if (!initialized.getAndSet(true)) {
            // 预加载调用 scanResult.close() 所需的非系统类，以便需要加载来关闭资源的类
            // 已经被加载并缓存这最初是为了在关机钩子(#331)中使用，现已移除，
            // 但在初始化时确保取消映射 DirectByteBuffer 实例所需的类可用可能仍然是个好主意
            // 我们通过内存映射一个文件然后关闭它来实现这一点，
            // 因为唯一有问题的类是 FileUtils::closeDirectByteBuffer 使用的 PriviledgedAction 匿名内部类
            FileUtils.closeDirectByteBuffer(ByteBuffer.allocateDirect(32), reflectionUtils, /* log = */ null);
        }
    }

    /**
     * 关闭所有尚未关闭的 {@link ScanResult} 实例注意，这将关闭所有使用缓存了 {@link ScanResult} 类的
     * classloader 的类的所有打开的 {@link ScanResult} 实例 -- 因此如果你调用此方法，
     * 你需要确保 classloader 的生命周期与你的应用程序的生命周期相匹配，
     * 或者两个并发的应用程序不共享同一个 classloader，
     * 否则一个应用程序可能会关闭另一个应用程序仍在使用的 {@link ScanResult} 实例
     */
    public static void closeAll() {
        for (final WeakReference<ScanResult> nonClosedWeakReference : new ArrayList<>(nonClosedWeakReferences)) {
            final ScanResult scanResult = nonClosedWeakReference.get();
            if (scanResult != null) {
                scanResult.close();
            }
        }
    }

    /**
     * 索引 {@link Resource} 和 {@link ClassInfo} 对象
     *
     * @param log
     *            日志
     */
    private void indexResourcesAndClassInfo(final LogNode log) {
        // 从 Info 对象添加回引到此 ScanResult
        final Collection<ClassInfo> allClassInfo = classNameToClassInfo.values();
        for (final ClassInfo classInfo : allClassInfo) {
            classInfo.setScanResult(this);
        }

        // 如果启用了类间依赖，则为任何未被扫描的引用类创建占位 ClassInfo 对象
        if (ScanConfig.enableInterClassDependencies) {
            for (final ClassInfo ci : new ArrayList<>(classNameToClassInfo.values())) {
                final Set<ClassInfo> refdClassesFiltered = new HashSet<>();
                for (final ClassInfo refdClassInfo : ci.findReferencedClassInfo(log)) {
                    // 不添加自引用或对 Object 的引用
                    if (refdClassInfo != null && !ci.equals(refdClassInfo)
                            && !"java.lang.Object".equals(refdClassInfo.getName())
                            // 仅当类被接受或启用了外部类时才将类添加到结果中
                            && (!refdClassInfo.isExternalClass() || ScanConfig.enableExternalClasses)) {
                        refdClassInfo.setScanResult(this);
                        refdClassesFiltered.add(refdClassInfo);
                    }
                }
                ci.setReferencedClasses(new ClassInfoList(refdClassesFiltered, /* sortByName = */ true));
            }
        }

        if (ScanConfig.enableClassInfo) {
            for (final PackageInfo pkgInfo : packageNameToPackageInfo.values()) {
                pkgInfo.setScanResult(this);
            }

            for (final ModuleInfo moduleInfo : moduleNameToModuleInfo.values()) {
                moduleInfo.setScanResult(this);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 类路径 / 模块路径

    /**
     * 返回唯一类路径元素(目录或 jar 文件)的 File 对象列表，按类加载器解析顺序排列
     *
     * @return 唯一类路径元素
     */
    public List<File> getClasspathFiles() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final List<File> classpathElementOrderFiles = new ArrayList<>();
        for (final Classpath Classpath : classpathOrder) {
            final File file = Classpath.getFile();
            if (file != null) {
                classpathElementOrderFiles.add(file);
            }
        }
        return classpathElementOrderFiles;
    }

    /**
     * 返回类路径上所有唯一目录或 zip/jar 文件，按类加载器解析顺序排列，
     * 作为用标准路径分隔符分隔的类路径字符串
     *
     * @return 类路径上唯一目录和 jar 文件的路径字符串，按类路径解析顺序排列
     */
    public String getClasspath() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        return JarUtils.pathElementsToPathStr(getClasspathFiles());
    }

    /**
     * 返回唯一类路径元素和模块 URI 的有序列表
     *
     * @return 唯一类路径元素和模块 URI
     */
    public List<URI> getClasspathURIs() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final List<URI> classpathElementOrderURIs = new ArrayList<>();
        for (final Classpath Classpath : classpathOrder) {
            try {
                for (final URI uri : Classpath.getAllURIs()) {
                    if (uri != null) {
                        classpathElementOrderURIs.add(uri);
                    }
                }
            } catch (final IllegalArgumentException e) {
                // 跳过 null 位置 URI
            }
        }
        return classpathElementOrderURIs;
    }

    /**
     * 返回唯一类路径元素和模块 URL 的有序列表将跳过任何系统模块或作为 jlink 运行时镜像一部分的模块，
     * 因为 {@link URL} 不支持 {@code jrt:} {@link URI} 协议
     *
     * @return 唯一类路径元素和模块 URL
     */
    public List<URL> getClasspathURLs() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final List<URL> classpathElementOrderURLs = new ArrayList<>();
        for (final URI uri : getClasspathURIs()) {
            try {
                classpathElementOrderURLs.add(uri.toURL());
            } catch (final IllegalArgumentException | MalformedURLException e) {
                // 跳过 "jrt:" URI 和格式错误的 URL
            }
        }
        return classpathElementOrderURLs;
    }

    /**
     * 获取所有可见模块的 {@link ModuleRef} 引用
     *
     * @return 所有可见模块的 {@link ModuleRef} 引用
     */
    public List<ModuleRef> getModules() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final List<ModuleRef> moduleRefs = new ArrayList<>();
        for (final Classpath Classpath : classpathOrder) {
            if (Classpath instanceof ModuleClasspath) {
                moduleRefs.add(((ModuleClasspath) Classpath).getModuleRef());
            }
        }
        return moduleRefs;
    }

    /**
     * 获取通过命令行 {@code --module-path}、{@code --add-modules}、{@code --patch-module}、
     * {@code --add-exports}、{@code --add-opens} 和 {@code --add-reads} 提供的模块路径信息，
     * 以及扫描期间遇到的 jar 文件清单文件中的 {@code Add-Exports} 和 {@code Add-Opens} 条目
     *
     * <p>
     * 注意，返回的 {@link ModulePathInfo} 对象不包括来自传统类路径或系统模块的类路径条目
     * 使用 {@link #getModules()} 来获取所有可见模块，包括匿名模块、自动模块和系统模块
     *
     * @return {@link ModulePathInfo}
     */
    public ModulePathInfo getModulePathInfo() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        ScanConfig.modulePathInfo.getRuntimeInfo(reflectionUtils);
        return ScanConfig.modulePathInfo;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 资源

    /**
     * 获取所有资源的列表
     *
     * @return 在被接受的包中找到的所有资源(包括类文件和非类文件)的列表
     */
    public ResourceList getAllResources() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        synchronized (this) {
            if (allAcceptedResourcesCached == null) {
                // 按路径索引 Resource 对象
                final ResourceList acceptedResourcesList = new ResourceList();
                for (final Classpath classpathElt : classpathOrder) {
                    acceptedResourcesList.addAll(classpathElt.acceptedResources);
                }
                // 原子性设置以确保线程安全
                allAcceptedResourcesCached = acceptedResourcesList;
            }
            return allAcceptedResourcesCached;
        }
    }

    /**
     * 获取从资源路径到 {@link Resource} 的映射，包含在被接受的包中找到的所有资源(包括类文件和非类文件)
     *
     * @return 从资源路径到 {@link Resource} 的映射，包含在被接受的包中找到的所有资源(包括类文件和非类文件)
     */
    public Map<String, ResourceList> getAllResourcesAsMap() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        synchronized (this) {
            if (pathToAcceptedResourcesCached == null) {
                final Map<String, ResourceList> pathToAcceptedResourceListMap = new HashMap<>();
                for (final Resource res : getAllResources()) {
                    ResourceList resList = pathToAcceptedResourceListMap.get(res.getPath());
                    if (resList == null) {
                        pathToAcceptedResourceListMap.put(res.getPath(), resList = new ResourceList());
                    }
                    resList.add(res);
                }
                // 原子性设置以确保线程安全
                pathToAcceptedResourcesCached = pathToAcceptedResourceListMap;
            }
            return pathToAcceptedResourcesCached;
        }
    }

    /**
     * 获取在被接受的包中找到的具有给定路径(相对于类路径元素的包根)的所有资源的列表
     * 可能匹配多个资源，每个类路径元素最多一个
     *
     * @param resourcePath
     *            完整的资源路径，相对于类路径条目的包根
     * @return 在被接受的包中找到的具有给定路径(相对于类路径元素的包根)的所有资源的列表
     *         可能匹配多个资源，每个类路径元素最多一个
     */
    public ResourceList getResourcesWithPath(final String resourcePath) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final String path = FileUtils.sanitizeEntryPath(resourcePath, /* removeInitialSlash = */ true,
                /* removeFinalSlash = */ true);
        ResourceList matchingResources = null;
        if (getResourcesWithPathCallCount.incrementAndGet() > 3) {
            // 如果进行了多次调用，则生成并缓存一个 HashMap 以实现 O(1) 访问时间
            matchingResources = getAllResourcesAsMap().get(path);
        } else {
            // 如果只进行了少量调用，则直接搜索具有请求路径的资源
            for (final Classpath classpathElt : classpathOrder) {
                for (final Resource res : classpathElt.acceptedResources) {
                    if (res.getPath().equals(path)) {
                        if (matchingResources == null) {
                            matchingResources = new ResourceList();
                        }
                        matchingResources.add(res);
                    }
                }
            }
        }
        return matchingResources == null ? ResourceList.EMPTY_LIST : matchingResources;
    }

    /**
     * 获取在任何类路径元素中找到的具有给定路径(相对于类路径元素的包根)的所有资源的列表，
     * <i>无论是否在被接受的包中(只要资源未被拒绝)</i>
     * 可能匹配多个资源，每个类路径元素最多一个注意，这可能不会返回未被接受的资源，
     * 特别是在扫描目录类路径元素时，因为一旦给定目录下不再可能有被接受的资源，递归扫描就会终止
     * 但是，可以使用此方法找到被接受目录的祖先目录中的资源
     *
     * @param resourcePath
     *            完整的资源路径，相对于类路径条目的包根
     * @return 在任何类路径元素中找到的具有给定路径的所有资源的列表，
     *         <i>无论是否在被接受的包中(只要资源未被拒绝)</i>，
     *         相对于类路径元素的包根可能匹配多个资源，每个类路径元素最多一个
     */
    public ResourceList getResourcesWithPathIgnoringAccept(final String resourcePath) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final String path = FileUtils.sanitizeEntryPath(resourcePath, /* removeInitialSlash = */ true,
                /* removeFinalSlash = */ true);
        final ResourceList matchingResources = new ResourceList();
        for (final Classpath classpathElt : classpathOrder) {
            final Resource matchingResource = classpathElt.getResource(path);
            if (matchingResource != null) {
                matchingResources.add(matchingResource);
            }
        }
        return matchingResources;
    }

    /**
     * 请改用 {@link #getResourcesWithPathIgnoringAccept(String)}
     *
     * @param resourcePath
     *            完整的资源路径，相对于类路径条目的包根
     * @return 在任何类路径元素中找到的具有给定路径的所有资源的列表，
     *         <i>无论是否在被接受的包中(只要资源未被拒绝)</i>，
     *         相对于类路径元素的包根可能匹配多个资源，每个类路径元素最多一个
     * @deprecated 请改用 {@link #getResourcesWithPathIgnoringAccept(String)}
     */
    @Deprecated
    public ResourceList getResourcesWithPathIgnoringWhitelist(final String resourcePath) {
        return getResourcesWithPathIgnoringAccept(resourcePath);
    }

    /**
     * 获取在被接受的包中找到的具有请求的叶子名称的所有资源的列表
     *
     * @param leafName
     *            资源叶子文件名
     * @return 在被接受的包中找到的具有请求的叶子名称的所有资源的列表
     */
    public ResourceList getResourcesWithLeafName(final String leafName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final ResourceList allAcceptedResources = getAllResources();
        if (allAcceptedResources.isEmpty()) {
            return ResourceList.EMPTY_LIST;
        } else {
            final ResourceList filteredResources = new ResourceList();
            for (final Resource classpathResource : allAcceptedResources) {
                final String relativePath = classpathResource.getPath();
                final int lastSlashIdx = relativePath.lastIndexOf('/');
                if (relativePath.substring(lastSlashIdx + 1).equals(leafName)) {
                    filteredResources.add(classpathResource);
                }
            }
            return filteredResources;
        }
    }

    /**
     * 获取在被接受的包中找到的具有请求的文件扩展名的所有资源的列表
     *
     * @param extension
     *            文件扩展名，例如 "xml" 可匹配所有以 ".xml" 结尾的资源
     * @return 在被接受的包中找到的具有请求的文件扩展名的所有资源的列表
     */
    public ResourceList getResourcesWithExtension(final String extension) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final ResourceList allAcceptedResources = getAllResources();
        if (allAcceptedResources.isEmpty()) {
            return ResourceList.EMPTY_LIST;
        } else {
            String bareExtension = extension;
            while (bareExtension.startsWith(".")) {
                bareExtension = bareExtension.substring(1);
            }
            final ResourceList filteredResources = new ResourceList();
            for (final Resource classpathResource : allAcceptedResources) {
                final String relativePath = classpathResource.getPath();
                final int lastSlashIdx = relativePath.lastIndexOf('/');
                final int lastDotIdx = relativePath.lastIndexOf('.');
                if (lastDotIdx > lastSlashIdx
                        && relativePath.substring(lastDotIdx + 1).equalsIgnoreCase(bareExtension)) {
                    filteredResources.add(classpathResource);
                }
            }
            return filteredResources;
        }
    }

    /**
     * 获取在被接受的包中找到的路径与请求的正则表达式模式匹配的所有资源的列表
     * 另请参阅 {{@link #getResourcesMatchingWildcard(String)}
     *
     * @param pattern
     *            用于匹配 {@link Resource} 路径的模式
     * @return 在被接受的包中找到的路径与请求的模式匹配的所有资源的列表
     */
    public ResourceList getResourcesMatchingPattern(final Pattern pattern) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final ResourceList allAcceptedResources = getAllResources();
        if (allAcceptedResources.isEmpty()) {
            return ResourceList.EMPTY_LIST;
        } else {
            final ResourceList filteredResources = new ResourceList();
            for (final Resource classpathResource : allAcceptedResources) {
                final String relativePath = classpathResource.getPath();
                if (pattern.matcher(relativePath).matches()) {
                    filteredResources.add(classpathResource);
                }
            }
            return filteredResources;
        }
    }

    /**
     * 获取在被接受的包中找到的路径与请求的通配符字符串匹配的所有资源的列表
     *
     * <p>
     * 通配符字符串可以包含：
     * <ul>
     * <li>单个星号，匹配零个或多个非 '/' 字符</li>
     * <li>双星号，匹配零个或多个任意字符</li>
     * <li>问号，匹配一个字符</li>
     * <li>任何其他正则表达式风格的语法，例如字符集(用方括号表示)——表达式的其余部分
     * 在转义点字符后传递给 Java 正则表达式解析器</li>
     * </ul>
     *
     * <p>
     * 通配符字符串以简化的方式转换为正则表达式如果你需要更复杂的模式匹配，
     * 请直接通过 {@link #getResourcesMatchingPattern(Pattern)} 使用正则表达式
     *
     * @param wildcardString
     *            用于匹配 {@link Resource} 路径的通配符(glob)模式
     * @return 在被接受的包中找到的路径与请求的通配符字符串匹配的所有资源的列表
     */
    public ResourceList getResourcesMatchingWildcard(final String wildcardString) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        return getResourcesMatchingPattern(Filter.globToPattern(wildcardString, /* simpleGlob = */ false));
    }

    // -------------------------------------------------------------------------------------------------------------
    // 模块

    /**
     * 获取命名模块的 {@link ModuleInfo} 对象，如果扫描期间未找到请求名称的模块则返回 null
     *
     * @param moduleName
     *            模块名称
     * @return 命名模块的 {@link ModuleInfo} 对象，如果未找到该模块则返回 null
     */
    public ModuleInfo getModuleInfo(final String moduleName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return moduleNameToModuleInfo.get(moduleName);
    }

    /**
     * 获取扫描期间找到的所有模块
     *
     * @return 扫描期间找到的所有模块的列表，如果没有则返回空列表
     */
    public ModuleInfoList getModuleInfo() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return new ModuleInfoList(moduleNameToModuleInfo.values());
    }

    // -------------------------------------------------------------------------------------------------------------
    // 包

    /**
     * 获取命名包的 {@link PackageInfo} 对象，如果扫描期间未找到请求名称的包则返回 null
     *
     * @param packageName
     *            包名称
     * @return 命名包的 {@link PackageInfo} 对象，如果未找到该包则返回 null
     */
    public PackageInfo getPackageInfo(final String packageName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return packageNameToPackageInfo.get(packageName);
    }

    /**
     * 获取扫描期间找到的所有包
     *
     * @return 扫描期间找到的所有包的列表，如果没有则返回空列表
     */
    public PackageInfoList getPackageInfo() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return new PackageInfoList(packageNameToPackageInfo.values());
    }

    // -------------------------------------------------------------------------------------------------------------
    // 类依赖

    /**
     * 获取从每个被接受类的 {@link ClassInfo} 对象到该类引用的类列表的映射
     * (即返回从依赖者到依赖项的映射)注意，你需要调用
     * {@link ClassGraph#enableInterClassDependencies()} 然后再调用 {@link ClassGraph#scan()}，此方法才能工作
     * 如果你希望未被接受的类出现在结果中，你还应该在 {@link ClassGraph#scan()} 之前调用
     * {@link ClassGraph#enableExternalClasses()}
     * 另请参阅 {@link #getReverseClassDependencyMap()}，它将映射反转
     *
     * @return 从每个被接受类的 {@link ClassInfo} 对象到该类引用的类列表的映射
     *         (即返回从依赖者到依赖项的映射)每个映射值是在对应键上调用
     *         {@link ClassInfo#getClassDependencies()} 的结果
     */
    public Map<ClassInfo, ClassInfoList> getClassDependencyMap() {
        final Map<ClassInfo, ClassInfoList> map = new HashMap<>();
        for (final ClassInfo ci : getAllClasses()) {
            map.put(ci, ci.getClassDependencies());
        }
        return map;
    }

    /**
     * 获取反向类依赖映射，即从每个依赖类(无论是否被接受)的 {@link ClassInfo} 对象到
     * 将该类作为依赖引用的被接受类的列表的映射(即返回从依赖项到依赖者的映射)
     * 注意，你需要调用 {@link ClassGraph#enableInterClassDependencies()} 然后再调用
     * {@link ClassGraph#scan()}，此方法才能工作如果你希望未被接受的类出现在结果中，
     * 你还应该在 {@link ClassGraph#scan()} 之前调用 {@link ClassGraph#enableExternalClasses()}
     * 另请参阅 {@link #getClassDependencyMap}
     *
     * @return 从每个依赖类(无论是否被接受)的 {@link ClassInfo} 对象到
     *         将该类作为依赖引用的被接受类的列表的映射(即返回从依赖项到依赖者的映射)
     */
    public Map<ClassInfo, ClassInfoList> getReverseClassDependencyMap() {
        final Map<ClassInfo, Set<ClassInfo>> revMapSet = new HashMap<>();
        for (final ClassInfo ci : getAllClasses()) {
            for (final ClassInfo dep : ci.getClassDependencies()) {
                Set<ClassInfo> set = revMapSet.get(dep);
                if (set == null) {
                    revMapSet.put(dep, set = new HashSet<>());
                }
                set.add(ci);
            }
        }
        final Map<ClassInfo, ClassInfoList> revMapList = new HashMap<>();
        for (final Entry<ClassInfo, Set<ClassInfo>> ent : revMapSet.entrySet()) {
            revMapList.put(ent.getKey(), new ClassInfoList(ent.getValue(), /* sortByName = */ true));
        }
        return revMapList;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 类

    /**
     * 获取命名类的 {@link ClassInfo} 对象，如果扫描期间在被接受/未被拒绝的包中未找到请求名称的类则返回 null
     *
     * @param className
     *            类名称
     * @return 命名类的 {@link ClassInfo} 对象，如果未找到该类则返回 null
     */
    public ClassInfo getClassInfo(final String className) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return classNameToClassInfo.get(className);
    }

    /**
     * 返回扫描期间跳过的文件数
     *
     * @return 跳过的文件数
     */
    public int getSkippedFileCount() {
        return skippedFileCount;
    }

    /**
     * 设置跳过的文件数
     *
     * @param skippedFileCount 跳过的文件数
     */
    public void setSkippedFileCount(final int skippedFileCount) {
        this.skippedFileCount = skippedFileCount;
    }

    /**
     * 获取扫描期间找到的所有类、接口和注解
     *
     * @return 扫描期间找到的所有被接受类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllClasses() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return ClassInfo.getAllClasses(classNameToClassInfo.values(), ScanConfig);
    }

    /**
     * 获取扫描期间找到的所有 {@link Enum} 类
     *
     * @return 扫描期间找到的所有 {@link Enum} 类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllEnums() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return ClassInfo.getAllEnums(classNameToClassInfo.values(), ScanConfig);
    }

    /**
     * 获取扫描期间找到的所有 {@code record} 类(JDK 14+)
     *
     * @return 扫描期间找到的所有 {@code record} 类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllRecords() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return ClassInfo.getAllRecords(classNameToClassInfo.values(), ScanConfig);
    }

    /**
     * 获取从类名到 {@link ClassInfo} 对象的映射，包含扫描期间找到的所有类、接口和注解
     *
     * @return 从类名到 {@link ClassInfo} 对象的映射，包含扫描期间找到的所有类、接口和注解
     */
    public Map<String, ClassInfo> getAllClassesAsMap() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return classNameToClassInfo;
    }

    /**
     * 获取扫描期间找到的所有标准(非接口/非注解)类
     *
     * @return 扫描期间找到的所有被接受标准类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllStandardClasses() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return ClassInfo.getAllStandardClasses(classNameToClassInfo.values(), ScanConfig);
    }

    /**
     * 获取超类的所有子类
     *
     * @param superclass
     *            超类
     * @return 超类的子类列表，如果没有则返回空列表
     */
    public ClassInfoList getSubclasses(final Class<?> superclass) {
        return getSubclasses(superclass.getName());
    }

    /**
     * 获取命名超类的所有子类
     *
     * @param superclassName
     *            超类的名称
     * @return 命名超类的子类列表，如果没有则返回空列表
     */
    public ClassInfoList getSubclasses(final String superclassName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        if ("java.lang.Object".equals(superclassName)) {
            // 返回所有标准类(接口不继承 Object)
            return getAllStandardClasses();
        } else {
            final ClassInfo superclass = classNameToClassInfo.get(superclassName);
            return superclass == null ? ClassInfoList.EMPTY_LIST : superclass.getSubclasses();
        }
    }

    /**
     * 获取命名子类的超类
     *
     * @param subclassName
     *            子类的名称
     * @return 命名子类的超类列表，如果没有则返回空列表
     */
    public ClassInfoList getSuperclasses(final String subclassName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        final ClassInfo subclass = classNameToClassInfo.get(subclassName);
        return subclass == null ? ClassInfoList.EMPTY_LIST : subclass.getSuperclasses();
    }

    /**
     * 获取子类的超类
     *
     * @param subclass
     *            子类
     * @return 命名子类的超类列表，如果没有则返回空列表
     */
    public ClassInfoList getSuperclasses(final Class<?> subclass) {
        return getSuperclasses(subclass.getName());
    }

    /**
     * 获取具有带有命名类型注解的方法的类
     *
     * @param methodAnnotation
     *            方法注解
     * @return 具有带有命名类型注解的方法的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodAnnotation(final Class<? extends Annotation> methodAnnotation) {
        Assert.isAnnotation(methodAnnotation);
        return getClassesWithMethodAnnotation(methodAnnotation.getName());
    }

    /**
     * 获取具有带有命名类型注解的方法的类
     *
     * @param methodAnnotationName
     *            方法注解的名称
     * @return 具有带有命名类型注解的方法的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodAnnotation(final String methodAnnotationName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo || !ScanConfig.enableMethodInfo || !ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo(), #enableMethodInfo(), "
                    + "and #enableAnnotationInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(methodAnnotationName);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getClassesWithMethodAnnotation();
    }

    /**
     * 获取具有带有命名类型注解的方法参数的类
     *
     * @param methodParameterAnnotation
     *            方法参数注解
     * @return 具有带有命名类型注解的方法参数的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodParameterAnnotation(
            final Class<? extends Annotation> methodParameterAnnotation) {
        Assert.isAnnotation(methodParameterAnnotation);
        return getClassesWithMethodParameterAnnotation(methodParameterAnnotation.getName());
    }

    /**
     * 获取具有带有命名类型注解的方法参数的类
     *
     * @param methodParameterAnnotationName
     *            方法参数注解的名称
     * @return 具有带有命名类型注解的方法参数的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodParameterAnnotation(final String methodParameterAnnotationName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo || !ScanConfig.enableMethodInfo || !ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo(), #enableMethodInfo(), "
                    + "and #enableAnnotationInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(methodParameterAnnotationName);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getClassesWithMethodParameterAnnotation();
    }

    /**
     * 获取具有带有命名类型注解的字段的类
     *
     * @param fieldAnnotation
     *            字段注解
     * @return 具有带有命名类型注解的字段的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithFieldAnnotation(final Class<? extends Annotation> fieldAnnotation) {
        Assert.isAnnotation(fieldAnnotation);
        return getClassesWithFieldAnnotation(fieldAnnotation.getName());
    }

    /**
     * 获取具有带有命名类型注解的字段的类
     *
     * @param fieldAnnotationName
     *            字段注解的名称
     * @return 具有带有命名类型注解的字段的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithFieldAnnotation(final String fieldAnnotationName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo || !ScanConfig.enableFieldInfo || !ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo(), #enableFieldInfo(), "
                    + "and #enableAnnotationInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(fieldAnnotationName);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getClassesWithFieldAnnotation();
    }

    // -------------------------------------------------------------------------------------------------------------
    // 接口

    /**
     * 获取扫描期间找到的所有接口类(不包括注解，注解在技术上也是接口)
     * 另请参阅 {@link #getAllInterfacesAndAnnotations()}
     *
     * @return 扫描期间找到的所有被接受接口的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllInterfaces() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return ClassInfo.getAllImplementedInterfaceClasses(classNameToClassInfo.values(), ScanConfig);
    }

    /**
     * 获取由命名类或其超类之一实现的所有接口(如果命名类是标准类)，
     * 或由此接口扩展的超接口(如果它是接口)
     *
     * @param className
     *            类名称
     * @return 由命名类实现的接口(或由命名接口扩展的超接口)列表，如果没有则返回空列表
     */
    public ClassInfoList getInterfaces(final String className) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(className);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getInterfaces();
    }

    /**
     * 获取由给定类或其超类之一实现的所有接口(如果给定类是标准类)，
     * 或由此接口扩展的超接口(如果它是接口)
     *
     * @param classRef
     *            类
     * @return 由给定类实现的接口(或由给定接口扩展的超接口)列表，如果没有则返回空列表
     */
    public ClassInfoList getInterfaces(final Class<?> classRef) {
        return getInterfaces(classRef.getName());
    }

    /**
     * 获取实现(或有超类实现)该接口(或其子接口之一)的所有类
     *
     * @param interfaceClass
     *            接口类
     * @return 实现该接口的所有类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesImplementing(final Class<?> interfaceClass) {
        Assert.isInterface(interfaceClass);
        return getClassesImplementing(interfaceClass.getName());
    }

    /**
     * 获取实现(或有超类实现)命名接口(或其子接口之一)的所有类
     *
     * @param interfaceName
     *            接口名称
     * @return 实现命名接口的所有类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesImplementing(final String interfaceName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(interfaceName);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getClassesImplementing();
    }

    // -------------------------------------------------------------------------------------------------------------
    // 注解

    /**
     * 获取扫描期间找到的所有注解类另请参阅 {@link #getAllInterfacesAndAnnotations()}
     *
     * @return 扫描期间找到的所有注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllAnnotations() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo || !ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableClassInfo() and #enableAnnotationInfo() before #scan()");
        }
        return ClassInfo.getAllAnnotationClasses(classNameToClassInfo.values(), ScanConfig);
    }

    /**
     * 获取扫描期间找到的所有接口或注解类(注解在技术上是接口，并且它们可以被实现)
     *
     * @return 扫描期间找到的所有被接受接口的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllInterfacesAndAnnotations() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo || !ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableClassInfo() and #enableAnnotationInfo() before #scan()");
        }
        return ClassInfo.getAllInterfacesOrAnnotationClasses(classNameToClassInfo.values(), ScanConfig);
    }

    /**
     * 获取具有类注解或元注解的类
     *
     * @param annotation
     *            类注解或元注解
     * @return 在扫描期间找到的具有该类注解的所有非注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithAnnotation(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getClassesWithAnnotation(annotation.getName());
    }

    /**
     * 获取具有所有指定类注解或元注解的类
     *
     * @param annotations
     *            类注解或元注解
     * @return 在扫描期间找到的具有任何该类注解的所有非注解类的列表，如果没有则返回空列表
     */
    @SuppressWarnings("unchecked")
    public ClassInfoList getClassesWithAllAnnotations(final Class<? extends Annotation>... annotations) {
        final List<String> annotationNames = new ArrayList<>();
        for (final Class<?> cls : annotations) {
            Assert.isAnnotation(cls);
            annotationNames.add(cls.getName());
        }
        return getClassesWithAllAnnotations(annotationNames.toArray(new String[0]));
    }

    /**
     * 获取具有任意指定类注解或元注解的类
     *
     * @param annotations
     *            类注解或元注解
     * @return 在扫描期间找到的具有任何该类注解的所有非注解类的列表，如果没有则返回空列表
     */
    @SuppressWarnings("unchecked")
    public ClassInfoList getClassesWithAnyAnnotation(final Class<? extends Annotation>... annotations) {
        final List<String> annotationNames = new ArrayList<>();
        for (final Class<?> cls : annotations) {
            Assert.isAnnotation(cls);
            annotationNames.add(cls.getName());
        }
        return getClassesWithAnyAnnotation(annotationNames.toArray(new String[0]));
    }

    /**
     * 获取具有命名类注解或元注解的类
     *
     * @param annotationName
     *            类注解或元注解的名称
     * @return 在扫描期间找到的具有命名类注解的所有非注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithAnnotation(final String annotationName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo || !ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableClassInfo() and #enableAnnotationInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(annotationName);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getClassesWithAnnotation();
    }

    /**
     * 获取具有所有命名类注解或元注解的类
     *
     * @param annotationNames
     *            类注解或元注解的名称
     * @return 在扫描期间找到的具有所有命名类注解的所有非注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithAllAnnotations(final String... annotationNames) {
        ClassInfoList foundClassInfo = null;
        for (final String annotationName : annotationNames) {
            final ClassInfoList classInfoList = getClassesWithAnnotation(annotationName);
            if (foundClassInfo == null) {
                foundClassInfo = classInfoList;
            } else {
                foundClassInfo = foundClassInfo.intersect(classInfoList);
            }
        }
        CollectionUtils.sortIfNotEmpty(foundClassInfo);
        return foundClassInfo == null ? ClassInfoList.EMPTY_LIST : foundClassInfo;
    }

    /**
     * 获取具有任意命名类注解或元注解的类
     *
     * @param annotationNames
     *            类注解或元注解的名称
     * @return 在扫描期间找到的具有任意命名类注解的所有非注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithAnyAnnotation(final String... annotationNames) {
        ClassInfoList foundClassInfo = null;
        for (final String annotationName : annotationNames) {
            final ClassInfoList classInfoList = getClassesWithAnnotation(annotationName);
            if (foundClassInfo == null) {
                foundClassInfo = classInfoList;
            } else {
                foundClassInfo = foundClassInfo.union(classInfoList);
            }
        }
        CollectionUtils.sortIfNotEmpty(foundClassInfo);
        return foundClassInfo == null ? ClassInfoList.EMPTY_LIST : foundClassInfo;
    }

    /**
     * 获取命名类上的注解这仅返回注解类；要读取注解参数，请调用
     * {@link #getClassInfo(String)} 获取命名类的 {@link ClassInfo} 对象，
     * 然后如果 {@link ClassInfo} 对象非 null，调用 {@link ClassInfo#getAnnotationInfo()} 获取详细的注解信息
     *
     * @param className
     *            类的名称
     * @return 在扫描期间找到的具有命名类注解的所有注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAnnotationsOnClass(final String className) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!ScanConfig.enableClassInfo || !ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableClassInfo() and #enableAnnotationInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(className);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getAnnotations();
    }

    // -------------------------------------------------------------------------------------------------------------
    // 类路径修改测试

    /**
     * 判断自上次扫描以来类路径内容是否已被修改检查上次扫描期间遇到的
     * 文件和 jar 文件的时间戳，以查看它们是否已更改不执行完整扫描，
     * 因此无法检测到新匹配接受条件的目录的添加——你需要执行完整扫描来检测这些更改
     *
     * @return 如果自上次扫描以来类路径内容已被修改，则返回 true
     */
    public boolean classpathContentsModifiedSinceScan() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (fileToLastModified == null) {
            return true;
        } else {
            for (final Entry<File, Long> ent : fileToLastModified.entrySet()) {
                if (ent.getKey().lastModified() != ent.getValue()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 查找扫描期间遇到的任何被接受文件/目录/jar 文件的最大最后修改时间戳
     * 检查当前的时间戳，因此如果在被接受的路径中有所更改，此值应在调用之间增加
     * 假设文件和系统时间戳都是根据准确的时钟生成的忽略大于系统时间的时间戳
     *
     * <p>
     * 如果此方法在同一运行时会话中运行两次，通常无法判断类路径是否已更改
     * (或模块是否已添加或移除)
     *
     * @return 扫描期间遇到的被接受文件/目录/jar 的最大最后修改时间
     */
    public long classpathContentsLastModifiedTime() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        long maxLastModifiedTime = 0L;
        if (fileToLastModified != null) {
            final long currTime = System.currentTimeMillis();
            for (final long timestamp : fileToLastModified.values()) {
                if (timestamp > maxLastModifiedTime && timestamp < currTime) {
                    maxLastModifiedTime = timestamp;
                }
            }
        }
        return maxLastModifiedTime;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 类加载

    /**
     * 获取 ClassLoader 顺序，遵循父优先/父后置的委托顺序
     *
     * @return 类加载器顺序
     */
    ClassLoader[] getClassLoaderOrderRespectingParentDelegation() {
        return classpathFinder.getClassLoaderOrderRespectingParentDelegation();
    }

    /**
     * 根据类名加载类如果 ignoreExceptions 为 false，且类无法加载(由于类加载错误，
     * 或由于类初始化块中抛出异常)，则抛出 IllegalArgumentException；
     * 否则，如果抛出异常，该类将被静默跳过
     *
     * <p>
     * 启用详细扫描以查看类加载期间抛出的任何异常的详细信息，即使 ignoreExceptions 为 false
     *
     * @param className
     *            要加载的类
     * @param returnNullIfClassNotFound
     *            如果为 true，则在类加载期间发生异常时返回 null，
     *            否则如果类无法加载则抛出 IllegalArgumentException
     * @return 对已加载类的引用，如果类无法加载且 ignoreExceptions 为 true，则返回 null
     * @throws IllegalArgumentException
     *             如果 ignoreExceptions 为 false，则在加载或初始化类时出现问题时抛出
     *             IllegalArgumentException(注意，加载时的类初始化默认是禁用的，
     *             你可以通过 {@code ClassGraph#initializeLoadedClasses(true)} 启用它)
     *             否则异常被抑制，如果发生任何这些问题则返回 null
     */
    public Class<?> loadClass(final String className, final boolean returnNullIfClassNotFound)
            throws IllegalArgumentException {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (className == null || className.isEmpty()) {
            throw new NullPointerException("className cannot be null or empty");
        }
        try {
            return Class.forName(className, ScanConfig.initializeLoadedClasses, ScanClassLoader);
        } catch (final ClassNotFoundException | LinkageError e) {
            if (returnNullIfClassNotFound) {
                return null;
            } else {
                throw new IllegalArgumentException("Could not load class " + className + " : " + e, e);
            }
        }
    }

    /**
     * 根据类名加载类如果 ignoreExceptions 为 false，且类无法加载(由于类加载错误，
     * 或由于类初始化块中抛出异常)，则抛出 IllegalArgumentException；
     * 否则，如果抛出异常，该类将被静默跳过
     *
     * <p>
     * 启用详细扫描以查看类加载期间抛出的任何异常的详细信息，即使 ignoreExceptions 为 false
     *
     * @param <T>
     *            超类或接口类型
     * @param className
     *            要加载的类
     * @param superclassOrInterfaceType
     *            要将结果转换到的类类型
     * @param returnNullIfClassNotFound
     *            如果为 true，则在类加载期间发生异常时返回 null，
     *            否则如果类无法加载则抛出 IllegalArgumentException
     * @return 对已加载类的引用，如果类无法加载且 ignoreExceptions 为 true，则返回 null
     * @throws IllegalArgumentException
     *             如果 ignoreExceptions 为 false，则在加载类、初始化类或将其转换为请求的类型时
     *             出现问题时抛出 IllegalArgumentException(注意，加载时的类初始化默认是禁用的，
     *             你可以通过 {@code ClassGraph#initializeLoadedClasses(true)} 启用它)
     *             否则异常被抑制，如果发生任何这些问题则返回 null
     */
    public <T> Class<T> loadClass(final String className, final Class<T> superclassOrInterfaceType,
                                  final boolean returnNullIfClassNotFound) throws IllegalArgumentException {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (className == null || className.isEmpty()) {
            throw new NullPointerException("className cannot be null or empty");
        }
        if (superclassOrInterfaceType == null) {
            throw new NullPointerException("superclassOrInterfaceType parameter cannot be null");
        }
        final Class<?> loadedClass;
        try {
            loadedClass = Class.forName(className, ScanConfig.initializeLoadedClasses, ScanClassLoader);
        } catch (final ClassNotFoundException | LinkageError e) {
            if (returnNullIfClassNotFound) {
                return null;
            } else {
                throw new IllegalArgumentException("Could not load class " + className + " : " + e);
            }
        }
        if (loadedClass != null && !superclassOrInterfaceType.isAssignableFrom(loadedClass)) {
            if (returnNullIfClassNotFound) {
                return null;
            } else {
                throw new IllegalArgumentException("Loaded class " + loadedClass.getName() + " cannot be cast to "
                        + superclassOrInterfaceType.getName());
            }
        }
        @SuppressWarnings("unchecked") final Class<T> castClass = (Class<T>) loadedClass;
        return castClass;

    }


    /**
     * 释放通过从 jar 内部提取 jar 或文件而创建的任何临时文件如果不调用此方法，
     * 通过提取内层 jar 创建的临时文件将在终结器中移除，由垃圾回收器调用(或在 JVM 关闭时)
     * 如果你不想经历长时间的 GC 暂停，请确保在使用完 {@link ScanResult} 后调用此关闭方法
     */
    @Override
    public void close() {
        if (!closed.getAndSet(true)) {
            nonClosedWeakReferences.remove(weakReference);
            if (classpathOrder != null) {
                classpathOrder.clear();
                classpathOrder = null;
            }
            if (allAcceptedResourcesCached != null) {
                for (final Resource classpathResource : allAcceptedResourcesCached) {
                    classpathResource.close();
                }
                allAcceptedResourcesCached.clear();
                allAcceptedResourcesCached = null;
            }
            if (pathToAcceptedResourcesCached != null) {
                pathToAcceptedResourcesCached.clear();
                pathToAcceptedResourcesCached = null;
            }
            ScanClassLoader = null;
            if (classNameToClassInfo != null) {
                // 不清除 classNameToClassInfo，因为它可能被 ScanClassLoader 使用 (#399)
                // 仅依赖垃圾回收器在 ScanResult 超出作用域后收集这些对象
                // classNameToClassInfo.clear();
                // classNameToClassInfo = null;
            }
            if (packageNameToPackageInfo != null) {
                packageNameToPackageInfo.clear();
                packageNameToPackageInfo = null;
            }
            if (moduleNameToModuleInfo != null) {
                moduleNameToModuleInfo.clear();
                moduleNameToModuleInfo = null;
            }
            if (fileToLastModified != null) {
                fileToLastModified.clear();
                fileToLastModified = null;
            }
            // JarReader 应该最后关闭，因为它需要在尝试删除写入磁盘的
            // 任何临时文件之前释放所有 MappedByteBuffer 引用
            if (JarReader != null) {
                JarReader.close(topLevelLog);
                JarReader = null;
            }
            ScanClassLoader = null;
            classpathFinder = null;
            reflectionUtils = null;
            // 在退出时刷新日志，以防在 scan() 完成后生成了额外的日志条目
            if (topLevelLog != null) {
                topLevelLog.flush();
            }
        }
    }

    /**
     * 返回此 ScanResult 是否已被关闭
     * @return 如果此 ScanResult 已被关闭，则返回 {@code true}
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * 用于保存序列化后的 ScanResult 以及用于扫描的 ScanConfig 的类
     */
    private static class SerializationFormat {
        /** 序列化格式 */
        public String format;

        /** 扫描规格 */
        public ScanConfig ScanConfig;

        /** 类路径，作为 URL 字符串列表 */
        public List<String> classpath;

        /** 所有 {@link ClassInfo} 对象的列表 */
        public List<ClassInfo> classInfo;

        /** 所有 {@link PackageInfo} 对象的列表 */
        public List<PackageInfo> packageInfo;

        /** 所有 {@link ModuleInfo} 对象的列表 */
        public List<ModuleInfo> moduleInfo;

        /**
         * 构造函数
         */
        @SuppressWarnings("unused")
        public SerializationFormat() {
            // 空
        }

        /**
         * 构造函数
         *
         * @param serializationFormatStr
         *            序列化格式字符串
         * @param ScanConfig
         *            扫描规格
         * @param classInfo
         *            所有 {@link ClassInfo} 对象的列表
         * @param packageInfo
         *            所有 {@link PackageInfo} 对象的列表
         * @param moduleInfo
         *            所有 {@link ModuleInfo} 对象的列表
         * @param classpath
         *            作为 URL 字符串列表的类路径
         */
        public SerializationFormat(final String serializationFormatStr, final ScanConfig ScanConfig,
                                   final List<ClassInfo> classInfo, final List<PackageInfo> packageInfo,
                                   final List<ModuleInfo> moduleInfo, final List<String> classpath) {
            this.format = serializationFormatStr;
            this.ScanConfig = ScanConfig;
            this.classpath = classpath;
            this.classInfo = classInfo;
            this.packageInfo = packageInfo;
            this.moduleInfo = moduleInfo;
        }
    }
}
