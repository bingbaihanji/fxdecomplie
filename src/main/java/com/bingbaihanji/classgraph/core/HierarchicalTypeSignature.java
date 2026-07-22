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

import java.util.List;

/**
 * Java 类型签名子类包括 ClassTypeSignature、MethodTypeSignature 和 TypeSignature
 */
public abstract class HierarchicalTypeSignature extends ScanResultObject {
    protected AnnotationInfoList typeAnnotationInfo;

    /** 一个层次化的类型签名 */
    public HierarchicalTypeSignature() {
        super();
    }

    /**
     * 添加一个类型注解
     *
     * @param annotationInfo
     *            注解
     */
    protected void addTypeAnnotation(final AnnotationInfo annotationInfo) {
        if (typeAnnotationInfo == null) {
            typeAnnotationInfo = new AnnotationInfoList(1);
        }
        typeAnnotationInfo.add(annotationInfo);
    }

    @Override
    void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (typeAnnotationInfo != null) {
            for (final AnnotationInfo annotationInfo : typeAnnotationInfo) {
                annotationInfo.setScanResult(scanResult);
            }
        }
    }

    /**
     * 获取此类型上的所有类型注解的 {@link AnnotationInfo} 对象列表，如果没有则返回 null
     *
     * @return 此类型上的所有类型注解的 {@link AnnotationInfo} 对象列表，如果没有则返回 null
     */
    public AnnotationInfoList getTypeAnnotationInfo() {
        return typeAnnotationInfo;
    }

    /**
     * 添加一个类型注解
     *
     * @param typePath
     *            类型路径
     * @param annotationInfo
     *            注解
     */
    protected abstract void addTypeAnnotation(List<TypePathNode> typePath, AnnotationInfo annotationInfo);

    /**
     * 将类型签名渲染为字符串
     *
     * @param useSimpleNames
     *            是否使用类的简单名称
     * @param annotationsToExclude
     *            要排除的顶层注解，用于消除重复(顶层注解同时是类/字段/方法注解和类型注解)
     * @param buf
     *            要写入的 {@link StringBuilder}
     */
    protected abstract void toStringInternal(final boolean useSimpleNames, AnnotationInfoList annotationsToExclude,
                                             StringBuilder buf);

    /**
     * 将类型签名渲染为字符串
     *
     * @param useSimpleNames
     *            是否使用类的简单名称
     * @param buf
     *            要写入的 {@link StringBuilder}
     */
    @Override
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        toStringInternal(useSimpleNames, /* annotationsToExclude = */ null, buf);
    }
}