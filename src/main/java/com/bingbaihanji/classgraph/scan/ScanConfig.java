 
package com.bingbaihanji.classgraph.scan;

import com.bingbaihanji.classgraph.classpath.ModulePathInfo;
import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.scan.ClassGraph.ClasspathFilter;
import com.bingbaihanji.classgraph.scan.ClassGraph.ClasspathURLFilter;
import com.bingbaihanji.classgraph.scan.Filter.FilterLeafname;
import com.bingbaihanji.classgraph.scan.Filter.FilterPrefix;
import com.bingbaihanji.classgraph.scan.Filter.FilterWholeString;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * 扫描规范
 */
public class ScanConfig {
    /** 包接受/拒绝条件(分隔符 '.') */
    public FilterWholeString packageAcceptReject = new FilterWholeString('.');

    /** 包前缀接受/拒绝条件，用于递归扫描(分隔符 '.'，以 '.' 结尾) */
    public FilterPrefix packagePrefixAcceptReject = new FilterPrefix('.');

    /** 路径接受/拒绝条件(分隔符 '/') */
    public FilterWholeString pathAcceptReject = new FilterWholeString('/');

    /** 路径前缀接受/拒绝条件，用于递归扫描(分隔符 '/'，以 '/' 结尾) */
    public FilterPrefix pathPrefixAcceptReject = new FilterPrefix('/');

    /** 类接受/拒绝条件(完全限定类名，分隔符 '.') */
    public FilterWholeString classAcceptReject = new FilterWholeString('.');

    /** 类文件接受/拒绝条件(类文件路径，分隔符 '/'，以 ".class" 结尾) */
    public FilterWholeString classfilePathAcceptReject = new FilterWholeString('/');

    /** 包含受条件限制的类的包(分隔符 '.') */
    public FilterWholeString classPackageAcceptReject = new FilterWholeString('.');

    /** 包含受条件限制的类的路径(分隔符 '/') */
    public FilterWholeString classPackagePathAcceptReject = new FilterWholeString('/');

    /** 模块接受/拒绝条件(分隔符 '.') */
    public FilterWholeString moduleAcceptReject = new FilterWholeString('.');

    /** Jar 接受/拒绝条件(仅叶子名称，以 ".jar" 结尾) */
    public FilterLeafname jarAcceptReject = new FilterLeafname('/');

    /** 类路径元素资源路径接受/拒绝条件 */
    public FilterWholeString classpathElementResourcePathAcceptReject = //
            new FilterWholeString('/');

    /** lib/ext jar 接受/拒绝条件(仅叶子名称，以 ".jar" 结尾) */
    public FilterLeafname libOrExtJarAcceptReject = new FilterLeafname('/');

    // -------------------------------------------------------------------------------------------------------------

    /** 如果为 true，扫描 jar 文件 */
    public boolean scanJars = true;

    /** 如果为 true，扫描嵌套 jar 文件(jar 文件中的 jar 文件) */
    public boolean scanNestedJars = true;

    /** 如果为 true，扫描目录 */
    public boolean scanDirs = true;

    /** 如果为 true，扫描模块 */
    public boolean scanModules = true;

    /** 如果为 true，扫描类文件字节码，生成 {@link ClassInfo} 对象 */
    public boolean enableClassInfo;

    /**
     * 如果为 true，在扫描期间启用字段信息保存此信息可以通过
     * {@link ClassInfo#getFieldInfo()} 获取默认情况下，为提高效率不扫描字段信息
     */
    public boolean enableFieldInfo;

    /**
     * 如果为 true，在扫描期间启用方法信息保存此信息可以通过
     * {@link ClassInfo#getMethodInfo()} 获取默认情况下，为提高效率不扫描方法信息
     */
    public boolean enableMethodInfo;

    /**
     * 如果为 true，在扫描期间启用注解信息(用于类、字段、方法或方法参数注解)
     * 的保存此信息可以通过 {@link ClassInfo#getAnnotationInfo()} 等获取
     * 默认情况下，为提高效率不扫描注解信息
     */
    public boolean enableAnnotationInfo;

    /** 启用在 ClassInfo 对象中存储静态 final 字段的常量初始化值 */
    public boolean enableStaticFinalFieldConstantInitializerValues;

    /** 如果为 true，启用类间依赖关系的确定 */
    public boolean enableInterClassDependencies;

    /**
     * 如果为 true，允许外部类(不在接受包中的类)在 ScanResult 中返回，前提是
     * 它们被某个已接受的类直接引用，作为超类、实现的接口或注解
     * 默认情况下禁用
     */
    public boolean enableExternalClasses;

    /**
     * 如果为 true，应扫描系统 jar 文件(rt.jar)和系统包与模块(java.*、jre.* 等)
     */
    public boolean enableSystemJarsAndModules;

    /**
     * 如果为 true，忽略类可见性如果为 false，类必须为 public 才能被扫描
     */
    public boolean ignoreClassVisibility;

    /**
     * 如果为 true，忽略字段可见性如果为 false，字段必须为 public 才能被扫描
     */
    public boolean ignoreFieldVisibility;

    /**
     * 如果为 true，忽略方法可见性如果为 false，方法必须为 public 才能被扫描
     */
    public boolean ignoreMethodVisibility;

    /**
     * 如果为 true，不扫描运行时不可见的注解(仅扫描 RetentionPolicy.RUNTIME 的注解)
     */
    public boolean disableRuntimeInvisibleAnnotations;

    /**
     * 如果为 true，当类具有外部类形式的超类、实现的接口或注解时，
     * 这些类也会被扫描(尽管这会稍微减慢扫描速度，但目前没有禁用此功能的 API，
     * 因为禁用它可能导致问题 -- 参见 #261)
     */
    public boolean extendScanningUpwardsToExternalClasses = true;

    /**
     * 类路径元素中允许的 URL 方案(不包括可选的 "jar:" 前缀和/或 "file:"，
     * 这些是自动允许的)
     */
    public Set<String> allowedURLSchemes;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 如果非 null，指定在上下文类加载器之后应搜索的手动添加的类加载器
     */
    public transient List<ClassLoader> addedClassLoaders;

    /**
     * 如果非 null，将搜索此列表中的 ClassLoader，而不是可见的/上下文的 ClassLoader
     * 特别地，这会导致 ClassGraph 忽略 java.class.path 系统属性
     */
    public transient List<ClassLoader> overrideClassLoaders;

    /**
     * 如果非 null，指定在可见的 ModuleLayer 之后应搜索的手动添加的 ModuleLayer
     */
    public transient List<Object> addedModuleLayers;

    /**
     * 如果非 null，将搜索此列表中的 ModuleLayer，而不是可见的 ModuleLayer
     */
    public transient List<Object> overrideModuleLayers;

    /**
     * 如果非 null，指定用于覆盖默认类路径的类路径元素列表(String、{@link URL} 或 {@link URI})
     */
    public List<Object> overrideClasspath;

    /** 如果非 null，要应用于类路径元素的过滤器操作列表 */
    public transient List<Object> classpathElementFilters;

    /** 加载类时是否初始化它们 */
    public boolean initializeLoadedClasses;

    /**
     * 如果为 true，扫描期间提取的嵌套 jar 文件(jar 文件中的 jar 文件)将在扫描完成后
     * 从其临时目录(例如 /tmp/ClassGraph-8JX2u4w)中移除如果为 false，临时文件
     * 由 {@link ScanResult} 终结器移除，或在 JVM 退出时移除
     */
    public boolean removeTemporaryFilesAfterScan;

    /** 如果为 true，不从父类加载器获取路径 */
    public boolean ignoreParentClassLoaders;

    /** 如果为 true，不扫描作为其他模块层父级的模块层 */
    public boolean ignoreParentModuleLayers;

    /** 命令行模块路径参数 */
    public ModulePathInfo modulePathInfo = new ModulePathInfo();

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 外部 jar 中已压缩(即压缩存储而非仅存储)的内部(嵌套)jar 在被解压读取其条目时，
     * 可暂存在 RAM 支持的 {@link ByteBuffer} 中的最大大小；超过此大小时则必须溢出到磁盘
     * (注意，为了读取嵌套 jar 而需要将其解压到 RAM 或磁盘的情况很少见，因为通常将一个 jar 文件
     * 添加到另一个 jar 文件时会存储内部 jar 而非压缩它，因为压缩 jar 文件通常不会产生
     * 进一步的压缩收益如果内部 jar 是存储的而非压缩的，则其 zip 条目可以使用 ClassGraph
     * 自己的 zipfile 中央目录解析器直接读取，该解析器可以使用文件切片直接从存储的嵌套 jar
     * 中提取条目)
     *
     * <p>
     * 这也是从 {@code http://} 或 {@code https://} 类路径 {@link URL} 下载到 RAM 的
     * jar 的最大大小一旦从 {@link URL} 的 {@link InputStream} 读取了这么多字节，
     * RAM 内容就会溢出到磁盘上的临时文件，其余内容则下载到临时文件
     * (这种情况也很少见，因为通常没有 {@code http://} 或 {@code https://} 类路径条目)
     *
     * <p>
     * 默认值：64MB(即尽可能避免写入磁盘)如果发生上述任一罕见情况，
     * 设置较低的 RAM 最值将减少 ClassGraph 的内存使用量
     */
    public int maxBufferedJarRAMSize = 64 * 1024 * 1024;

    /** 如果为 true，使用 {@link MappedByteBuffer} 而非 {@link FileChannel} API 来访问文件内容 */
    public boolean enableMemoryMapping;

    /** 如果为 true，查找资源的所有多版本发布版本 */
    public boolean enableMultiReleaseVersions;

    // -------------------------------------------------------------------------------------------------------------

    /** 反序列化构造函数 */
    public ScanConfig() {
        // 有意留空
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 如果参数是 ModuleLayer 或其子类则返回 true
     *
     * @param moduleLayer
     *            模块层
     * @return 如果参数是 ModuleLayer 或其子类则返回 true
     */
    private static boolean isModuleLayer(final Object moduleLayer) {
        if (moduleLayer == null) {
            throw new IllegalArgumentException("ModuleLayer references must not be null");
        }
        for (Class<?> currClass = moduleLayer.getClass(); currClass != null; currClass = currClass
                .getSuperclass()) {
            if ("java.lang.ModuleLayer".equals(currClass.getName())) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 对前缀进行排序以确保正确的接受/拒绝评估(参见 Issue #167) */
    public void sortPrefixes() {
        for (final Field field : ScanConfig.class.getDeclaredFields()) {
            if (Filter.class.isAssignableFrom(field.getType())) {
                try {
                    ((Filter) field.get(this)).sortPrefixes();
                } catch (final ReflectiveOperationException e) {
                    throw new RuntimeException("Field is not accessible: " + field, e);
                }
            }
        }
    }

    /**
     * 用自定义路径覆盖自动检测到的类路径可以在单独的调用中指定多个元素，
     * 并且即使只调用此方法一次，默认类路径也将被覆盖，从而仅扫描提供的类路径，
     * 即导致 ClassLoader 以及 java.class.path 系统属性被忽略
     *
     * @param overrideClasspath
     *            要作为覆盖添加到默认类路径的类路径元素
     */
    public void addClasspathOverride(final Object overrideClasspath) {
        if (this.overrideClasspath == null) {
            this.overrideClasspath = new ArrayList<>();
        }
        if (overrideClasspath instanceof ClassLoader) {
            throw new IllegalArgumentException(
                    "Need to pass ClassLoader instances to overrideClassLoaders, not overrideClasspath");
        }
        this.overrideClasspath
                .add(overrideClasspath instanceof String || overrideClasspath instanceof URL
                        || overrideClasspath instanceof URI ? overrideClasspath
                        : overrideClasspath.toString());
    }

    /**
     * 添加一个类路径元素过滤器提供的 {@link ClasspathFilter} 或
     * {@link ClasspathURLFilter} 应在传入的路径字符串或 {@link URL} 是应扫描的路径时
     * 返回 true
     *
     * @param filterLambda
     *            要应用于所有发现的类路径元素的类路径元素过滤器，以决定哪些应被扫描
     */
    public void filterClasspaths(final Object filterLambda) {
        if (!(filterLambda instanceof ClasspathFilter
                || filterLambda instanceof ClasspathURLFilter)) {
            throw new IllegalArgumentException();
        }
        if (this.classpathElementFilters == null) {
            this.classpathElementFilters = new ArrayList<>(2);
        }
        this.classpathElementFilters.add(filterLambda);
    }

    /**
     * 将一个 ClassLoader 添加到要扫描的 ClassLoader 列表中(仅在未调用 overrideClasspath() 时有效)
     *
     * @param classLoader
     *            要添加的类加载器
     */
    public void addClassLoader(final ClassLoader classLoader) {
        if (this.addedClassLoaders == null) {
            this.addedClassLoaders = new ArrayList<>();
        }
        if (classLoader != null) {
            this.addedClassLoaders.add(classLoader);
        }
    }

    /**
     * 允许在类路径元素中使用指定的 URL 方案
     *
     * @param scheme
     *            方案，例如 "http"
     */
    public void enableURLScheme(final String scheme) {
        if (scheme == null || scheme.length() < 2) {
            throw new IllegalArgumentException("URL schemes must contain at least two characters");
        }
        if (allowedURLSchemes == null) {
            allowedURLSchemes = new HashSet<>();
        }
        allowedURLSchemes.add(scheme.toLowerCase());
    }

    /**
     * 完全覆盖要扫描的 ClassLoader 列表(仅在未调用 overrideClasspath() 时有效)
     * 导致 java.class.path 系统属性被忽略
     *
     * @param overrideClassLoaders
     *            用于覆盖默认上下文类加载器的类加载器
     */
    public void overrideClassLoaders(final ClassLoader... overrideClassLoaders) {
        if (overrideClassLoaders.length == 0) {
            throw new IllegalArgumentException("At least one override ClassLoader must be provided");
        }
        this.addedClassLoaders = null;
        this.overrideClassLoaders = new ArrayList<>();
        for (final ClassLoader classLoader : overrideClassLoaders) {
            if (classLoader != null) {
                this.overrideClassLoaders.add(classLoader);
            }
        }
    }

    /**
     * 将一个 ModuleLayer 添加到要扫描的 ModuleLayer 列表中如果您定义了自己的 ModuleLayer，
     * 但扫描代码未在该自定义 ModuleLayer 中运行，请使用此方法
     *
     * <p>
     * 如果在 {@link #overrideModuleLayers(Object...)} 之前调用，此调用将被忽略
     *
     * @param moduleLayer
     *            要扫描的额外 ModuleLayer(参数类型为 {@link Object} 是为了向后兼容
     *            JDK 7 和 JDK 8，但参数应为 ModuleLayer 类型)
     */
    public void addModuleLayer(final Object moduleLayer) {
        if (!isModuleLayer(moduleLayer)) {
            throw new IllegalArgumentException("moduleLayer must be of type java.lang.ModuleLayer");
        }
        if (this.addedModuleLayers == null) {
            this.addedModuleLayers = new ArrayList<>();
        }
        this.addedModuleLayers.add(moduleLayer);
    }

    /**
     * 完全覆盖(并忽略)可见的 ModuleLayer，转而扫描请求的 ModuleLayer
     *
     * <p>
     * 如果调用了 overrideClasspath()，此调用将被忽略
     *
     * @param overrideModuleLayers
     *            要扫描的 ModuleLayer，替代自动检测到的 ModuleLayer
     *            (参数类型为 {@link Object}[] 是为了向后兼容 JDK 7 和 JDK 8，
     *            但参数应为 ModuleLayer[] 类型)
     */
    public void overrideModuleLayers(final Object... overrideModuleLayers) {
        if (overrideModuleLayers == null) {
            throw new IllegalArgumentException("overrideModuleLayers cannot be null");
        }
        if (overrideModuleLayers.length == 0) {
            throw new IllegalArgumentException("At least one override ModuleLayer must be provided");
        }
        for (final Object moduleLayer : overrideModuleLayers) {
            if (!isModuleLayer(moduleLayer)) {
                throw new IllegalArgumentException("moduleLayer must be of type java.lang.ModuleLayer");
            }
        }
        this.addedModuleLayers = null;
        this.overrideModuleLayers = new ArrayList<>();
        Collections.addAll(this.overrideModuleLayers, overrideModuleLayers);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 如果给定的目录路径是被拒绝路径的后代，或是已接受路径的祖先或后代，
     * 则返回 true路径应以 "/" 结尾
     *
     * @param relativePath
     *            相对路径
     * @return {@link ScanConfigPathMatch}
     */
    public ScanConfigPathMatch dirAcceptMatchStatus(final String relativePath) {
        // 在被拒绝的路径中
        if (pathAcceptReject.isRejected(relativePath) || pathPrefixAcceptReject.isRejected(relativePath)) {
            // 此路径的某个前缀被拒绝
            return ScanConfigPathMatch.HAS_REJECTED_PATH_PREFIX;
        }

        if (pathAcceptReject.acceptIsEmpty() && classPackagePathAcceptReject.acceptIsEmpty()) {
            // 如果没有已接受的包，则根包被接受
            return relativePath.isEmpty() || "/".equals(relativePath) ? ScanConfigPathMatch.AT_ACCEPTED_PATH
                    : ScanConfigPathMatch.HAS_ACCEPTED_PATH_PREFIX;
        }

        // 位于已接受的路径
        if (pathAcceptReject.isSpecificallyAcceptedAndNotRejected(relativePath)) {
            // 到达一个已接受的路径
            return ScanConfigPathMatch.AT_ACCEPTED_PATH;
        }
        if (classPackagePathAcceptReject.isSpecificallyAcceptedAndNotRejected(relativePath)) {
            // 到达一个包含明确接受的类的包
            return ScanConfigPathMatch.AT_ACCEPTED_CLASS_PACKAGE;
        }

        // 已接受路径的后代
        if (pathPrefixAcceptReject.isSpecificallyAccepted(relativePath)) {
            // 路径前缀匹配接受列表中的某一项
            return ScanConfigPathMatch.HAS_ACCEPTED_PATH_PREFIX;
        }

        // 已接受路径的祖先
        if (
            // 默认包始终是已接受路径的祖先(需要继续递归)
                "/".equals(relativePath)
                        // relativePath 是某个已接受路径的祖先(前缀)
                        || pathAcceptReject.acceptHasPrefix(relativePath)
                        // relativePath 是某个已接受类的父目录的祖先(前缀)
                        || classfilePathAcceptReject.acceptHasPrefix(relativePath)) {
            return ScanConfigPathMatch.ANCESTOR_OF_ACCEPTED_PATH;
        }

        // 不在已接受的路径中
        return ScanConfigPathMatch.NOT_WITHIN_ACCEPTED_PATH;
    }

    /**
     * 如果给定的相对路径(类文件名，包含 ".class")匹配某个明确接受
     * (且未被拒绝)的类文件的相对路径，则返回 true
     *
     * @param relativePath
     *            相对路径
     * @return 如果给定的相对路径(类文件名，包含 ".class")匹配某个明确接受
     *         (且未被拒绝)的类文件的相对路径则返回 true
     */
    public boolean classfileIsSpecificallyAccepted(final String relativePath) {
        return classfilePathAcceptReject.isSpecificallyAcceptedAndNotRejected(relativePath);
    }

    /**
     * 如果类被明确拒绝或位于被拒绝的包中，则返回 true
     *
     * @param className
     *            类名
     * @return 如果类被明确拒绝或位于被拒绝的包中则返回 true
     */
    public boolean classOrPackageIsRejected(final String className) {
        return classAcceptReject.isRejected(className) || packagePrefixAcceptReject.isRejected(className);
    }

    /**
     * 写入日志
     *
     * @param log
     *            要写入的 {@link LogNode}
     */
    public void log(final LogNode log) {
        if (log != null) {
            final LogNode ScanConfigLog = log.log("ScanConfig:");
            for (final Field field : ScanConfig.class.getDeclaredFields()) {
                try {
                    ScanConfigLog.log(field.getName() + ": " + field.get(this));
                } catch (final ReflectiveOperationException e) {
                    // 忽略
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 表示路径是被拒绝路径的后代，还是已接受路径的祖先或后代
     */
    public enum ScanConfigPathMatch {
        /** 路径以被拒绝路径前缀开头(或就是该前缀) */
        HAS_REJECTED_PATH_PREFIX,
        /** 路径以已接受路径前缀开头 */
        HAS_ACCEPTED_PATH_PREFIX,
        /** 路径已被接受 */
        AT_ACCEPTED_PATH,
        /** 路径是已接受路径的祖先 */
        ANCESTOR_OF_ACCEPTED_PATH,
        /** 路径是明确接受的类的包路径 */
        AT_ACCEPTED_CLASS_PACKAGE,
        /** 路径未被接受也未被拒绝 */
        NOT_WITHIN_ACCEPTED_PATH
    }
}
