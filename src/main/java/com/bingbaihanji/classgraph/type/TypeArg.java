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

import com.bingbaihanji.classgraph.bytecode.ClassParser.TypePathNode;
import com.bingbaihanji.classgraph.type.ParseException;
import com.bingbaihanji.classgraph.type.TypeParser;

import java.util.*;

/** 一个类型参数 */
public final class TypeArg extends HierarchicalType {
    /** 通配符类型 */
    private final Wildcard wildcard;
    /** 类型签名(如果 wildcard == ANY，则为 null) */
    private final ReferenceType typeSignature;

    /**
     * 构造函数
     *
     * @param wildcard
     *            通配符类型
     * @param typeSignature
     *            类型签名
     */
    private TypeArg(final Wildcard wildcard, final ReferenceType typeSignature) {
        super();
        this.wildcard = wildcard;
        this.typeSignature = typeSignature;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 解析一个类型参数
     *
     * @param TypeParser
     *            解析器
     * @param definingClassName
     *            定义类的名称(用于解析类型变量)
     * @return 解析后的方法类型签名
     * @throws ParseException
     *             如果方法类型签名无法解析
     */
    private static TypeArg parse(final TypeParser TypeParser, final String definingClassName) throws ParseException {
        final char peek = TypeParser.peek();
        if (peek == '*') {
            TypeParser.expect('*');
            return new TypeArg(Wildcard.ANY, null);
        } else if (peek == '+') {
            TypeParser.expect('+');
            final ReferenceType typeSignature = ReferenceType.parseReferenceType(TypeParser,
                    definingClassName);
            if (typeSignature == null) {
                throw new ParseException(TypeParser, "Missing '+' type bound");
            }
            return new TypeArg(Wildcard.EXTENDS, typeSignature);
        } else if (peek == '-') {
            TypeParser.expect('-');
            final ReferenceType typeSignature = ReferenceType.parseReferenceType(TypeParser,
                    definingClassName);
            if (typeSignature == null) {
                throw new ParseException(TypeParser, "Missing '-' type bound");
            }
            return new TypeArg(Wildcard.SUPER, typeSignature);
        } else {
            final ReferenceType typeSignature = ReferenceType.parseReferenceType(TypeParser,
                    definingClassName);
            if (typeSignature == null) {
                throw new ParseException(TypeParser, "Missing type bound");
            }
            return new TypeArg(Wildcard.NONE, typeSignature);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 解析一个类型参数列表
     *
     * @param TypeParser
     *            解析器
     * @param definingClassName
     *            定义类的名称(用于解析类型变量)
     * @return 类型参数列表
     * @throws ParseException
     *             如果类型签名无法解析
     */
    static List<TypeArg> parseList(final TypeParser TypeParser, final String definingClassName) throws ParseException {
        if (TypeParser.peek() == '<') {
            TypeParser.expect('<');
            final List<TypeArg> TypeArgs = new ArrayList<>(2);
            while (TypeParser.peek() != '>') {
                if (!TypeParser.hasMore()) {
                    throw new ParseException(TypeParser, "Missing '>'");
                }
                TypeArgs.add(parse(TypeParser, definingClassName));
            }
            TypeParser.expect('>');
            return TypeArgs;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * 获取类型通配符，其值为 {NONE, ANY, EXTENDS, SUPER} 之一
     *
     * @return 类型通配符
     */
    public Wildcard getWildcard() {
        return wildcard;
    }

    /**
     * 获取与通配符关联的类型签名(如果通配符为 ANY，则为 null)
     *
     * @return 类型签名
     */
    public ReferenceType getTypeSignature() {
        return typeSignature;
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    protected void addTypeAnnotation(final List<TypePathNode> typePath, final AnnotationInfo annotationInfo) {
        if (typePath.size() == 0 && wildcard != Wildcard.NONE) {
            // 通配符之前的注解
            addTypeAnnotation(annotationInfo);
        } else if (typePath.size() > 0 && typePath.get(0).typePathKind == 2) {
            // 注解位于参数化类型的通配符类型参数的边界上
            // TypeSignature 在损坏的类文件中可能为 null (#758)
            if (typeSignature != null) {
                typeSignature.addTypeAnnotation(typePath.subList(1, typePath.size()), annotationInfo);
            }
        } else {
            // 注解位于参数化类型的类型参数上
            // TypeSignature 在损坏的类文件中可能为 null (#758)
            if (typeSignature != null) {
                typeSignature.addTypeAnnotation(typePath, annotationInfo);
            }
        }
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#getClassName()
     */
    @Override
    protected String getClassName() {
        // getClassInfo() 对此类型无效，因此 getClassName() 不需要实现
        throw new IllegalArgumentException("getClassName() cannot be called here");
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#getClassInfo()
     */
    @Override
    protected ClassInfo getClassInfo() {
        throw new IllegalArgumentException("getClassInfo() cannot be called here");
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (this.typeSignature != null) {
            this.typeSignature.setScanResult(scanResult);
        }
    }

    /**
     * 获取类型签名中引用的任何类的名称
     *
     * @param refdClassNames
     *            引用的类名
     */
    public void findReferencedClassNames(final Set<String> refdClassNames) {
        if (typeSignature != null) {
            typeSignature.findReferencedClassNames(refdClassNames);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return (typeSignature != null ? typeSignature.hashCode() : 0) + 7 * wildcard.hashCode();
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof TypeArg)) {
            return false;
        }
        final TypeArg other = (TypeArg) obj;
        return Objects.equals(this.typeAnnotationInfo, other.typeAnnotationInfo)
                && (Objects.equals(this.typeSignature, other.typeSignature)
                && other.wildcard.equals(this.wildcard));
    }

    @Override
    protected void toStringInternal(final boolean useSimpleNames, final AnnotationInfoList annotationsToExclude,
                                    final StringBuilder buf) {
        if (typeAnnotationInfo != null) {
            for (final AnnotationInfo annotationInfo : typeAnnotationInfo) {
                if (annotationsToExclude == null || !annotationsToExclude.contains(annotationInfo)) {
                    annotationInfo.toString(useSimpleNames, buf);
                    buf.append(' ');
                }
            }
        }
        switch (wildcard) {
            case ANY:
                buf.append('?');
                break;
            case EXTENDS:
                final String typeSigStr = typeSignature.toString(useSimpleNames);
                buf.append("java.lang.Object".equals(typeSigStr) ? "?" : "? extends " + typeSigStr);
                break;
            case SUPER:
                buf.append("? super ");
                typeSignature.toString(useSimpleNames, buf);
                break;
            case NONE:
            default:
                typeSignature.toString(useSimpleNames, buf);
                break;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 类型通配符 */
    public enum Wildcard {
        /** 无通配符 */
        NONE,

        /** '?' 通配符 */
        ANY,

        /** extends 通配符 */
        EXTENDS,

        /** super 通配符 */
        SUPER
    }
}
