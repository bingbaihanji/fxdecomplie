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
package com.bingbaihanji.classgraph.classpath;

import com.bingbaihanji.classgraph.classpath.handler.HandlerRegistry;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.scan.ClassGraph.ClasspathFilter;
import com.bingbaihanji.classgraph.scan.ClassGraph.ClasspathURLFilter;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.FastPathResolver;
import com.bingbaihanji.classgraph.util.FileUtils;
import com.bingbaihanji.classgraph.util.JarUtils;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.File;
import java.io.IOError;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 用于查找唯一有序类路径元素的类 */
public class ClasspathOrder {
    /** 自动包根的后缀，例如 "!/BOOT-INF/classes" */
    private static final List<String> AUTOMATIC_PACKAGE_ROOT_SUFFIXES = new ArrayList<>();
    /** 匹配 URL 方案(必须包含至少两个字符，否则可能是 Windows 驱动器号) */
    private static final Pattern schemeMatcher = Pattern.compile("^[a-zA-Z][a-zA-Z+\\-.]+:");

    static {
        for (final String prefix : HandlerRegistry.AUTOMATIC_PACKAGE_ROOT_PREFIXES) {
            AUTOMATIC_PACKAGE_ROOT_SUFFIXES.add("!/" + prefix.substring(0, prefix.length() - 1));
        }
    }

    /** 扫描规格 */
    private final ScanConfig ScanConfig;
    /** 唯一的类路径条目 */
    private final Set<String> classpathEntryUniqueResolvedPaths = new HashSet<>();
    /** 类路径顺序键是 {@link String} 或 {@link URL} 的实例 */
    private final List<ClasspathEntry> order = new ArrayList<>();
    public ReflectionUtils reflectionUtils;

    /**
     * 构造函数
     *
     * @param ScanConfig
     *            扫描规格
     */
    ClasspathOrder(final ScanConfig ScanConfig, final ReflectionUtils reflectionUtils) {
        this.ScanConfig = ScanConfig;
        this.reflectionUtils = reflectionUtils;
    }

    /**
     * 获取类路径元素的顺序，已去重且有序
     *
     * @return 类路径顺序
     */
    public List<ClasspathEntry> getOrder() {
        return order;
    }

    /**
     * 获取唯一的类路径条目字符串
     *
     * @return 类路径条目字符串
     */
    public Set<String> getClasspathEntryUniqueResolvedPaths() {
        return classpathEntryUniqueResolvedPaths;
    }

    /**
     * 测试类路径元素是否被用户过滤掉
     *
     * @param classpathElementURL
     *            类路径元素 URL
     * @param classpathElementPath
     *            类路径元素路径
     * @return 如果未被过滤掉则返回 true
     */
    private boolean filter(final URL classpathElementURL, final String classpathElementPath) {
        if (ScanConfig.classpathElementFilters != null) {
            for (final Object filterObj : ScanConfig.classpathElementFilters) {
                if ((classpathElementURL != null && filterObj instanceof ClasspathURLFilter
                        && !((ClasspathURLFilter) filterObj).includeClasspath(classpathElementURL))
                        || (classpathElementPath != null && filterObj instanceof ClasspathFilter
                        && !((ClasspathFilter) filterObj)
                        .includeClasspath(classpathElementPath))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 添加系统类路径条目
     *
     * @param pathEntry
     *            系统类路径条目 -- 路径字符串应该已经通过
     *            FastPathResolver.resolve(FileUtils.currDirPath(), path) 处理过
     * @param classLoader
     *            类加载器
     * @return 如果添加成功且唯一则返回 true
     */
    boolean addSystemClasspathEntry(final String pathEntry, final ClassLoader classLoader) {
        if (classpathEntryUniqueResolvedPaths.add(pathEntry)) {
            order.add(new ClasspathEntry(pathEntry, classLoader));
            return true;
        }
        return false;
    }

    /**
     * 添加类路径条目
     *
     * @param pathElement
     *            类路径元素的 {@link String} 路径、{@link File}、{@link Path}、{@link URL} 或 {@link URI}
     * @param pathElementStr
     *            字符串格式的路径元素
     * @param classLoader
     *            类加载器
     * @param ScanConfig
     *            扫描规格
     * @return 如果添加成功且唯一则返回 true
     */
    private boolean addClasspathEntry(final Object pathElement, final String pathElementStr,
                                      final ClassLoader classLoader, final ScanConfig ScanConfig) {
        // 检查类路径元素路径是否以自动包根结尾如果是，则将其剥离以
        // 消除重复，因为自动包根会被自动检测到(#435)
        String pathElementStrWithoutSuffix = pathElementStr;
        boolean hasSuffix = false;
        for (final String suffix : AUTOMATIC_PACKAGE_ROOT_SUFFIXES) {
            if (pathElementStr.endsWith(suffix)) {
                // 剥离自动包根后缀
                pathElementStrWithoutSuffix = pathElementStr.substring(0,
                        pathElementStr.length() - suffix.length());
                hasSuffix = true;
                break;
            }
        }
        if (pathElement instanceof URL || pathElement instanceof URI || pathElement instanceof Path
                || pathElement instanceof File) {
            Object pathElementWithoutSuffix = pathElement;
            if (hasSuffix) {
                try {
                    pathElementWithoutSuffix = pathElement instanceof URL ? new URL(pathElementStrWithoutSuffix)
                            : pathElement instanceof URI ? new URI(pathElementStrWithoutSuffix)
                            : pathElement instanceof Path ? Paths.get(pathElementStrWithoutSuffix)
                            // 对于 File，仅使用路径字符串
                            : pathElementStrWithoutSuffix;
                } catch (MalformedURLException | URISyntaxException | InvalidPathException e) {
                    try {
                        pathElementWithoutSuffix = pathElement instanceof URL
                                ? new URL("file:" + pathElementStrWithoutSuffix)
                                : pathElement instanceof URI ? new URI("file:" + pathElementStrWithoutSuffix)
                                : pathElementStrWithoutSuffix;
                    } catch (MalformedURLException | URISyntaxException | InvalidPathException e2) {
                        return false;
                    }
                }
            }
            // 去重类路径元素
            if (classpathEntryUniqueResolvedPaths.add(pathElementStrWithoutSuffix)) {
                // 在类路径顺序中记录类路径元素
                order.add(new ClasspathEntry(pathElementWithoutSuffix, classLoader));
                return true;
            }
        } else {
            final String pathElementStrResolved = FastPathResolver.resolve(FileUtils.currDirPath(),
                    pathElementStrWithoutSuffix);
            if (ScanConfig.overrideClasspath == null //
                    && (SystemJarFinder.getJreLibOrExtJars().contains(pathElementStrResolved)
                    || pathElementStrResolved.equals(SystemJarFinder.getJreRtJarPath()))) {
                // JRE lib 和 ext JAR 会单独处理，因此如果它们由系统类加载器返回，
                // 则作为重复项拒绝
                return false;
            }
            if (classpathEntryUniqueResolvedPaths.add(pathElementStrResolved)) {
                order.add(new ClasspathEntry(pathElementStrResolved, classLoader));
                return true;
            }
        }
        return false;
    }

    /**
     * 添加相对于基础文件的类路径元素可由 ClassLoaderHandler 调用以添加其已知的类路径元素
     * ClassLoader 将按顺序被调用
     *
     * @param pathElement
     *            类路径元素的 {@link String} 路径、{@link URL} 或 {@link URI}，或是某个可以调用其
     *            {@link Object#toString()} 方法来获取类路径元素的对象
     * @param classLoader
     *            获取此 classpath 元素的 ClassLoader
     * @param ScanConfig
     *            扫描规格
     * @param log
     *            在详细模式下记录日志时使用的 LogNode 实例
     * @return 如果 pathElement 不为 null、不为空、存在且未被用户指定的条件过滤掉，则返回 true
     *         (并添加类路径元素)，否则返回 false
     */
    public boolean addClasspathEntry(final Object pathElement, final ClassLoader classLoader,
                                     final ScanConfig ScanConfig, final LogNode log) {
        if (pathElement == null) {
            return false;
        }
        String pathElementStr;
        if (pathElement instanceof Path) {
            try {
                // Path 对象在调用 .toString() 之前必须先转换为 URI，否则 scheme 会丢失
                pathElementStr = ((Path) pathElement).toUri().toString();
                // Windows 路径("C:\x\y")会被 Path.toUri().toString() 编码为 "file:///C:/x/y"，
                // 但 Paths.get() 无法处理 "///C:/x/y" 形式的路径
                if (pathElementStr.startsWith("file:///")) {
                    pathElementStr = ((Path) pathElement).toFile().toString();
                }
            } catch (IOError | SecurityException e) {
                pathElementStr = pathElement.toString();
            }
        } else {
            pathElementStr = pathElement.toString();
        }
        pathElementStr = FastPathResolver.resolve(FileUtils.currDirPath(), pathElementStr);
        if (pathElementStr.isEmpty()) {
            return false;
        }
        URL pathElementURL = null;
        boolean hasWildcardSuffix = false;
        // 回退方案 -- 对路径元素调用 toString()，然后尝试转换为 URL
        if (pathElementStr.endsWith("/*") || pathElementStr.endsWith("\\*")) {
            hasWildcardSuffix = true;
            pathElementStr = pathElementStr.substring(0, pathElementStr.length() - 2);
            // 将 pathElementURL 保持为 null，以便下面可以处理通配符
        } else if ("*".equals(pathElementStr)) {
            hasWildcardSuffix = true;
            pathElementStr = "";
            // 将 pathElementURL 保持为 null，以便下面可以处理通配符
        } else {
            final Matcher m1 = schemeMatcher.matcher(pathElementStr);
            if (m1.find()) {
                // 路径元素字符串是带有 `[jar:]file:` 以外 scheme 的 URL，因此需要实际
                // 解析 URL，因为 scheme 可能是自定义 scheme
                try {
                    pathElementURL = pathElement instanceof URL ? (URL) pathElement
                            : pathElement instanceof URI ? ((URI) pathElement).toURL()
                            : pathElement instanceof Path ? ((Path) pathElement).toUri().toURL()
                            : pathElement instanceof File ? ((File) pathElement).toURI().toURL()
                            : null;
                } catch (final MalformedURLException | IllegalArgumentException | IOError | SecurityException e2) {
                    // 穿透处理
                }
                if (pathElementURL == null) {
                    // 对 URL 中的百分号字符进行转义(#255)
                    final String urlStr = pathElementStr.replace("%", "%25");
                    try {
                        pathElementURL = new URL(urlStr);
                    } catch (final MalformedURLException e) {
                        try {
                            pathElementURL = new File(urlStr).toURI().toURL();
                        } catch (final MalformedURLException | IllegalArgumentException | IOError
                                       | SecurityException e1) {
                            // 最终回退方案 -- 尝试直接使用原始字符串作为 URL
                            try {
                                pathElementURL = new URL(pathElementStr);
                            } catch (final MalformedURLException e2) {
                                // 穿透处理
                            }
                        }
                    }
                }
                if (pathElementURL == null) {
                    if (log != null) {
                        log.log("Failed to convert classpath element to URL: " + pathElement);
                    }
                }
            }
        }
        if (pathElementURL != null || pathElement instanceof URI || pathElement instanceof File
                || pathElement instanceof Path) {
            if (!filter(pathElementURL, pathElementStr)) {
                if (log != null) {
                    log.log("Classpath element did not match filter criterion, skipping: " + pathElementStr);
                }
                return false;
            }
            // 对于 URL 对象，使用对象本身(以便稍后可以进行 URL scheme 处理)；
            // 对于 URI 和 Path 对象，转换为 URL；对于 File 对象，使用 toString 结果(路径)
            final Object classpathElementObj;
            classpathElementObj = pathElement instanceof File ? pathElementStr
                    : pathElementURL != null ? pathElementURL : pathElement;
            if (addClasspathEntry(classpathElementObj, pathElementStr, classLoader, ScanConfig)) {
                if (log != null) {
                    log.log("Found classpath element: " + pathElementStr);
                }
                return true;
            } else {
                if (log != null) {
                    log.log("Ignoring duplicate classpath element: " + pathElementStr);
                }
                return false;
            }
        }
        if (hasWildcardSuffix) {
            // 具有通配符路径元素(自 JDK 6 起允许用于本地类路径)
            // 应用类路径元素过滤器(如果有的话)
            final String baseDirPath = pathElementStr;
            final String baseDirPathResolved = FastPathResolver.resolve(FileUtils.currDirPath(), baseDirPath);
            if (!filter(pathElementURL, baseDirPath)
                    || (!baseDirPathResolved.equals(baseDirPath) && !filter(pathElementURL, baseDirPathResolved))) {
                if (log != null) {
                    log.log("Classpath element did not match filter criterion, skipping: " + pathElementStr);
                }
                return false;
            }

            // 检查 "/*" 后缀之前的路径是否为目录
            final File baseDir = new File(baseDirPathResolved);
            if (!baseDir.exists()) {
                if (log != null) {
                    log.log("Directory does not exist for wildcard classpath element: " + pathElementStr);
                }
                return false;
            }
            if (!FileUtils.canRead(baseDir)) {
                if (log != null) {
                    log.log("Cannot read directory for wildcard classpath element: " + pathElementStr);
                }
                return false;
            }
            if (!baseDir.isDirectory()) {
                if (log != null) {
                    log.log("Wildcard is appended to something other than a directory: " + pathElementStr);
                }
                return false;
            }

            // 将请求目录中的所有元素添加到类路径
            final LogNode dirLog = log == null ? null
                    : log.log("Adding classpath elements from wildcarded directory: " + pathElementStr);
            final File[] baseDirFiles = baseDir.listFiles();
            if (baseDirFiles != null) {
                for (final File fileInDir : baseDirFiles) {
                    final String name = fileInDir.getName();
                    if (!".".equals(name) && !"..".equals(name)) {
                        // 将每个目录条目作为类路径元素添加
                        final String fileInDirPath = fileInDir.getPath();
                        final String fileInDirPathResolved = FastPathResolver.resolve(FileUtils.currDirPath(),
                                fileInDirPath);
                        if (addClasspathEntry(fileInDirPathResolved, fileInDirPathResolved, classLoader,
                                ScanConfig)) {
                            if (dirLog != null) {
                                dirLog.log("Found classpath element: " + fileInDirPath
                                        + (fileInDirPath.equals(fileInDirPathResolved) ? ""
                                        : " -> " + fileInDirPathResolved));
                            }
                        } else {
                            if (dirLog != null) {
                                dirLog.log("Ignoring duplicate classpath element: " + fileInDirPath
                                        + (fileInDirPath.equals(fileInDirPathResolved) ? ""
                                        : " -> " + fileInDirPathResolved));
                            }
                        }
                    }
                }
                return true;
            } else {
                return false;
            }
        } else {
            // 非通配符(标准)类路径元素
            if (pathElementStr.indexOf('*') >= 0) {
                if (log != null) {
                    log.log("Wildcard classpath elements can only end with a suffix of \"/*\", "
                            + "can't use globs elsewhere in the path: " + pathElementStr);
                }
                return false;
            }
            final String pathElementResolved = FastPathResolver.resolve(FileUtils.currDirPath(), pathElementStr);
            if (!filter(pathElementURL, pathElementStr) || (!pathElementResolved.equals(pathElementStr)
                    && !filter(pathElementURL, pathElementResolved))) {
                if (log != null) {
                    log.log("Classpath element did not match filter criterion, skipping: " + pathElementStr
                            + (pathElementStr.equals(pathElementResolved) ? "" : " -> " + pathElementResolved));
                }
                return false;
            }
            if (pathElementResolved.startsWith("//")) {
                // 处理 Windows UNC 路径(#705)
                // File 直接支持 UNC 路径：
                // https://wiki.eclipse.org/Eclipse/UNC_Paths#Programming_with_UNC_paths
                try {
                    final File file = new File(pathElementResolved);
                    if (addClasspathEntry(file, pathElementResolved, classLoader, ScanConfig)) {
                        if (log != null) {
                            log.log("Found classpath element: " + file
                                    + (pathElementStr.equals(pathElementResolved) ? ""
                                    : " -> " + pathElementResolved));
                        }
                        return true;
                    } else {
                        if (log != null) {
                            log.log("Ignoring duplicate classpath element: " + pathElementStr
                                    + (pathElementStr.equals(pathElementResolved) ? ""
                                    : " -> " + pathElementResolved));
                        }
                        return false;
                    }
                } catch (final Exception e) {
                    // 穿透处理
                }
            }
            if (addClasspathEntry(pathElementResolved, pathElementResolved, classLoader, ScanConfig)) {
                if (log != null) {
                    log.log("Found classpath element: " + pathElementStr
                            + (pathElementStr.equals(pathElementResolved) ? "" : " -> " + pathElementResolved));
                }
                return true;
            } else {
                if (log != null) {
                    log.log("Ignoring duplicate classpath element: " + pathElementStr
                            + (pathElementStr.equals(pathElementResolved) ? "" : " -> " + pathElementResolved));
                }
                return false;
            }
        }
    }

    /**
     * 添加由系统路径分隔符分隔的类路径条目
     *
     * @param overrideClasspath
     *            一个由 {@link String}、{@link URL}、{@link URI} 或 {@link File} 对象组成的路径列表
     * @param classLoader
     *            获取此 classpath 的 ClassLoader
     * @param ScanConfig
     *            扫描规格
     * @param log
     *            在详细模式下记录日志时使用的 LogNode 实例
     * @return 如果 pathElement 不为 null 或空，则返回 true(并添加类路径元素)，否则返回 false
     */
    public boolean addClasspathEntries(final List<Object> overrideClasspath, final ClassLoader classLoader,
                                       final ScanConfig ScanConfig, final LogNode log) {
        if (overrideClasspath == null || overrideClasspath.isEmpty()) {
            return false;
        } else {
            for (final Object pathElement : overrideClasspath) {
                addClasspathEntry(pathElement, classLoader, ScanConfig, log);
            }
            return true;
        }
    }

    /**
     * 添加由系统路径分隔符分隔的类路径条目
     *
     * @param pathStr
     *            包含 URL 或路径的分隔字符串形式的类路径
     * @param classLoader
     *            获取此 classpath 的 ClassLoader
     * @param ScanConfig
     *            扫描规格
     * @param log
     *            在详细模式下记录日志时使用的 LogNode 实例
     * @return 如果 pathElement 不为 null 或空，则返回 true(并添加类路径元素)，否则返回 false
     */
    public boolean addClasspathPathStr(final String pathStr, final ClassLoader classLoader, final ScanConfig ScanConfig,
                                       final LogNode log) {
        if (pathStr == null || pathStr.isEmpty()) {
            return false;
        } else {
            final String[] parts = JarUtils.smartPathSplit(pathStr, ScanConfig);
            if (parts.length == 0) {
                return false;
            } else {
                for (final String pathElement : parts) {
                    addClasspathEntry(pathElement, classLoader, ScanConfig, log);
                }
                return true;
            }
        }
    }

    /**
     * 从通过反射获取的对象中添加类路径条目该对象可以是 {@link URL}、{@link URI}、
     * {@link File}、{@link Path} 或 {@link String}(包含单个类路径元素路径，或包含由
     * File.pathSeparator 分隔的多个路径)、List 或其他 Iterable，或数组对象对于 Iterable
     * 和数组，元素可以是任何其 {@code toString()} 方法返回路径或 URL 字符串的类型(包括
     * {@code URL} 和 {@code Path} 类型)
     *
     * @param pathObject
     *            包含一个或多个类路径字符串的对象
     * @param classLoader
     *            获取此 classpath 的 ClassLoader
     * @param ScanConfig
     *            扫描规格
     * @param log
     *            在详细模式下记录日志时使用的 LogNode 实例
     * @return 如果 pathElement 不为 null 或空，则返回 true(并添加类路径元素)，否则返回 false
     */
    public boolean addClasspathEntryObject(final Object pathObject, final ClassLoader classLoader,
                                           final ScanConfig ScanConfig, final LogNode log) {
        boolean valid = false;
        if (pathObject != null) {
            if (pathObject instanceof URL || pathObject instanceof URI || pathObject instanceof Path
                    || pathObject instanceof File) {
                valid |= addClasspathEntry(pathObject, classLoader, ScanConfig, log);
            } else if (pathObject instanceof Iterable) {
                for (final Object elt : (Iterable<?>) pathObject) {
                    valid |= addClasspathEntryObject(elt, classLoader, ScanConfig, log);
                }
            } else {
                final Class<?> valClass = pathObject.getClass();
                if (valClass.isArray()) {
                    for (int j = 0, n = Array.getLength(pathObject); j < n; j++) {
                        final Object elt = Array.get(pathObject, j);
                        valid |= addClasspathEntryObject(elt, classLoader, ScanConfig, log);
                    }
                } else {
                    // 作为最终回退方案，简单地调用 toString() 来处理 String 对象，
                    // 或尝试处理其他任何类型
                    valid |= addClasspathPathStr(pathObject.toString(), classLoader, ScanConfig, log);
                }
            }
        }
        return valid;
    }

    /**
     * 一个类路径元素及其来源的 {@link ClassLoader}
     */
    public static class ClasspathEntry {
        /** 类路径条目对象({@link String} 路径、{@link Path}、{@link URL} 或 {@link URI}) */
        public final Object classpathEntryObj;

        /** 获取此 classpath 元素的类加载器 */
        public final ClassLoader classLoader;

        /**
         * 构造函数
         *
         * @param classpathEntryObj
         *            类路径条目对象({@link String} 或 {@link URL} 或 {@link Path})
         * @param classLoader
         *            获取此 classpath 元素的类加载器
         */
        public ClasspathEntry(final Object classpathEntryObj, final ClassLoader classLoader) {
            this.classpathEntryObj = classpathEntryObj;
            this.classLoader = classLoader;
        }

        @Override
        public int hashCode() {
            return Objects.hash(classpathEntryObj);
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            } else if (!(obj instanceof ClasspathEntry)) {
                return false;
            }
            final ClasspathEntry other = (ClasspathEntry) obj;
            return Objects.equals(this.classpathEntryObj, other.classpathEntryObj);
        }

        @Override
        public String toString() {
            return classpathEntryObj + " [" + classLoader + "]";
        }
    }
}
