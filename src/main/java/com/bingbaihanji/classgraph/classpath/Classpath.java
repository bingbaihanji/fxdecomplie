 
package com.bingbaihanji.classgraph.classpath;

import com.bingbaihanji.classgraph.resource.Resource;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.scan.ScanConfig.ScanConfigPathMatch;
import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.scan.Scanner.ClasspathEntryWorkUnit;
import com.bingbaihanji.classgraph.util.FileUtils;
import com.bingbaihanji.classgraph.util.JarUtils;
import com.bingbaihanji.classgraph.util.LogNode;
import com.bingbaihanji.classgraph.util.WorkQueue;

import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** 类路径元素(类路径上的目录或 jar 文件) */
public abstract class Classpath implements Comparable<Classpath> {
    /**
     * 在此类路径元素中找到的被接受且未被拒绝的资源(仅由一个线程写入，因此不需要使用并发列表)
     */
    public final List<Resource> acceptedResources = new ArrayList<>();
    /** 如果 scanFiles 为 true，则从 File 到上次修改时间戳的映射 */
    public final Map<File, Long> fileToLastModified = new ConcurrentHashMap<>();
    /** 确保类路径元素只被扫描一次的标志 */
    protected final AtomicBoolean scanned = new AtomicBoolean(false);
    /**
     * 在父类路径元素中的类路径元素索引(例如，对于通过清单文件中的 Class-Path 条目添加的类路径元素)
     * 初始设置为 -1，以防同一个 Classpath 在类路径中出现两次(作为不同父 Classpath 的子元素)
     */
    final int classpathElementIdxWithinParent;
    /** 扫描规格 */
    final ScanConfig ScanConfig;
    /**
     * 在此类路径元素中找到的所有被接受且未被拒绝的 class 文件列表(仅由一个线程写入，因此不需要使用并发列表)
     */
    public List<Resource> acceptedClassfileResources = new ArrayList<>();
    /** 类路径元素在类路径或模块路径中的索引 */
    public int classpathElementIdx;
    /**
     * 如果非空，包含嵌套在此类路径元素内部的任何类路径元素根的已解析路径列表(扫描应在嵌套类路径元素根处停止，
     * 否则该子树将被多次扫描)注意：仅包含已解析路径的嵌套部分(已移除公共前缀)还包括尾部 '/'，
     * 因为只需要捕获嵌套的目录类路径元素(嵌套 jar 不需要捕获，因为我们不会扫描 jar 内嵌 jar，
     * 除非内部 jar 被显式列在类路径上)
     */
    public List<String> nestedClasspathRootPrefixes;
    /**
     * 如果尝试打开此类路径元素时发生异常(例如损坏的 ZipFile)，则为 true
     */
    public boolean skipClasspath;
    /** 如果类路径元素包含一个被明确接受的资源路径，则为 true */
    public boolean containsSpecificallyAcceptedClasspathResourcePath;
    /**
     * 子类路径元素，按子类路径元素在其所在清单文件的 Class-Path 条目中的顺序(或文件在排序后的 lib 目录条目中的位置)
     * 作为键
     */
    public Collection<Classpath> childClasspaths = new ConcurrentLinkedQueue<>();
    /**
     * 从 {@code module-info.class} 模块描述符中获取的模块名称(如果类路径元素根中存在该描述符)
     */
    public String moduleNameFromModuleDescriptor;
    /** 获取此类路径元素的类加载器 */
    protected ClassLoader classLoader;
    /** jar 文件或 Path 中的包根路径 */
    protected String packageRootPrefix;
    /** 此类路径元素所属的 ScanResult */
    protected ScanResult scanResult;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 一个类路径元素
     *
     * @param workUnit
     *            工作单元
     * @param ScanConfig
     *            扫描规格
     */
    Classpath(final ClasspathEntryWorkUnit workUnit, final ScanConfig ScanConfig) {
        this.packageRootPrefix = workUnit.packageRootPrefix;
        this.classpathElementIdxWithinParent = workUnit.classpathElementIdxWithinParent;
        this.classLoader = workUnit.classLoader;
        this.ScanConfig = ScanConfig;
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 用于在扫描完成后设置 ScanResult */
    public void setScanResult(final ScanResult scanResult) {
        this.scanResult = scanResult;
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 按 classpathElementIdxWithinParent 升序排序 */
    @Override
    public int compareTo(final Classpath other) {
        return this.classpathElementIdxWithinParent - other.classpathElementIdxWithinParent;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此类路径元素所属的 ClassLoader
     *
     * @return 类加载器
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * 获取匹配的 class 文件数量
     *
     * @return class 文件匹配数量
     */
    int getNumClassfileMatches() {
        return acceptedClassfileResources == null ? 0 : acceptedClassfileResources.size();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 检查 relativePath 是否符合 classpathElementResourcePathAcceptReject 的接受/拒绝条件
     *
     * @param relativePath
     *            相对路径
     * @param log
     *            日志
     * @return 如果路径应该被扫描，则返回 true
     */
    protected boolean checkResourcePathAcceptReject(final String relativePath, final LogNode log) {
        // 根据文件资源路径接受/拒绝类路径元素
        if (!ScanConfig.classpathElementResourcePathAcceptReject.acceptAndRejectAreEmpty()) {
            if (ScanConfig.classpathElementResourcePathAcceptReject.isRejected(relativePath)) {
                if (log != null) {
                    log.log("Reached rejected classpath element resource path, stopping scanning: " + relativePath);
                }
                return false;
            }
            if (ScanConfig.classpathElementResourcePathAcceptReject.isSpecificallyAccepted(relativePath)) {
                if (log != null) {
                    log.log("Reached specifically accepted classpath element resource path: " + relativePath);
                }
                containsSpecificallyAcceptedClasspathResourcePath = true;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 在此类路径资源中应用相对路径屏蔽 -- 移除在较早类路径元素中已找到的相对路径
     *
     * @param classpathIdx
     *            类路径索引
     * @param classpathRelativePathsFound
     *            已发现的类路径相对路径集合
     * @param log
     *            日志
     */
    public void maskClassfiles(final int classpathIdx, final Set<String> classpathRelativePathsFound, final LogNode log) {
        // 查找在类路径/模块路径中出现多次的相对路径
        // 通常重复的相对路径仅出现在类路径/模块路径元素之间，而非内部，
        // 但实际上 zip 文件中的路径并没有唯一性限制，事实上
        // 现实中的 zip 文件确实可能多次包含具有完全相同路径的 class 文件，
        // 例如：xmlbeans-2.6.0.jar!org/apache/xmlbeans/xml/stream/Location.class
        final List<Resource> acceptedClassfileResourcesFiltered = new ArrayList<>(
                acceptedClassfileResources.size());
        boolean foundMasked = false;
        for (final Resource res : acceptedClassfileResources) {
            final String pathRelativeToPackageRoot = res.getPath();
            // 不要屏蔽 module-info.class 或 package-info.class，这些会为每个模块/包读取，
            // 它们不会产生 ClassInfo 对象，因此不会创建重复的 ClassInfo 对象，
            // 即使它们被多次遇到对于模块或包上的注解，
            // 会被合并到相应的 ModuleInfo/PackageInfo 对象中
            if (!"module-info.class".equals(pathRelativeToPackageRoot)
                    && !"package-info.class".equals(pathRelativeToPackageRoot)
                    && !pathRelativeToPackageRoot.endsWith("/package-info.class")
                    // 检查 pathRelativeToPackageRoot 是否之前已见过
                    && !classpathRelativePathsFound.add(pathRelativeToPackageRoot)) {
                // 此相对路径已多次遇到；
                // 屏蔽第二次及之后出现的该路径
                foundMasked = true;
                if (log != null) {
                    log.log(String.format("%06d-1", classpathIdx), "Ignoring duplicate (masked) class "
                            + JarUtils.classfilePathToClassName(pathRelativeToPackageRoot) + " found at " + res);
                }
            } else {
                acceptedClassfileResourcesFiltered.add(res);
            }
        }
        if (foundMasked) {
            // 移除被屏蔽(重复)的路径注意：这将并发集合替换为非并发集合，
            // 但这是扫描期间最后一次更改该集合，且此方法
            // 由单个线程运行
            acceptedClassfileResources = acceptedClassfileResourcesFiltered;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 添加扫描过程中发现的资源
     *
     * @param resource
     *            资源
     * @param parentMatchStatus
     *            父级匹配状态
     * @param isClassfileOnly
     *            如果为 true，则仅将资源添加到 class 文件资源列表，而不添加到非 class 文件资源列表
     * @param log
     *            日志
     */
    protected void addAcceptedResource(final Resource resource, final ScanConfigPathMatch parentMatchStatus,
                                       final boolean isClassfileOnly, final LogNode log) {
        final String path = resource.getPath();
        final boolean isClassFile = FileUtils.isClassfile(path);
        boolean isAccepted = false;
        if (isClassFile) {
            // 检查 class 文件扫描是否已启用，且该 class 文件未被明确拒绝
            if (ScanConfig.enableClassInfo && !ScanConfig.classfilePathAcceptReject.isRejected(path)) {
                // ClassInfo 已启用，且发现了一个被接受的 class 文件
                acceptedClassfileResources.add(resource);
                isAccepted = true;
            }
        } else {
            // 如果在被接受的目录中发现资源，则始终接受
            isAccepted = true;
        }

        if (!isClassfileOnly) {
            // 将资源添加到已接受资源列表中，无论其为 class 文件资源还是非 class 文件资源
            acceptedResources.add(resource);
        }

        // 如果启用了日志记录，且只要 class 文件扫描未被禁用，且这不是
        // 被拒绝的 class 文件
        if (log != null && isAccepted) {
            final String type = isClassFile ? "ClassParser" : "resource";
            String logStr;
            switch (parentMatchStatus) {
                case HAS_ACCEPTED_PATH_PREFIX:
                    logStr = "Found " + type + " within subpackage of accepted package: ";
                    break;
                case AT_ACCEPTED_PATH:
                    logStr = "Found " + type + " within accepted package: ";
                    break;
                case AT_ACCEPTED_CLASS_PACKAGE:
                    logStr = "Found specifically-accepted " + type + ": ";
                    break;
                default:
                    logStr = "Found accepted " + type + ": ";
                    break;
            }
            // 在日志条目的排序键前加上 "0:file:"，使文件条目在目录条目之前出现(针对
            // DirClasspath 类路径元素)
            resource.scanLog = log.log("0:" + path,
                    logStr + path + (path.equals(resource.getPathRelativeToClasspath()) ? ""
                            : " ; full path: " + resource.getPathRelativeToClasspath()));
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 扫描完成后由 scanPaths() 调用
     *
     * @param log
     *            日志
     */
    protected void finishScanPaths(final LogNode log) {
        if (log != null) {
            if (acceptedResources.isEmpty() && acceptedClassfileResources.isEmpty()) {
                log.log(ScanConfig.enableClassInfo ? "No accepted classfiles or resources found"
                        : "ClassParser scanning is disabled, and no accepted resources found");
            } else if (acceptedResources.isEmpty()) {
                log.log("No accepted resources found");
            } else if (acceptedClassfileResources.isEmpty()) {
                log.log(ScanConfig.enableClassInfo ? "No accepted classfiles found"
                        : "ClassParser scanning is disabled");
            }
        }
        if (log != null) {
            log.addElapsedTime();
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 按类路径/模块路径顺序将条目写入日志
     *
     * @param classpathElementIdx
     *            类路径元素索引
     * @param msg
     *            日志消息
     * @param log
     *            日志
     * @return 新的 {@link LogNode}
     */
    protected LogNode log(final int classpathElementIdx, final String msg, final LogNode log) {
        return log.log(String.format("%07d", classpathElementIdx), msg);
    }

    /**
     * 按类路径/模块路径顺序将条目写入日志
     *
     * @param classpathElementIdx
     *            类路径元素索引
     * @param msg
     *            日志消息
     * @param t
     *            抛出的异常
     * @param log
     *            日志
     * @return 新的 {@link LogNode}
     */
    protected LogNode log(final int classpathElementIdx, final String msg, final Throwable t, final LogNode log) {
        return log.log(String.format("%07d", classpathElementIdx), msg, t);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 判断此类路径元素是否有效如果无效，则设置 skipClasspath对于 {@link JarClasspath}，
     * 可能还会打开或提取内部 jar，并读取 jar 文件清单以查找 Class-Path 条目如果发现嵌套 jar 或 Class-Path 条目，
     * 它们将被添加到工作队列中此方法每个类路径元素仅运行一次，且由单个线程执行
     *
     * @param workQueue
     *            工作队列
     * @param log
     *            日志
     * @throws InterruptedException
     *             如果线程在尝试打开类路径元素时被中断
     */
    public abstract void open(final WorkQueue<ClasspathEntryWorkUnit> workQueue, final LogNode log)
            throws InterruptedException;

    /**
     * 扫描类路径元素中的路径，根据接受/拒绝条件创建被接受且未被拒绝的资源和 class 文件的 Resource 对象
     *
     * @param log
     *            日志
     */
    public abstract void scanPaths(final LogNode log);

    /**
     * 获取给定相对路径的 {@link Resource}
     *
     * @param relativePath
     *            要返回的 {@link Resource} 的相对路径路径应已通过调用
     *            {@link FileUtils#sanitizeEntryPath(String, boolean)} 进行清理，或提供的路径已经是清理过的
     *            (即不以 "/" 开头或结尾，不包含 "/../" 或 "/./" 等)
     * @return 给定相对路径的 {@link Resource}，如果 relativePath 在此类路径元素中不存在则返回 null
     */
    public abstract Resource getResource(final String relativePath);

    /**
     * 获取此类路径元素的 URI
     *
     * @return 类路径元素的 URI
     */
    public abstract URI getURI();

    /**
     * 获取此类路径元素的 URI，以及此 jar 文件内任何自动嵌套包前缀(例如 "spring-boot.jar/BOOT-INF/classes")的 URI
     *
     * @return 类路径元素的 URI
     */
    public abstract List<URI> getAllURIs();

    /**
     * 获取此类路径元素的文件，如果这是一个带有 "jrt:" URI 的模块，则返回 null
     *
     * @return 类路径元素的文件
     */
    public abstract File getFile();

    /**
     * 获取此类路径元素的模块名称，如果没有模块名称则返回 null
     *
     * @return 模块名称
     */
    public abstract String getModuleName();
}
