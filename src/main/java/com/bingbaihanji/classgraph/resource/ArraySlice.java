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

import com.bingbaihanji.classgraph.resource.JarReader;
import com.bingbaihanji.classgraph.resource.RandomAccessArrayReader;
import com.bingbaihanji.classgraph.resource.RandomAccessReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** 字节数组切片 */
public class ArraySlice extends Slice {
    /** 包装的字节数组 */
    public byte[] arr;

    /**
     * 用于将数组的一个范围视为切片的构造函数
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
    private ArraySlice(final ArraySlice parentSlice, final long offset, final long length,
                       final boolean isDeflatedZipEntry, final long inflatedLengthHint,
                       final JarReader JarReader) {
        super(parentSlice, offset, length, isDeflatedZipEntry, inflatedLengthHint, JarReader);
        this.arr = parentSlice.arr;
    }

    /**
     * 用于将整个数组视为切片的构造函数
     *
     * @param arr
     *            包含切片的数组
     * @param isDeflatedZipEntry
     *            如果这是压缩的 zip 条目则为 true
     * @param inflatedLengthHint
     *            压缩 zip 条目的未压缩大小，未知为 -1，如果是非压缩 zip 条目则为 0
     * @param JarReader
     *            嵌套 jar 处理器
     */
    public ArraySlice(final byte[] arr, final boolean isDeflatedZipEntry, final long inflatedLengthHint,
                      final JarReader JarReader) {
        super(arr.length, isDeflatedZipEntry, inflatedLengthHint, JarReader);
        this.arr = arr;
    }

    /**
     * 从此切片切出子切片
     *
     * @param offset
     *            相对于此切片起始位置的偏移量，用作子切片的起始位置
     * @param length
     *            子切片的长度
     * @param isDeflatedZipEntry
     *            是否是压缩的 zip 条目
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
        return new ArraySlice(this, offset, length, isDeflatedZipEntry, inflatedLengthHint, JarReader);
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
            // 如有必要，解压到内存中
            try (InputStream inputStream = open()) {
                return JarReader.readAllBytesAsArray(inputStream, inflatedLengthHint);
            }
        } else if (sliceStartPos == 0L && sliceLength == arr.length) {
            // 快速路径 —— 如果数组是整个切片且未压缩，则直接返回整个数组
            return arr;
        } else {
            // 如果是切片且未压缩，则复制数组的范围
            return Arrays.copyOfRange(arr, (int) sliceStartPos, (int) (sliceStartPos + sliceLength));
        }
    }

    /**
     * 返回新的随机访问读取器
     *
     * @return 随机访问读取器
     */
    @Override
    public RandomAccessReader randomAccessReader() {
        return new RandomAccessArrayReader(arr, (int) sliceStartPos, (int) sliceLength);
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}