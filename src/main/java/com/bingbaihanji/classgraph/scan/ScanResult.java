 
package com.bingbaihanji.classgraph.scan;

import com.bingbaihanji.classgraph.classpath.Classpath;
import com.bingbaihanji.classgraph.classpath.ClasspathFinder;
import com.bingbaihanji.classgraph.classpath.ModuleClasspath;
import com.bingbaihanji.classgraph.classpath.ModulePathInfo;
import com.bingbaihanji.classgraph.metadata.*;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.resource.JarReader;
import com.bingbaihanji.classgraph.resource.Resource;
import com.bingbaihanji.classgraph.util.FileUtils;
import com.bingbaihanji.classgraph.util.JarUtils;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.Closeable;
import java.io.File;
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
    /** Facade for class-related queries */
    private final ClassQuery classQuery;
    /** Facade for resource-related queries */
    private final ResourceQuery resourceQuery;
    public ReflectionUtils reflectionUtils;
    /** 从类名到 {@link ClassInfo} 的映射 */
    public Map<String, ClassInfo> classNameToClassInfo;
    /** 扫描规格 */
    public ScanConfig ScanConfig;
    /** {@link ClasspathFinder} */
    ClasspathFinder classpathFinder;
    /** 扫描期间跳过的文件数(读取或解析失败的 class 文件) */
    int skippedFileCount;
    /** 原始类路径元素的顺序 */
    private List<String> rawClasspathEltOrderStrs;
    /**
     * 类路径元素的顺序，在内层 jar 被提取到临时文件等操作之后
     */
    private List<Classpath> classpathOrder;
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

        this.classQuery = new ClassQuery(classNameToClassInfo, ScanConfig, closed);
        this.resourceQuery = new ResourceQuery(classpathOrder, getResourcesWithPathCallCount, closed);

        if (classNameToClassInfo != null) {
            indexResourcesAndClassInfo(topLevelLog);
        }

        if (classNameToClassInfo != null) {
            // 处理 @Repeatable 注解
            final Set<String> allRepeatableAnnotationNames = new HashSet<>();
            for (final ClassInfo classInfo : classNameToClassInfo.values()) {
                if (classInfo.isAnnotation() && classInfo.annotationInfo != null) {
                    AnnotationInfo repeatableMetaAnnotation = null;
                    for (final AnnotationInfo ai : classInfo.annotationInfo) {
                        if ("java.lang.annotation.Repeatable".equals(ai.getName())) {
                            repeatableMetaAnnotation = ai;
                            break;
                        }
                    }
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
        this.classQuery = new ClassQuery(this.classNameToClassInfo, this.ScanConfig, closed);
        this.resourceQuery = new ResourceQuery(this.classpathOrder, getResourcesWithPathCallCount, closed);
        // 为每个 ClassInfo 设置 scanResult
        for (final ClassInfo ci : this.classNameToClassInfo.values()) {
            ci.setScanResult(this);
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 构造函数 / 访问器

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

    // -------------------------------------------------------------------------------------------------------------
    // 向后兼容的委托方法(供 ClassGraphWorkspaceAdapter 等内部调用者使用)

    /**
     * 获取 {@link ClassQuery} facade，提供所有类相关的查询方法
     *
     * @return {@link ClassQuery} 实例
     */
    public ClassQuery classes() {
        return classQuery;
    }

    /**
     * 获取 {@link ResourceQuery} facade，提供所有资源相关的查询方法
     *
     * @return {@link ResourceQuery} 实例
     */
    public ResourceQuery resources() {
        return resourceQuery;
    }

    /**
     * 获取扫描期间找到的所有类、接口和注解
     *
     * @return 扫描期间找到的所有被接受类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllClasses() {
        return classQuery.getAllClasses();
    }

    /**
     * 获取命名类的 {@link ClassInfo} 对象
     *
     * @param className 类名称
     * @return 命名类的 {@link ClassInfo} 对象，如果未找到则返回 null
     */
    public ClassInfo getClassInfo(final String className) {
        return classQuery.getClassInfo(className);
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
            if (resourceQuery != null) {
                resourceQuery.close();
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
