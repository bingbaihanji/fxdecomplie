
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

import com.bingbaihanji.classgraph.resource.Resource;
import com.bingbaihanji.classgraph.resource.NestedJarHandler;
import com.bingbaihanji.classgraph.resource.reader.RandomAccessReader;
import com.bingbaihanji.classgraph.utils.FileUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link File}、{@link ByteBuffer} 或 {@link InputStream} 的切片单个 {@link Slice} 实例应仅由
 * 单个线程使用
 */
public abstract class Slice implements Closeable {
    /** 切片的起始位置 */
    public final long sliceStartPos;
    /** 如果为 true，则切片是压缩的 zip 条目，需要解压才能访问内容 */
    public final boolean isDeflatedZipEntry;
    /** 如果切片是压缩的 zip 条目，则这是预期的未压缩长度，未知则为 -1L */
    public final long inflatedLengthHint;
    /** {@link NestedJarHandler} */
    protected final NestedJarHandler nestedJarHandler;
    /** 父切片 */
    protected final Slice parentSlice;
    /** 切片的长度，如果未知(对于 {@link InputStream})则为 -1L */
    public long sliceLength;
    /** 缓存的 hashCode */
    private int hashCode;

    /**
     * 用于将切片的一个范围视为子切片的构造函数
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
     * @param nestedJarHandler
     *            嵌套 jar 处理器
     */
    protected Slice(final Slice parentSlice, final long offset, final long length, final boolean isDeflatedZipEntry,
                    final long inflatedLengthHint, final NestedJarHandler nestedJarHandler) {
        this.parentSlice = parentSlice;
        final long parentSliceStartPos = parentSlice == null ? 0L : parentSlice.sliceStartPos;
        this.sliceStartPos = parentSliceStartPos + offset;
        this.sliceLength = length;
        this.isDeflatedZipEntry = isDeflatedZipEntry;
        this.inflatedLengthHint = inflatedLengthHint;
        this.nestedJarHandler = nestedJarHandler;

        if (sliceStartPos < 0L) {
            throw new IllegalArgumentException("Invalid startPos");
        }
        if (length < 0L) {
            throw new IllegalArgumentException("Invalid length");
        }
        if (parentSlice != null && (sliceStartPos < parentSliceStartPos
                || sliceStartPos + length > parentSliceStartPos + parentSlice.sliceLength)) {
            throw new IllegalArgumentException("Child slice is not completely contained within parent slice");
        }
    }

    /**
     * 构造函数
     *
     * @param length
     *            长度
     * @param isDeflatedZipEntry
     *            如果这是压缩的 zip 条目则为 true
     * @param inflatedLengthHint
     *            压缩 zip 条目的未压缩大小，未知为 -1，如果是非压缩 zip 条目则为 0
     * @param nestedJarHandler
     *            嵌套 jar 处理器
     */
    protected Slice(final long length, final boolean isDeflatedZipEntry, final long inflatedLengthHint,
                    final NestedJarHandler nestedJarHandler) {
        this(/* parentSlice = */ null, 0L, length, isDeflatedZipEntry, inflatedLengthHint, nestedJarHandler);
    }

    /**
     * 从此父 {@link Slice} 获取子 {@link Slice}子切片必须小于父切片，
     * 并且完全包含在父切片中
     *
     * @param offset
     *            相对于此父切片起始位置开始切片的偏移量
     * @param length
     *            切片的长度
     * @param isDeflatedZipEntry
     *            如果这是压缩的 zip 条目则为 true
     * @param inflatedLengthHint
     *            压缩 zip 条目的未压缩大小，未知为 -1，如果是非压缩 zip 条目则为 0
     * @return 子切片
     */
    public abstract Slice slice(long offset, long length, boolean isDeflatedZipEntry,
                                final long inflatedLengthHint);

    /**
     * 将此 {@link Slice} 作为 {@link InputStream} 打开
     *
     * @return 输入流
     * @throws IOException
     *             如果无法为此 {@link Slice} 创建解压器
     */
    public InputStream open() throws IOException {
        return open(null);
    }

    /**
     * 将此 {@link Slice} 作为 {@link InputStream} 打开
     *
     * @param resourceToClose
     *            当返回的 {@code InputStream} 关闭时需要关闭的 {@link Resource}，如果没有则为 null
     * @return 输入流
     * @throws IOException
     *             如果无法为此 {@link Slice} 创建解压器
     */
    public InputStream open(final Resource resourceToClose) throws IOException {
        final InputStream rawInputStream = new InputStream() {
            private final byte[] byteBuf = new byte[1];
            private final AtomicBoolean closed = new AtomicBoolean();
            RandomAccessReader randomAccessReader = randomAccessReader();
            private long currOff;
            private long markOff;

            @Override
            public int read() throws IOException {
                if (closed.get()) {
                    throw new IOException("Already closed");
                }
                return read(byteBuf, 0, 1);
            }

            // InputStream 的默认实现此方法的方式非常慢 —— 它对每个字节调用 read()
            // 本方法在一次调用中尽可能多地读取字节
            @Override
            public int read(final byte[] buf, final int off, final int len) throws IOException {
                if (closed.get()) {
                    throw new IOException("Already closed");
                } else if (len == 0) {
                    return 0;
                }
                final int numBytesToRead = Math.min(len, available());
                if (numBytesToRead < 1) {
                    return -1;
                }
                final int numBytesRead = randomAccessReader.read(currOff, buf, off, numBytesToRead);
                if (numBytesRead > 0) {
                    currOff += numBytesRead;
                }
                return numBytesRead;
            }

            @Override
            public long skip(final long n) throws IOException {
                if (closed.get()) {
                    throw new IOException("Already closed");
                }
                final long newOff = Math.min(currOff + n, sliceLength);
                final long skipped = newOff - currOff;
                currOff = newOff;
                return skipped;
            }

            @Override
            public int available() {
                return (int) Math.min(Math.max(sliceLength - currOff, 0L), FileUtils.MAX_BUFFER_SIZE);
            }

            @Override
            public synchronized void mark(final int readlimit) {
                // 忽略 readlimit
                markOff = currOff;
            }

            @Override
            public synchronized void reset() {
                currOff = markOff;
            }

            @Override
            public boolean markSupported() {
                return true;
            }

            @Override
            public void close() {
                if (resourceToClose != null) {
                    try {
                        resourceToClose.close();
                    } catch (final Exception e) {
                        // 忽略
                    }
                }
                closed.getAndSet(true);
            }
        };
        return isDeflatedZipEntry ? nestedJarHandler.openInflaterInputStream(rawInputStream) : rawInputStream;
    }

    /**
     * 为此 {@link Slice} 创建新的 {@link RandomAccessReader}
     *
     * @return 随机访问读取器
     */
    public abstract RandomAccessReader randomAccessReader();

    /**
     * 将切片作为字节数组加载
     *
     * @return 字节数组
     * @throws IOException
     *             如果发生 I/O 异常
     */
    public abstract byte[] load() throws IOException;

    /**
     * 将切片作为字符串加载
     *
     * @return 字符串
     * @throws IOException
     *             如果切片无法读取
     */
    public String loadAsString() throws IOException {
        return new String(load(), StandardCharsets.UTF_8);
    }

    /**
     * 将切片读入 {@link ByteBuffer}
     *
     * @return 字节缓冲区
     * @throws IOException
     *             如果发生 I/O 异常
     */
    public ByteBuffer read() throws IOException {
        return ByteBuffer.wrap(load());
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = (parentSlice == null ? 1 : parentSlice.hashCode()) ^ ((int) sliceStartPos * 7)
                    ^ ((int) sliceLength * 15);
            if (hashCode == 0) {
                hashCode = 1;
            }
        }
        return hashCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof Slice)) {
            return false;
        } else {
            final Slice other = (Slice) o;
            return this.parentSlice == other.parentSlice && this.sliceStartPos == other.sliceStartPos
                    && this.sliceLength == other.sliceLength;
        }
    }
}
