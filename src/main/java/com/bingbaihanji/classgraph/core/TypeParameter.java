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

import com.bingbaihanji.classgraph.core.ClassFile.TypePathNode;
import com.bingbaihanji.classgraph.types.ParseException;
import com.bingbaihanji.classgraph.types.Parser;
import com.bingbaihanji.classgraph.types.TypeUtils;

import java.util.*;

/** 一个类型形参 */
public final class TypeParameter extends HierarchicalTypeSignature {
    /** 类型形参标识符 */
    final String name;

    /** 类边界 -- 可能为 null */
    final ReferenceTypeSignature classBound;

    /** 接口边界 -- 可能为空 */
    final List<ReferenceTypeSignature> interfaceBounds;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param identifier
     *            类型形参标识符
     * @param classBound
     *            类型形参类边界
     * @param interfaceBounds
     *            类型形参接口边界
     */
    protected TypeParameter(final String identifier, final ReferenceTypeSignature classBound,
                            final List<ReferenceTypeSignature> interfaceBounds) {
        super();
        this.name = identifier;
        this.classBound = classBound;
        this.interfaceBounds = interfaceBounds;
    }

    /**
     * 将类型形参列表解析为 {@link TypeParameter} 对象
     *
     * @param parser
     *            解析器
     * @param definingClassName
     *            定义类名
     * @return {@link TypeParameter} 对象的列表
     * @throws ParseException
     *             如果解析失败
     */
    static List<TypeParameter> parseList(final Parser parser, final String definingClassName)
            throws ParseException {
        if (parser.peek() != '<') {
            return Collections.emptyList();
        }
        parser.expect('<');
        final List<TypeParameter> typeParams = new ArrayList<>(1);
        while (parser.peek() != '>') {
            if (!parser.hasMore()) {
                throw new ParseException(parser, "Missing '>'");
            }
            // Scala 的类型形参名称中可能包含 '$' (#495)
            if (!TypeUtils.getIdentifierToken(parser, /* stopAtDollarSign = */ false, /* stopAtDot = */ true)) {
                throw new ParseException(parser, "Could not parse identifier token");
            }
            final String identifier = parser.currToken();
            // classBound 可能为 null
            final ReferenceTypeSignature classBound = ReferenceTypeSignature.parseClassBound(parser,
                    definingClassName);
            List<ReferenceTypeSignature> interfaceBounds;
            if (parser.peek() == ':') {
                interfaceBounds = new ArrayList<>();
                while (parser.peek() == ':') {
                    parser.expect(':');
                    final ReferenceTypeSignature interfaceTypeSignature = ReferenceTypeSignature
                            .parseReferenceTypeSignature(parser, definingClassName);
                    if (interfaceTypeSignature == null) {
                        throw new ParseException(parser, "Missing interface type signature");
                    }
                    interfaceBounds.add(interfaceTypeSignature);
                }
            } else {
                interfaceBounds = Collections.emptyList();
            }
            typeParams.add(new TypeParameter(identifier, classBound, interfaceBounds));
        }
        parser.expect('>');
        return typeParams;
    }

    /**
     * 获取类型形参标识符
     *
     * @return 类型形参标识符
     */
    public String getName() {
        return name;
    }

    /**
     * 获取类型形参类边界
     *
     * @return 类型形参类边界可能为 null
     */
    public ReferenceTypeSignature getClassBound() {
        return classBound;
    }

    /**
     * 获取类型形参接口边界
     *
     * @return 获取类型形参接口边界，可能为空列表
     */
    public List<ReferenceTypeSignature> getInterfaceBounds() {
        return interfaceBounds;
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    protected void addTypeAnnotation(final List<TypePathNode> typePath, final AnnotationInfo annotationInfo) {
        if (typePath.isEmpty()) {
            addTypeAnnotation(annotationInfo);
        } else {
            throw new IllegalArgumentException("Type parameter should have empty typePath");
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#getClassName()
     */
    @Override
    protected String getClassName() {
        // getClassInfo() 对此类型无效，因此 getClassName() 不需要实现
        throw new IllegalArgumentException("getClassName() cannot be called here");
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#getClassInfo()
     */
    @Override
    protected ClassInfo getClassInfo() {
        throw new IllegalArgumentException("getClassInfo() cannot be called here");
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (this.classBound != null) {
            this.classBound.setScanResult(scanResult);
        }
        if (interfaceBounds != null) {
            for (final ReferenceTypeSignature referenceTypeSignature : interfaceBounds) {
                referenceTypeSignature.setScanResult(scanResult);
            }
        }
    }

    /**
     * 获取类型签名中引用的任何类的名称
     *
     * @param refdClassNames
     *            引用的类名
     */
    protected void findReferencedClassNames(final Set<String> refdClassNames) {
        if (classBound != null) {
            classBound.findReferencedClassNames(refdClassNames);
        }
        for (final ReferenceTypeSignature typeSignature : interfaceBounds) {
            typeSignature.findReferencedClassNames(refdClassNames);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return name.hashCode() + (classBound == null ? 0 : classBound.hashCode() * 7)
                + interfaceBounds.hashCode() * 15;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof TypeParameter)) {
            return false;
        }
        final TypeParameter other = (TypeParameter) obj;
        return other.name.equals(this.name) && Objects.equals(other.typeAnnotationInfo, this.typeAnnotationInfo)
                && ((other.classBound == null && this.classBound == null)
                || (other.classBound != null && other.classBound.equals(this.classBound)))
                && other.interfaceBounds.equals(this.interfaceBounds);
    }

    // -------------------------------------------------------------------------------------------------------------

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
        buf.append(useSimpleNames ? ClassInfo.getSimpleName(name) : name);
        String classBoundStr;
        if (classBound == null) {
            classBoundStr = null;
        } else {
            classBoundStr = classBound.toString(useSimpleNames);
            if ("java.lang.Object".equals(classBoundStr) || ("Object".equals(classBoundStr)
                    && "java.lang.Object".equals(((ClassRefTypeSignature) classBound).className))) {
                // 不添加 "extends java.lang.Object"
                classBoundStr = null;
            }
        }
        if (classBoundStr != null || !interfaceBounds.isEmpty()) {
            buf.append(" extends");
        }
        if (classBoundStr != null) {
            buf.append(' ');
            buf.append(classBoundStr);
        }
        for (int i = 0; i < interfaceBounds.size(); i++) {
            if (i > 0 || classBoundStr != null) {
                buf.append(" &");
            }
            buf.append(' ');
            interfaceBounds.get(i).toString(useSimpleNames, buf);
        }
    }
}