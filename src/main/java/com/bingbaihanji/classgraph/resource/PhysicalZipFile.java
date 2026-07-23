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

import com.bingbaihanji.classgraph.resource.ArraySlice;
import com.bingbaihanji.classgraph.resource.FileSlice;
import com.bingbaihanji.classgraph.resource.PathSlice;
import com.bingbaihanji.classgraph.resource.Slice;
import com.bingbaihanji.classgraph.util.FastPathResolver;
import com.bingbaihanji.classgraph.util.FileUtils;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/** 物理 ZIP 文件，通过 {@link FileChannel} 进行内存映射(mmap) */
class PhysicalZipFile {
    /** ZIP 文件的路径 */
    private final String pathStr;
    /** 该 ZIP 文件的 {@link Slice} */
    Slice slice;
    /** 嵌套 JAR 处理器 */
    JarReader JarReader;
    /** 支撑此 {@link PhysicalZipFile} 的 {@link Path}(如果有) */
    private Path path;
    /** 支撑此 {@link PhysicalZipFile} 的 {@link File}(如果有) */
    private File file;
    /** 缓存的 hashCode */
    private volatile int hashCode;

    /**
     * 从磁盘上的文件构造一个 {@link PhysicalZipFile}
     *
     * @param file
     *            文件
     * @param JarReader
     *            嵌套 JAR 处理器
     * @param log
     *            日志
     * @throws IOException
     *            如果发生 I/O 异常
     */
    PhysicalZipFile(final File file, final JarReader JarReader, final LogNode log)
            throws IOException {
        this.JarReader = JarReader;
        this.file = file;
        this.pathStr = FastPathResolver.resolve(FileUtils.currDirPath(), file.getPath());
        this.slice = new FileSlice(file, JarReader, log);
    }

    /**
     * 从 {@link Path} 构造一个 {@link PhysicalZipFile}
     *
     * @param path
     *            路径
     * @param JarReader
     *            嵌套 JAR 处理器
     * @param log
     *            日志
     * @throws IOException
     *            如果发生 I/O 异常
     */
    PhysicalZipFile(final Path path, final JarReader JarReader, final LogNode log)
            throws IOException {
        this.JarReader = JarReader;
        this.path = path;
        this.pathStr = FastPathResolver.resolve(FileUtils.currDirPath(), path.toString());
        this.slice = new PathSlice(path, JarReader);
    }

    /**
     * 从字节数组构造一个 {@link PhysicalZipFile}
     *
     * @param arr
     *            包含 ZIP 文件内容的数组
     * @param outermostFile
     *            最外层的文件
     * @param pathStr
     *            路径
     * @param JarReader
     *            嵌套 JAR 处理器
     * @throws IOException
     *            如果发生 I/O 异常
     */
    PhysicalZipFile(final byte[] arr, final File outermostFile, final String pathStr,
                    final JarReader JarReader) throws IOException {
        this.JarReader = JarReader;
        this.file = outermostFile;
        this.pathStr = pathStr;
        this.slice = new ArraySlice(arr, /* isDeflatedZipEntry = */ false, /* inflatedSizeHint = */ 0L,
                JarReader);
    }

    /**
     * 通过将 {@link InputStream} 读入 RAM 中的数组来构造 {@link PhysicalZipFile}，如果
     * {@link InputStream} 过长则溢出到磁盘
     *
     * @param inputStream
     *            输入流
     * @param inputStreamLengthHint
     *            要从 inputStream 读取的字节数，如果未知则为 -1
     * @param pathStr
     *            InputStream 打开的源 URL，或此项在父 ZIP 文件中的 ZIP 条目路径
     * @param JarReader
     *            嵌套 JAR 处理器
     * @param log
     *            日志
     * @throws IOException
     *            如果发生 I/O 异常
     */
    PhysicalZipFile(final InputStream inputStream, final long inputStreamLengthHint, final String pathStr,
                    final JarReader JarReader, final LogNode log) throws IOException {
        this.JarReader = JarReader;
        this.pathStr = pathStr;
        // 尝试将 InputStream 下载到字节数组如果成功，将产生一个 ArraySlice
        // 如果失败，InputStream 将溢出到磁盘，产生一个 FileSlice
        this.slice = JarReader.readAllBytesWithSpilloverToDisk(inputStream, /* tempFileBaseName = */ pathStr,
                inputStreamLengthHint, log);
        this.file = this.slice instanceof FileSlice ? ((FileSlice) this.slice).file : null;
    }

    /**
     * 获取此 PhysicalZipFile 最外层 JAR 文件的 {@link Path}
     *
     * @return 此 PhysicalZipFile 最外层 JAR 文件的 {@link Path}，如果此文件是从 URL 直接下载到 RAM，
     *         或由 {@link File} 支撑，则返回 null
     */
    public Path getPath() {
        return path;
    }

    /**
     * 获取此 PhysicalZipFile 最外层 JAR 文件的 {@link File}
     *
     * @return 此 PhysicalZipFile 最外层 JAR 文件的 {@link File}，如果此文件是从 URL 直接下载到 RAM，
     *         或由 {@link Path} 支撑，则返回 null
     */
    public File getFile() {
        return file;
    }

    /**
     * 获取此 PhysicalZipFile 的路径：如果是文件支撑的，则为文件路径；如果是内存支撑的，则为复合嵌套 JAR 路径
     *
     * @return 此 PhysicalZipFile 的路径：如果是文件支撑的，则为文件路径；如果是内存支撑的，则为复合嵌套 JAR 路径
     */
    public String getPathStr() {
        return pathStr;
    }

    /**
     * 获取映射文件的长度，如果包装了 ByteBuffer，则返回其初始剩余字节数
     *
     * @return 映射文件的长度
     */
    public long length() {
        return slice.sliceLength;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = pathStr.hashCode();
            if (hashCode == 0) {
                hashCode = 1;
            }
        }
        return hashCode;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof PhysicalZipFile)) {
            return false;
        }
        final PhysicalZipFile other = (PhysicalZipFile) o;
        return pathStr.equals(other.pathStr);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return pathStr;
    }
}