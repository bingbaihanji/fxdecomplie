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

import com.bingbaihanji.classgraph.bytecode.ClassFileReader;
import com.bingbaihanji.classgraph.classpath.handler.HandlerRegistry;
import com.bingbaihanji.classgraph.resource.*;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.scan.ScanConfig.ScanConfigPathMatch;
import com.bingbaihanji.classgraph.scan.Scanner.ClasspathEntryWorkUnit;
import com.bingbaihanji.classgraph.util.*;
import com.bingbaihanji.classgraph.util.SingletonMap.NewInstanceException;
import com.bingbaihanji.classgraph.util.SingletonMap.NullSingletonException;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一个 zip/jar 文件类路径元素 */
public class JarClasspath extends Classpath {
    /**
     * 此 zip 文件的路径字符串、{@link URL}、{@link URI} 或 {@link Path} 的 {@link String} 表示形式
     */
    private final String rawPath;
    /** 从相对路径到未被拒绝的 zip 条目的 {@link Resource} 映射 */
    private final ConcurrentHashMap<String, Resource> relativePathToResource = new ConcurrentHashMap<>();
    /** 在此 zip 文件内作为路径前缀找到的所有自动包根前缀的列表 */
    private final Set<String> strippedAutomaticPackageRootPrefixes = new HashSet<>();
    /** 嵌套 jar 处理器 */
    private final JarReader JarReader;
    /** 此类路径元素的逻辑 zip 文件 */
    public LogicalZipFile logicalZipFile;
    /**
     * 从 {@code Automatic-Module-Name} 清单属性中获取的模块名称(如果类路径元素根中存在该属性)
     */
    public String moduleNameFromManifestFile;
    /** jar 文件的规范化路径，如果嵌套则以 "!/" 分隔，不包含任何包根路径 */
    private String zipFilePath;
    /** 从 jar 文件名派生的自动模块名称 */
    private String derivedAutomaticModuleName;

    /**
     * 一个 jar 文件类路径元素
     *
     * @param workUnit
     *            工作单元
     * @param JarReader
     *            嵌套 jar 处理器
     * @param ScanConfig
     *            扫描规格
     */
    public JarClasspath(final ClasspathEntryWorkUnit workUnit, final JarReader JarReader,
                        final ScanConfig ScanConfig) {
        super(workUnit, ScanConfig);
        final Object rawPathObj = workUnit.classpathEntryObj;

        // 将原始路径对象(Path、URL 或 URI)转换为字符串
        // 任何需要的 URL/URI 解析都在 JarReader 中完成
        String rawPath = null;
        if (rawPathObj instanceof Path) {
            // Path.toString 不包含 URI 协议 => 转换为 URI 以便 toString 正常工作
            try {
                rawPath = ((Path) rawPathObj).toUri().toString();
            } catch (final IOError | SecurityException e) {
                // 继续执行
            }
        }
        if (rawPath == null) {
            rawPath = rawPathObj.toString();
        }
        this.rawPath = rawPath;
        this.zipFilePath = rawPath; // 可能在调用 open() 时更改
        this.JarReader = JarReader;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.Classpath#open(
     * com.bingbaihanji.classgraph.concurrency.WorkQueue, com.bingbaihanji.classgraph.utils.LogNode)
     */
    @Override
    public void open(final WorkQueue<ClasspathEntryWorkUnit> workQueue, final LogNode log) throws InterruptedException {
        if (!ScanConfig.scanJars) {
            if (log != null) {
                log(classpathElementIdx, "Skipping classpath element, since jar scanning is disabled: " + rawPath,
                        log);
            }
            skipClasspath = true;
            return;
        }
        final LogNode subLog = log == null ? null : log(classpathElementIdx, "Opening jar: " + rawPath, log);
        final int plingIdx = rawPath.indexOf('!');
        final String outermostZipFilePathResolved = FastPathResolver.resolve(FileUtils.currDirPath(),
                plingIdx < 0 ? rawPath : rawPath.substring(0, plingIdx));
        if (!ScanConfig.jarAcceptReject.isAcceptedAndNotRejected(outermostZipFilePathResolved)) {
            if (subLog != null) {
                subLog.log("Skipping jarfile that is rejected or not accepted: " + rawPath);
            }
            skipClasspath = true;
            return;
        }

        try {
            // 获取最内层嵌套 jar 文件的 LogicalZipFile
            Entry<LogicalZipFile, String> logicalZipFileAndPackageRoot;
            try {
                logicalZipFileAndPackageRoot = JarReader.nestedPathToLogicalZipFileAndPackageRootMap
                        .get(rawPath, subLog);
            } catch (final NullSingletonException | NewInstanceException e) {
                // 通常在第一次调用 .get() 失败后，第二次及后续尝试时抛出，
                // 或者 newInstance() 抛出了异常
                throw new IOException("Could not get logical zipfile " + rawPath + " : "
                        + (e.getCause() == null ? e : e.getCause()));
            }
            logicalZipFile = logicalZipFileAndPackageRoot.getKey();
            if (logicalZipFile == null) {
                // 不应该发生，但这样可以让 lgtm 静态分析满意
                throw new IOException("Logical zipfile was null");
            }

            // 获取逻辑 zip 文件的规范化路径
            zipFilePath = FastPathResolver.resolve(FileUtils.currDirPath(), logicalZipFile.getPath());

            // 获取 jar 文件的包根路径
            final String packageRoot = logicalZipFileAndPackageRoot.getValue();
            if (!packageRoot.isEmpty()) {
                packageRootPrefix = packageRoot + "/";
            }
        } catch (final IOException | IllegalArgumentException e) {
            if (subLog != null) {
                subLog.log("Could not open jarfile " + rawPath + " : " + e);
            }
            skipClasspath = true;
            return;
        }

        if (!ScanConfig.enableSystemJarsAndModules && logicalZipFile.isJREJar) {
            // 发现一个被拒绝的 JRE jar，该 jar 未被 ClasspathFinder 中的 rt.jar 过滤捕获
            // (isJREJar 值是通过检测 jar 清单文件中的 JRE 头设置的)
            if (subLog != null) {
                subLog.log("Ignoring JRE jar: " + rawPath);
            }
            skipClasspath = true;
            return;
        }

        if (!logicalZipFile.isAcceptedAndNotRejected(ScanConfig.jarAcceptReject)) {
            if (subLog != null) {
                subLog.log("Skipping jarfile that is rejected or not accepted: " + rawPath);
            }
            skipClasspath = true;
            return;
        }

        // 自动将任何嵌套的 "lib/" 目录添加到类路径中，因为并非所有类加载器都将其
        // 作为类路径元素返回
        int childClasspathEntryIdx = 0;
        if (ScanConfig.scanNestedJars) {
            for (final FastZipEntry zipEntry : logicalZipFile.entries) {
                for (final String libDirPrefix : HandlerRegistry.AUTOMATIC_LIB_DIR_PREFIXES) {
                    // 即使给出了包根路径(例如 BOOT-INF/classes)，仍然在 lib/ 等目录中查找 jar 文件
                    if (zipEntry.entryNameUnversioned.startsWith(libDirPrefix)
                            && zipEntry.entryNameUnversioned.endsWith(".jar")) {
                        final String entryPath = zipEntry.getPath();
                        if (subLog != null) {
                            subLog.log("Found nested lib jar: " + entryPath);
                        }
                        workQueue.addWorkUnit(new ClasspathEntryWorkUnit(entryPath, getClassLoader(),
                                /* parentClasspath = */ this,
                                /* orderWithinParentClasspath = */
                                childClasspathEntryIdx++, /* packageRootPrefix = */ ""));
                        break;
                    }
                }
            }
        }

        // 不要添加与此类路径元素相同或重复的子类路径元素
        final Set<String> scheduledChildClasspaths = new HashSet<>();
        scheduledChildClasspaths.add(rawPath);

        // 从清单文件 Class-Path 条目中获取的值创建子类路径元素，
        // 将路径解析为相对于包含该 jar 文件的目录或父 jar 文件的路径
        if (logicalZipFile.classPathManifestEntryValue != null) {
            // 获取逻辑 zip 文件在祖父切片中的父目录，
            // 例如，对于 zip 文件切片路径 "/path/to/jar1.jar!/lib/jar2.jar"，结果是 "lib"，
            // 对于 "/path/to/jar1.jar"，结果是 "/path/to"，如果 jar 在顶层目录则结果为 ""
            final String jarParentDir = FileUtils
                    .getParentDirPath(logicalZipFile.getPathWithinParentZipFileSlice());
            // 将清单文件 "Class-Path" 条目中的路径添加到类路径中，
            // 将路径解析为相对于父目录或 jar 的路径
            for (final String childClassPathEltPathRelative : logicalZipFile.classPathManifestEntryValue
                    .split(" ")) {
                if (!childClassPathEltPathRelative.isEmpty()) {
                    // 将 Class-Path 条目解析为相对于包含目录的路径
                    final String childClassPathEltPath = FastPathResolver.resolve(jarParentDir,
                            childClassPathEltPathRelative);
                    // 如果这是一个嵌套 jar，则在前面添加外部 jar 前缀
                    final ZipFileSlice parentZipFileSlice = logicalZipFile.getParentZipFileSlice();
                    final String childClassPathEltPathWithPrefix = parentZipFileSlice == null
                            ? childClassPathEltPath
                            : parentZipFileSlice.getPath() + (childClassPathEltPath.startsWith("/") ? "!" : "!/")
                            + childClassPathEltPath;
                    // 只添加子类路径元素一次
                    if (scheduledChildClasspaths.add(childClassPathEltPathWithPrefix)) {
                        // 安排子类路径元素进行扫描
                        workQueue.addWorkUnit( //
                                new ClasspathEntryWorkUnit(childClassPathEltPathWithPrefix, getClassLoader(),
                                        /* parentClasspath = */ this,
                                        /* orderWithinParentClasspath = */
                                        childClasspathEntryIdx++, /* packageRootPrefix = */ ""));
                    }
                }
            }
        }
        // 将 OSGi bundle jar 清单文件 "Bundle-ClassPath" 条目中的路径添加到类路径中，
        // 将路径解析为相对于 jar 文件根目录的路径
        if (logicalZipFile.bundleClassPathManifestEntryValue != null) {
            final String zipFilePathPrefix = zipFilePath + "!/";
            // Class-Path 以 " " 分隔，但 Bundle-ClassPath 以 "," 分隔
            for (String childBundlePath : logicalZipFile.bundleClassPathManifestEntryValue.split(",")) {
                // 假设 Bundle-ClassPath 路径必须相对于 jar 文件根目录给出
                while (childBundlePath.startsWith("/")) {
                    childBundlePath = childBundlePath.substring(1);
                }
                // 目前 "." 相对于子类路径条目的位置被忽略(
                // Bundle-ClassPath 路径被当作 "." 在第一个位置，因为子
                // 类路径条目总是在父类路径条目之后添加到类路径中，
                // 它们是从父类路径条目获取的)
                if (!childBundlePath.isEmpty() && !".".equals(childBundlePath)) {
                    // 在 jar 内解析 Bundle-ClassPath 条目
                    final String childClassPathEltPath = zipFilePathPrefix + FileUtils.sanitizeEntryPath(
                            childBundlePath, /* removeInitialSlash = */ true, /* removeFinalSlash = */ true);
                    // 只添加子类路径元素一次
                    if (scheduledChildClasspaths.add(childClassPathEltPath)) {
                        // 安排子类路径元素进行扫描
                        workQueue.addWorkUnit(new ClasspathEntryWorkUnit(childClassPathEltPath, getClassLoader(),
                                /* parentClasspath = */ this,
                                /* orderWithinParentClasspath = */
                                childClasspathEntryIdx++, /* packageRootPrefix = */ ""));
                    }
                }
            }
        }
    }

    /**
     * 为扫描路径时发现的资源或 class 文件创建新的 {@link Resource} 对象
     *
     * @param zipEntry
     *            zip 条目
     * @param pathRelativeToPackageRoot
     *            相对于包根的路径
     * @return 资源对象
     */
    private Resource newResource(final FastZipEntry zipEntry, final String pathRelativeToPackageRoot) {
        return new Resource(this, zipEntry.uncompressedSize) {
            /** 如果资源已打开，则为 true */
            private final AtomicBoolean isOpen = new AtomicBoolean();

            /**
             * 已移除包根前缀和/或任何 Spring Boot 前缀("BOOT-INF/classes/" 或 "WEB-INF/classes/")的路径
             */
            @Override
            public String getPath() {
                return pathRelativeToPackageRoot;
            }

            @Override
            public String getPathRelativeToClasspath() {
                if (zipEntry.entryName.startsWith(packageRootPrefix)) {
                    return zipEntry.entryName.substring(packageRootPrefix.length());
                } else {
                    return zipEntry.entryName;
                }
            }

            @Override
            public long getLastModified() {
                return zipEntry.getLastModifiedTimeMillis();
            }

            @Override
            public Set<PosixFilePermission> getPosixFilePermissions() {
                final int fileAttributes = zipEntry.fileAttributes;
                Set<PosixFilePermission> perms;
                if (fileAttributes == 0) {
                    perms = null;
                } else {
                    perms = new HashSet<>();
                    if ((fileAttributes & 0400) > 0) {
                        perms.add(PosixFilePermission.OWNER_READ);
                    }
                    if ((fileAttributes & 0200) > 0) {
                        perms.add(PosixFilePermission.OWNER_WRITE);
                    }
                    if ((fileAttributes & 0100) > 0) {
                        perms.add(PosixFilePermission.OWNER_EXECUTE);
                    }
                    if ((fileAttributes & 0040) > 0) {
                        perms.add(PosixFilePermission.GROUP_READ);
                    }
                    if ((fileAttributes & 0020) > 0) {
                        perms.add(PosixFilePermission.GROUP_WRITE);
                    }
                    if ((fileAttributes & 0010) > 0) {
                        perms.add(PosixFilePermission.GROUP_EXECUTE);
                    }
                    if ((fileAttributes & 0004) > 0) {
                        perms.add(PosixFilePermission.OTHERS_READ);
                    }
                    if ((fileAttributes & 0002) > 0) {
                        perms.add(PosixFilePermission.OTHERS_WRITE);
                    }
                    if ((fileAttributes & 0001) > 0) {
                        perms.add(PosixFilePermission.OTHERS_EXECUTE);
                    }
                }
                return perms;
            }

            protected void checkCanOpen() {
                if (skipClasspath) {
                    // 不应该发生
                    throw new IllegalStateException("Classpath element could not be opened");
                }
                if (isOpen.getAndSet(true)) {
                    throw new IllegalStateException(
                            "Resource is already open -- cannot open it again without first calling close()");
                }
                if (scanResult != null && scanResult.isClosed()) {
                    throw new IllegalStateException("Cannot open a resource after the ScanResult is closed");
                }
            }

            @Override
            public ClassFileReader openClassfile() throws IOException {
                return new ClassFileReader(open(), this);
            }

            @Override
            public InputStream open() throws IOException {
                checkCanOpen();
                try {
                    inputStream = zipEntry.getSlice().open(this);
                    length = zipEntry.uncompressedSize;
                    return inputStream;

                } catch (final IOException e) {
                    close();
                    throw e;
                }
            }

            @Override
            public ByteBuffer read() throws IOException {
                checkCanOpen();
                try {
                    byteBuffer = zipEntry.getSlice().read();
                    length = byteBuffer.remaining();
                    return byteBuffer;
                } catch (final IOException e) {
                    close();
                    throw e;
                }
            }

            @Override
            public byte[] load() throws IOException {
                checkCanOpen();
                try (Resource res = this) { // 使用后关闭
                    final byte[] byteArray = zipEntry.getSlice().load();
                    res.length = byteArray.length;
                    return byteArray;
                }
            }

            @Override
            public void close() {
                if (isOpen.getAndSet(false)) {
                    if (byteBuffer != null) {
                        // ByteBuffer 应为副本或切片，或包装了数组，因此它不需要
                        // 被取消映射
                        byteBuffer = null;
                    }

                    // 关闭 inputStream
                    super.close();
                }
            }
        };
    }

    /**
     * 获取给定相对路径的 {@link Resource}
     *
     * @param relativePath
     *            要返回的 {@link Resource} 的相对路径
     * @return 给定相对路径的 {@link Resource}，如果 relativePath 在此类路径元素中不存在则返回 null
     */
    @Override
    public Resource getResource(final String relativePath) {
        return relativePathToResource.get(relativePath);
    }

    /**
     * 扫描 jar 文件内的路径匹配，并记录匹配文件的 ZipEntry 对象
     *
     * @param log
     *            日志
     */
    @Override
    public void scanPaths(final LogNode log) {
        if (logicalZipFile == null) {
            skipClasspath = true;
        }
        if (!checkResourcePathAcceptReject(getZipFilePath(), log)) {
            skipClasspath = true;
        }
        if (skipClasspath) {
            return;
        }
        if (scanned.getAndSet(true)) {
            // 不应该发生
            throw new IllegalArgumentException("Already scanned classpath element " + getZipFilePath());
        }

        final LogNode subLog = log == null ? null
                : log(classpathElementIdx, "Scanning jarfile classpath element " + getZipFilePath(), log);

        boolean isModularJar = false;
        if (VersionFinder.JAVA_MAJOR_VERSION >= 9) {
            // 确定是否是在 JRE 9+ 下运行的模块化 jar
            String moduleName = moduleNameFromModuleDescriptor;
            if (moduleName == null || moduleName.isEmpty()) {
                moduleName = moduleNameFromManifestFile;
            }
            if (moduleName != null && moduleName.isEmpty()) {
                moduleName = null;
            }
            if (moduleName != null) {
                isModularJar = true;
            }
        }

        Set<String> loggedNestedClasspathRootPrefixes = null;
        String prevParentRelativePath = null;
        ScanConfigPathMatch prevParentMatchStatus = null;
        for (final FastZipEntry zipEntry : logicalZipFile.entries) {
            String relativePath = zipEntry.entryNameUnversioned;

            // 路径不应以 "META-INF/versions/{version}/" 开头，因为要么这是一个版本化
            // jar，此时 zipEntry.entryNameUnversioned 已剥离版本前缀，要么这是一个
            // 非版本化 jar(例如清单文件中未设置多版本标志)，并且多版本路径中有一些
            // 多余的条目(在这种情况下，应忽略它们)
            if (!ScanConfig.enableMultiReleaseVersions
                    && relativePath.startsWith(LogicalZipFile.MULTI_RELEASE_PATH_PREFIX)) {
                if (subLog != null) {
                    if (VersionFinder.JAVA_MAJOR_VERSION < 9) {
                        subLog.log("Skipping versioned entry in jar, because JRE version "
                                + VersionFinder.JAVA_MAJOR_VERSION + " does not support this: " + relativePath);
                    } else {
                        subLog.log(
                                "Found unexpected versioned entry in jar (the jar's manifest file may be missing "
                                        + "the \"Multi-Release\" key) -- skipping: " + relativePath);
                    }
                }
                continue;
            }

            // 如果这是一个模块化 jar，则忽略默认包中除 "module-info.class" 之外的所有 class 文件，
            // 因为这些都是不允许的
            if (isModularJar && relativePath.indexOf('/') < 0 && relativePath.endsWith(".class")
                    && !"module-info.class".equals(relativePath)) {
                continue;
            }

            // 检查相对路径是否在嵌套类路径根内
            if (nestedClasspathRootPrefixes != null) {
                // 这是 O(mn) 复杂度，效率不高，但嵌套类路径根的数量应该很少
                boolean reachedNestedRoot = false;
                for (final String nestedClasspathRoot : nestedClasspathRootPrefixes) {
                    if (relativePath.startsWith(nestedClasspathRoot)) {
                        // relativePath 具有 nestedClasspathRoot 的前缀
                        if (subLog != null) {
                            if (loggedNestedClasspathRootPrefixes == null) {
                                loggedNestedClasspathRootPrefixes = new HashSet<>();
                            }
                            if (loggedNestedClasspathRootPrefixes.add(nestedClasspathRoot)) {
                                subLog.log("Reached nested classpath root, stopping recursion to avoid duplicate "
                                        + "scanning: " + nestedClasspathRoot);
                            }
                        }
                        reachedNestedRoot = true;
                        break;
                    }
                }
                if (reachedNestedRoot) {
                    continue;
                }
            }

            // 忽略没有正确类路径根前缀的条目
            if (!packageRootPrefix.isEmpty() && !relativePath.startsWith(packageRootPrefix)) {
                continue;
            }

            // 从相对路径中去除包根前缀
            if (!packageRootPrefix.isEmpty()) {
                relativePath = relativePath.substring(packageRootPrefix.length());
            } else {
                // 从相对路径中去除任何包根前缀
                for (int i = 0; i < HandlerRegistry.AUTOMATIC_PACKAGE_ROOT_PREFIXES.length; i++) {
                    final String packageRoot = HandlerRegistry.AUTOMATIC_PACKAGE_ROOT_PREFIXES[i];
                    if (relativePath.startsWith(packageRoot)) {
                        // 去除包根
                        relativePath = relativePath.substring(packageRoot.length());
                        // 去除包根的尾部斜杠
                        final String packageRootWithoutFinalSlash = packageRoot.endsWith("/")
                                ? packageRoot.substring(0, packageRoot.length() - 1)
                                : packageRoot;
                        // 存储包根供 getAllURIs() 使用
                        strippedAutomaticPackageRootPrefixes.add(packageRootWithoutFinalSlash);
                    }
                }
            }

            // 根据文件资源路径接受/拒绝类路径元素
            if (!checkResourcePathAcceptReject(relativePath, log)) {
                continue;
            }

            // 获取此 ZipEntry 文件相对路径的父目录的匹配状态(或重用上一次的
            // 匹配状态以提高速度，如果目录名未更改的话)
            final int lastSlashIdx = relativePath.lastIndexOf('/');
            final String parentRelativePath = lastSlashIdx < 0 ? "/" : relativePath.substring(0, lastSlashIdx + 1);
            final boolean parentRelativePathChanged = !parentRelativePath.equals(prevParentRelativePath);
            final ScanConfigPathMatch parentMatchStatus = //
                    parentRelativePathChanged ? ScanConfig.dirAcceptMatchStatus(parentRelativePath)
                            : prevParentMatchStatus;
            prevParentRelativePath = parentRelativePath;
            prevParentMatchStatus = parentMatchStatus;

            if (parentMatchStatus == ScanConfigPathMatch.HAS_REJECTED_PATH_PREFIX) {
                // 父目录或其某个祖先目录被拒绝
                if (subLog != null) {
                    subLog.log("Skipping rejected path: " + relativePath);
                }
                continue;
            }

            // 将 ZipEntry 路径添加为 Resource
            final Resource resource = newResource(zipEntry, relativePath);
            if (relativePathToResource.putIfAbsent(relativePath, resource) == null) {
                // 如果资源被接受
                if (parentMatchStatus == ScanConfigPathMatch.HAS_ACCEPTED_PATH_PREFIX
                        || parentMatchStatus == ScanConfigPathMatch.AT_ACCEPTED_PATH
                        || (parentMatchStatus == ScanConfigPathMatch.AT_ACCEPTED_CLASS_PACKAGE
                        && ScanConfig.classfileIsSpecificallyAccepted(relativePath))) {
                    // 资源被接受
                    addAcceptedResource(resource, parentMatchStatus, /* isClassfileOnly = */ false, subLog);
                } else if (ScanConfig.enableClassInfo && "module-info.class".equals(relativePath)) {
                    // 将模块描述符添加为被接受的 class 文件资源，以便对其进行扫描，
                    // 但不要将其添加到 ScanResult 的资源列表中，因为它不在
                    // 被接受的包中 (#352)
                    addAcceptedResource(resource, parentMatchStatus, /* isClassfileOnly = */ true, subLog);
                }
            }
        }

        // 保存 zip 文件的最后修改时间
        final File zipfile = getFile();
        if (zipfile != null) {
            fileToLastModified.put(zipfile, zipfile.lastModified());
        }

        finishScanPaths(subLog);
    }

    /**
     * 从模块描述符获取模块名称，或从清单文件获取自动模块名称，或从 jar 名称派生自动模块名称
     *
     * @return 模块名称
     */
    @Override
    public String getModuleName() {
        String moduleName = moduleNameFromModuleDescriptor;
        if (moduleName == null || moduleName.isEmpty()) {
            moduleName = moduleNameFromManifestFile;
        }
        if (moduleName == null || moduleName.isEmpty()) {
            if (derivedAutomaticModuleName == null) {
                derivedAutomaticModuleName = JarUtils.derivedAutomaticModuleName(zipFilePath);
            }
            moduleName = derivedAutomaticModuleName;
        }
        return moduleName == null || moduleName.isEmpty() ? null : moduleName;
    }

    /**
     * 获取 zip 文件路径
     *
     * @return zip 文件的路径，包含任何包根路径
     */
    public String getZipFilePath() {
        return packageRootPrefix.isEmpty() ? zipFilePath
                : zipFilePath + "!/" + packageRootPrefix.substring(0, packageRootPrefix.length() - 1);
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.Classpath#getURI()
     */
    @Override
    public URI getURI() {
        try {
            return new URI(URLPathEncoder.normalizeURLPath(getZipFilePath()));
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Could not form URI: " + e);
        }
    }

    /**
     * 返回类路径元素的 URI，以及任何已去除的嵌套自动包根前缀的 URI，例如 "!/BOOT-INF/classes"
     */
    @Override
    public List<URI> getAllURIs() {
        if (strippedAutomaticPackageRootPrefixes.isEmpty()) {
            return Collections.singletonList(getURI());
        } else {
            final URI uri = getURI();
            final List<URI> uris = new ArrayList<>();
            uris.add(uri);
            final String uriStr = uri.toString();
            for (final String prefix : strippedAutomaticPackageRootPrefixes) {
                try {
                    uris.add(new URI(uriStr + "!/" + prefix));
                } catch (final URISyntaxException e) {
                    // 忽略
                }
            }
            return uris;
        }
    }

    /**
     * 获取此类路径元素的最外层 zip 文件的 {@link File}
     *
     * @return 此类路径元素的最外层 zip 文件的 {@link File}，如果此文件是从 URL 直接下载到 RAM，
     *         或者类路径元素由支持 {@link Path} API 但不支持 {@link File} API 的自定义文件系统支持，
     *         则返回 null
     */
    @Override
    public File getFile() {
        if (logicalZipFile != null) {
            return logicalZipFile.getPhysicalFile();
        } else {
            // 未执行完整扫描(仅获取类路径元素)，因此未设置 logicalZipFile
            final int plingIdx = rawPath.indexOf('!');
            final String outermostZipFilePathResolved = FastPathResolver.resolve(FileUtils.currDirPath(),
                    plingIdx < 0 ? rawPath : rawPath.substring(0, plingIdx));
            return new File(outermostZipFilePathResolved);
        }
    }

    /**
     * 返回类路径元素路径
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return getZipFilePath();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof JarClasspath)) {
            return false;
        }
        final JarClasspath other = (JarClasspath) obj;
        return this.getZipFilePath().equals(other.getZipFilePath());
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getZipFilePath().hashCode();
    }
}
