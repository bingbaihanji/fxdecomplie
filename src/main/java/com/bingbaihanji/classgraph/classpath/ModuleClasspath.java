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
import com.bingbaihanji.classgraph.metadata.ModuleRef;
import com.bingbaihanji.classgraph.resource.*;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.scan.ScanConfig.ScanConfigPathMatch;
import com.bingbaihanji.classgraph.scan.Scanner.ClasspathEntryWorkUnit;
import com.bingbaihanji.classgraph.util.*;
import com.bingbaihanji.classgraph.util.SingletonMap.NewInstanceException;
import com.bingbaihanji.classgraph.util.SingletonMap.NullSingletonException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模块类路径元素
 *
 * @author luke
 */
public class ModuleClasspath extends Classpath {

    /** 模块引用 */
    public final ModuleRef moduleRef;
    /** 所有资源路径 */
    private final Set<String> allResourcePaths = new HashSet<>();
    /** 从 {@link ModuleRef} 到模块的 {@link ModuleReaderProxy} 回收器的单例映射 */
    SingletonMap<ModuleRef, Pool<ModuleReaderProxy, IOException>, IOException> //
            moduleRefToModuleReaderProxyRecyclerMap;
    /** 模块读取器代理回收器 */
    private Pool<ModuleReaderProxy, IOException> moduleReaderProxyRecycler;

    /**
     * 一个 zip/jar 文件类路径元素
     *
     * @param moduleRef
     *            模块引用
     * @param workUnit
     *            工作单元
     * @param moduleRefToModuleReaderProxyRecyclerMap
     *            从模块引用到模块读取器代理回收器的映射
     * @param ScanConfig
     *            扫描规格
     */
    public ModuleClasspath(final ModuleRef moduleRef,
                           final SingletonMap<ModuleRef, Pool<ModuleReaderProxy, IOException>, IOException> //
                                   moduleRefToModuleReaderProxyRecyclerMap, final ClasspathEntryWorkUnit workUnit,
                           final ScanConfig ScanConfig) {
        super(workUnit, ScanConfig);
        this.moduleRefToModuleReaderProxyRecyclerMap = moduleRefToModuleReaderProxyRecyclerMap;
        this.moduleRef = moduleRef;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.Classpath#open(
     * com.bingbaihanji.classgraph.concurrency.WorkQueue, com.bingbaihanji.classgraph.utils.LogNode)
     */
    @Override
    public void open(final WorkQueue<ClasspathEntryWorkUnit> workQueueIgnored, final LogNode log)
            throws InterruptedException {
        if (!ScanConfig.scanModules) {
            if (log != null) {
                log(classpathElementIdx, "Skipping module, since module scanning is disabled: " + getModuleName(),
                        log);
            }
            skipClasspath = true;
            return;
        }
        try {
            moduleReaderProxyRecycler = moduleRefToModuleReaderProxyRecyclerMap.get(moduleRef, log);
        } catch (final IOException | NullSingletonException | NewInstanceException e) {
            if (log != null) {
                log(classpathElementIdx, "Skipping invalid module " + getModuleName() + " : "
                        + (e.getCause() == null ? e : e.getCause()), log);
            }
            skipClasspath = true;
            return;
        }
    }

    /**
     * 为扫描路径时发现的资源或 class 文件创建新的 {@link Resource} 对象
     *
     * @param resourcePath
     *            资源路径
     * @return 资源对象
     */
    private Resource newResource(final String resourcePath) {
        return new Resource(this, /* length unknown */ -1L) {
            /** 如果资源已打开，则为 true */
            private final AtomicBoolean isOpen = new AtomicBoolean();
            /** 模块读取器代理 */
            private ModuleReaderProxy moduleReaderProxy;

            @Override
            public String getPath() {
                return resourcePath;
            }

            @Override
            public long getLastModified() {
                return 0L; // 未知
            }

            @Override
            public Set<PosixFilePermission> getPosixFilePermissions() {
                return null; // 不适用
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
            public ByteBuffer read() throws IOException {
                checkCanOpen();
                try {
                    moduleReaderProxy = moduleReaderProxyRecycler.acquire();
                    // ModuleReader#read(String name) 内部调用：
                    // InputStream is = open(name); return ByteBuffer.wrap(is.readAllBytes());
                    byteBuffer = moduleReaderProxy.read(resourcePath);
                    length = byteBuffer.remaining();
                    return byteBuffer;

                } catch (final SecurityException | OutOfMemoryError e) {
                    close();
                    throw new IOException("Could not open " + this, e);
                }
            }

            @Override
            public ClassFileReader openClassfile() throws IOException {
                return new ClassFileReader(open(), this);
            }

            @Override
            public URI getURI() {
                try {
                    final ModuleReaderProxy localModuleReaderProxy = moduleReaderProxyRecycler.acquire();
                    try {
                        return localModuleReaderProxy.find(resourcePath);
                    } finally {
                        moduleReaderProxyRecycler.recycle(localModuleReaderProxy);
                    }
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public InputStream open() throws IOException {
                checkCanOpen();
                try {
                    final Resource thisResource = this;
                    moduleReaderProxy = moduleReaderProxyRecycler.acquire();
                    inputStream = new ProxyingInputStream(moduleReaderProxy.open(resourcePath)) {
                        @Override
                        public void close() throws IOException {
                            // 关闭从 moduleReaderProxy 获取的包装后的 InputStream
                            super.close();
                            try {
                                // 关闭 Resource，释放任何底层的 ByteBuffer 并回收
                                // moduleReaderProxy
                                thisResource.close();
                            } catch (final Exception e) {
                                // 忽略
                            }
                        }
                    };
                    // 无法从 ModuleReader 获取长度
                    length = -1L;
                    return inputStream;

                } catch (final SecurityException e) {
                    close();
                    throw new IOException("Could not open " + this, e);
                }
            }

            @Override
            public byte[] load() throws IOException {
                try (Resource res = this) { // 使用后关闭
                    read(); // 填充 byteBuffer
                    final byte[] byteArray;
                    if (res.byteBuffer.hasArray() && res.byteBuffer.position() == 0
                            && res.byteBuffer.limit() == res.byteBuffer.capacity()) {
                        byteArray = res.byteBuffer.array();
                    } else {
                        byteArray = new byte[res.byteBuffer.remaining()];
                        res.byteBuffer.get(byteArray);
                    }
                    res.length = byteArray.length;
                    return byteArray;
                }
            }

            @Override
            public void close() {
                if (isOpen.getAndSet(false)) {
                    if (moduleReaderProxy != null) {
                        if (byteBuffer != null) {
                            // 释放任何打开的 ByteBuffer
                            moduleReaderProxy.release(byteBuffer);
                            byteBuffer = null;
                        }
                        // 回收(打开的)ModuleReaderProxy 实例
                        moduleReaderProxyRecycler.recycle(moduleReaderProxy);
                        // 不要调用 ModuleReaderProxy#close()，让 ModuleReaderProxy 在回收器中保持打开状态
                        // 这里只需将引用设为 nullModuleReaderProxy 将由
                        // ModuleClasspath#close() 来关闭
                        moduleReaderProxy = null;
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
        return allResourcePaths.contains(relativePath) ? newResource(relativePath) : null;
    }

    /**
     * 扫描模块内的包匹配
     *
     * @param log
     *            日志
     */
    @Override
    public void scanPaths(final LogNode log) {
        if (skipClasspath) {
            return;
        }
        if (scanned.getAndSet(true)) {
            // 不应该发生
            throw new IllegalArgumentException("Already scanned classpath element " + this);
        }

        final LogNode subLog = log == null ? null
                : log(classpathElementIdx, "Scanning module " + moduleRef.getName(), log);

        // 确定是否是在 JRE 9+ 下运行的模块化 jar
        final boolean isModularJar = VersionFinder.JAVA_MAJOR_VERSION >= 9 && getModuleName() != null;

        try (RecycleOnClose<ModuleReaderProxy, IOException> moduleReaderProxyRecycleOnClose //
                     = moduleReaderProxyRecycler.acquireRecycleOnClose()) {
            // 在模块中查找被接受的文件
            List<String> resourceRelativePaths;
            try {
                resourceRelativePaths = moduleReaderProxyRecycleOnClose.get().list();
            } catch (final SecurityException e) {
                if (subLog != null) {
                    subLog.log("Could not get resource list for module " + moduleRef.getName(), e);
                }
                return;
            }
            CollectionUtils.sortIfNotEmpty(resourceRelativePaths);

            String prevParentRelativePath = null;
            ScanConfigPathMatch prevParentMatchStatus = null;
            for (final String relativePath : resourceRelativePaths) {
                // 来自 ModuleReader#find()："如果模块读取器可以确定该名称定位到一个目录，
                // 则结果 URI 将以斜杠('/')结尾"但根据 ModuleReader#list() 的文档：
                // "元素流是否包含对应于模块中目录的名称取决于模块读取器的具体实现"
                // 我们无法在不尝试打开资源的情况下检查资源是否为目录，除非 ModuleReader#list() 也决定
                // 在对应于目录的资源路径末尾添加 "/"如果发现目录则跳过它们，
                // 但如果无法跳过，我们将不得不接受在目录被误认为是资源文件时抛出一些 IOException
                if (relativePath.endsWith("/")) {
                    continue;
                }

                // 模块中的路径不应以 "META-INF/versions/{version}/" 开头，因为模块
                // 系统应该已经将这些前缀剥离如果发现了它们，则 jar 文件
                // 必须包含类似 "META-INF/versions/{version}/META-INF/versions/{version}/" 的路径，这不可能
                // 有效(META-INF 应仅存在于模块根中)，嵌套的版本化部分
                // 应该被忽略
                if (!ScanConfig.enableMultiReleaseVersions
                        && relativePath.startsWith(LogicalZipFile.MULTI_RELEASE_PATH_PREFIX)) {
                    if (subLog != null) {
                        subLog.log(
                                "Found unexpected nested versioned entry in module -- skipping: " + relativePath);
                    }
                    continue;
                }

                // 如果这是一个模块化 jar，则忽略默认包中除 "module-info.class" 之外的所有 class 文件，
                // 因为这些都是不允许的
                if (isModularJar && relativePath.indexOf('/') < 0 && relativePath.endsWith(".class")
                        && !"module-info.class".equals(relativePath)) {
                    continue;
                }

                // 根据文件资源路径接受/拒绝类路径元素
                if (!checkResourcePathAcceptReject(relativePath, log)) {
                    continue;
                }

                // 获取此资源相对路径的父目录的匹配状态(或重用上一次的
                // 匹配状态以提高速度，如果目录名未更改的话)
                final int lastSlashIdx = relativePath.lastIndexOf('/');
                final String parentRelativePath = lastSlashIdx < 0 ? "/"
                        : relativePath.substring(0, lastSlashIdx + 1);
                final boolean parentRelativePathChanged = !parentRelativePath.equals(prevParentRelativePath);
                final ScanConfigPathMatch parentMatchStatus = //
                        prevParentRelativePath == null || parentRelativePathChanged
                                ? ScanConfig.dirAcceptMatchStatus(parentRelativePath)
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

                // 找到未被拒绝的相对路径
                if (allResourcePaths.add(relativePath)) {
                    // 如果资源被接受
                    if (parentMatchStatus == ScanConfigPathMatch.HAS_ACCEPTED_PATH_PREFIX
                            || parentMatchStatus == ScanConfigPathMatch.AT_ACCEPTED_PATH
                            || (parentMatchStatus == ScanConfigPathMatch.AT_ACCEPTED_CLASS_PACKAGE
                            && ScanConfig.classfileIsSpecificallyAccepted(relativePath))) {
                        // 添加被接受的资源
                        addAcceptedResource(newResource(relativePath), parentMatchStatus,
                                /* isClassfileOnly = */ false, subLog);
                    } else if (ScanConfig.enableClassInfo && "module-info.class".equals(relativePath)) {
                        // 将模块描述符添加为被接受的 class 文件资源，以便对其进行扫描，
                        // 但不要将其添加到 ScanResult 的资源列表中，因为它不在
                        // 被接受的包中 (#352)
                        addAcceptedResource(newResource(relativePath), parentMatchStatus,
                                /* isClassfileOnly = */ true, subLog);
                    }
                }
            }

            // 保存模块文件的最后修改时间
            final File moduleFile = moduleRef.getLocationFile();
            if (moduleFile != null && moduleFile.exists()) {
                fileToLastModified.put(moduleFile, moduleFile.lastModified());
            }

        } catch (final IOException e) {
            if (subLog != null) {
                subLog.log("Exception opening module " + moduleRef.getName(), e);
            }
            skipClasspath = true;
        }

        finishScanPaths(subLog);
    }

    /**
     * 获取此类路径元素的 ModuleRef
     *
     * @return 模块引用
     */
    public ModuleRef getModuleRef() {
        return moduleRef;
    }

    /**
     * 从模块引用或模块描述符获取模块名称
     *
     * @return 模块名称，如果模块没有名称则返回 null
     */
    @Override
    public String getModuleName() {
        String moduleName = moduleRef.getName();
        if (moduleName == null || moduleName.isEmpty()) {
            moduleName = moduleNameFromModuleDescriptor;
        }
        return moduleName == null || moduleName.isEmpty() ? null : moduleName;
    }

    /**
     * 从模块引用或模块描述符获取模块名称
     *
     * @return 模块名称，如果模块没有名称则返回空字符串
     */
    private String getModuleNameOrEmpty() {
        final String moduleName = getModuleName();
        return moduleName == null ? "" : moduleName;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.Classpath#getURI()
     */
    @Override
    public URI getURI() {
        final URI uri = moduleRef.getLocation();
        if (uri == null) {
            // 某些模块没有已知的模块位置(ModuleReference#location() 可以返回 null)
            throw new IllegalArgumentException("Module " + getModuleName() + " has a null location");
        }
        return uri;
    }

    @Override
    public List<URI> getAllURIs() {
        return Collections.singletonList(getURI());
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.Classpath#getFile()
     */
    @Override
    public File getFile() {
        try {
            final URI uri = moduleRef.getLocation();
            if (uri != null && !"jrt".equals(uri.getScheme())) {
                final File file = new File(uri);
                if (file.exists()) {
                    return file;
                }
            }
        } catch (final Exception e) {
            // 无效的 "file:" URI
        }
        return null;
    }

    /**
     * 以 String 形式返回模块引用
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return moduleRef.toString();
    }

    /**
     * 判断相等
     *
     * @param obj
     *            要比较的对象
     * @return 如果相等则返回 true
     */
    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof ModuleClasspath)) {
            return false;
        }
        final ModuleClasspath other = (ModuleClasspath) obj;
        return this.getModuleNameOrEmpty().equals(other.getModuleNameOrEmpty());
    }

    /**
     * 哈希码
     *
     * @return 哈希码值
     */
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getModuleNameOrEmpty().hashCode();
    }
}
