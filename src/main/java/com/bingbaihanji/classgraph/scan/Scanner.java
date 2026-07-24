 
package com.bingbaihanji.classgraph.scan;

import com.bingbaihanji.classgraph.bytecode.ClassParser;
import com.bingbaihanji.classgraph.bytecode.ClassParser.ClassfileFormatException;
import com.bingbaihanji.classgraph.bytecode.ClassParser.SkipClassException;
import com.bingbaihanji.classgraph.classpath.*;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder.ClasspathEntry;
import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.metadata.ModuleInfo;
import com.bingbaihanji.classgraph.metadata.ModuleRef;
import com.bingbaihanji.classgraph.metadata.PackageInfo;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.resource.JarReader;
import com.bingbaihanji.classgraph.resource.Resource;
import com.bingbaihanji.classgraph.scan.ClassGraph.FailureHandler;
import com.bingbaihanji.classgraph.scan.ClassGraph.ScanResultProcessor;
import com.bingbaihanji.classgraph.util.*;
import com.bingbaihanji.classgraph.util.SingletonMap.NewInstanceFactory;
import com.bingbaihanji.classgraph.util.WorkQueue.WorkUnitProcessor;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.concurrent.*;

/** 类路径扫描器 */
public class Scanner implements Callable<ScanResult> {

    /** 扫描规格 */
    private final ScanConfig ScanConfig;
    /** 嵌套 jar 处理器 */
    private final JarReader JarReader;
    /** 执行器服务 */
    private final ExecutorService executorService;
    /** 中断检查器 */
    private final InterruptionChecker interruptionChecker;
    /** 并行任务数 */
    private final int numParallelTasks;
    /** 扫描结果处理器 */
    private final ScanResultProcessor scanResultProcessor;
    /** 失败处理器 */
    private final FailureHandler failureHandler;
    /** 顶层日志 */
    private final LogNode topLevelLog;
    /** 类路径查找器 */
    private final ClasspathFinder classpathFinder;
    /** 模块顺序 */
    private final List<ModuleClasspath> moduleOrder;
    /**
     * 一个单例映射，用于消除重复 {@link Classpath} 对象的创建，通过将规范化的
     * Path 对象、URL 等映射到 Classpath，以降低资源被扫描两次的机会
     */
    private final SingletonMap<Object, Classpath, IOException> //
            classpathEntryObjToClasspathEntrySingletonMap = //
            new SingletonMap<Object, Classpath, IOException>() {
                @Override
                public Classpath newInstance(final Object classpathEntryObj, final LogNode log)
                        throws IOException, InterruptedException {
                    // 由 NewInstanceFactory 重写
                    throw new IOException("Should not reach here");
                }
            };

    // -------------------------------------------------------------------------------------------------------------
    /** 如果为 true，表示正在执行扫描如果为 false，则仅获取类路径 */
    public boolean performScan;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 类路径扫描器通过在此对象上调用 {@link #call()} 来启动扫描
     *
     * @param performScan
     *            如果为 true，表示正在执行扫描如果为 false，则仅获取类路径
     * @param ScanConfig
     *            扫描规格
     * @param executorService
     *            执行器服务
     * @param numParallelTasks
     *            并行任务数
     * @param scanResultProcessor
     *            扫描结果处理器
     * @param failureHandler
     *            失败处理器
     * @param topLevelLog
     *            日志
     *
     * @throws InterruptedException
     *             如果被中断
     */
    Scanner(final boolean performScan, final ScanConfig ScanConfig, final ExecutorService executorService,
            final int numParallelTasks, final ScanResultProcessor scanResultProcessor,
            final FailureHandler failureHandler, final ReflectionUtils reflectionUtils, final LogNode topLevelLog)
            throws InterruptedException {
        this.ScanConfig = ScanConfig;
        this.performScan = performScan;
        ScanConfig.sortPrefixes();
        ScanConfig.log(topLevelLog);
        if (topLevelLog != null) {
            if (ScanConfig.pathAcceptReject != null
                    && ScanConfig.packagePrefixAcceptReject.isSpecificallyAccepted("")) {
                topLevelLog.log("Note: There is no need to accept the root package (\"\") -- not accepting "
                        + "anything will have the same effect of causing all packages to be scanned");
            }
            topLevelLog.log("Number of worker threads: " + numParallelTasks);
        }

        this.executorService = executorService;
        this.interruptionChecker = executorService instanceof AutoCloseableExecutorService
                ? ((AutoCloseableExecutorService) executorService).interruptionChecker
                : new InterruptionChecker();
        this.JarReader = new JarReader(ScanConfig, interruptionChecker, reflectionUtils);
        this.numParallelTasks = numParallelTasks;
        this.scanResultProcessor = scanResultProcessor;
        this.failureHandler = failureHandler;
        this.topLevelLog = topLevelLog;

        final LogNode classpathFinderLog = topLevelLog == null ? null : topLevelLog.log("Finding classpath");
        this.classpathFinder = new ClasspathFinder(ScanConfig, reflectionUtils, classpathFinderLog);

        try {
            this.moduleOrder = new ArrayList<>();

            // 检查是否应扫描模块
            final ModuleFinder moduleFinder = classpathFinder.getModuleFinder();
            if (moduleFinder != null) {
                // 将模块添加到类路径顺序的开头，位于传统类路径之前
                final List<ModuleRef> systemModuleRefs = moduleFinder.getSystemModuleRefs();
                final ClassLoader[] classLoaderOrderRespectingParentDelegation = classpathFinder
                        .getClassLoaderOrderRespectingParentDelegation();
                final ClassLoader defaultClassLoader = classLoaderOrderRespectingParentDelegation != null
                        && classLoaderOrderRespectingParentDelegation.length != 0
                        ? classLoaderOrderRespectingParentDelegation[0]
                        : null;
                if (systemModuleRefs != null) {
                    for (final ModuleRef systemModuleRef : systemModuleRefs) {
                        final String moduleName = systemModuleRef.getName();
                        if (
                            // 如果启用了系统包和模块的扫描，且接受/拒绝条件为空，则扫描所有系统模块
                                (ScanConfig.enableSystemJarsAndModules
                                        && ScanConfig.moduleAcceptReject.acceptAndRejectAreEmpty())
                                        // 否则仅扫描被明确接受的系统模块
                                        || ScanConfig.moduleAcceptReject.isSpecificallyAcceptedAndNotRejected(moduleName)) {
                            // 创建一个新的 ModuleClasspath
                            final ModuleClasspath ModuleClasspath = new ModuleClasspath(
                                    systemModuleRef, JarReader.moduleRefToModuleReaderProxyRecyclerMap,
                                    new ClasspathEntryWorkUnit(null, defaultClassLoader, null, moduleOrder.size(),
                                            ""),
                                    ScanConfig);
                            moduleOrder.add(ModuleClasspath);
                            // 打开 ModuleClasspath
                            ModuleClasspath.open(/* ignored */ null, classpathFinderLog);
                        } else {
                            if (classpathFinderLog != null) {
                                classpathFinderLog
                                        .log("Skipping non-accepted or rejected system module: " + moduleName);
                            }
                        }
                    }
                }
                final List<ModuleRef> nonSystemModuleRefs = moduleFinder.getNonSystemModuleRefs();
                if (nonSystemModuleRefs != null) {
                    for (final ModuleRef nonSystemModuleRef : nonSystemModuleRefs) {
                        String moduleName = nonSystemModuleRef.getName();
                        if (moduleName == null) {
                            moduleName = "";
                        }
                        if (ScanConfig.moduleAcceptReject.isAcceptedAndNotRejected(moduleName)) {
                            // 创建一个新的 ModuleClasspath
                            final ModuleClasspath ModuleClasspath = new ModuleClasspath(
                                    nonSystemModuleRef, JarReader.moduleRefToModuleReaderProxyRecyclerMap,
                                    new ClasspathEntryWorkUnit(null, defaultClassLoader, null, moduleOrder.size(),
                                            ""),
                                    ScanConfig);
                            moduleOrder.add(ModuleClasspath);
                            // 打开 ModuleClasspath
                            ModuleClasspath.open(/* ignored */ null, classpathFinderLog);
                        } else {
                            if (classpathFinderLog != null) {
                                classpathFinderLog.log("Skipping non-accepted or rejected module: " + moduleName);
                            }
                        }
                    }
                }
            }
        } catch (final InterruptedException e) {
            JarReader.close(/* log = */ null);
            throw e;
        }
    }

    /**
     * 递归执行 jar 相互依赖的深度优先搜索，必要时打破循环，以确定最终的类路径元素顺序
     *
     * @param currClasspath
     *            当前类路径元素
     * @param visitedClasspathElts
     *            已访问的类路径元素
     * @param order
     *            类路径元素顺序
     */
    private static void findClasspathOrderRec(final Classpath currClasspath,
                                              final Set<Classpath> visitedClasspathElts, final List<Classpath> order) {
        if (visitedClasspathElts.add(currClasspath)) {
            // 类路径顺序需要对类路径依赖的 DAG 进行前序遍历
            if (!currClasspath.skipClasspath) {
                // 如果类路径元素被标记为跳过，则不添加
                order.add(currClasspath);
                // 无论类路径元素是否应被跳过，都添加任何未被标记为跳过的子类路径元素
                // (即继续向下递归)
            }
            // 将子元素排序为正确的顺序，然后按顺序遍历它们
            final List<Classpath> childClasspathsSorted = CollectionUtils
                    .sortCopy(currClasspath.childClasspaths);
            for (final Classpath childClasspathElt : childClasspathsSorted) {
                findClasspathOrderRec(childClasspathElt, visitedClasspathElts, order);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 规范化类路径条目对象，以便尽可能将其映射到规范的 {@link Path} 对象，
     * 如果不行则回退到 {@link URL} 或 {@link URI}
     * 这是为了避免将 "file:///path/to/x.jar" 和 "/path/to/x.jar" 视为不同的类路径元素
     * 将 URL("jar:file:x.jar!/") 映射到 Path("x.jar") 等
     *
     * @param classpathEntryObj
     *            类路径条目对象
     * @return 规范化后的类路径条目对象
     * @throws IOException
     */
    private static Object normalizeClasspathEntry(final Object classpathEntryObj) throws IOException {
        if (classpathEntryObj == null) {
            // 不应该发生
            throw new IOException("Got null classpath entry object");
        }
        Object classpathEntryObjNormalized = classpathEntryObj;

        // 将 URL/URI(或除 URL/URI 或 Path 之外的其他任何类型)转换为 String
        // Paths.get 对于像 "jar:file:myjar.jar!/" 这样的路径会失败，
        // 抛出 "IllegalArgumentException: URI is not hierarchical" (#625)
        // -- 需要去掉末尾的 "!/"同时去掉开头的 "jar:file:" 或 "file:"
        // 这会将 "file:x.jar" 和 "x.jar" 规范化为相同的字符串，例如
        if (!(classpathEntryObjNormalized instanceof Path)) {
            classpathEntryObjNormalized = FastPathResolver.resolve(FileUtils.currDirPath(),
                    classpathEntryObjNormalized.toString());
        }

        // 如果类路径条目对象是 URL 格式的字符串，则转换回(或转换到)URL 实例
        if (classpathEntryObjNormalized instanceof String) {
            String classpathEntStr = (String) classpathEntryObjNormalized;
            final boolean isURL = JarUtils.URL_SCHEME_PATTERN.matcher(classpathEntStr).matches();
            final boolean isMultiSection = classpathEntStr.contains("!");
            if (isURL || isMultiSection) {
                // 对类路径条目中的空格和井号进行编码，因为它们在转换为 URL/URI 时可能无效
                classpathEntStr = classpathEntStr.replace(" ", "%20").replace("#", "%23");
                // 如果具有 URL 协议或者是多段路径(需要 "jar:file:" 协议)，则转换回 URL(或 URI)
                if (!isURL) {
                    // 如果没有协议，则添加 "file:" 协议
                    classpathEntStr = "file:" + classpathEntStr;
                }
                if (isMultiSection) {
                    // 没有 URL 协议的多段 URL 字符串需要具有 "jar:file:" 协议
                    classpathEntStr = "jar:" + classpathEntStr;
                    // "jar:" URL 也需要至少一个 "!/" 实例 -- 如果只使用了 "!" 而没有跟随 "/"，则替换
                    classpathEntStr = classpathEntStr.replaceAll("!([^/])", "!/$1");
                }
                try {
                    // 将类路径条目转换回(或转换到)URL
                    final URL classpathEntryURL = new URL(classpathEntStr);
                    classpathEntryObjNormalized = classpathEntryURL;

                    // 如果这不是多段 URL，则尝试将 URL 转换为 Path
                    if (!isMultiSection) {
                        try {
                            final String scheme = classpathEntryURL.getProtocol();
                            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                                final URI classpathEntryURI = classpathEntryURL.toURI();
                                // 查看 URL 是否通过 Path API 解析到文件或目录
                                classpathEntryObjNormalized = Paths.get(classpathEntryURI);
                            }
                        } catch (final URISyntaxException | IllegalArgumentException | SecurityException e1) {
                            // URL 无法表示为 URI 或 Path
                        } catch (final FileSystemNotFoundException e) {
                            // 这是一个没有后端 FileSystem 的自定义 URL 协议
                        }
                    } // 否则这是一个远程 jar URL

                } catch (final MalformedURLException e) {
                    // 如果 URL 创建失败，尝试创建 URI，以防存在仅 URI 协议的方案
                    try {
                        final URI classpathEntryURI = new URI(classpathEntStr);
                        classpathEntryObjNormalized = classpathEntryURI;

                        final String scheme = classpathEntryURI.getScheme();
                        if (!"http".equals(scheme) && !"https".equals(scheme)) {
                            // 查看 URI 是否通过 Path API 解析到文件或目录
                            classpathEntryObjNormalized = Paths.get(classpathEntryURI);
                        } // 否则这是一个远程 jar URI

                    } catch (final URISyntaxException e1) {
                        throw new IOException("Malformed URI: " + classpathEntryObjNormalized + " : " + e1);
                    } catch (final IllegalArgumentException | SecurityException e1) {
                        // URI 无法表示为 Path
                    } catch (final FileSystemNotFoundException e1) {
                        // 这是一个没有后端 FileSystem 的自定义 URI 协议
                    }
                }
            }
            // 最后的尝试 -- 尝试将 String 转换为 Path
            if (classpathEntryObjNormalized instanceof String) {
                try {
                    classpathEntryObjNormalized = new File((String) classpathEntryObjNormalized).toPath();
                } catch (final Exception e) {
                    try {
                        classpathEntryObjNormalized = Paths.get((String) classpathEntryObjNormalized);
                    } catch (final InvalidPathException e2) {
                        throw new IOException("Malformed path: " + classpathEntryObj + " : " + e2);
                    }
                }
            }
        }
        // 此时，classpathEntryObjNormalized 要么是 Path(在类路径条目指向 jar 文件或目录时)，
        // 要么是 URL/URI(对于带有 "!" 分隔符的多段 "jar:" URL、没有后端文件系统的自定义 URL 协议、
        // 或因任何其他原因无法转换为 Path 的 URL)

        // 规范化 Path 对象，以便同一个文件只打开一次
        if (classpathEntryObjNormalized instanceof Path) {
            try {
                // 规范化路径以避免重复
                // 如果文件不存在或发生 I/O 错误，则抛出 IOException
                classpathEntryObjNormalized = ((Path) classpathEntryObjNormalized).toRealPath();
            } catch (final IOException | SecurityException e) {
                // 忽略
            }
        }

        return classpathEntryObjNormalized;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 递归执行子类路径元素的深度优先遍历，必要时打破循环，以确定最终的类路径元素顺序
     * 这会导致子类路径元素被就地插入到类路径顺序中，位于包含它们的父类路径元素之后
     *
     * @param toplevelClasspathElts
     *            顶层类路径元素，按顶层类路径中的顺序索引
     * @return 最终的类路径顺序，在深度优先遍历子类路径元素之后
     */
    private List<Classpath> findClasspathOrder(final Set<Classpath> toplevelClasspathElts) {
        // 将顶层类路径元素排序为正确的顺序
        final List<Classpath> toplevelClasspathEltsSorted = CollectionUtils.sortCopy(toplevelClasspathElts);

        // 对类路径元素的 DAG 执行深度优先前序遍历
        final Set<Classpath> visitedClasspathElts = new HashSet<>();
        final List<Classpath> order = new ArrayList<>();
        for (final Classpath elt : toplevelClasspathEltsSorted) {
            findClasspathOrderRec(elt, visitedClasspathElts, order);
        }
        return order;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 处理工作单元
     *
     * @param <W>
     *            工作单元类型
     * @param workUnits
     *            工作单元
     * @param log
     *            用于分组工作单元的日志条目文本
     * @param workUnitProcessor
     *            工作单元处理器
     * @throws InterruptedException
     *             如果工作线程被中断
     * @throws ExecutionException
     *             如果工作线程抛出了未捕获的异常
     */
    private <W> void processWorkUnits(final Collection<W> workUnits, final LogNode log,
                                      final WorkUnitProcessor<W> workUnitProcessor) throws InterruptedException, ExecutionException {
        WorkQueue.runWorkQueue(workUnits, executorService, interruptionChecker, numParallelTasks, log,
                workUnitProcessor);
        if (log != null) {
            log.addElapsedTime();
        }
        // 如果任何工作线程失败，则抛出 InterruptedException
        interruptionChecker.check();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 创建一个 WorkUnitProcessor，用于打开传统类路径条目(映射到
     * {@link DirClasspath} 或 {@link JarClasspath} -- {@link ModuleClasspath 是单独处理的})
     *
     * @param allClasspathEltsOut
     *            输出时，所有类路径元素的集合
     * @param toplevelClasspathEltsOut
     *            输出时，顶层类路径元素
     * @return 工作单元处理器
     */
    private WorkUnitProcessor<ClasspathEntryWorkUnit> newClasspathEntryWorkUnitProcessor(
            final Set<Classpath> allClasspathEltsOut, final Set<Classpath> toplevelClasspathEltsOut) {
        return new WorkUnitProcessor<ClasspathEntryWorkUnit>() {
            @Override
            public void processWorkUnit(final ClasspathEntryWorkUnit workUnit,
                                        final WorkQueue<ClasspathEntryWorkUnit> workQueue, final LogNode log)
                    throws InterruptedException {
                try {
                    // 规范化类路径条目对象，并在工作单元中更新它
                    workUnit.classpathEntryObj = normalizeClasspathEntry(workUnit.classpathEntryObj);

                    // 判断类路径条目是 jar 还是目录
                    final boolean isJar;
                    if (workUnit.classpathEntryObj instanceof URL || workUnit.classpathEntryObj instanceof URI) {
                        // URL 和 URI 总是指向 jar
                        isJar = true;
                    } else if (workUnit.classpathEntryObj instanceof Path) {
                        final Path path = (Path) workUnit.classpathEntryObj;
                        if ("JrtFileSystem".equals(path.getFileSystem().getClass().getSimpleName())) {
                            // 忽略 JrtFileSystem (#553) -- 路径格式为：
                            // /modules/java.base/module-info.class
                            throw new IOException("Ignoring JrtFS filesystem path "
                                    + "(modules are scanned using the JPMS API): " + path);
                        }
                        if (!FileUtils.canRead(path)) {
                            throw new IOException("Cannot read path: " + path);
                        } else {
                            final BasicFileAttributes attributes = Files.readAttributes(path,
                                    BasicFileAttributes.class);
                            if (attributes.isRegularFile()) {
                                // classpathEntObj 是指向文件的 Path，因此它必须是 jar
                                isJar = true;
                            } else if (attributes.isDirectory()) {
                                // classpathEntObj 是指向目录的 Path
                                isJar = false;
                            } else {
                                throw new IOException("Not a file or directory: " + path);
                            }
                        }
                    } else {
                        // 不应该发生
                        throw new IOException("Got unexpected classpath entry object type "
                                + workUnit.classpathEntryObj.getClass().getName() + " : "
                                + workUnit.classpathEntryObj);
                    }

                    // 从类路径条目创建 JarClasspath 或 DirClasspath
                    // 使用单例映射确保类路径元素仅对每个唯一的 Path、URL 或 URI 打开一次
                    classpathEntryObjToClasspathEntrySingletonMap.get(workUnit.classpathEntryObj, log,
                            // 此处使用 NewInstanceFactory，因为需要传入 workUnit，
                            // 而标准的 newInstance API 不支持像这样的额外参数
                            new NewInstanceFactory<Classpath, IOException>() {
                                @Override
                                public Classpath newInstance() throws IOException, InterruptedException {
                                    final Classpath Classpath = isJar
                                            ? new JarClasspath(workUnit, JarReader, ScanConfig)
                                            : new DirClasspath(workUnit, JarReader, ScanConfig);

                                    allClasspathEltsOut.add(Classpath);

                                    // 在 Classpath 上运行 open()
                                    final LogNode subLog = log == null ? null
                                            : log.log(Classpath.getURI().toString(),
                                            "Opening classpath element " + Classpath);

                                    // 检查类路径元素是否有效(如果无效，classpathElt.skipClasspath 将被设置)
                                    // 对于 JarClasspath，将嵌套 jar 作为 LogicalZipFile 实例打开或提取
                                    // 读取 jar 文件的清单文件以查找 Class-Path 清单条目
                                    // 如果找到，将额外的类路径元素添加到工作队列中
                                    Classpath.open(workQueue, subLog);

                                    if (workUnit.parentClasspath != null) {
                                        // 将类路径元素链接到其父元素(如果它不是顶层元素)
                                        workUnit.parentClasspath.childClasspaths
                                                .add(Classpath);
                                    } else {
                                        toplevelClasspathEltsOut.add(Classpath);
                                    }

                                    return Classpath;
                                }
                            });

                } catch (final Exception e) {
                    if (log != null) {
                        log.log("Skipping invalid classpath entry " + workUnit.classpathEntryObj + " : "
                                + (e.getCause() == null ? e : e.getCause()));
                    }
                }
            }
        };
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 查找路径是另一个类路径元素前缀的类路径元素，并记录嵌套关系
     *
     * @param classpathElts
     *            类路径元素
     * @param log
     *            日志
     */
    private void findNestedClasspaths(final List<SimpleEntry<String, Classpath>> classpathElts,
                                      final LogNode log) {
        // 将类路径元素按字典序排序
        CollectionUtils.sortIfNotEmpty(classpathElts, new Comparator<SimpleEntry<String, Classpath>>() {
            @Override
            public int compare(final SimpleEntry<String, Classpath> o1,
                               final SimpleEntry<String, Classpath> o2) {
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        // 查找元素中是否有嵌套在其他元素中的情况
        for (int i = 0; i < classpathElts.size(); i++) {
            // 查看每个类路径元素是否是其他元素的前缀(如果是，它们将在字典序中紧随其后)
            final SimpleEntry<String, Classpath> ei = classpathElts.get(i);
            final String basePath = ei.getKey();
            final int basePathLen = basePath.length();
            for (int j = i + 1; j < classpathElts.size(); j++) {
                final SimpleEntry<String, Classpath> ej = classpathElts.get(j);
                final String comparePath = ej.getKey();
                final int comparePathLen = comparePath.length();
                boolean foundNestedClasspathRoot = false;
                if (comparePath.startsWith(basePath) && comparePathLen > basePathLen) {
                    // 要求前缀后有一个分隔符
                    final char nextChar = comparePath.charAt(basePathLen);
                    if (nextChar == '/' || nextChar == '!') {
                        // basePath 是 comparePath 的路径前缀确保嵌套的类路径不包含
                        // 另一个 '!' zip 分隔符(因为类路径扫描不会递归到 jar 内部的 jar，
                        // 除非它们在类路径上被显式列出)
                        final String nestedClasspathRelativePath = comparePath.substring(basePathLen + 1);
                        if (nestedClasspathRelativePath.indexOf('!') < 0) {
                            // 找到了嵌套的类路径根
                            foundNestedClasspathRoot = true;
                            // 存储从前缀元素到嵌套元素的链接
                            final Classpath baseElement = ei.getValue();
                            if (baseElement.nestedClasspathRootPrefixes == null) {
                                baseElement.nestedClasspathRootPrefixes = new ArrayList<>();
                            }
                            baseElement.nestedClasspathRootPrefixes.add(nestedClasspathRelativePath + "/");
                            if (log != null) {
                                log.log(basePath + " is a prefix of the nested element " + comparePath);
                            }
                        }
                    }
                }
                if (!foundNestedClasspathRoot) {
                    // 在第一个不匹配之后，排序顺序中不可能再有任何前缀匹配
                    break;
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 查找路径是另一个类路径元素前缀的类路径元素，并记录嵌套关系
     *
     * @param finalTraditionalClasspathEltOrder
     *            最终的传统类路径元素顺序
     * @param classpathFinderLog
     *            类路径查找器日志
     */
    private void preprocessClasspathsByType(final List<Classpath> finalTraditionalClasspathEltOrder,
                                            final LogNode classpathFinderLog) {
        final List<SimpleEntry<String, Classpath>> classpathEltDirs = new ArrayList<>();
        final List<SimpleEntry<String, Classpath>> classpathEltZips = new ArrayList<>();
        for (final Classpath classpathElt : finalTraditionalClasspathEltOrder) {
            if (classpathElt instanceof DirClasspath) {
                // 将 ClasspathFileDir 和 ClasspathPathDir 元素与其他类型分开
                final File file = classpathElt.getFile();
                final String path = file == null ? classpathElt.toString() : file.getPath();
                classpathEltDirs.add(new SimpleEntry<>(path, classpathElt));

            } else if (classpathElt instanceof JarClasspath) {
                // 将 JarClasspath 元素与其他类型分开
                final JarClasspath classpathEltZip = (JarClasspath) classpathElt;
                classpathEltZips.add(new SimpleEntry<>(classpathEltZip.getZipFilePath(), classpathElt));

                // 处理与模块相关的清单条目
                if (classpathEltZip.logicalZipFile != null) {
                    // 来自 JEP 261：
                    // "Add-Exports 属性值中的 <module>/<package> 对与命令行选项
                    // --add-exports <module>/<package>=ALL-UNNAMED 具有相同的含义
                    // Add-Opens 属性值中的 <module>/<package> 对与命令行选项
                    // --add-opens <module>/<package>=ALL-UNNAMED 具有相同的含义"
                    if (classpathEltZip.logicalZipFile.addExportsManifestEntryValue != null) {
                        for (final String addExports : JarUtils.smartPathSplit(
                                classpathEltZip.logicalZipFile.addExportsManifestEntryValue, ' ', ScanConfig)) {
                            ScanConfig.modulePathInfo.addExports.add(addExports + "=ALL-UNNAMED");
                        }
                    }
                    if (classpathEltZip.logicalZipFile.addOpensManifestEntryValue != null) {
                        for (final String addOpens : JarUtils.smartPathSplit(
                                classpathEltZip.logicalZipFile.addOpensManifestEntryValue, ' ', ScanConfig)) {
                            ScanConfig.modulePathInfo.addOpens.add(addOpens + "=ALL-UNNAMED");
                        }
                    }
                    // 检索 Automatic-Module-Name 清单条目(如果存在)
                    if (classpathEltZip.logicalZipFile.automaticModuleNameManifestEntryValue != null) {
                        classpathEltZip.moduleNameFromManifestFile = //
                                classpathEltZip.logicalZipFile.automaticModuleNameManifestEntryValue;
                    }
                }
            }
            // (忽略 ModuleClasspath，无需进行预处理)
        }
        // 查找嵌套的类路径元素(写入 Classpath#nestedClasspathRootPrefixes)
        findNestedClasspaths(classpathEltDirs, classpathFinderLog);
        findNestedClasspaths(classpathEltZips, classpathFinderLog);
    }

    /**
     * 对类文件执行类路径掩码如果相同的相对类文件路径在类路径中出现多次，
     * 会导致第二次及后续的出现被忽略(移除)
     *
     * @param classpathElementOrder
     *            类路径元素顺序
     * @param maskLog
     *            掩码日志
     */
    private void maskClassfiles(final List<Classpath> classpathElementOrder, final LogNode maskLog) {
        final Set<String> acceptedClasspathRelativePathsFound = new HashSet<>();
        for (int classpathIdx = 0; classpathIdx < classpathElementOrder.size(); classpathIdx++) {
            final Classpath Classpath = classpathElementOrder.get(classpathIdx);
            Classpath.maskClassfiles(classpathIdx, acceptedClasspathRelativePathsFound, maskLog);
        }
        if (maskLog != null) {
            maskLog.addElapsedTime();
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 扫描类路径和/或可见模块
     *
     * @param finalClasspathEltOrder
     *            最终的类路径元素顺序
     * @param finalClasspathEltOrderStrs
     *            最终的类路径元素顺序字符串
     * @param classpathFinder
     *            {@link ClasspathFinder}
     * @return 扫描结果
     * @throws InterruptedException
     *             如果扫描被中断
     * @throws ExecutionException
     *             如果扫描抛出了未捕获的异常
     */
    private ScanResult performScan(final List<Classpath> finalClasspathEltOrder,
                                   final List<String> finalClasspathEltOrderStrs, final ClasspathFinder classpathFinder)
            throws InterruptedException, ExecutionException {
        // 掩码类文件(移除任何被同一类的较早定义掩盖的类文件资源)
        if (ScanConfig.enableClassInfo) {
            maskClassfiles(finalClasspathEltOrder,
                    topLevelLog == null ? null : topLevelLog.log("Masking classfiles"));
        }

        // 合并所有类路径元素的文件到时间戳的映射
        final Map<File, Long> fileToLastModified = new HashMap<>();
        for (final Classpath Classpath : finalClasspathEltOrder) {
            fileToLastModified.putAll(Classpath.fileToLastModified);
        }

        // 如果 ScanConfig.enableClassInfo 为 true，则扫描类文件
        // (classNameToClassInfo 是一个 ConcurrentHashMap，因为在扫描完成后
        // ArrayType.getArrayClassInfo() 可能会修改它)
        final Map<String, ClassInfo> classNameToClassInfo = new ConcurrentHashMap<>();
        final Map<String, PackageInfo> packageNameToPackageInfo = new HashMap<>();
        final Map<String, ModuleInfo> moduleNameToModuleInfo = new HashMap<>();
        if (ScanConfig.enableClassInfo) {
            // 获取被接受的类文件顺序
            final List<ClassfileScanWorkUnit> classfileScanWorkItems = new ArrayList<>();
            final Set<String> acceptedClassNamesFound = new HashSet<>();
            for (final Classpath Classpath : finalClasspathEltOrder) {
                // 获取所有类路径元素的类文件扫描顺序
                for (final Resource resource : Classpath.acceptedClassfileResources) {
                    // 创建在类路径元素路径中找到的所有被接受类的名称集合，
                    // 并双重检查一个类不会被扫描两次
                    final String className = JarUtils.classfilePathToClassName(resource.getPath());
                    if (!acceptedClassNamesFound.add(className) && !"module-info".equals(className)
                            && !"package-info".equals(className) && !className.endsWith(".package-info")) {
                        // 由于已经应用了类路径掩码，该类不应被多次调度扫描
                        throw new IllegalArgumentException("Class " + className
                                + " should not have been scheduled more than once for scanning due to classpath"
                                + " masking -- please report this bug at:"
                                + " https://github.com/classgraph/classgraph/issues");
                    }
                    // 调度类进行扫描
                    classfileScanWorkItems
                            .add(new ClassfileScanWorkUnit(Classpath, resource, /* isExternal = */ false));
                }
            }

            // 并行扫描类文件
            final Queue<ClassParser> scannedClassfiles = new ConcurrentLinkedQueue<>();
            final ClassfileScannerWorkUnitProcessor classfileWorkUnitProcessor = //
                    new ClassfileScannerWorkUnitProcessor(ScanConfig, finalClasspathEltOrder,
                            Collections.unmodifiableSet(acceptedClassNamesFound), scannedClassfiles);
            processWorkUnits(classfileScanWorkItems,
                    topLevelLog == null ? null : topLevelLog.log("Scanning classfiles"),
                    classfileWorkUnitProcessor);

            // 链接 ClassParser 对象以生成 ClassInfo 对象这需要从单个线程完成
            final LogNode linkLog = topLevelLog == null ? null : topLevelLog.log("Linking related classfiles");
            while (!scannedClassfiles.isEmpty()) {
                final ClassParser c = scannedClassfiles.remove();
                c.link(classNameToClassInfo, packageNameToPackageInfo, moduleNameToModuleInfo);
            }

            // 取消注释以下代码，为类型描述符或类型签名中引用的任何类创建占位外部类，
            // 以便可以为这些类引用获取 ClassInfo 对象这将导致所有类型描述符和类型
            // 签名被解析，并从中提取类名这将增加扫描时间的一些开销，唯一的好处是
            // ClassRef.getClassInfo() 和 AnnotationClassRef.getClassInfo()
            // 永远不会返回 null，因为注解类引用中找到的所有外部类都将为其创建一个占位
            // ClassInfo 对象这个功能足够晦涩，可能不值得为了强制解析所有类型描述符
            // 和类型签名(在返回 ScanResult 之前)而减慢所有其他用例的扫描速度
            // 在此代码被注释掉的情况下，类型签名和类型描述符仅在需要时才会被延迟解析

            //    final Set<String> referencedClassNames = new HashSet<>();
            //    for (final ClassInfo classInfo : classNameToClassInfo.values()) {
            //        classInfo.findReferencedClassNames(referencedClassNames);
            //    }
            //    for (final String referencedClass : referencedClassNames) {
            //        ClassInfo.getOrCreateClassInfo(referencedClass, /* modifiers = */ 0, ScanConfig,
            //                classNameToClassInfo);
            //    }

            if (linkLog != null) {
                linkLog.addElapsedTime();
            }
        } else {
            if (topLevelLog != null) {
                topLevelLog.log("ClassParser scanning is disabled");
            }
        }

        // 返回一个新的 ScanResult
        final ScanResult scanResult = new ScanResult(ScanConfig, finalClasspathEltOrder, finalClasspathEltOrderStrs,
                classpathFinder, classNameToClassInfo, packageNameToPackageInfo, moduleNameToModuleInfo,
                fileToLastModified, JarReader, topLevelLog);

        // 在每个类路径元素中设置 ScanResult，以便类路径元素可以确定 ScanResult 何时被关闭
        for (final Classpath Classpath : finalClasspathEltOrder) {
            Classpath.setScanResult(scanResult);
        }

        return scanResult;
    }

    /**
     * 打开每个类路径元素，查找需要扫描的额外子类路径元素(例如 jar 清单文件中的
     * {@code Class-Path} 条目)，然后在 {@link #performScan} 为 true 时执行扫描，
     * 或者在 {@link #performScan} 为 false 时仅获取类路径
     *
     * @return 扫描结果
     * @throws InterruptedException
     *             如果扫描被中断
     * @throws ExecutionException
     *             如果工作线程抛出了未捕获的异常
     */
    private ScanResult openClasspathsThenScan() throws InterruptedException, ExecutionException {
        // 获取传统类路径中元素的顺序
        final List<ClasspathEntryWorkUnit> rawClasspathEntryWorkUnits = new ArrayList<>();
        final List<ClasspathEntry> rawClasspathOrder = classpathFinder.getClasspathOrder().getOrder();
        for (final ClasspathEntry rawClasspathEntry : rawClasspathOrder) {
            rawClasspathEntryWorkUnits.add(new ClasspathEntryWorkUnit(rawClasspathEntry.classpathEntryObj,
                    rawClasspathEntry.classLoader, /* parentClasspath = */ null,
                    // classpathElementIdxWithinParent 是原始类路径索引，
                    // 用于顶层类路径元素
                    /* classpathElementIdxWithinParent = */ rawClasspathEntryWorkUnits.size(),
                    /* packageRootPrefix = */ ""));
        }

        // 并行地为每个类路径元素创建一个 Classpath 单例，然后对每个
        // Classpath 对象调用 open()，对于 jar 文件，这将导致为每个(可能嵌套的)
        // jar 文件创建 LogicalZipFile 实例，然后读取清单文件和 zip 条目
        final Set<Classpath> allClasspathElts = Collections
                .newSetFromMap(new ConcurrentHashMap<Classpath, Boolean>());
        final Set<Classpath> toplevelClasspathElts = Collections
                .newSetFromMap(new ConcurrentHashMap<Classpath, Boolean>());
        processWorkUnits(rawClasspathEntryWorkUnits,
                topLevelLog == null ? null : topLevelLog.log("Opening classpath elements"),
                newClasspathEntryWorkUnitProcessor(allClasspathElts, toplevelClasspathElts));

        // 确定类路径元素的总体排序，将清单 Class-Path 条目中引用的 jar
        // 就地插入到排序中(如果它们尚未在类路径中更早地列出)
        final List<Classpath> classpathEltOrder = findClasspathOrder(toplevelClasspathElts);

        // 查找作为其他类路径元素的路径前缀的类路径元素，
        // 并为 JarClasspath 获取与模块相关的清单条目值
        preprocessClasspathsByType(classpathEltOrder,
                topLevelLog == null ? null : topLevelLog.log("Finding nested classpath elements"));

        // 将模块排序在传统类路径的类路径元素之前
        final LogNode classpathOrderLog = topLevelLog == null ? null
                : topLevelLog.log("Final classpath element order:");
        final int numElts = moduleOrder.size() + classpathEltOrder.size();
        final List<Classpath> finalClasspathEltOrder = new ArrayList<>(numElts);
        final List<String> finalClasspathEltOrderStrs = new ArrayList<>(numElts);
        int classpathOrderIdx = 0;
        for (final ModuleClasspath classpathElt : moduleOrder) {
            classpathElt.classpathElementIdx = classpathOrderIdx++;
            finalClasspathEltOrder.add(classpathElt);
            finalClasspathEltOrderStrs.add(classpathElt.toString());
            if (classpathOrderLog != null) {
                final ModuleRef moduleRef = classpathElt.getModuleRef();
                classpathOrderLog.log(moduleRef.toString());
            }
        }
        for (final Classpath classpathElt : classpathEltOrder) {
            classpathElt.classpathElementIdx = classpathOrderIdx++;
            finalClasspathEltOrder.add(classpathElt);
            finalClasspathEltOrderStrs.add(classpathElt.toString());
            if (classpathOrderLog != null) {
                classpathOrderLog.log(classpathElt.toString());
            }
        }

        // 并行地扫描每个类路径元素中的路径，将其与接受/拒绝条件进行比较
        processWorkUnits(finalClasspathEltOrder,
                topLevelLog == null ? null : topLevelLog.log("Scanning classpath elements"),
                new WorkUnitProcessor<Classpath>() {
                    @Override
                    public void processWorkUnit(final Classpath Classpath,
                                                final WorkQueue<Classpath> workQueueIgnored, final LogNode pathScanLog)
                            throws InterruptedException {
                        // 扫描类路径元素中的路径
                        Classpath.scanPaths(pathScanLog);
                    }
                });

        // 过滤掉不包含所需被接受路径的类路径元素
        List<Classpath> finalClasspathEltOrderFiltered = finalClasspathEltOrder;
        if (!ScanConfig.classpathElementResourcePathAcceptReject.acceptIsEmpty()) {
            finalClasspathEltOrderFiltered = new ArrayList<>(finalClasspathEltOrder.size());
            for (final Classpath Classpath : finalClasspathEltOrder) {
                if (Classpath.containsSpecificallyAcceptedClasspathResourcePath) {
                    finalClasspathEltOrderFiltered.add(Classpath);
                }
            }
        }

        if (performScan) {
            // 扫描类路径/模块，生成 ScanResult
            return performScan(finalClasspathEltOrderFiltered, finalClasspathEltOrderStrs, classpathFinder);
        } else {
            // 仅获取类路径 -- 返回一个占位 ScanResult 来持有类路径元素
            if (topLevelLog != null) {
                topLevelLog.log("Only returning classpath elements (not performing a scan)");
            }
            return new ScanResult(ScanConfig, finalClasspathEltOrderFiltered, finalClasspathEltOrderStrs,
                    classpathFinder, /* classNameToClassInfo = */ null, /* packageNameToPackageInfo = */ null,
                    /* moduleNameToModuleInfo = */ null, /* fileToLastModified = */ null, JarReader,
                    topLevelLog);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 确定唯一的有序类路径元素，并在必要时运行扫描以查找文件或类文件匹配项
     *
     * @return 扫描结果
     * @throws InterruptedException
     *             如果扫描被中断
     * @throws CancellationException
     *             如果扫描被取消
     * @throws ExecutionException
     *             如果工作线程抛出了未捕获的异常
     */
    @Override
    public ScanResult call() throws InterruptedException, CancellationException, ExecutionException {
        ScanResult scanResult = null;
        final long scanStart = System.currentTimeMillis();
        boolean removeTemporaryFilesAfterScan = ScanConfig.removeTemporaryFilesAfterScan;
        try {
            // 执行扫描
            scanResult = openClasspathsThenScan();

            // 在扫描完成后记录总时间，并刷新日志
            if (topLevelLog != null) {
                topLevelLog.log("~",
                        String.format("Total time: %.3f sec", (System.currentTimeMillis() - scanStart) * .001));
                topLevelLog.flush();
            }

            // 如果提供了 ScanResultProcessor，则调用它
            if (scanResultProcessor != null) {
                try {
                    scanResultProcessor.processScanResult(scanResult);
                } catch (final Exception e) {
                    scanResult.close();
                    throw new ExecutionException(e);
                }
                scanResult.close();
            }

        } catch (final Throwable e) {
            if (topLevelLog != null) {
                topLevelLog.log("~",
                        e instanceof InterruptedException || e instanceof CancellationException
                                ? "Scan interrupted or canceled"
                                : e instanceof ExecutionException || e instanceof RuntimeException
                                ? "Uncaught exception during scan"
                                : e.getMessage(),
                        InterruptionChecker.getCause(e));
                // 刷新日志
                topLevelLog.flush();
            }

            // 由于抛出了异常，移除临时文件
            removeTemporaryFilesAfterScan = true;

            // 停止所有正在运行的线程(应该不需要，线程应该已经处于静止状态)
            interruptionChecker.interrupt();

            if (failureHandler == null) {
                if (removeTemporaryFilesAfterScan) {
                    // 如果设置了 removeTemporaryFilesAfterScan，则移除临时文件并关闭资源、
                    // zip 文件和模块
                    JarReader.close(topLevelLog);
                }
                // 如果没有设置失败处理器，则重新抛出异常
                throw e;
            } else {
                // 否则，调用失败处理器
                try {
                    failureHandler.onFailure(e);
                } catch (final Exception f) {
                    // 失败处理器失败了
                    if (topLevelLog != null) {
                        topLevelLog.log("~", "The failure handler threw an exception:", f);
                        topLevelLog.flush();
                    }
                    // 使用抑制异常机制将两个异常组合在一起，
                    // 在失败处理器异常下方显示扫描异常
                    final ExecutionException failureHandlerException = new ExecutionException(
                            "Exception while calling failure handler", f);
                    failureHandlerException.addSuppressed(e);
                    if (removeTemporaryFilesAfterScan) {
                        // 如果设置了 removeTemporaryFilesAfterScan，则移除临时文件并关闭资源、
                        // zip 文件和模块
                        JarReader.close(topLevelLog);
                    }
                    // 抛出一个新的 ExecutionException(尽管这可能会被忽略，
                    // 因为任何带有 FailureHandler 的作业是通过 ExecutorService::execute 启动的，
                    // 而不是通过 ExecutorService::submit)
                    throw failureHandlerException;
                }
            }
        }

        if (removeTemporaryFilesAfterScan) {
            // 如果设置了 removeTemporaryFilesAfterScan，则移除临时文件并关闭资源、
            // zip 文件和模块
            JarReader.close(topLevelLog);
        }
        return scanResult;
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 用于将类路径元素加入队列以进行打开 */
    static public class ClasspathEntryWorkUnit {
        /** 类路径条目对象所来自的类加载器 */
        public final ClassLoader classLoader;
        /** 在父类路径元素中的顺序 */
        public final int classpathElementIdxWithinParent;
        /** 包根前缀(例如 "BOOT-INF/classes/") */
        public final String packageRootPrefix;
        /** 父类路径元素 */
        final Classpath parentClasspath;
        /** 类路径条目对象({@link String} 路径、{@link Path}、{@link URL} 或 {@link URI}) */
        public Object classpathEntryObj;

        /**
         * 构造函数
         *
         * @param classpathEntryObj
         *            原始类路径条目对象
         * @param classLoader
         *            类路径条目对象所来自的类加载器
         * @param parentClasspath
         *            父类路径元素
         * @param classpathElementIdxWithinParent
         *            在父类路径元素中的顺序
         * @param packageRootPrefix
         *            包根前缀
         */
        public ClasspathEntryWorkUnit(final Object classpathEntryObj, final ClassLoader classLoader,
                                      final Classpath parentClasspath, final int classpathElementIdxWithinParent,
                                      final String packageRootPrefix) {
            this.classpathEntryObj = classpathEntryObj;
            this.classLoader = classLoader;
            this.parentClasspath = parentClasspath;
            this.classpathElementIdxWithinParent = classpathElementIdxWithinParent;
            this.packageRootPrefix = packageRootPrefix;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 用于将类文件加入队列以进行扫描 */
    static public class ClassfileScanWorkUnit {

        /** 类路径元素 */
        private final Classpath Classpath;

        /** 类文件资源 */
        private final Resource classfileResource;

        /** 如果这是外部类，则为 true */
        private final boolean isExternalClass;

        /**
         * 构造函数
         *
         * @param Classpath
         *            类路径元素
         * @param classfileResource
         *            类文件资源
         * @param isExternalClass
         *            是否为外部类
         */
        public ClassfileScanWorkUnit(final Classpath Classpath, final Resource classfileResource,
                                     final boolean isExternalClass) {
            this.Classpath = Classpath;
            this.classfileResource = classfileResource;
            this.isExternalClass = isExternalClass;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 用于扫描类文件的 WorkUnitProcessor */
    private static class ClassfileScannerWorkUnitProcessor implements WorkUnitProcessor<ClassfileScanWorkUnit> {
        /** 扫描规格 */
        private final ScanConfig ScanConfig;

        /** 类路径顺序 */
        private final List<Classpath> classpathOrder;

        /**
         * 在扫描类路径元素中的路径时在类路径中找到的被接受类的名称
         */
        private final Set<String> acceptedClassNamesFound;

        /**
         * 被调度进行扩展扫描(扫描向上扩展到超类、接口和注解)的外部(未被接受的)类的名称
         */
        private final Set<String> classNamesScheduledForExtendedScanning = Collections
                .newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        /** 通过扫描类文件创建的有效 {@link ClassParser} 对象 */
        private final Queue<ClassParser> scannedClassfiles;

        /** 字符串驻留映射 */
        private final ConcurrentHashMap<String, String> stringInternMap = new ConcurrentHashMap<>();

        /**
         * 构造函数
         *
         * @param ScanConfig
         *            扫描规格
         * @param classpathOrder
         *            类路径顺序
         * @param acceptedClassNamesFound
         *            在扫描类路径元素中的路径时在类路径中找到的被接受类的名称
         * @param scannedClassfiles
         *            通过扫描类文件创建的 {@link ClassParser} 对象
         */
        public ClassfileScannerWorkUnitProcessor(final ScanConfig ScanConfig,
                                                 final List<Classpath> classpathOrder, final Set<String> acceptedClassNamesFound,
                                                 final Queue<ClassParser> scannedClassfiles) {
            this.ScanConfig = ScanConfig;
            this.classpathOrder = classpathOrder;
            this.acceptedClassNamesFound = acceptedClassNamesFound;
            this.scannedClassfiles = scannedClassfiles;
        }

        /**
         * 处理工作单元
         *
         * @param workUnit
         *            工作单元
         * @param workQueue
         *            工作队列
         * @param log
         *            日志
         * @throws InterruptedException
         *             中断异常
         */
        /* (non-Javadoc)
         * @see com.bingbaihanji.classgraph.concurrency.WorkQueue.WorkUnitProcessor#processWorkUnit(
         * java.lang.Object, com.bingbaihanji.classgraph.concurrency.WorkQueue)
         */
        @Override
        public void processWorkUnit(final ClassfileScanWorkUnit workUnit,
                                    final WorkQueue<ClassfileScanWorkUnit> workQueue, final LogNode log) throws InterruptedException {
            // 类文件扫描日志条目使用 Resource#scanLog 中存储的 LogNode，
            // 以内联方式列在对应资源路径被发现时添加的条目下方
            // 这允许路径扫描和类文件扫描日志合并为单个树，
            // 而不是让它们显示为两个独立的树
            final LogNode subLog = workUnit.classfileResource.scanLog == null ? null
                    : workUnit.classfileResource.scanLog.log(workUnit.classfileResource.getPath(),
                    "Parsing ClassParser");

            try {
                // 解析类文件二进制格式，创建 ClassParser 对象
                final ClassParser ClassParser = new ClassParser(workUnit.Classpath, classpathOrder,
                        acceptedClassNamesFound, classNamesScheduledForExtendedScanning,
                        workUnit.classfileResource.getPath(), workUnit.classfileResource, workUnit.isExternalClass,
                        stringInternMap, workQueue, ScanConfig, subLog);

                // 将类文件加入队列以进行链接
                scannedClassfiles.add(ClassParser);

                if (subLog != null) {
                    subLog.addElapsedTime();
                }
            } catch (final SkipClassException e) {
                if (subLog != null) {
                    subLog.log(workUnit.classfileResource.getPath(), "Skipping ClassParser: " + e.getMessage());
                    subLog.addElapsedTime();
                }
            } catch (final ClassfileFormatException e) {
                if (subLog != null) {
                    subLog.log(workUnit.classfileResource.getPath(), "Invalid ClassParser: " + e.getMessage());
                    subLog.addElapsedTime();
                }
            } catch (final IOException e) {
                if (subLog != null) {
                    subLog.log(workUnit.classfileResource.getPath(), "Could not read ClassParser: " + e);
                    subLog.addElapsedTime();
                }
            } catch (final Exception e) {
                if (subLog != null) {
                    subLog.log(workUnit.classfileResource.getPath(), "Could not read ClassParser", e);
                    subLog.addElapsedTime();
                }
            }
        }
    }
}
