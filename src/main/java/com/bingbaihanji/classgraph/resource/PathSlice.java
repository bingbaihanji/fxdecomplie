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
 * Copyright (c) 2020 Luke Hutchison
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

import com.bingbaihanji.classgraph.scan.ClassGraph;
import com.bingbaihanji.classgraph.util.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

/** {@link Path} 切片 */
public class PathSlice extends Slice {
    /** {@link Path} 路径 */
    public final Path path;

    /** 文件长度 */
    private final long fileLength;
    /** 如果这是顶级文件切片则为 true */
    private final boolean isTopLevelFileSlice;
    /** 如果已调用 {@link #close} 则为 true */
    private final AtomicBoolean isClosed = new AtomicBoolean();
    /** 在 {@link Path} 上打开的 {@link FileChannel} */
    private volatile FileChannel fileChannel;

    /**
     * 用于将文件的一个范围视为切片的构造函数
     *
     * @param parentSlice
     *            父切片
     * @param offset
     *            子切片在父切片中的偏移量
     * @param length
     *            子切片的长度
     * @param isDeflatedZipEntry
     *            如果这是压缩的 zip 条目则为 true
     * @param inflatedLengthHint
     *            压缩 zip 条目的未压缩大小，未知为 -1，如果是非压缩 zip 条目则为 0
     * @param JarReader
     *            嵌套 jar 处理器
     */
    private PathSlice(final PathSlice parentSlice, final long offset, final long length,
                      final boolean isDeflatedZipEntry, final long inflatedLengthHint,
                      final JarReader JarReader) {
        super(parentSlice, offset, length, isDeflatedZipEntry, inflatedLengthHint, JarReader);

        this.path = parentSlice.path;
        this.fileChannel = parentSlice.fileChannel;
        this.fileLength = parentSlice.fileLength;
        this.isTopLevelFileSlice = false;

        // 仅将顶级文件切片标记为打开状态(子切片不需要标记为打开状态，因为
        // 它们不需要被关闭，它们只是复制顶级切片的资源引用)
    }

    /**
     * 顶级文件切片的构造函数
     *
     * @param path
     *            路径
     * @param isDeflatedZipEntry
     *            如果这是压缩的 zip 条目则为 true
     * @param inflatedLengthHint
     *            压缩 zip 条目的未压缩大小，未知为 -1，如果是非压缩 zip 条目则为 0
     * @param JarReader
     *            嵌套 jar 处理器
     * @throws IOException
     *             如果文件无法打开
     */
    public PathSlice(final Path path, final boolean isDeflatedZipEntry, final long inflatedLengthHint,
                     final JarReader JarReader) throws IOException {
        this(path, isDeflatedZipEntry, inflatedLengthHint, JarReader, true);
    }

    /**
     * 顶级文件切片的构造函数
     *
     * @param path
     *            路径
     * @param isDeflatedZipEntry
     *            如果这是压缩的 zip 条目则为 true
     * @param inflatedLengthHint
     *            压缩 zip 条目的未压缩大小，未知为 -1，如果是非压缩 zip 条目则为 0
     * @param JarReader
     *            嵌套 jar 处理器
     * @param checkAccess
     *            是否需要检查读取权限以及是否为文件
     * @throws IOException
     *             如果文件无法打开
     */
    public PathSlice(final Path path, final boolean isDeflatedZipEntry, final long inflatedLengthHint,
                     final JarReader JarReader, final boolean checkAccess) throws IOException {
        super(0L, isDeflatedZipEntry, inflatedLengthHint, JarReader);

        if (checkAccess) {
            // 确保文件可读且是普通文件
            FileUtils.checkCanReadAndIsFile(path);
        }

        this.path = path;
        this.fileChannel = FileChannel.open(path, StandardOpenOption.READ);
        this.fileLength = fileChannel.size();
        this.isTopLevelFileSlice = true;

        // 调用 super 时不得不使用 0L 作为 sliceLength，因为那时 FileChannel 尚未打开
        // => 现在更新 sliceLength
        this.sliceLength = fileLength;

        // 将顶级切片标记为打开状态
        JarReader.markSliceAsOpen(this);
    }

    /**
     * 顶级文件切片的构造函数
     *
     * @param path
     *            路径
     * @param JarReader
     *            嵌套 jar 处理器
     * @throws IOException
     *             如果文件无法打开
     */
    public PathSlice(final Path path, final JarReader JarReader) throws IOException {
        this(path, /* isDeflatedZipEntry = */ false, /* inflatedSizeHint = */ 0L, JarReader);
    }

    /**
     * 切片文件
     *
     * @param offset
     *            子切片在父切片中的偏移量
     * @param length
     *            子切片的长度
     * @param isDeflatedZipEntry
     *            如果这是压缩的 zip 条目则为 true
     * @param inflatedLengthHint
     *            压缩 zip 条目的未压缩大小，未知为 -1，如果是非压缩 zip 条目则为 0
     * @return 切片
     */
    @Override
    public Slice slice(final long offset, final long length, final boolean isDeflatedZipEntry,
                       final long inflatedLengthHint) {
        if (this.isDeflatedZipEntry) {
            throw new IllegalArgumentException("Cannot slice a deflated zip entry");
        }
        return new PathSlice(this, offset, length, isDeflatedZipEntry, inflatedLengthHint, JarReader);
    }

    /**
     * 直接从 FileChannel 读取(慢速路径，但可以处理大于 2GB 的文件)
     *
     * @return 随机访问读取器
     */
    @Override
    public RandomAccessReader randomAccessReader() {
        // 返回使用 FileChannel 的 RandomAccessReader
        return new RandomAccessFileChannelReader(fileChannel, sliceStartPos, sliceLength);
    }

    /**
     * 将切片作为字节数组加载
     *
     * @return 字节数组
     * @throws IOException
     *             如果发生 I/O 异常
     */
    @Override
    public byte[] load() throws IOException {
        if (isDeflatedZipEntry) {
            // 如果已压缩，则解压到内存中
            if (inflatedLengthHint > FileUtils.MAX_BUFFER_SIZE) {
                throw new IOException("Uncompressed size is larger than 2GB");
            }
            try (InputStream inputStream = open()) {
                return JarReader.readAllBytesAsArray(inputStream, inflatedLengthHint);
            }
        } else {
            // 从 FileChannel 复制到字节数组
            if (sliceLength > FileUtils.MAX_BUFFER_SIZE) {
                throw new IOException("File is larger than 2GB");
            }
            final RandomAccessReader reader = randomAccessReader();
            final byte[] content = new byte[(int) sliceLength];
            if (reader.read(0, content, 0, content.length) < content.length) {
                // 不应发生
                throw new IOException("File is truncated");
            }
            return content;
        }
    }

    /**
     * 将切片读入 {@link ByteBuffer}(或者，如果调用了 {@link ClassGraph#withMemoryMapping()}，
     * 则将切片内存映射到 {@link MappedByteBuffer})
     *
     * @return 字节缓冲区
     * @throws IOException
     *             如果发生 I/O 异常
     */
    @Override
    public ByteBuffer read() throws IOException {
        if (isDeflatedZipEntry) {
            // 如果已压缩，则解压到内存中(遗憾的是，没有可以按需解压部分流的懒加载
            // ByteBuffer，因此我们不得不解压整个 zip 条目)
            if (inflatedLengthHint > FileUtils.MAX_BUFFER_SIZE) {
                throw new IOException("Uncompressed size is larger than 2GB");
            }
            return ByteBuffer.wrap(load());
        }
        // 从 FileChannel 复制到字节数组，然后包装成 ByteBuffer
        if (sliceLength > FileUtils.MAX_BUFFER_SIZE) {
            throw new IOException("File is larger than 2GB");
        }
        return ByteBuffer.wrap(load());
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /** 关闭切片取消映射所有后备 {@link MappedByteBuffer} */
    @Override
    public void close() {
        if (!isClosed.getAndSet(true)) {
            if (isTopLevelFileSlice && fileChannel != null) {
                // 仅在顶级文件切片中关闭 FileChannel，这样它只关闭一次
                try {
                    // 关闭 raf 也会关闭关联的 FileChannel
                    fileChannel.close();
                } catch (final IOException e) {
                    // 忽略
                }
                fileChannel = null;
            }
            fileChannel = null;
            JarReader.markSliceAsClosed(this);
        }
    }
}
