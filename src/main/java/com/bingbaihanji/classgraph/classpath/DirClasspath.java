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

import com.bingbaihanji.classgraph.classloaderhandler.ClassLoaderHandlerRegistry;
import com.bingbaihanji.classgraph.concurrency.WorkQueue;
import com.bingbaihanji.classgraph.core.Scanner.ClasspathEntryWorkUnit;
import com.bingbaihanji.classgraph.fastzipfilereader.LogicalZipFile;
import com.bingbaihanji.classgraph.fastzipfilereader.NestedJarHandler;
import com.bingbaihanji.classgraph.fileslice.PathSlice;
import com.bingbaihanji.classgraph.fileslice.reader.ClassfileReader;
import com.bingbaihanji.classgraph.scanspec.ScanSpec;
import com.bingbaihanji.classgraph.scanspec.ScanSpec.ScanSpecPathMatch;
import com.bingbaihanji.classgraph.utils.FastPathResolver;
import com.bingbaihanji.classgraph.utils.FileUtils;
import com.bingbaihanji.classgraph.utils.LogNode;
import com.bingbaihanji.classgraph.utils.VersionFinder;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 目录类路径元素，使用 {@link Path} API */
class DirClasspath extends ClasspathElement {
    /** 类路径元素根目录 */
    private final Path classpathEltPath;

    /** 用于确保递归扫描不会因链接循环而陷入无限循环 */
    private final Set<Path> scannedCanonicalPaths = new HashSet<>();

    /** 嵌套 jar 处理器 */
    private final NestedJarHandler nestedJarHandler;

    /**
     * 一个目录类路径元素
     *
     * @param workUnit
     *            工作单元 -- workUnit.classpathEntryObj 必须是一个 {@link Path} 对象
     * @param nestedJarHandler
     *            嵌套 jar 处理器
     * @param scanSpec
     *            扫描规格
     */
    DirClasspath(final ClasspathEntryWorkUnit workUnit, final NestedJarHandler nestedJarHandler,
                        final ScanSpec scanSpec) {
        super(workUnit, scanSpec);
        this.classpathEltPath = (Path) workUnit.classpathEntryObj;
        this.nestedJarHandler = nestedJarHandler;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ClasspathElement#open(
     * com.bingbaihanji.classgraph.concurrency.WorkQueue, com.bingbaihanji.classgraph.utils.LogNode)
     */
    @Override
    void open(final WorkQueue<ClasspathEntryWorkUnit> workQueue, final LogNode log) {
        if (!scanSpec.scanDirs) {
            if (log != null) {
                log(classpathElementIdx,
                        "Skipping classpath element, since dir scanning is disabled: " + classpathEltPath, log);
            }
            skipClasspathElement = true;
            return;
        }
        try {
            // 自动添加嵌套的 lib 目录
            int childClasspathEntryIdx = 0;
            for (final String libDirPrefix : ClassLoaderHandlerRegistry.AUTOMATIC_LIB_DIR_PREFIXES) {
                final Path libDirPath = classpathEltPath.resolve(libDirPrefix);
                if (FileUtils.canReadAndIsDir(libDirPath)) {
                    // 将 lib 目录中的所有 jar 文件添加为子类路径条目
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(libDirPath,
                            new DirectoryStream.Filter<Path>() {
                                @Override
                                public boolean accept(Path filePath) {
                                    return filePath.toString().toLowerCase().endsWith(".jar")
                                            && Files.isRegularFile(filePath);
                                }
                            })) {
                        for (final Path filePath : stream) {
                            if (log != null) {
                                log(classpathElementIdx, "Found lib jar: " + filePath, log);
                            }
                            workQueue.addWorkUnit(new ClasspathEntryWorkUnit(filePath, getClassLoader(),
                                    /* parentClasspathElement = */ this,
                                    /* orderWithinParentClasspathElement = */ childClasspathEntryIdx++,
                                    /* packageRootPrefix = */ ""));
                        }
                    } catch (final IOException e) {
                        // 忽略 -- 由 Files.newDirectoryStream 抛出
                    }
                }
            }
            // 仅当包根路径为空时才查找包根
            if (packageRootPrefix.isEmpty()) {
                for (final String packageRootPrefix : ClassLoaderHandlerRegistry.AUTOMATIC_PACKAGE_ROOT_PREFIXES) {
                    final Path packageRoot = classpathEltPath.resolve(packageRootPrefix);
                    if (FileUtils.canReadAndIsDir(packageRoot)) {
                        if (log != null) {
                            log(classpathElementIdx, "Found package root: " + packageRootPrefix, log);
                        }
                        workQueue.addWorkUnit(new ClasspathEntryWorkUnit(packageRoot, getClassLoader(),
                                /* parentClasspathElement = */ this,
                                /* orderWithinParentClasspathElement = */ childClasspathEntryIdx++,
                                packageRootPrefix));
                    }
                }
            }
        } catch (final SecurityException e) {
            if (log != null) {
                log(classpathElementIdx,
                        "Skipping classpath element, since dir cannot be accessed: " + classpathEltPath, log);
            }
            skipClasspathElement = true;
        }
    }

    /**
     * 为扫描路径时发现的资源或 class 文件创建新的 {@link Resource} 对象
     *
     * @param resourcePath
     *            资源的 {@link Path}
     * @return 资源对象
     */
    private Resource newResource(final Path resourcePath, final BasicFileAttributes attributes) {
        final int notYetLoadedLength = -2;
        return new Resource(this, attributes == null ? notYetLoadedLength : attributes.size()) {
            /** 如果资源已打开，则为 true */
            private final AtomicBoolean isOpen = new AtomicBoolean();
            /** 在文件上打开的 {@link PathSlice} */
            private PathSlice pathSlice;

            @Override
            public long getLength() {
                if (length == notYetLoadedLength) {
                    try {
                        length = Files.size(resourcePath);
                    } catch (IOException | SecurityException e) {
                        length = -1;
                    }
                }
                return length;
            }

            @Override
            public String getPath() {
                String path = FastPathResolver.resolve(classpathEltPath.relativize(resourcePath).toString());
                while (path.startsWith("/")) {
                    path = path.substring(1);
                }
                return path;
            }

            @Override
            public String getPathRelativeToClasspathElement() {
                return packageRootPrefix.isEmpty() ? getPath() : packageRootPrefix + getPath();
            }

            @Override
            public long getLastModified() {
                try {
                    return attributes == null ? resourcePath.toFile().lastModified()
                            : attributes.lastModifiedTime().toMillis();
                } catch (final UnsupportedOperationException e) {
                    return 0L;
                }
            }

            @SuppressWarnings("null")
            @Override
            public Set<PosixFilePermission> getPosixFilePermissions() {
                Set<PosixFilePermission> posixFilePermissions = null;
                try {
                    if (attributes instanceof PosixFileAttributes) {
                        posixFilePermissions = ((PosixFileAttributes) attributes).permissions();
                    } else {
                        posixFilePermissions = Files.readAttributes(resourcePath, PosixFileAttributes.class)
                                .permissions();
                    }
                } catch (UnsupportedOperationException | IOException | SecurityException e) {
                    // 不支持 POSIX 属性
                }
                return posixFilePermissions;
            }

            protected void checkCanOpen() {
                if (skipClasspathElement) {
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
            public ByteBuffer read() throws IOException {
                openAndCreateSlice();
                byteBuffer = pathSlice.read();
                return byteBuffer;
            }

            @Override
            ClassfileReader openClassfile() throws IOException {
                // class 文件不会被压缩，因此将其包装在新的 PathSlice 中然后打开
                openAndCreateSlice();
                return new ClassfileReader(pathSlice, this);
            }

            @Override
            public InputStream open() throws IOException {
                openAndCreateSlice();
                inputStream = pathSlice.open(this);
                return inputStream;
            }

            @Override
            public byte[] load() throws IOException {
                try {
                    openAndCreateSlice();
                    return pathSlice.load();
                } finally {
                    close();
                }
            }

            @Override
            public void close() {
                if (isOpen.getAndSet(false)) {
                    if (byteBuffer != null) {
                        // 任何 ByteBuffer 引用都应该是副本，因此不需要清理
                        byteBuffer = null;
                    }
                    if (pathSlice != null) {
                        pathSlice.close();
                        nestedJarHandler.markSliceAsClosed(pathSlice);
                        pathSlice = null;
                    }

                    // 关闭 inputStream
                    super.close();
                }
            }

            private void openAndCreateSlice() throws IOException {
                checkCanOpen();
                pathSlice = new PathSlice(resourcePath, false, 0L, nestedJarHandler, false);
                length = pathSlice.sliceLength;
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
    Resource getResource(final String relativePath) {
        final Path resourcePath = classpathEltPath.resolve(relativePath);
        return FileUtils.canReadAndIsFile(resourcePath) ? newResource(resourcePath, null) : null;
    }

    /**
     * 递归扫描 {@link Path} 以查找与扫描规格匹配的子路径模式
     *
     * @param path
     *            要扫描的 {@link Path}
     * @param log
     *            日志
     */
    private void scanPathRecursively(final Path path, final LogNode log) {
        // 检查此规范路径是否之前已被扫描过，以避免因符号链接导致递归扫描
        // 陷入无限循环
        Path canonicalPath;
        try {
            canonicalPath = path.toRealPath();
            if (!scannedCanonicalPaths.add(canonicalPath)) {
                if (log != null) {
                    log.log("Reached symlink cycle, stopping recursion: " + path);
                }
                return;
            }
        } catch (final IOException | SecurityException e) {
            if (log != null) {
                log.log("Could not canonicalize path: " + path, e);
            }
            return;
        }

        String dirRelativePathStr = FastPathResolver.resolve(classpathEltPath.relativize(path).toString());
        while (dirRelativePathStr.startsWith("/")) {
            dirRelativePathStr = dirRelativePathStr.substring(1);
        }
        if (!dirRelativePathStr.endsWith("/")) {
            dirRelativePathStr += "/";
        }
        final boolean isDefaultPackage = "/".equals(dirRelativePathStr);

        if (nestedClasspathRootPrefixes != null && nestedClasspathRootPrefixes.contains(dirRelativePathStr)) {
            if (log != null) {
                log.log("Reached nested classpath root, stopping recursion to avoid duplicate scanning: "
                        + dirRelativePathStr);
            }
            return;
        }

        // 忽略解压 jar 中的版本化部分 -- 它们仅应在 jar 中使用
        // TODO: 是否有必要同样支持多版本解压 jar？如果是，则目录类路径条目中的所有路径都必须像
        // ClasspathElementZip 中那样预先扫描和屏蔽
        if (!scanSpec.enableMultiReleaseVersions
                && dirRelativePathStr.startsWith(LogicalZipFile.MULTI_RELEASE_PATH_PREFIX)) {
            if (log != null) {
                log.log("Found unexpected nested versioned entry in directory classpath element -- skipping: "
                        + dirRelativePathStr);
            }
            return;
        }

        // 根据目录资源路径接受/拒绝类路径元素
        if (!checkResourcePathAcceptReject(dirRelativePathStr, log)) {
            return;
        }

        final ScanSpecPathMatch parentMatchStatus = scanSpec.dirAcceptMatchStatus(dirRelativePathStr);
        if (parentMatchStatus == ScanSpecPathMatch.HAS_REJECTED_PATH_PREFIX) {
            // 到达未被接受或已被拒绝的路径 -- 停止递归扫描
            if (log != null) {
                log.log("Reached rejected directory, stopping recursive scan: " + dirRelativePathStr);
            }
            return;
        }
        if (parentMatchStatus == ScanSpecPathMatch.NOT_WITHIN_ACCEPTED_PATH) {
            // 到达既未被接受也未被拒绝的路径 -- 停止递归扫描
            return;
        }

        final LogNode subLog = log == null ? null
                // 在文件之后记录目录(addAcceptedResources() 在日志条目前加上 "0:")
                : log.log("1:" + canonicalPath,
                "Scanning Path: " + FastPathResolver.resolve(path.toString()) + (path.equals(canonicalPath)
                        ? ""
                        : " ; canonical path: " + FastPathResolver.resolve(canonicalPath.toString())));

        final List<Path> pathsInDir = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (final Path subPath : stream) {
                pathsInDir.add(subPath);
            }
        } catch (IOException | SecurityException e) {
            if (log != null) {
                log.log("Could not read directory " + path + " : " + e.getMessage());
            }
            return;
        }
        Collections.sort(pathsInDir);
        final FileUtils.FileAttributesGetter getFileAttributes = FileUtils.createCachedAttributesGetter();

        // 确定是否是在 JRE 9+ 下运行的模块化 jar
        final boolean isModularJar = VersionFinder.JAVA_MAJOR_VERSION >= 9 && getModuleName() != null;

        // 仅当目录不仅仅是已接受路径的祖先时才扫描目录中的文件
        if (parentMatchStatus != ScanSpecPathMatch.ANCESTOR_OF_ACCEPTED_PATH) {
            // 执行先序遍历(先处理目录中的文件，再处理子目录)，以减少文件系统缓存未命中
            final Iterator<Path> pathsIterator = pathsInDir.iterator();
            while (pathsIterator.hasNext()) {
                final Path subPath = pathsIterator.next();
                // 在递归之前处理目录中的文件
                final BasicFileAttributes fileAttributes = getFileAttributes.get(subPath);
                if (fileAttributes.isRegularFile()) {
                    pathsIterator.remove();
                    final Path subPathRelative = classpathEltPath.relativize(subPath);
                    final String subPathRelativeStr = FastPathResolver.resolve(subPathRelative.toString());
                    // 如果这是一个模块化 jar，则忽略默认包中除 "module-info.class" 之外的所有 class 文件，
                    // 因为这些都是不允许的
                    if (isModularJar && isDefaultPackage && subPathRelativeStr.endsWith(".class")
                            && !"module-info.class".equals(subPathRelativeStr)) {
                        continue;
                    }

                    // 根据文件资源路径接受/拒绝类路径元素
                    if (!checkResourcePathAcceptReject(subPathRelativeStr, subLog)) {
                        return;
                    }

                    // 如果相对路径被接受
                    if (parentMatchStatus == ScanSpecPathMatch.HAS_ACCEPTED_PATH_PREFIX
                            || parentMatchStatus == ScanSpecPathMatch.AT_ACCEPTED_PATH
                            || (parentMatchStatus == ScanSpecPathMatch.AT_ACCEPTED_CLASS_PACKAGE
                            && scanSpec.classfileIsSpecificallyAccepted(subPathRelativeStr))) {
                        // 资源被接受
                        final Resource resource = newResource(subPath, fileAttributes);
                        addAcceptedResource(resource, parentMatchStatus, /* isClassfileOnly = */ false, subLog);

                        // 保存最后修改时间
                        try {
                            fileToLastModified.put(subPath.toFile(), fileAttributes.lastModifiedTime().toMillis());
                        } catch (final UnsupportedOperationException e) {
                            // 忽略
                        }
                    } else {
                        if (subLog != null) {
                            subLog.log("Skipping non-accepted file: " + subPathRelative);
                        }
                    }
                }
            }
        } else if (scanSpec.enableClassInfo && "/".equals(dirRelativePathStr)) {
            // 始终检查包根中的模块描述符，即使包根不在接受列表中
            final Iterator<Path> pathsIterator = pathsInDir.iterator();
            while (pathsIterator.hasNext()) {
                final Path subPath = pathsIterator.next();
                if ("module-info.class".equals(subPath.getFileName().toString())) {
                    final BasicFileAttributes fileAttributes = getFileAttributes.get(subPath);
                    if (fileAttributes.isRegularFile()) {
                        pathsIterator.remove();
                        final Resource resource = newResource(subPath, fileAttributes);
                        addAcceptedResource(resource, parentMatchStatus, /* isClassfileOnly = */ true, subLog);
                        try {
                            fileToLastModified.put(subPath.toFile(), fileAttributes.lastModifiedTime().toMillis());
                        } catch (final UnsupportedOperationException e) {
                            // 忽略
                        }
                        break;
                    }
                }
            }
        }
        // 递归进入子目录
        for (final Path subPath : pathsInDir) {
            try {
                if (getFileAttributes.get(subPath).isDirectory()) {
                    scanPathRecursively(subPath, subLog);
                }
            } catch (final SecurityException e) {
                if (subLog != null) {
                    subLog.log("Could not read sub-directory " + subPath + " : " + e.getMessage());
                }
            }
        }

        if (subLog != null) {
            subLog.addElapsedTime();
        }

        // 保存目录的最后修改时间
        try {
            final File file = path.toFile();
            fileToLastModified.put(file, file.lastModified());
        } catch (final UnsupportedOperationException e) {
            // 忽略
        }
    }

    /**
     * 层次化扫描目录结构，查找 class 文件和匹配的文件
     *
     * @param log
     *            日志
     */
    @Override
    void scanPaths(final LogNode log) {
        if (!checkResourcePathAcceptReject(classpathEltPath.toString(), log)) {
            skipClasspathElement = true;
        }
        if (skipClasspathElement) {
            return;
        }
        if (scanned.getAndSet(true)) {
            // 不应该发生
            throw new IllegalArgumentException("Already scanned classpath element " + this);
        }

        final LogNode subLog = log == null ? null
                : log(classpathElementIdx, "Scanning Path classpath element " + getURI(), log);

        scanPathRecursively(classpathEltPath, subLog);

        finishScanPaths(subLog);
    }

    /**
     * 从模块描述符获取模块名称
     *
     * @return 模块名称
     */
    @Override
    public String getModuleName() {
        return moduleNameFromModuleDescriptor == null || moduleNameFromModuleDescriptor.isEmpty() ? null
                : moduleNameFromModuleDescriptor;
    }

    /**
     * 获取目录 {@link File}
     *
     * @return 作为 {@link File} 的类路径元素目录，如果此类路径元素不由目录支持则返回 null(不应该发生)
     */
    @Override
    public File getFile() {
        try {
            return classpathEltPath.toFile();
        } catch (final UnsupportedOperationException e) {
            return null;
        }
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ClasspathElement#getURI()
     */
    @Override
    URI getURI() {
        try {
            return classpathEltPath.toUri();
        } catch (IOError | SecurityException e) {
            throw new IllegalArgumentException("Could not convert to URI: " + classpathEltPath);
        }
    }

    @Override
    List<URI> getAllURIs() {
        return Collections.singletonList(getURI());
    }

    /**
     * 以 String 形式返回类路径元素目录
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        try {
            // 由于某些原因，Path.toString() 不包含 URI 协议
            return classpathEltPath.toUri().toString();
        } catch (IOError | SecurityException e) {
            return classpathEltPath.toString();
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(classpathEltPath);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof DirClasspath)) {
            return false;
        }
        final DirClasspath other = (DirClasspath) obj;
        return Objects.equals(this.classpathEltPath, other.classpathEltPath);
    }
}
