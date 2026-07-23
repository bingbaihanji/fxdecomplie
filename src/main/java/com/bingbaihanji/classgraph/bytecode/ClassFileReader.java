
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
package com.bingbaihanji.classgraph.bytecode;

import com.bingbaihanji.classgraph.resource.Resource;
import com.bingbaihanji.classgraph.resource.ArraySlice;
import com.bingbaihanji.classgraph.resource.FileSlice;
import com.bingbaihanji.classgraph.resource.Slice;
import com.bingbaihanji.classgraph.utils.FileUtils;
import com.bingbaihanji.classgraph.utils.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

/**
 * 一种 {@link Slice} 读取器，既可作为 {@link RandomAccessReader} 也可作为 {@link SequentialReader} 使用
 * 文件缓冲到目前已读取的位置按 classfile 格式所需的<b>大端序</b>读取
 */
public class ClassFileReader implements RandomAccessReader, SequentialReader, Closeable {
    /**
     * 初始缓冲区大小对于大多数 classfile，只需要读取前 16-64kb(我们不读取字节码)
     */
    private static final int INITIAL_BUF_SIZE = 16384;
    /**
     * 每次发生缓冲区不足时读取的字节数比 8k 小 8 字节，以防止当最后一个块不能完全容纳在
     * INITIAL_BUF_SIZE 的 16kb 内时数组大小翻倍，因为最多可以请求读取 8 字节(用于 long 类型)
     * 否则我们可能需要读取到 (8kb * 2 + 8)，这会将缓冲区大小翻倍到 32kb，但如果我们只需要读取
     * 8kb 到 16kb 之间，则我们就不必要地多复制了一次缓冲区内容
     */
    private static final int BUF_CHUNK_SIZE = 8192 - 8;
    /** 调用 {@link ClassFileReader#close()} 时要关闭的底层资源 */
    private Resource resourceToClose;
    /** 如果切片是压缩的，则是 {@link InflateInputStream} 的包装器 */
    private InputStream inflaterInputStream;
    /**
     * 如果切片未压缩，则是 {@link ArraySlice} 或 {@link FileSlice} 具体子类的
     * {@link RandomAccessReader}
     */
    private RandomAccessReader randomAccessReader;
    /** 缓冲区 */
    private byte[] arr;
    /** arr 中已使用的字节数 */
    private int arrUsed;
    /** 切片内的当前读取索引 */
    private int currIdx;
    /**
     * 如果已知 classfile 长度(因为未压缩)则为该长度，如果未知(因为已压缩)则为 -1
     */
    private int classfileLengthHint = -1;

    /**
     * 构造函数
     *
     * @param slice
     *            要读取的 {@link Slice}
     * @param resourceToClose
     *            调用 {@link ClassFileReader#close()} 时要关闭的资源，或 null
     * @throws IOException
     *             如果无法在 {@link Slice} 上打开解压器
     */
    public ClassFileReader(final Slice slice, final Resource resourceToClose) throws IOException {
        this.classfileLengthHint = (int) slice.sliceLength;
        this.resourceToClose = resourceToClose;
        if (slice.isDeflatedZipEntry) {
            // 如果这是压缩的切片，需要从 InflaterInputStream 读取来填充缓冲区
            inflaterInputStream = slice.open();
            arr = new byte[INITIAL_BUF_SIZE];
            classfileLengthHint = (int) Math.min(slice.inflatedLengthHint, FileUtils.MAX_BUFFER_SIZE);
        } else {
            if (slice instanceof ArraySlice) {
                // 如果切片是 ArraySlice，通过直接重用包装的字节数组代替缓冲区数组来避免复制，
                // 并将其标记为已完全加载
                final ArraySlice arraySlice = (ArraySlice) slice;
                if (arraySlice.sliceStartPos == 0 && arraySlice.sliceLength == arraySlice.arr.length) {
                    // ArraySlice 是整个数组
                    arr = arraySlice.arr;
                } else {
                    // ArraySlice 仅覆盖部分数组，而此类不支持起始偏移量，
                    // 因此将切片部分的数组复制到新缓冲区
                    arr = Arrays.copyOfRange(arraySlice.arr, (int) arraySlice.sliceStartPos,
                            (int) (arraySlice.sliceStartPos + arraySlice.sliceLength));
                }
                arrUsed = arr.length;
                classfileLengthHint = arr.length;
            } else {
                // 否则这是一个 FileSlice —— 需要使用随机访问读取器获取字节块
                randomAccessReader = slice.randomAccessReader();
                arr = new byte[INITIAL_BUF_SIZE];
                classfileLengthHint = (int) Math.min(slice.sliceLength, FileUtils.MAX_BUFFER_SIZE);
            }
        }
    }

    /**
     * 用于模块 {@link InputStream}(未压缩)的读取器构造函数
     *
     * @param inputStream
     *            要读取的 {@link InputStream}
     * @param resourceToClose
     *            调用 {@link ClassFileReader#close()} 时要关闭的底层资源，或 null
     * @throws IOException
     *             如果无法在 {@link Slice} 上打开解压器
     */
    public ClassFileReader(final InputStream inputStream, final Resource resourceToClose) throws IOException {
        inflaterInputStream = inputStream;
        arr = new byte[INITIAL_BUF_SIZE];
        this.resourceToClose = resourceToClose;
    }

    /**
     * 当前读取位置
     *
     * @return 当前读取位置
     */
    public int currPos() {
        return currIdx;
    }

    /**
     * 缓冲区
     *
     * @return 缓冲区
     */
    public byte[] buf() {
        return arr;
    }

    /**
     * 在发生缓冲区不足时调用，以确保数组中有足够的字节可用于从给定起始索引处
     * 读取指定数量的字节
     *
     * @param targetArrUsed
     *            {@link #arrUsed} 的目标值(即数组中必须填充的字节数)
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private void readTo(final int targetArrUsed) throws IOException {
        // 数组不需要增长到超过长度提示的大小(如果 zip 条目的未压缩大小被低估，
        // classfile 将被截断)如果为 -1，则假定 2GB 为最大大小
        final int maxArrLen = classfileLengthHint == -1 ? FileUtils.MAX_BUFFER_SIZE : classfileLengthHint;
        if (inflaterInputStream == null && randomAccessReader == null) {
            // 如果 inflaterInputStream 和 randomAccessReader 都未设置，则切片是 ArraySlice，
            // 并且数组已经"完全加载"(ArraySlice 的后备数组用作缓冲区)
            throw new IOException("Tried to read past end of fixed array buffer");
        }
        if (targetArrUsed > FileUtils.MAX_BUFFER_SIZE || targetArrUsed < 0 || arrUsed == maxArrLen) {
            throw new IOException("Hit 2GB limit while trying to grow buffer array");
        }

        // 需要至少读取 BUF_CHUNK_SIZE 字节(但不要超过 2GB 限制)
        final int maxNewArrUsed = (int) Math.min(Math.max(targetArrUsed, (long) (arrUsed + BUF_CHUNK_SIZE)),
                maxArrLen);

        // 如果数组太小，无法容纳新的字节块，则将数组大小翻倍
        long newArrLength = arr.length;
        while (newArrLength < maxNewArrUsed) {
            newArrLength = Math.min(maxNewArrUsed, newArrLength * 2L);
        }
        if (newArrLength > FileUtils.MAX_BUFFER_SIZE) {
            throw new IOException("Hit 2GB limit while trying to grow buffer array");
        }
        arr = Arrays.copyOf(arr, (int) Math.min(newArrLength, maxArrLen));

        // 计算可以读入数组的最大字节数
        final int maxBytesToRead = arr.length - arrUsed;

        // 将新的数据块读入缓冲区，从位置 arrUsed 开始
        if (inflaterInputStream != null) {
            // 从解压器输入流读取
            final int numRead = inflaterInputStream.read(arr, arrUsed, maxBytesToRead);
            if (numRead > 0) {
                arrUsed += numRead;
            }
        } else /* randomAccessReader != null，所以这是一个(未压缩的)FileSlice */ {
            // 不要读超过切片末尾
            final int bytesToRead = Math.min(maxBytesToRead, maxArrLen - arrUsed);
            // 从 FileSlice 读取字节到 arr
            final int numBytesRead = randomAccessReader.read(/* srcOffset = */ arrUsed, /* dstArr = */ arr,
                    /* dstArrStart = */ arrUsed, /* numBytes = */ bytesToRead);
            if (numBytesRead > 0) {
                arrUsed += numBytesRead;
            }
        }

        // 检查缓冲区是否能够填充到请求的位置
        if (arrUsed < targetArrUsed) {
            throw new IOException("Buffer underflow");
        }
    }

    /**
     * 确保从切片开头已将指定数量的字节读入缓冲区
     *
     * @param numBytes
     *            要确保已缓冲的字节数
     * @throws IOException
     *             在 EOF 时或如果字节无法读取
     */
    public void bufferTo(final int numBytes) throws IOException {
        if (numBytes > arrUsed) {
            readTo(numBytes);
        }
    }

    @Override
    public int read(final long srcOffset, final byte[] dstArr, final int dstArrStart, final int numBytes)
            throws IOException {
        if (numBytes == 0) {
            return 0;
        }
        final int idx = (int) srcOffset;
        if (idx + numBytes > arrUsed) {
            readTo(idx + numBytes);
        }
        final int numBytesToRead = Math.max(Math.min(numBytes, dstArr.length - dstArrStart), 0);
        if (numBytesToRead == 0) {
            return -1;
        }
        try {
            System.arraycopy(arr, idx, dstArr, dstArrStart, numBytesToRead);
            return numBytesToRead;
        } catch (final IndexOutOfBoundsException e) {
            throw new IOException("Read index out of bounds");
        }
    }

    @Override
    public int read(final long srcOffset, final ByteBuffer dstBuf, final int dstBufStart, final int numBytes)
            throws IOException {
        if (numBytes == 0) {
            return 0;
        }
        final int idx = (int) srcOffset;
        if (idx + numBytes > arrUsed) {
            readTo(idx + numBytes);
        }
        final int numBytesToRead = Math.max(Math.min(numBytes, dstBuf.capacity() - dstBufStart), 0);
        if (numBytesToRead == 0) {
            return -1;
        }
        try {
            ((Buffer) dstBuf).position(dstBufStart);
            ((Buffer) dstBuf).limit(dstBufStart + numBytesToRead);
            dstBuf.put(arr, idx, numBytesToRead);
            return numBytesToRead;
        } catch (BufferUnderflowException | IndexOutOfBoundsException | ReadOnlyBufferException e) {
            throw new IOException("Read index out of bounds");
        }
    }

    @Override
    public byte readByte(final long offset) throws IOException {
        final int idx = (int) offset;
        if (idx + 1 > arrUsed) {
            readTo(idx + 1);
        }
        return arr[idx];
    }

    @Override
    public int readUnsignedByte(final long offset) throws IOException {
        final int idx = (int) offset;
        if (idx + 1 > arrUsed) {
            readTo(idx + 1);
        }
        return arr[idx] & 0xff;
    }

    @Override
    public short readShort(final long offset) throws IOException {
        return (short) readUnsignedShort(offset);
    }

    @Override
    public int readUnsignedShort(final long offset) throws IOException {
        final int idx = (int) offset;
        if (idx + 2 > arrUsed) {
            readTo(idx + 2);
        }
        return ((arr[idx] & 0xff) << 8) //
                | (arr[idx + 1] & 0xff);
    }

    @Override
    public int readInt(final long offset) throws IOException {
        final int idx = (int) offset;
        if (idx + 4 > arrUsed) {
            readTo(idx + 4);
        }
        return ((arr[idx] & 0xff) << 24) //
                | ((arr[idx + 1] & 0xff) << 16) //
                | ((arr[idx + 2] & 0xff) << 8) //
                | (arr[idx + 3] & 0xff);
    }

    @Override
    public long readUnsignedInt(final long offset) throws IOException {
        return readInt(offset) & 0xffffffffL;
    }

    @Override
    public long readLong(final long offset) throws IOException {
        final int idx = (int) offset;
        if (idx + 8 > arrUsed) {
            readTo(idx + 8);
        }
        return ((arr[idx] & 0xffL) << 56) //
                | ((arr[idx + 1] & 0xffL) << 48) //
                | ((arr[idx + 2] & 0xffL) << 40) //
                | ((arr[idx + 3] & 0xffL) << 32) //
                | ((arr[idx + 4] & 0xffL) << 24) //
                | ((arr[idx + 5] & 0xffL) << 16) //
                | ((arr[idx + 6] & 0xffL) << 8) //
                | (arr[idx + 7] & 0xffL);
    }

    @Override
    public byte readByte() throws IOException {
        final byte val = readByte(currIdx);
        currIdx++;
        return val;
    }

    @Override
    public int readUnsignedByte() throws IOException {
        final int val = readUnsignedByte(currIdx);
        currIdx++;
        return val;
    }

    @Override
    public short readShort() throws IOException {
        final short val = readShort(currIdx);
        currIdx += 2;
        return val;
    }

    @Override
    public int readUnsignedShort() throws IOException {
        final int val = readUnsignedShort(currIdx);
        currIdx += 2;
        return val;
    }

    @Override
    public int readInt() throws IOException {
        final int val = readInt(currIdx);
        currIdx += 4;
        return val;
    }

    @Override
    public long readUnsignedInt() throws IOException {
        final long val = readUnsignedInt(currIdx);
        currIdx += 4;
        return val;
    }

    @Override
    public long readLong() throws IOException {
        final long val = readLong(currIdx);
        currIdx += 8;
        return val;
    }

    @Override
    public void skip(final int bytesToSkip) throws IOException {
        if (bytesToSkip < 0) {
            throw new IllegalArgumentException("Tried to skip a negative number of bytes");
        }
        final int idx = currIdx;
        if (idx + bytesToSkip > arrUsed) {
            readTo(idx + bytesToSkip);
        }
        currIdx += bytesToSkip;
    }

    @Override
    public String readString(final long offset, final int numBytes, final boolean replaceSlashWithDot,
                             final boolean stripLSemicolon) throws IOException {
        final int idx = (int) offset;
        if (idx + numBytes > arrUsed) {
            readTo(idx + numBytes);
        }
        return StringUtils.readString(arr, idx, numBytes, replaceSlashWithDot, stripLSemicolon);
    }

    @Override
    public String readString(final int numBytes, final boolean replaceSlashWithDot, final boolean stripLSemicolon)
            throws IOException {
        final String val = StringUtils.readString(arr, currIdx, numBytes, replaceSlashWithDot, stripLSemicolon);
        currIdx += numBytes;
        return val;
    }

    @Override
    public String readString(final long offset, final int numBytes) throws IOException {
        return readString(offset, numBytes, false, false);
    }

    @Override
    public String readString(final int numBytes) throws IOException {
        return readString(numBytes, false, false);
    }

    @Override
    public void close() {
        try {
            if (inflaterInputStream != null) {
                inflaterInputStream.close();
                inflaterInputStream = null;
            }
            if (resourceToClose != null) {
                resourceToClose.close();
                resourceToClose = null;
            }
        } catch (final Exception e) {
            // Ignore
        }
    }
}
