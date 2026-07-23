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

import java.io.IOException;
import java.nio.ByteBuffer;

/** 用于按字节顺序随机访问各值的接口 */
public interface RandomAccessReader {
    /**
     * 将字节读入 {@link ByteBuffer}
     *
     * @param srcOffset
     *            开始读取的偏移量
     * @param dstBuf
     *            要写入的 {@link ByteBuffer}
     * @param dstBufStart
     *            目标缓冲区中开始写入的偏移量
     * @param numBytes
     *            要读取的字节数
     * @return 实际读取的字节数，如果无法读取更多字节则返回 -1
     * @throws IOException
     *             如果读取时发生异常
     */
    int read(long srcOffset, ByteBuffer dstBuf, int dstBufStart, int numBytes) throws IOException;

    /**
     * 将字节读入字节数组
     *
     * @param srcOffset
     *            开始读取的偏移量
     * @param dstArr
     *            要写入的字节数组
     * @param dstArrStart
     *            目标数组中开始写入的偏移量
     * @param numBytes
     *            要读取的字节数
     * @return 实际读取的字节数，如果无法读取更多字节则返回 -1
     * @throws IOException
     *             如果读取时发生异常
     */
    int read(long srcOffset, byte[] dstArr, int dstArrStart, int numBytes) throws IOException;

    /**
     * 在指定偏移量处读取一个字节(不改变当前游标偏移量)
     *
     * @param offset
     *            要读取的缓冲区偏移量
     * @return 该偏移量处的字节
     * @throws IOException
     *             如果读取时发生异常
     */
    byte readByte(final long offset) throws IOException;

    /**
     * 在指定偏移量处读取一个无符号字节(不改变当前游标偏移量)
     *
     * @param offset
     *            要读取的缓冲区偏移量
     * @return 该偏移量处的无符号字节
     * @throws IOException
     *             如果读取时发生异常
     */
    int readUnsignedByte(final long offset) throws IOException;

    /**
     * 在指定偏移量处读取一个 short(不改变当前游标偏移量)
     *
     * @param offset
     *            要读取的缓冲区偏移量
     * @return 该偏移量处的 short
     * @throws IOException
     *             如果读取时发生异常
     */
    short readShort(final long offset) throws IOException;

    /**
     * 在指定偏移量处读取一个无符号 short(不改变当前游标偏移量)
     *
     * @param offset
     *            要读取的缓冲区偏移量
     * @return 该偏移量处的无符号 short
     * @throws IOException
     *             如果读取时发生异常
     */
    int readUnsignedShort(final long offset) throws IOException;

    /**
     * 在指定偏移量处读取一个 int(不改变当前游标偏移量)
     *
     * @param offset
     *            要读取的缓冲区偏移量
     * @return 该偏移量处的 int
     * @throws IOException
     *             如果读取时发生异常
     */
    int readInt(final long offset) throws IOException;

    /**
     * 在指定偏移量处读取一个无符号 int(不改变当前游标偏移量)
     *
     * @param offset
     *            要读取的缓冲区偏移量
     * @return 该偏移量处的 int，以 long 形式返回
     * @throws IOException
     *             如果读取时发生异常
     */
    long readUnsignedInt(final long offset) throws IOException;

    /**
     * 在指定偏移量处读取一个 long(不改变当前游标偏移量)
     *
     * @param offset
     *            要读取的缓冲区偏移量
     * @return 该偏移量处的 long
     * @throws IOException
     *             如果读取时发生异常
     */
    long readLong(final long offset) throws IOException;

    /**
     * 读取 Java classfile 规范中定义的"modified UTF8"格式，可选地将 '/' 替换为 '.'，
     * 并可选择去除前缀 "L" 和后缀 ";"
     *
     * @param offset
     *            字符串的起始偏移量
     * @param numBytes
     *            字符串 UTF8 编码的字节数
     * @param replaceSlashWithDot
     *            如果为 true，将 '/' 替换为 '.'
     * @param stripLSemicolon
     *            如果为 true，去除字符串末尾的 ';' 字符
     * @return 字符串
     * @throws IOException
     *             如果发生 I/O 异常
     */
    String readString(final long offset, final int numBytes, final boolean replaceSlashWithDot,
                      final boolean stripLSemicolon) throws IOException;

    /**
     * 读取 Java classfile 规范中定义的"modified UTF8"格式
     *
     * @param offset
     *            字符串的起始偏移量
     * @param numBytes
     *            字符串 UTF8 编码的字节数
     * @return 字符串
     * @throws IOException
     *             如果发生 I/O 异常
     */
    String readString(final long offset, final int numBytes) throws IOException;
}
