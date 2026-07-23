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
package com.bingbaihanji.classgraph.type;

import com.bingbaihanji.classgraph.type.ParseException;
import com.bingbaihanji.classgraph.type.TypeParser;

/**
 * 引用类型的类型签名子类包括 {@link ClassRefOrTypeVar}
 * ({@link ClassRef} 或 {@link TypeVar})，以及 {@link ArrayType}
 */
public abstract class ReferenceType extends TypeSignature {
    /** 构造函数 */
    protected ReferenceType() {
        super();
    }

    /**
     * 解析一个引用类型签名
     *
     * @param TypeParser
     *            解析器
     * @param definingClassName
     *            包含该类型描述符的类
     * @return 解析后的引用类型签名
     * @throws ParseException
     *             如果类型签名无法解析
     */
    static ReferenceType parseReferenceType(final TypeParser TypeParser, final String definingClassName)
            throws ParseException {
        final ClassRef ClassType = ClassRef.parse(TypeParser, definingClassName);
        if (ClassType != null) {
            return ClassType;
        }
        final TypeVar TypeVar = TypeVar.parse(TypeParser, definingClassName);
        if (TypeVar != null) {
            return TypeVar;
        }
        final ArrayType ArrayType = ArrayType.parse(TypeParser, definingClassName);
        if (ArrayType != null) {
            return ArrayType;
        }
        return null;
    }

    /**
     * 解析一个类边界
     *
     * @param TypeParser
     *            解析器
     * @param definingClassName
     *            包含该类型描述符的类
     * @return 解析后的类边界
     * @throws ParseException
     *             如果类型签名无法解析
     */
    static ReferenceType parseClassBound(final TypeParser TypeParser, final String definingClassName)
            throws ParseException {
        TypeParser.expect(':');
        // 如果 ':' 之后没有签名，则可能返回 null(类边界签名可以为空)
        return parseReferenceType(TypeParser, definingClassName);
    }
}