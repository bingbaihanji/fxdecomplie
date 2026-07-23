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
import com.bingbaihanji.classgraph.resource.JarReader;
import com.bingbaihanji.classgraph.resource.RandomAccessByteBufferReader;
import com.bingbaihanji.classgraph.resource.RandomAccessFileChannelReader;
import com.bingbaihanji.classgraph.resource.RandomAccessReader;
import com.bingbaihanji.classgraph.util.FileUtils;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.concurrent.atomic.AtomicBoolean;

/** {@link File} 切片 */
public class FileSlice extends Slice {
    /** {@link File} 文件 */
    public final File file;
    /** 文件长度 */
    private final long fileLength;
    /** 如果这是顶级文件切片则为 true */
    private final boolean isTopLevelFileSlice;
    /** 如果已调用 {@link #close} 则为 true */
    private final AtomicBoolean isClosed = new AtomicBoolean();
    /** 在 {@link File} 上打开的 {@link RandomAccessFile} */
    public RandomAccessFile raf;
    /** 文件通道 */
    private FileChannel fileChannel;
    /** 后备字节缓冲区(如果有) */
    private java.nio.ByteBuffer backingByteBuffer;

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
    private FileSlice(final FileSlice parentSlice, final long offset, final long length,
                      final boolean isDeflatedZipEntry, final long inflatedLengthHint,
                      final JarReader JarReader) {
        super(parentSlice, offset, length, isDeflatedZipEntry, inflatedLengthHint, JarReader);
        this.file = parentSlice.file;
        this.raf = parentSlice.raf;
        this.fileChannel = parentSlice.fileChannel;
        this.fileLength = parentSlice.fileLength;
        this.isTopLevelFileSlice = false;

        if (parentSlice.backingByteBuffer != null) {
            // 如果存在后备字节缓冲区，则复制并切片它
            this.backingByteBuffer = parentSlice.backingByteBuffer.duplicate();
            ((Buffer) this.backingByteBuffer).position((int) sliceStartPos);
            ((Buffer) this.backingByteBuffer).limit((int) (sliceStartPos + sliceLength));
        }

        // 仅将顶级文件切片标记为打开状态(子切片不需要标记为打开状态，因为
        // 它们不需要被关闭，它们只是复制顶级切片的资源引用)
    }

    /**
     * 顶级文件切片的构造函数
     *
     * @param file
     *            文件
     * @param isDeflatedZipEntry
     *            如果这是压缩的 zip 条目则为 true
     * @param inflatedLengthHint
     *            压缩 zip 条目的未压缩大小，未知为 -1，如果是非压缩 zip 条目则为 0
     * @param JarReader
     *            嵌套 jar 处理器
     * @param log
     *            日志
     * @throws IOException
     *             如果文件无法打开
     */
    public FileSlice(final File file, final boolean isDeflatedZipEntry, final long inflatedLengthHint,
                     final JarReader JarReader, final LogNode log) throws IOException {
        super(file.length(), isDeflatedZipEntry, inflatedLengthHint, JarReader);
        // 确保文件可读且是普通文件
        FileUtils.checkCanReadAndIsFile(file);
        this.file = file;
        this.raf = new RandomAccessFile(file, "r");
        this.fileChannel = raf.getChannel();
        this.fileLength = file.length();
        this.isTopLevelFileSlice = true;

        if (JarReader.ScanConfig.enableMemoryMapping) {
            // TODO: for JDK 24+, use the new Arena API to memory-map the file to a MemorySegment:
            // https://docs.oracle.com/en/java/javase/22/docs//api/java.base/java/nio/channels/FileChannel.html#map(java.nio.channels.FileChannel.MapMode,long,long,java.lang.foreign.Arena)
            try {
                // 尝试映射文件(某些操作系统在文件无法映射时抛出 OutOfMemoryError，
                // 有些则抛出 IOException)
                backingByteBuffer = fileChannel.map(MapMode.READ_ONLY, 0L, fileLength);
            } catch (IOException | OutOfMemoryError e) {
                // 尝试运行垃圾回收，然后再次尝试映射文件
                System.gc();
                JarReader.runFinalizationMethod();
                try {
                    backingByteBuffer = fileChannel.map(MapMode.READ_ONLY, 0L, fileLength);
                } catch (IOException | OutOfMemoryError e2) {
                    if (log != null) {
                        log.log("File " + file + " cannot be memory mapped: " + e2
                                + " (using RandomAccessFile API instead)");
                    }
                    // 穿透 —— 将改用 RandomAccessFile API
                }
            }
        }

        // 将顶级切片标记为打开状态
        JarReader.markSliceAsOpen(this);
    }

    /**
     * 顶级文件切片的构造函数
     *
     * @param file
     *            文件
     * @param JarReader
     *            嵌套 jar 处理器
     * @param log
     *            日志
     * @throws IOException
     *             如果文件无法打开
     */
    public FileSlice(final File file, final JarReader JarReader, final LogNode log)
            throws IOException {
        this(file, /* isDeflatedZipEntry = */ false, /* inflatedSizeHint = */ 0L, JarReader, log);
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
        return new FileSlice(this, offset, length, isDeflatedZipEntry, inflatedLengthHint, JarReader);
    }

    /**
     * 直接从 FileChannel 读取(慢速路径，但可以处理大于 2GB 的文件)
     *
     * @return 随机访问读取器
     */
    @Override
    public RandomAccessReader randomAccessReader() {
        if (backingByteBuffer == null) {
            // 如果文件未被 mmap，返回使用 FileChannel 的 RandomAccessReader
            return new RandomAccessFileChannelReader(fileChannel, sliceStartPos, sliceLength);
        } else {
            // 如果文件已被 mmap，返回使用 ByteBuffer 的 RandomAccessReader
            return new RandomAccessByteBufferReader(backingByteBuffer, sliceStartPos, sliceLength);
        }
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
            // 从 RandomAccessFile 或 MappedByteBuffer 复制到字节数组
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
     * 将切片读入 {@link ByteBuffer}(或者，如果调用了 {@link ClassGraph#enableMemoryMapping()}，
     * 则将切片内存映射到 {@link MappedByteBuffer})
     *
     * @return 字节缓冲区
     * @throws IOException
     *             如果发生 I/O 异常
     */
    @Override
    public java.nio.ByteBuffer read() throws IOException {
        if (isDeflatedZipEntry) {
            // 如果已压缩，则解压到内存中(遗憾的是，没有可以按需解压部分流的懒加载
            // ByteBuffer，因此我们不得不解压整个 zip 条目)
            if (inflatedLengthHint > FileUtils.MAX_BUFFER_SIZE) {
                throw new IOException("Uncompressed size is larger than 2GB");
            }
            return java.nio.ByteBuffer.wrap(load());
        } else if (backingByteBuffer == null) {
            // 从 RandomAccessFile 复制到字节数组，然后包装成 ByteBuffer
            if (sliceLength > FileUtils.MAX_BUFFER_SIZE) {
                throw new IOException("File is larger than 2GB");
            }
            return java.nio.ByteBuffer.wrap(load());
        } else {
            // FileSlice 由 MappedByteBuffer 支持 —— 复制并返回(低成本操作)
            return backingByteBuffer.duplicate();
        }
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
            if (isTopLevelFileSlice && backingByteBuffer != null) {
                // 仅在顶级文件切片中关闭 ByteBuffer，这样 ByteBuffer 只关闭一次
                // (此外，MappedByteBuffer 的副本无法通过 cleaner API 关闭)
                JarReader.closeDirectByteBuffer(backingByteBuffer);
            }
            backingByteBuffer = null;
            fileChannel = null;
            if (isTopLevelFileSlice) {
                // 仅关闭顶级文件切片的 RAF；子切片共享父切片的 RAF，
                // 关闭它们会导致父切片和其他子切片的底层文件被意外关闭。
                try {
                    // 关闭 raf 也会关闭关联的 FileChannel
                    raf.close();
                } catch (final IOException e) {
                    // 忽略
                }
            }
            raf = null;
            JarReader.markSliceAsClosed(this);
        }
    }
}
