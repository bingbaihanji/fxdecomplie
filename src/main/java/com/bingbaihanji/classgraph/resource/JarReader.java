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
package com.bingbaihanji.classgraph.resource;

import com.bingbaihanji.classgraph.util.InterruptionChecker;
import com.bingbaihanji.classgraph.util.SingletonMap;
import com.bingbaihanji.classgraph.resource.ModuleReaderProxy;
import com.bingbaihanji.classgraph.metadata.ModuleRef;
import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.resource.ArraySlice;
import com.bingbaihanji.classgraph.resource.FileSlice;
import com.bingbaihanji.classgraph.resource.Slice;
import com.bingbaihanji.classgraph.resource.Pool;
import com.bingbaihanji.classgraph.resource.Resettable;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.FastPathResolver;
import com.bingbaihanji.classgraph.util.FileUtils;
import com.bingbaihanji.classgraph.util.JarUtils;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

/** 打开并读取 JAR 文件，这些文件可能嵌套在其他 JAR 文件中 */
public class JarReader {
    /** 随机临时文件名部分与叶子名称之间的分隔符 */
    public static final String TEMP_FILENAME_LEAF_SEPARATOR = "---";
    /** 文件缓冲区的默认大小 */
    private static final int DEFAULT_BUFFER_SIZE = 16384;
    /** 最大初始缓冲区大小 */
    private static final int MAX_INITIAL_BUFFER_SIZE = 16 * 1024 * 1024;
    /** HTTP(S) 超时时间(毫秒) */
    private static final int HTTP_TIMEOUT = 5000;
    /**
     * System.runFinalization() -- 在 JDK 18 中已被弃用，因此通过反射访问
     */
    private static Method runFinalizationMethod;
    /** {@link ScanConfig} 扫描规范 */
    public final ScanConfig ScanConfig;
    /** 如果 {@link #close(LogNode)} 已被调用则为 true */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    public ReflectionUtils reflectionUtils;
    /**
     * 从嵌套 JAR 文件路径到(该路径的逻辑 ZIP 文件，逻辑 ZIP 文件中的包根)二元组的单例映射
     */
    public SingletonMap<String, Entry<LogicalZipFile, String>, IOException> //
            nestedPathToLogicalZipFileAndPackageRootMap = //
            new SingletonMap<String, Entry<LogicalZipFile, String>, IOException>() {
                @Override
                public Entry<LogicalZipFile, String> newInstance(final String nestedJarPathRaw, final LogNode log)
                        throws IOException, InterruptedException {
                    final String nestedJarPath = FastPathResolver.resolve(nestedJarPathRaw);
                    final int lastPlingIdx = nestedJarPath.lastIndexOf('!');
                    if (lastPlingIdx < 0) {
                        // nestedJarPath 是一个简单的文件路径或 URL(即不包含任何 '!' 部分)
                        // 这也是下面 'else' 子句递归的最后一帧

                        // 如果路径以 "http://" 或 "https://" 或任何其他 URI/URL 方案开头，
                        // 则将 JAR 下载到临时文件或 RAM 中的 ByteBuffer("jar:" 和 "file:"
                        // 前缀已从任何 URL/URI 中去除)
                        final boolean isURL = JarUtils.URL_SCHEME_PATTERN.matcher(nestedJarPath).matches();
                        PhysicalZipFile physicalZipFile;
                        if (isURL) {
                            final String scheme = nestedJarPath.substring(0, nestedJarPath.indexOf(':'));
                            if (ScanConfig.allowedURLSchemes == null
                                    || !ScanConfig.allowedURLSchemes.contains(scheme)) {
                                // 不允许 "file:"(带可选的 "jar:" 前缀)以外的 URL 方案
                                // (这些方案已由 FastPathResolver.resolve(nestedJarPathRaw) 去除)
                                throw new IOException("Scanning of URL scheme \"" + scheme
                                        + "\" has not been enabled -- cannot scan classpath element: "
                                        + nestedJarPath);
                            }

                            // 从 URL 下载 JAR 到 RAM 中的 ByteBuffer，或磁盘上的临时文件
                            physicalZipFile = downloadJarFromURL(nestedJarPath, log);

                        } else {
                            // JAR 文件应该是本地文件 -- 包装到 PhysicalZipFile 实例中
                            try {
                                // 获取规范文件
                                final File canonicalFile = new File(nestedJarPath).getCanonicalFile();
                                // 获取或创建规范文件的 PhysicalZipFile 实例
                                physicalZipFile = canonicalFileToPhysicalZipFileMap.get(canonicalFile, log);
                            } catch (final NullSingletonException | NewInstanceException e) {
                                // 如果获取 PhysicalZipFile 失败，重新包装为 IOException
                                throw new IOException("Could not get PhysicalZipFile for path " + nestedJarPath
                                        + " : " + (e.getCause() == null ? e : e.getCause()));
                            } catch (final SecurityException e) {
                                // getCanonicalFile() 失败(可能也已因 IOException 失败)
                                throw new IOException(
                                        "Path component " + nestedJarPath + " could not be canonicalized: " + e);
                            }
                        }

                        // 创建整个物理 ZIP 文件的新逻辑切片
                        final ZipFileSlice topLevelSlice = new ZipFileSlice(physicalZipFile);
                        LogicalZipFile logicalZipFile;
                        try {
                            logicalZipFile = zipFileSliceToLogicalZipFileMap.get(topLevelSlice, log);
                        } catch (final NullSingletonException e) {
                            throw new IOException("Could not get toplevel slice " + topLevelSlice + " : " + e);
                        } catch (final NewInstanceException e) {
                            throw new IOException("Could not get toplevel slice " + topLevelSlice, e);
                        }

                        // 返回带有空包根的新逻辑 ZIP 文件
                        return new SimpleEntry<>(logicalZipFile, "");

                    } else {
                        // 此路径包含一个或多个 '!' 部分
                        final String parentPath = nestedJarPath.substring(0, lastPlingIdx);
                        String childPath = nestedJarPath.substring(lastPlingIdx + 1);
                        // "file.jar!/path" -> "file.jar!path"
                        childPath = FileUtils.sanitizeEntryPath(childPath, /* removeInitialSlash = */ true,
                                /* removeFinalSlash = */ true);

                        // 每次递归移除一个 '!' 部分，向 URL 或文件路径的开头方向回溯
                        // 在递归的最后一帧，将达到并返回顶层 JAR 文件
                        // 递归保证会终止，因为 parentPath 每次递归帧都会缩短一个 '!' 部分
                        Entry<LogicalZipFile, String> parentLogicalZipFileAndPackageRoot;
                        try {
                            parentLogicalZipFileAndPackageRoot = nestedPathToLogicalZipFileAndPackageRootMap
                                    .get(parentPath, log);
                        } catch (final NullSingletonException e) {
                            throw new IOException("Could not get parent logical zipfile " + parentPath + " : " + e);
                        } catch (final NewInstanceException e) {
                            throw new IOException("Could not get parent logical zipfile " + parentPath, e);
                        }

                        // '!'-分隔列表中的最后一项才可能是非 JAR 路径，因此父项必须始终是 JAR 文件
                        final LogicalZipFile parentLogicalZipFile = parentLogicalZipFileAndPackageRoot.getKey();

                        // 在父 ZIP 文件中查找子路径
                        boolean isDirectory = false;
                        while (childPath.endsWith("/")) {
                            // 子路径肯定是一个目录，因为它以斜杠结尾
                            isDirectory = true;
                            childPath = childPath.substring(0, childPath.length() - 1);
                        }
                        FastZipEntry childZipEntry = null;
                        if (!isDirectory) {
                            // 如果子路径不以斜杠结尾，查看是否存在名称匹配子路径的非目录条目
                            // (LogicalZipFile 在读取 ZIP 文件的中央目录时会丢弃以斜杠结尾的目录条目)
                            // 注意：我们在此执行 O(N) 搜索，因为假设包含 "!" 部分的 classpath 元素数量
                            // 相对于所有 JAR 文件中的条目总数来说较少(即为每个 JAR 文件建立一个从条目路径
                            // 到条目的 HashMap 通常比执行此线性搜索更昂贵，而且除非 classpath 非常庞大，
                            // 整体时间性能不会趋向 O(N^2))
                            for (final FastZipEntry entry : parentLogicalZipFile.entries) {
                                if (entry.entryName.equals(childPath)) {
                                    childZipEntry = entry;
                                    break;
                                }
                            }
                        }
                        if (childZipEntry == null) {
                            // 如果没有名称匹配子路径的非目录 ZIP 文件条目，
                            // 则测试 ZIP 文件中是否有条目以子路径作为目录前缀
                            final String childPathPrefix = childPath + "/";
                            for (final FastZipEntry entry : parentLogicalZipFile.entries) {
                                if (entry.entryName.startsWith(childPathPrefix)) {
                                    isDirectory = true;
                                    break;
                                }
                            }
                        }
                        // 此时，要么 isDirectory 为 true，要么 childZipEntry 非 null

                        // 如果路径组件是目录，则它是包根
                        if (isDirectory) {
                            if (!childPath.isEmpty()) {
                                // 将目录路径添加到父 JAR 文件根相对路径集合中
                                // (这会产生副作用，即对于所有对父路径的引用，
                                // 将此父 JAR 文件根也添加到根集合中)
                                if (log != null) {
                                    log.log("Path " + childPath + " in jarfile " + parentLogicalZipFile
                                            + " is a directory, not a file -- using as package root");
                                }
                                parentLogicalZipFile.classpathRoots.add(childPath);
                            }
                            // 返回父逻辑 ZIP 文件，并将子路径作为包根
                            return new SimpleEntry<>(parentLogicalZipFile, childPath);
                        }

                        if (childZipEntry == null /* i.e. if (!isDirectory) */) {
                            throw new IOException(
                                    "Path " + childPath + " does not exist in jarfile " + parentLogicalZipFile);
                        }

                        // 如果嵌套 JAR 扫描被禁用，则不提取嵌套 JAR
                        if (!ScanConfig.scanNestedJars) {
                            throw new IOException(
                                    "Nested jar scanning is disabled -- skipping nested jar " + nestedJarPath);
                        }

                        // 子路径对应一个非目录 ZIP 条目，因此它必须是一个嵌套 JAR
                        // (因为非 JAR 的嵌套文件不能在 classpath 上使用)
                        // 如果嵌套 JAR 是已存储的，则将其映射为新的 ZipFileSlice；
                        // 如果它是已压缩的，则将其膨胀到 RAM 或临时文件，
                        // 然后在临时文件或 ByteBuffer 上创建新的 ZipFileSlice

                        // 将 ZIP 条目获取为 ZipFileSlice，可能膨胀到磁盘或 RAM

                        final ZipFileSlice childZipEntrySlice;
                        try {
                            childZipEntrySlice = fastZipEntryToZipFileSliceMap.get(childZipEntry, log);
                        } catch (final NullSingletonException e) {
                            throw new IOException(
                                    "Could not get child zip entry slice " + childZipEntry + " : " + e);
                        } catch (final NewInstanceException e) {
                            throw new IOException("Could not get child zip entry slice " + childZipEntry, e);
                        }

                        final LogNode zipSliceLog = log == null ? null
                                : log.log("Getting zipfile slice " + childZipEntrySlice + " for nested jar "
                                + childZipEntry.entryName);

                        // 获取或创建子 ZIP 文件的新 LogicalZipFile
                        LogicalZipFile childLogicalZipFile;
                        try {
                            childLogicalZipFile = zipFileSliceToLogicalZipFileMap.get(childZipEntrySlice,
                                    zipSliceLog);
                        } catch (final NullSingletonException e) {
                            throw new IOException(
                                    "Could not get child logical zipfile " + childZipEntrySlice + " : " + e);
                        } catch (final NewInstanceException e) {
                            throw new IOException("Could not get child logical zipfile " + childZipEntrySlice, e);
                        }

                        // 返回带有空包根的新逻辑 ZIP 文件
                        return new SimpleEntry<>(childLogicalZipFile, "");
                    }
                }
            };
    /**
     * 从 {@link ModuleRef} 到该模块的 {@link ModuleReaderProxy} 回收器的单例映射
     */
    public SingletonMap<ModuleRef, Pool<ModuleReaderProxy, IOException>, IOException> //
            moduleRefToModuleReaderProxyRecyclerMap = //
            new SingletonMap<ModuleRef, Pool<ModuleReaderProxy, IOException>, IOException>() {
                @Override
                public Pool<ModuleReaderProxy, IOException> newInstance(final ModuleRef moduleRef,
                                                                            final LogNode ignored) {
                    return new Pool<ModuleReaderProxy, IOException>() {
                        @Override
                        public ModuleReaderProxy newInstance() throws IOException {
                            return moduleRef.open();
                        }
                    };
                }
            };
    /** 中断检查器 */
    public InterruptionChecker interruptionChecker;
    /**
     * 从 ZIP 文件的 {@link File} 到该文件对应的 {@link PhysicalZipFile} 的单例映射，
     * 用于确保任何给定 ZIP 文件的 {@link RandomAccessFile} 和 {@link FileChannel} 只被打开一次
     */
    private SingletonMap<File, PhysicalZipFile, IOException> //
            canonicalFileToPhysicalZipFileMap = new SingletonMap<File, PhysicalZipFile, IOException>() {
        @Override
        public PhysicalZipFile newInstance(final File canonicalFile, final LogNode log) throws IOException {
            return new PhysicalZipFile(canonicalFile, JarReader.this, log);
        }
    };
    /**
     * 从 {@link FastZipEntry} 到 {@link ZipFileSlice} 的单例映射，
     * 包装的内容要么是 ZIP 条目数据(如果条目已存储)，
     * 要么是 ByteBuffer(如果 ZIP 条目已膨胀到内存)，
     * 要么是磁盘上的物理文件(如果 ZIP 条目已膨胀到临时文件)
     */
    private SingletonMap<FastZipEntry, ZipFileSlice, IOException> //
            fastZipEntryToZipFileSliceMap = new SingletonMap<FastZipEntry, ZipFileSlice, IOException>() {
        @Override
        public ZipFileSlice newInstance(final FastZipEntry childZipEntry, final LogNode log)
                throws IOException, InterruptedException {
            ZipFileSlice childZipEntrySlice;
            if (!childZipEntry.isDeflated) {
                // 子 ZIP 条目是一个已存储的嵌套 ZIP 文件 -- 将其包装在新的 ZipFileSlice 中
                // 希望嵌套 ZIP 文件是已存储的而非已压缩的，因为这是快速路径
                childZipEntrySlice = new ZipFileSlice(childZipEntry);

            } else {
                // 如果子条目已压缩(即对于已压缩的嵌套 ZIP 文件)，必须先膨胀
                // 条目的内容，然后才能读取其中央目录(大多数情况下嵌套 ZIP 文件
                // 是已存储的而非已压缩的，因此这种情况应该很罕见)
                if (log != null) {
                    log.log("Inflating nested zip entry: " + childZipEntry + " ; uncompressed size: "
                            + childZipEntry.uncompressedSize);
                }

                // 将子 ZIP 条目的 InputStream 读取到 RAM 缓冲区，或者如果太大则溢出到磁盘
                final PhysicalZipFile physicalZipFile = new PhysicalZipFile(childZipEntry.getSlice().open(),
                        childZipEntry.uncompressedSize >= 0L
                                && childZipEntry.uncompressedSize <= FileUtils.MAX_BUFFER_SIZE
                                ? (int) childZipEntry.uncompressedSize
                                : -1,
                        childZipEntry.entryName, JarReader.this, log);

                // 创建解压出的内部 ZIP 文件的新逻辑切片
                childZipEntrySlice = new ZipFileSlice(physicalZipFile, childZipEntry);
            }
            return childZipEntrySlice;
        }
    };
    /**
     * 从 {@link ZipFileSlice} 到该切片对应的 {@link LogicalZipFile} 的单例映射
     */
    private SingletonMap<ZipFileSlice, LogicalZipFile, IOException> //
            zipFileSliceToLogicalZipFileMap = new SingletonMap<ZipFileSlice, LogicalZipFile, IOException>() {
        @Override
        public LogicalZipFile newInstance(final ZipFileSlice zipFileSlice, final LogNode log)
                throws IOException, InterruptedException {
            // 读取 ZIP 文件的中央目录
            return new LogicalZipFile(zipFileSlice, JarReader.this, log,
                    ScanConfig.enableMultiReleaseVersions);
        }
    };
    /** {@link Inflater} 实例的回收器 */
    private Pool<RecyclableInflater, RuntimeException> //
            inflaterRecycler = new Pool<RecyclableInflater, RuntimeException>() {
        @Override
        public RecyclableInflater newInstance() throws RuntimeException {
            return new RecyclableInflater();
        }
    };
    /** 当前打开的 {@link FileSlice} 实例 */
    private Set<Slice> openSlices = Collections.newSetFromMap(new ConcurrentHashMap<Slice, Boolean>());

    // -------------------------------------------------------------------------------------------------------------
    /** 扫描过程中创建的所有临时文件 */
    private Set<File> tempFiles = Collections.newSetFromMap(new ConcurrentHashMap<File, Boolean>());

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 嵌套 JAR 的处理器
     *
     * @param ScanConfig
     *            {@link ScanConfig} 扫描规范
     * @param interruptionChecker
     *            中断检查器
     */
    public JarReader(final ScanConfig ScanConfig, final InterruptionChecker interruptionChecker,
                            final ReflectionUtils reflectionUtils) {
        this.ScanConfig = ScanConfig;
        this.interruptionChecker = interruptionChecker;
        this.reflectionUtils = reflectionUtils;
    }

    /**
     * 获取路径的叶子名称
     *
     * @param path
     *            路径
     * @return 叶子名称字符串
     */
    private static String leafname(final String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * 读取 {@link InputStream} 中的所有字节
     *
     * @param inputStream
     *            {@link InputStream} 输入流
     * @param uncompressedLengthHint
     *            从 {@link InputStream} 膨胀后数据的长度(如果已知)，否则为 -1L
     * @return {@link InputStream} 的内容，以字节数组形式返回
     * @throws IOException
     *             如果内容无法读取
     */
    public static byte[] readAllBytesAsArray(final InputStream inputStream, final long uncompressedLengthHint)
            throws IOException {
        if (uncompressedLengthHint > FileUtils.MAX_BUFFER_SIZE) {
            throw new IOException("InputStream is too large to read");
        }
        try (InputStream inptStream = inputStream) {
            final int bufferSize = uncompressedLengthHint < 1L
                    // 如果 fileSizeHint 为零或未知，使用默认缓冲区大小
                    ? DEFAULT_BUFFER_SIZE
                    // fileSizeHint 只是一个提示 -- 限制最大分配的缓冲区大小，
                    // 以防止无效的 ZipEntry 长度成为内存分配攻击向量
                    : Math.min((int) uncompressedLengthHint, MAX_INITIAL_BUFFER_SIZE);
            byte[] buf = new byte[bufferSize];
            int totBytesRead = 0;
            for (int bytesRead; ; ) {
                while ((bytesRead = inptStream.read(buf, totBytesRead, buf.length - totBytesRead)) > 0) {
                    // 填充缓冲区直到无法读取更多内容
                    totBytesRead += bytesRead;
                }
                if (bytesRead < 0) {
                    // 已达到流末尾，缓冲区未被填满
                    break;
                }

                // bytesRead == 0：要么缓冲区大小刚好正确且已达到流末尾，
                // 要么缓冲区太小需要尝试再读取一个字节来分辨是哪种情况
                final int extraByte = inptStream.read();
                if (extraByte == -1) {
                    // 已达到流末尾
                    break;
                }

                // 尚未达到流末尾需要增大缓冲区(翻倍)，并追加刚刚读取的额外字节
                if (buf.length == FileUtils.MAX_BUFFER_SIZE) {
                    throw new IOException("InputStream too large to read into array");
                }
                buf = Arrays.copyOf(buf, (int) Math.min(buf.length * 2L, FileUtils.MAX_BUFFER_SIZE));
                buf[totBytesRead++] = (byte) extraByte;
            }
            // 返回缓冲区及已读取的字节数
            return totBytesRead == buf.length ? buf : Arrays.copyOf(buf, totBytesRead);
        }
    }

    /**
     * 清理文件名
     *
     * @param filename
     *            原始文件名
     * @return 清理后的文件名
     */
    private String sanitizeFilename(final String filename) {
        return filename.replace('/', '_').replace('\\', '_').replace(':', '_').replace('?', '_').replace('&', '_')
                .replace('=', '_').replace(' ', '_');
    }

    /**
     * 创建临时文件，并标记为在退出时删除
     *
     * @param filePathBase
     *            用于派生临时文件名的路径
     * @param onlyUseLeafname
     *            如果为 true，则仅使用 filePath 的叶子名称来派生临时文件名
     * @return 临时 {@link File}
     * @throws IOException
     *             如果无法创建临时文件
     */
    public File makeTempFile(final String filePathBase, final boolean onlyUseLeafname) throws IOException {
        final File tempFile = File.createTempFile("ClassGraph--", TEMP_FILENAME_LEAF_SEPARATOR
                + sanitizeFilename(onlyUseLeafname ? leafname(filePathBase) : filePathBase));
        tempFile.deleteOnExit();
        tempFiles.add(tempFile);
        return tempFile;
    }

    /**
     * 尝试移除临时文件
     *
     * @param tempFile
     *            临时文件
     * @throws IOException
     *             如果无法移除临时文件
     * @throws SecurityException
     *             如果临时文件不可访问
     */
    void removeTempFile(final File tempFile) throws IOException, SecurityException {
        if (tempFiles.remove(tempFile)) {
            Files.delete(tempFile.toPath());
        } else {
            throw new IOException("Not a temp file: " + tempFile);
        }
    }

    /**
     * 将 {@link Slice} 标记为打开状态，以便在 {@link ScanResult} 关闭时可以将其关闭
     *
     * @param slice
     *            刚刚打开的 {@link Slice}
     * @throws IOException
     *             表示发生了 I/O 异常
     */
    public void markSliceAsOpen(final Slice slice) throws IOException {
        openSlices.add(slice);
    }

    /**
     * 将 {@link Slice} 标记为已关闭
     *
     * @param slice
     *            要关闭的 {@link Slice}
     */
    public void markSliceAsClosed(final Slice slice) {
        openSlices.remove(slice);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 从 URL 下载 JAR 到临时文件，或者如果临时目录不可写或已满，则下载到 ByteBuffer
     * 下载的 JAR 包装在 {@link PhysicalZipFile} 实例中返回
     *
     * @param jarURL
     *            JAR 的 URL
     * @param log
     *            日志
     * @return JAR 下载到的临时文件或 {@link ByteBuffer}，包装在 {@link PhysicalZipFile} 实例中
     * @throws IOException
     *             如果无法下载 JAR，或 JAR URL 格式错误
     * @throws InterruptedException
     *             如果线程被中断
     * @throws IllegalArgumentException
     *             如果临时目录不可写或空间不足以下载 JAR(此异常与 IOException 分开抛出，
     *             以便可以单独处理临时目录不可写的情况，即通过将 JAR 下载到 RAM 中的 ByteBuffer)
     */
    private PhysicalZipFile downloadJarFromURL(final String jarURL, final LogNode log)
            throws IOException, InterruptedException {
        URL url = null;
        try {
            url = new URL(jarURL);
        } catch (final MalformedURLException e1) {
            try {
                url = new URI(jarURL).toURL();
            } catch (final MalformedURLException | IllegalArgumentException | URISyntaxException e2) {
                throw new IOException("Could not parse URL: " + jarURL);
            }
        }

        final String scheme = url.getProtocol();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            // 检查此 URL 是否由文件系统支撑 -- 如果是，则不通过 URL 下载文件的副本；
            // 而是直接访问文件系统
            try {
                final Path path = Paths.get(url.toURI());
                // 如果 URL 的文件系统未注册，将抛出 FileSystemNotFoundException
                final FileSystem fs = path.getFileSystem();
                if (log != null) {
                    log.log("URL " + jarURL + " is backed by filesystem " + fs.getClass().getName());
                }
                // 将 Path 包装在 PhysicalZipFile 中并返回
                return new PhysicalZipFile(path, this, log);
            } catch (final IllegalArgumentException | SecurityException | URISyntaxException e) {
                throw new IOException("Could not convert URL to URI (" + e + "): " + url);
            } catch (final FileSystemNotFoundException e) {
                // 不是自定义文件系统
            }
        }
        try (final CloseableUrlConnection urlConn = new CloseableUrlConnection(url)) {
            long contentLengthHint = -1L;
            urlConn.conn.setConnectTimeout(HTTP_TIMEOUT);
            urlConn.conn.connect();
            if (urlConn.httpConn != null) {
                // 从 HTTP 头部获取内容长度(如果可用)
                if (urlConn.httpConn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException(
                            "Got response code " + urlConn.httpConn.getResponseCode() + " for URL " + url);
                }
            } else if ("file".equalsIgnoreCase(url.getProtocol())) {
                // 我们得到了一个 "file:" URL，这可能是因为自定义 URL 方案
                // 将其 URL 重写为 "file:" URL(参见 Issue400.java)
                try {
                    // 如果这是 "file:" URL，从 URL 获取文件并将其作为新的 PhysicalZipFile 返回
                    // (这避免了通过 InputStream 的方式)如果文件无法读取则抛出 IOException
                    final File file = Paths.get(url.toURI()).toFile();
                    return new PhysicalZipFile(file, this, log);

                } catch (final Exception e) {
                    // 穿透 -- 未知的 URL 类型
                }
            }
            // 尝试读取内容长度提示
            contentLengthHint = urlConn.conn.getContentLengthLong();
            if (contentLengthHint < -1L) {
                contentLengthHint = -1L;
            }
            // 从 URL 获取内容
            final LogNode subLog = log == null ? null : log.log("Downloading jar from URL " + jarURL);
            try (InputStream inputStream = urlConn.conn.getInputStream()) {
                // 从 URL 的 InputStream 获取 JAR 内容如果内容无法装入 RAM，则溢出到磁盘
                final PhysicalZipFile physicalZipFile = new PhysicalZipFile(inputStream, contentLengthHint, jarURL,
                        this, subLog);
                if (subLog != null) {
                    subLog.addElapsedTime();
                    subLog.log("***** Note that it is time-consuming to scan jars at non-\"file:\" URLs, "
                            + "the URL must be opened (possibly after an http(s) fetch) for every scan, "
                            + "and the same URL must also be separately opened by the ClassLoader *****");
                }
                return physicalZipFile;

            } catch (final MalformedURLException e) {
                throw new IOException("Malformed URL: " + jarURL);
            }
        }
    }

    /**
     * 用 {@link InflaterInputStream} 包装 {@link InputStream}，同时回收 {@link Inflater} 实例
     *
     * @param rawInputStream
     *            原始输入流
     * @return 膨胀输入流
     * @throws IOException
     *             表示发生了 I/O 异常
     */
    public InputStream openInflaterInputStream(final InputStream rawInputStream) throws IOException {
        if (closed.get()) {
            throw new IOException("Already closed");
        }
        @SuppressWarnings("resource") final RecyclableInflater recyclableInflater = inflaterRecycler.acquire();
        final Inflater inflater = recyclableInflater.getInflater();
        return new InputStream() {
            private static final int INFLATE_BUF_SIZE = 8192;
            // 生成 Inflater 实例，nowrap 设置为 true(ZIP 条目需要此设置)
            private final AtomicBoolean closed = new AtomicBoolean();
            private final byte[] buf = new byte[INFLATE_BUF_SIZE];

            @Override
            public int read() throws IOException {
                if (closed.get()) {
                    throw new IOException("Already closed");
                } else if (inflater.finished()) {
                    return -1;
                }
                final int numDeflatedBytesRead = read(buf, 0, 1);
                if (numDeflatedBytesRead < 0) {
                    return -1;
                } else {
                    return buf[0] & 0xff;
                }
            }

            @Override
            public int read(final byte[] outBuf, final int off, final int len) throws IOException {
                if (closed.get()) {
                    throw new IOException("Already closed");
                } else if (len < 0) {
                    throw new IllegalArgumentException("len cannot be negative");
                } else if (len == 0) {
                    return 0;
                }
                try {
                    // 持续从 rawInputStream 获取数据，直到缓冲区填满或 inflater 完成
                    int totInflatedBytes = 0;
                    while (!inflater.finished() && totInflatedBytes < len) {
                        final int numInflatedBytes = inflater.inflate(outBuf, off + totInflatedBytes,
                                len - totInflatedBytes);
                        if (numInflatedBytes == 0) {
                            if (inflater.needsDictionary()) {
                                // 对于 JAR 文件不应发生此情况
                                throw new IOException("Inflater needs preset dictionary");
                            } else if (inflater.needsInput()) {
                                // 从原始 InputStream 读取一块数据
                                final int numRawBytesRead = rawInputStream.read(buf, 0, buf.length);
                                if (numRawBytesRead == -1) {
                                    // 使用 "nowrap" Inflater 选项时，输入流末尾需要一个额外的哑字节
                                    // 参见：ZipFile.ZipFileInflaterInputStream.fill()
                                    buf[0] = (byte) 0;
                                    inflater.setInput(buf, 0, 1);
                                } else {
                                    // 压缩(deflate)数据块
                                    inflater.setInput(buf, 0, numRawBytesRead);
                                }
                            }
                        } else {
                            totInflatedBytes += numInflatedBytes;
                        }
                    }
                    if (totInflatedBytes == 0) {
                        // 如果没有膨胀任何字节，按照 read() API 约定返回 -1
                        return -1;
                    }
                    return totInflatedBytes;

                } catch (final DataFormatException e) {
                    throw new ZipException(
                            e.getMessage() != null ? e.getMessage() : "Invalid deflated zip entry data");
                }
            }

            @Override
            public long skip(final long numToSkip) throws IOException {
                if (closed.get()) {
                    throw new IOException("Already closed");
                } else if (numToSkip < 0) {
                    throw new IllegalArgumentException("numToSkip cannot be negative");
                } else if (numToSkip == 0) {
                    return 0;
                } else if (inflater.finished()) {
                    return -1;
                }
                long totBytesSkipped = 0L;
                for (; ; ) {
                    final int readLen = (int) Math.min(numToSkip - totBytesSkipped, buf.length);
                    final int numBytesRead = read(buf, 0, readLen);
                    if (numBytesRead > 0) {
                        totBytesSkipped += numBytesRead;
                    } else {
                        break;
                    }
                }
                return totBytesSkipped;
            }

            @Override
            public int available() throws IOException {
                if (closed.get()) {
                    throw new IOException("Already closed");
                }
                // 我们不知道有多少字节可用，但根据 API 约定，如果仍有输入，
                // 必须返回大于零的值希望没有代码依赖此方法并最终一次只读取一个字节
                return inflater.finished() ? 0 : 1;
            }

            @Override
            public synchronized void mark(final int readlimit) {
                throw new IllegalArgumentException("Not supported");
            }

            @Override
            public synchronized void reset() throws IOException {
                throw new IllegalArgumentException("Not supported");
            }

            @Override
            public boolean markSupported() {
                return false;
            }

            @Override
            public void close() {
                if (!closed.getAndSet(true)) {
                    try {
                        rawInputStream.close();
                    } catch (final Exception e) {
                        // 忽略
                    }
                    // 重置并回收 inflater 实例
                    inflaterRecycler.recycle(recyclableInflater);
                }
            }
        };
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 读取 {@link InputStream} 中的所有字节，如果超过最大缓冲区大小则溢出到磁盘上的临时文件
     *
     * @param inputStream
     *            要从中读取的 {@link InputStream}
     * @param tempFileBaseName
     *            inputStream 打开的源 URL 或 ZIP 条目(用于命名临时文件，如果需要)
     * @param inputStreamLengthHint
     *            inputStream 的长度(如果已知)，否则为 -1L
     * @param log
     *            日志
     * @return 如果 {@link InputStream} 可以读入字节数组，则返回 {@link ArraySlice}
     *         如果失败且 {@link InputStream} 溢出到磁盘，则返回 {@link FileSlice}
     *
     * @throws IOException
     *             如果内容无法读取
     */
    public Slice readAllBytesWithSpilloverToDisk(final InputStream inputStream, final String tempFileBaseName,
                                                 final long inputStreamLengthHint, final LogNode log) throws IOException {
        // 在切片上打开 InflaterInputStream
        try (InputStream inptStream = inputStream) {
            if (inputStreamLengthHint <= ScanConfig.maxBufferedJarRAMSize) {
                // inputStreamLengthHint 未知 (-1) 或小于 ScanConfig.maxBufferedJarRAMSize，
                // 因此尝试从 InputStream 读取到大小为 ScanConfig.maxBufferedJarRAMSize
                // 或 inputStreamLengthHint 的数组中此外，如果 inputStreamLengthHint == 0
                // (可能有效也可能无效)，使用 16kB 的缓冲区大小，以防此值有误但文件仍然较小，
                // 从而避免溢出到磁盘
                final int bufSize = inputStreamLengthHint == -1L ? ScanConfig.maxBufferedJarRAMSize
                        : inputStreamLengthHint == 0L ? 16384
                        : Math.min((int) inputStreamLengthHint, ScanConfig.maxBufferedJarRAMSize);
                byte[] buf = new byte[bufSize];
                final int bufLength = buf.length;

                int bufBytesUsed = 0;
                int bytesRead = 0;
                while ((bytesRead = inptStream.read(buf, bufBytesUsed, bufLength - bufBytesUsed)) > 0) {
                    // 填充缓冲区直到无法读取更多内容
                    bufBytesUsed += bytesRead;
                }
                if (bytesRead == 0) {
                    // 如果 bytesRead 是 0 而不是 -1，我们需要探测 InputStream(通过再读取一个字节)，
                    // 以查看 inputStreamHint 是否低估了流的实际长度
                    final byte[] overflowBuf = new byte[1];
                    final int overflowBufBytesUsed = inptStream.read(overflowBuf, 0, 1);
                    if (overflowBufBytesUsed == 1) {
                        // 我们能够读取到额外一个字节，说明仍未到达流末尾，
                        // 需要溢出到磁盘，因为 buf 已满
                        return spillToDisk(inptStream, tempFileBaseName, buf, overflowBuf, log);
                    }
                    // else (overflowBufBytesUsed == -1)，说明已到达流末尾 => 不溢出到磁盘
                }
                // 成功到达流末尾
                if (bufBytesUsed < buf.length) {
                    // 如果需要则裁剪数组(当 inputStreamLengthHint 为 -1 或高估了
                    // InputStream 长度时需要这样做)
                    buf = Arrays.copyOf(buf, bufBytesUsed);
                }
                // 将 buf 作为新的 ArraySlice 返回
                return new ArraySlice(buf, /* isDeflatedZipEntry = */ false, /* inflatedSizeHint = */
                        0L, this);

            }
            // inputStreamLengthHint 大于 ScanConfig.maxJarRamSize，因此立即溢出到磁盘
            return spillToDisk(inptStream, tempFileBaseName, /* buf = */ null, /* overflowBuf = */ null, log);
        }
    }

    /**
     * 如果流太大无法放入 RAM，则将 {@link InputStream} 溢出到磁盘
     *
     * @param inputStream
     *            {@link InputStream} 输入流
     * @param tempFileBaseName
     *            临时文件名的词干
     * @param buf
     *            要写入文件开头的第一个缓冲区，如果没有则为 null
     * @param overflowBuf
     *            要写入文件开头的第二个缓冲区，如果没有则为 null(应与 buf 具有相同的空值性)
     * @param log
     *            日志
     * @return 文件切片
     * @throws IOException
     *             如果创建或写入临时文件时出现任何问题
     */
    private FileSlice spillToDisk(final InputStream inputStream, final String tempFileBaseName, final byte[] buf,
                                  final byte[] overflowBuf, final LogNode log) throws IOException {
        // 创建临时文件
        File tempFile;
        try {
            tempFile = makeTempFile(tempFileBaseName, /* onlyUseLeafname = */ true);
        } catch (final IOException e) {
            throw new IOException("Could not create temporary file: " + e.getMessage());
        }
        if (log != null) {
            log.log("Could not fit InputStream content into max RAM buffer size, saving to temporary file: "
                    + tempFileBaseName + " -> " + tempFile);
        }

        // 将迄今为止读取的所有内容以及 InputStream 的其余部分复制到临时文件
        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            // 将已读取的缓冲字节写入临时文件(如果有读取任何内容)
            if (buf != null) {
                outputStream.write(buf);
                outputStream.write(overflowBuf);
            }
            // 将 InputStream 的其余部分复制到文件
            final byte[] copyBuf = new byte[8192];
            for (int bytesRead; (bytesRead = inputStream.read(copyBuf, 0, copyBuf.length)) > 0; ) {
                outputStream.write(copyBuf, 0, bytesRead);
            }
        }

        // 为临时文件返回新的 FileSlice
        return new FileSlice(tempFile, this, log);
    }

    /**
     * 关闭 ZIP 文件、模块和回收器，并删除临时文件由 {@link ScanResult#close()} 调用
     *
     * @param log
     *            日志
     */
    public void close(final LogNode log) {
        if (!closed.getAndSet(true)) {
            boolean interrupted = false;
            if (moduleRefToModuleReaderProxyRecyclerMap != null) {
                boolean completedWithoutInterruption = false;
                while (!completedWithoutInterruption) {
                    try {
                        for (final Pool<ModuleReaderProxy, IOException> Pool : //
                                moduleRefToModuleReaderProxyRecyclerMap.values()) {
                            Pool.forceClose();
                        }
                        completedWithoutInterruption = true;
                    } catch (final InterruptedException e) {
                        // 如果被中断则重试
                        interrupted = true;
                    }
                }
                moduleRefToModuleReaderProxyRecyclerMap.clear();
                moduleRefToModuleReaderProxyRecyclerMap = null;
            }
            if (zipFileSliceToLogicalZipFileMap != null) {
                zipFileSliceToLogicalZipFileMap.clear();
                zipFileSliceToLogicalZipFileMap = null;
            }
            if (nestedPathToLogicalZipFileAndPackageRootMap != null) {
                nestedPathToLogicalZipFileAndPackageRootMap.clear();
                nestedPathToLogicalZipFileAndPackageRootMap = null;
            }
            if (canonicalFileToPhysicalZipFileMap != null) {
                canonicalFileToPhysicalZipFileMap.clear();
                canonicalFileToPhysicalZipFileMap = null;
            }
            if (fastZipEntryToZipFileSliceMap != null) {
                fastZipEntryToZipFileSliceMap.clear();
                fastZipEntryToZipFileSliceMap = null;
            }
            if (openSlices != null) {
                while (!openSlices.isEmpty()) {
                    for (final Slice slice : new ArrayList<>(openSlices)) {
                        try {
                            slice.close();
                        } catch (final IOException e) {
                            // 忽略
                        }
                        markSliceAsClosed(slice);
                    }
                }
                openSlices.clear();
                openSlices = null;
            }
            if (inflaterRecycler != null) {
                inflaterRecycler.forceClose();
            }
            // 临时文件必须最后删除，在所有 PhysicalZipFile 关闭且文件取消映射之后
            if (tempFiles != null) {
                final LogNode rmLog = tempFiles.isEmpty() || log == null ? null
                        : log.log("Removing temporary files");
                while (!tempFiles.isEmpty()) {
                    for (final File tempFile : new ArrayList<>(tempFiles)) {
                        try {
                            removeTempFile(tempFile);
                        } catch (IOException | SecurityException e) {
                            if (rmLog != null) {
                                rmLog.log("Removing temporary file failed: " + tempFile);
                            }
                        }
                    }
                }
                tempFiles = null;
            }
            if (interrupted) {
                interruptionChecker.interrupt();
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    public void runFinalizationMethod() {
        if (runFinalizationMethod == null) {
            runFinalizationMethod = reflectionUtils.staticMethodForNameOrNull("System", "runFinalization");
        }
        if (runFinalizationMethod != null) {
            try {
                // 调用 System.runFinalization()(在 JDK 18 中已弃用)
                runFinalizationMethod.invoke(null);
            } catch (final Throwable t) {
                // 忽略
            }
        }
    }

    public void closeDirectByteBuffer(final ByteBuffer backingByteBuffer) {
        FileUtils.closeDirectByteBuffer(backingByteBuffer, reflectionUtils, /* log = */ null);
    }

    private static class CloseableUrlConnection implements AutoCloseable {
        public final URLConnection conn;
        public final HttpURLConnection httpConn;

        public CloseableUrlConnection(final URL url) throws IOException {
            conn = url.openConnection();
            httpConn = conn instanceof HttpURLConnection ? (HttpURLConnection) conn : null;
        }

        @Override
        public void close() {
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
    }

    /**
     * 包装类，允许 {@link Inflater} 实例被重置以供复用，然后由 {@link Pool} 回收
     */
    private static class RecyclableInflater implements Resettable, AutoCloseable {
        /**
         * 创建新的 {@link Inflater} 实例，使用 "nowrap" 选项(ZIP 文件条目需要此选项)
         */
        private final Inflater inflater = new Inflater(/* nowrap = */ true);

        /**
         * 获取 {@link Inflater} 实例
         *
         * @return {@link Inflater} 实例
         */
        public Inflater getInflater() {
            return inflater;
        }

        /**
         * 当 {@link Inflater} 实例被回收时调用，用于重置 inflater 以便接受新输入
         */
        @Override
        public void reset() {
            inflater.reset();
        }

        /**
         * 当 {@link Pool} 实例关闭时调用，用于销毁 {@link Inflater} 实例
         */
        @Override
        public void close() {
            inflater.end();
        }
    }
}
