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
package com.bingbaihanji.classgraph.core;

/**
 * 在扫描过程中发生错误状态或捕获到未处理异常时抛出的非受检异常
 *
 * <p>
 * (继承自 {@link IllegalArgumentException}，而后者又继承自 {@link RuntimeException}，
 * 因此可以捕获这两个更通用的异常中的任意一个)
 */
public class ClassGraphException extends IllegalArgumentException {
    /** serialVersionUID */
    static final long serialVersionUID = 1L;

    /**
     * 构造函数
     *
     * @param message
     *            消息
     */
    ClassGraphException(final String message) {
        super(message);
    }

    /**
     * 构造函数
     *
     * @param message
     *            消息
     * @param cause
     *            原因
     */
    ClassGraphException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
