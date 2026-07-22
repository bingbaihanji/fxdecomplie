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

import com.bingbaihanji.classgraph.types.ParseException;
import com.bingbaihanji.classgraph.types.Parser;

/**
 * 引用类型的类型签名子类包括 {@link ClassRefOrTypeVariableSignature}
 * ({@link ClassRefTypeSignature} 或 {@link TypeVariableSignature})，以及 {@link ArrayTypeSignature}
 */
public abstract class ReferenceTypeSignature extends TypeSignature {
    /** 构造函数 */
    protected ReferenceTypeSignature() {
        super();
    }

    /**
     * 解析一个引用类型签名
     *
     * @param parser
     *            解析器
     * @param definingClassName
     *            包含该类型描述符的类
     * @return 解析后的引用类型签名
     * @throws ParseException
     *             如果类型签名无法解析
     */
    static ReferenceTypeSignature parseReferenceTypeSignature(final Parser parser, final String definingClassName)
            throws ParseException {
        final ClassRefTypeSignature classTypeSignature = ClassRefTypeSignature.parse(parser, definingClassName);
        if (classTypeSignature != null) {
            return classTypeSignature;
        }
        final TypeVariableSignature typeVariableSignature = TypeVariableSignature.parse(parser, definingClassName);
        if (typeVariableSignature != null) {
            return typeVariableSignature;
        }
        final ArrayTypeSignature arrayTypeSignature = ArrayTypeSignature.parse(parser, definingClassName);
        if (arrayTypeSignature != null) {
            return arrayTypeSignature;
        }
        return null;
    }

    /**
     * 解析一个类边界
     *
     * @param parser
     *            解析器
     * @param definingClassName
     *            包含该类型描述符的类
     * @return 解析后的类边界
     * @throws ParseException
     *             如果类型签名无法解析
     */
    static ReferenceTypeSignature parseClassBound(final Parser parser, final String definingClassName)
            throws ParseException {
        parser.expect(':');
        // 如果 ':' 之后没有签名，则可能返回 null(类边界签名可以为空)
        return parseReferenceTypeSignature(parser, definingClassName);
    }
}