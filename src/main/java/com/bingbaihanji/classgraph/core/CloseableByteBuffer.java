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
 * Copyright (c) 2021 Luke Hutchison
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
package com.bingbaihanji.classgraph.core;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * {@link ByteBuffer} 的包装类，实现了 {@link Closeable} 接口，在不再需要时释放
 * {@link ByteBuffer}
 */
public class CloseableByteBuffer implements Closeable {
    private ByteBuffer byteBuffer;
    private Runnable onClose;

    /**
     * {@link ByteBuffer} 的包装类，实现了 {@link Closeable} 接口，在不再需要时释放
     * {@link ByteBuffer}
     *
     * @param byteBuffer
     *            要包装的 {@link ByteBuffer}
     * @param onClose
     *            当 {@link #close()} 被调用时要执行的方法
     */
    CloseableByteBuffer(final ByteBuffer byteBuffer, final Runnable onClose) {
        this.byteBuffer = byteBuffer;
        this.onClose = onClose;
    }

    /**
     * 获取被包装的 ByteBuffer
     *
     * @return 被包装的 {@link ByteBuffer}
     */
    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    /** 释放被包装的 {@link ByteBuffer} */
    @Override
    public void close() throws IOException {
        if (onClose != null) {
            try {
                onClose.run();
            } catch (final Exception e) {
                // 忽略异常
            }
            onClose = null;
        }
        byteBuffer = null;
    }
}
