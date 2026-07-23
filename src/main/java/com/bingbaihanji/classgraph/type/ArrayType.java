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
import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.AnnotationInfoList;
import com.bingbaihanji.classgraph.metadata.ArrayClassInfo;
import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.scan.ScanResult;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 数组类型签名 */
public class ArrayType extends ReferenceType {
    /** 数组类型的原始类型签名字符串 */
    private final String typeSignatureStr;
    /** 嵌套类型(另一个 {@link ArrayType}，或基础元素类型) */
    private final TypeSignature nestedType;
    /** 人类可读的类名，例如 "java.lang.String[]" */
    private String className;
    /** 数组类信息 */
    private ArrayClassInfo arrayClassInfo;
    /** 元素类 */
    private Class<?> elementClassRef;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param elementTypeSignature
     *            数组元素的类型签名
     * @param numDims
     *            数组维度数
     * @param typeSignatureStr
     *            原始数组类型签名字符串(例如 "[[I")
     */
    public ArrayType(final TypeSignature elementTypeSignature, final int numDims, final String typeSignatureStr) {
        super();
        final boolean typeSigHasTwoOrMoreDims = typeSignatureStr.startsWith("[[");
        if (numDims < 1) {
            throw new IllegalArgumentException("numDims < 1");
        } else if ((numDims >= 2) != typeSigHasTwoOrMoreDims) {
            throw new IllegalArgumentException("numDims does not match type signature");
        }
        this.typeSignatureStr = typeSignatureStr;
        this.nestedType = typeSigHasTwoOrMoreDims
                // 为嵌套类型剥离一个数组维度
                ? new ArrayType(elementTypeSignature, numDims - 1, typeSignatureStr.substring(1))
                // 最内层维度的嵌套类型是元素类型
                : elementTypeSignature;
    }

    /**
     * 解析数组类型签名
     *
     * @param TypeParser
     *            解析器
     * @param definingClassName
     *            定义类名
     * @return 数组类型签名
     * @throws ParseException
     *             如果解析失败
     */
    public static ArrayType parse(final TypeParser TypeParser, final String definingClassName) throws ParseException {
        int numArrayDims = 0;
        final int begin = TypeParser.getPosition();
        while (TypeParser.peek() == '[') {
            numArrayDims++;
            TypeParser.next();
        }
        if (numArrayDims > 0) {
            final TypeSignature elementTypeSignature = TypeSignature.parse(TypeParser, definingClassName);
            if (elementTypeSignature == null) {
                throw new ParseException(TypeParser, "elementTypeSignature == null");
            }
            final String typeSignatureStr = TypeParser.getSubsequence(begin, TypeParser.getPosition()).toString();
            return new ArrayType(elementTypeSignature, numArrayDims, typeSignatureStr);
        } else {
            return null;
        }
    }

    /**
     * 获取原始数组类型签名字符串，例如 "[[I"
     *
     * @return 原始数组类型签名字符串
     */
    public String getTypeSignatureStr() {
        return typeSignatureStr;
    }

    /**
     * 获取数组最内层元素类型的类型签名
     *
     * @return 最内层元素类型的类型签名
     */
    public TypeSignature getElementTypeSignature() {
        ArrayType curr = this;
        while (curr.nestedType instanceof ArrayType) {
            curr = (ArrayType) curr.nestedType;
        }
        return curr.getNestedType();
    }

    /**
     * 获取数组的维度数
     *
     * @return 数组的维度数
     */
    public int getNumDimensions() {
        int numDims = 1;
        ArrayType curr = this;
        while (curr.nestedType instanceof ArrayType) {
            curr = (ArrayType) curr.nestedType;
            numDims++;
        }
        return numDims;
    }

    /**
     * 获取嵌套类型如果此数组有 2 个或更多维度，则为少一个维度的另一个 {@link ArrayType}；
     * 否则返回元素类型
     *
     * @return 嵌套类型
     */
    public TypeSignature getNestedType() {
        return nestedType;
    }

    @Override
    public void addTypeAnnotation(final List<TypePathNode> typePath, final AnnotationInfo annotationInfo) {
        if (typePath.isEmpty()) {
            addTypeAnnotation(annotationInfo);
        } else {
            final TypePathNode head = typePath.get(0);
            if (head.typePathKind != 0 || head.TypeArgIdx != 0) {
                throw new IllegalArgumentException("typePath element contains bad values: " + head);
            }
            nestedType.addTypeAnnotation(typePath.subList(1, typePath.size()), annotationInfo);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此数组类型上的类型注解的 {@link AnnotationInfo} 对象列表，如果没有则返回 null
     *
     * @see #getNestedType() 如果需要读取数组类型内部(嵌套)维度上的类型注解
     * @return 此数组类型上的类型注解的 {@link AnnotationInfo} 对象列表，如果没有则返回 null
     */
    @Override
    public AnnotationInfoList getTypeAnnotationInfo() {
        return typeAnnotationInfo;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#getClassName()
     */
    @Override
    public String getClassName() {
        if (className == null) {
            className = toString();
        }
        return className;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#getClassInfo()
     */
    @Override
    public ClassInfo getClassInfo() {
        return getArrayClassInfo();
    }

    /**
     * 返回数组类的 {@link ArrayClassInfo} 实例，并向上转型为其父类
     *
     * @return {@link ArrayClassInfo} 实例
     */
    public ArrayClassInfo getArrayClassInfo() {
        if (arrayClassInfo == null) {
            if (scanResult != null) {
                final String clsName = getClassName();
                // 如果 scanResult 可用，则使用 scanResult.classNameToClassInfo 缓存 ArrayClassInfo 实例
                arrayClassInfo = (ArrayClassInfo) scanResult.classNameToClassInfo.get(clsName);
                if (arrayClassInfo == null) {
                    scanResult.classNameToClassInfo.put(clsName, arrayClassInfo = new ArrayClassInfo(this));
                    arrayClassInfo.setScanResult(this.scanResult);
                }
            } else {
                // scanResult 尚不可用，为此类型创建一个未缓存的 ArrayClassInfo 实例
                arrayClassInfo = new ArrayClassInfo(this);
            }
        }
        return arrayClassInfo;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    public void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        nestedType.setScanResult(scanResult);
        if (arrayClassInfo != null) {
            arrayClassInfo.setScanResult(scanResult);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取类型签名中引用的任何类的名称
     *
     * @param refdClassNames
     *            被引用的类名
     */
    @Override
    public void findReferencedClassNames(final Set<String> refdClassNames) {
        nestedType.findReferencedClassNames(refdClassNames);
    }

    /**
     * 获取最内层数组元素类型的 {@code Class<?>} 引用如果类尚未加载，会导致 ClassLoader 加载它
     *
     * @param ignoreExceptions
     *            是否忽略异常
     * @return 最内层数组元素类型的 {@code Class<?>} 引用对于基本类型元素的数组同样有效
     */
    public Class<?> loadElementClass(final boolean ignoreExceptions) {
        if (elementClassRef == null) {
            // 尝试将元素类型解析为基本类型(int 等)
            final TypeSignature elementTypeSignature = getElementTypeSignature();
            if (elementTypeSignature instanceof BaseType) {
                elementClassRef = ((BaseType) elementTypeSignature).getType();
            } else {
                if (scanResult != null) {
                    elementClassRef = elementTypeSignature.loadClass(ignoreExceptions);
                } else {
                    // 回退方案，如果 scanResult 未设置
                    final String elementTypeName = elementTypeSignature.getClassName();
                    try {
                        elementClassRef = Class.forName(elementTypeName);
                    } catch (final Throwable t) {
                        if (!ignoreExceptions) {
                            throw new IllegalArgumentException(
                                    "Could not load array element class " + elementTypeName, t);
                        }
                    }
                }
            }
        }
        return elementClassRef;
    }

    /**
     * 获取数组元素类型的 {@code Class<?>} 引用如果元素类尚未加载，会导致 ClassLoader 加载它
     *
     * @return 数组元素类型的 {@code Class<?>} 引用对于基本类型元素的数组同样有效
     */
    public Class<?> loadElementClass() {
        return loadElementClass(/* ignoreExceptions = */ false);
    }

    /**
     * 获取此 {@link ArrayClassInfo} 对象所命名的数组类的 {@code Class<?>} 引用如果元素类尚未加载，
     * 会导致 ClassLoader 加载它
     *
     * @param ignoreExceptions
     *            是否忽略异常
     * @return 类引用，如果 ignoreExceptions 为 true 且在加载类时发生了异常或错误，则返回 null
     * @throws IllegalArgumentException
     *             如果 ignoreExceptions 为 false 且加载类时出现问题
     */
    @Override
    public Class<?> loadClass(final boolean ignoreExceptions) {
        if (classRef == null) {
            // 获取元素类型
            Class<?> eltClassRef = null;
            if (ignoreExceptions) {
                try {
                    eltClassRef = loadElementClass();
                } catch (final IllegalArgumentException e) {
                    return null;
                }
            } else {
                eltClassRef = loadElementClass();
            }
            if (eltClassRef == null) {
                throw new IllegalArgumentException(
                        "Could not load array element class " + getElementTypeSignature());
            }
            // 创建目标维度数的数组，每个维度大小为零
            final Object eltArrayInstance = Array.newInstance(eltClassRef, new int[getNumDimensions()]);
            // 从数组实例获取类引用
            classRef = eltArrayInstance.getClass();
        }
        return classRef;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此 {@link ArrayClassInfo} 对象所命名的数组类的 {@code Class<?>} 引用如果元素类尚未加载，
     * 会导致 ClassLoader 加载它
     *
     * @return 类引用
     * @throws IllegalArgumentException
     *             如果加载类时出现问题
     */
    @Override
    public Class<?> loadClass() {
        return loadClass(/* ignoreExceptions = */ false);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return 1 + nestedType.hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof ArrayType)) {
            return false;
        }
        final ArrayType other = (ArrayType) obj;
        return Objects.equals(this.typeAnnotationInfo, other.typeAnnotationInfo)
                && this.nestedType.equals(other.nestedType);
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.TypeSignature#equalsIgnoringTypeParams(com.bingbaihanji.classgraph.core.TypeSignature)
     */
    @Override
    public boolean equalsIgnoringTypeParams(final TypeSignature other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ArrayType)) {
            return false;
        }
        final ArrayType o = (ArrayType) other;
        return this.nestedType.equalsIgnoringTypeParams(o.nestedType);
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    public void toStringInternal(final boolean useSimpleNames, final AnnotationInfoList annotationsToExclude,
                                 final StringBuilder buf) {
        // 从最内层数组元素类型开始
        getElementTypeSignature().toStringInternal(useSimpleNames, annotationsToExclude, buf);

        // 追加数组维度
        for (ArrayType curr = this; ; ) {
            if (curr.typeAnnotationInfo != null && !curr.typeAnnotationInfo.isEmpty()) {
                for (final AnnotationInfo annotationInfo : curr.typeAnnotationInfo) {
                    if (buf.length() == 0 || buf.charAt(buf.length() - 1) != ' ') {
                        buf.append(' ');
                    }
                    annotationInfo.toString(useSimpleNames, buf);
                }
                buf.append(' ');
            }

            buf.append("[]");

            if (curr.nestedType instanceof ArrayType) {
                curr = (ArrayType) curr.nestedType;
            } else {
                break;
            }
        }
    }
}