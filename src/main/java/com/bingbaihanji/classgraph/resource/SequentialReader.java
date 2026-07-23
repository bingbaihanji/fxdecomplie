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

/** 用于按字节顺序顺序读取各值的接口 */
public interface SequentialReader {
    /**
     * 在当前游标位置读取一个字节
     *
     * @return 当前游标位置的字节
     * @throws IOException
     *             如果读取时发生异常
     */
    byte readByte() throws IOException;

    /**
     * 在当前游标位置读取一个无符号字节
     *
     * @return 当前游标位置的无符号字节
     * @throws IOException
     *             如果读取时发生异常
     */
    int readUnsignedByte() throws IOException;

    /**
     * 在当前游标位置读取一个 short
     *
     * @return 当前游标位置的 short
     * @throws IOException
     *             如果读取时发生异常
     */
    short readShort() throws IOException;

    /**
     * 在当前游标位置读取一个无符号 short
     *
     * @return 当前游标位置的无符号 short
     * @throws IOException
     *             如果读取时发生异常
     */
    int readUnsignedShort() throws IOException;

    /**
     * 在当前游标位置读取一个 int
     *
     * @return 当前游标位置的 int
     * @throws IOException
     *             如果读取时发生异常
     */
    int readInt() throws IOException;

    /**
     * 在当前游标位置读取一个无符号 int
     *
     * @return 当前游标位置的 int，以 long 形式返回
     * @throws IOException
     *             如果读取时发生异常
     */
    long readUnsignedInt() throws IOException;

    /**
     * 在当前游标位置读取一个 long
     *
     * @return 当前游标位置的 long
     * @throws IOException
     *             如果读取时发生异常
     */
    long readLong() throws IOException;

    /**
     * 跳过指定数量的字节
     *
     * @param bytesToSkip
     *            要跳过的字节数
     * @throws IOException
     *             如果读取时发生异常
     */
    void skip(final int bytesToSkip) throws IOException;

    /**
     * 读取 Java classfile 规范中定义的"modified UTF8"格式，可选地将 '/' 替换为 '.'，
     * 并可选择去除前缀 "L" 和后缀 ";"
     *
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
    String readString(final int numBytes, final boolean replaceSlashWithDot, final boolean stripLSemicolon)
            throws IOException;

    /**
     * 读取 Java classfile 规范中定义的"modified UTF8"格式
     *
     * @param numBytes
     *            字符串 UTF8 编码的字节数
     * @return 字符串
     * @throws IOException
     *             如果发生 I/O 异常
     */
    String readString(final int numBytes) throws IOException;
}
