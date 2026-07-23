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
package com.bingbaihanji.classgraph.metadata;

import com.bingbaihanji.classgraph.utils.LogNode;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** {@link AnnotationParameterValue} 对象的列表 */
public class AnnotationParameterValueList extends MappableInfoList<AnnotationParameterValue> {
    /** 不可修改的空 {@link AnnotationParameterValueList} */
    static final AnnotationParameterValueList EMPTY_LIST = new AnnotationParameterValueList();
    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    static {
        EMPTY_LIST.makeUnmodifiable();
    }

    /**
     * 构造一个新的可修改的空 {@link AnnotationParameterValue} 对象列表
     */
    public AnnotationParameterValueList() {
        super();
    }

    /**
     * 构造一个新的可修改的空 {@link AnnotationParameterValue} 对象列表，并给出大小提示
     *
     * @param sizeHint
     *            大小提示
     */
    public AnnotationParameterValueList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * 构造一个新的可修改的空 {@link AnnotationParameterValueList}，并给出初始的
     * {@link AnnotationParameterValue} 对象列表
     *
     * @param annotationParameterValueCollection
     *            {@link AnnotationParameterValue} 对象的集合
     */
    public AnnotationParameterValueList(
            final Collection<AnnotationParameterValue> annotationParameterValueCollection) {
        super(annotationParameterValueCollection);
    }

    /**
     * 返回一个不可修改的空 {@link AnnotationParameterValueList}
     *
     * @return 不可修改的空 {@link AnnotationParameterValueList}
     */
    public static AnnotationParameterValueList emptyList() {
        return EMPTY_LIST;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此列表中方法所引用的任何类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            被引用的类信息
     * @param log
     *            日志
     */
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        for (final AnnotationParameterValue apv : this) {
            apv.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 对于基本类型数组参数，将包含包装类型的 Object[] 数组替换为基本类型数组(需要检查注解类的每个方法
     * 的类型，以确定其是否为基本类型数组)
     *
     * @param annotationClassInfo
     *            注解类信息
     */
    void convertWrapperArraysToPrimitiveArrays(final ClassInfo annotationClassInfo) {
        for (final AnnotationParameterValue apv : this) {
            apv.convertWrapperArraysToPrimitiveArrays(annotationClassInfo);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取注解参数值，通过对 {@link #get(String)} 的结果(如果非空)调用
     * {@link AnnotationParameterValue#getValue()} 来实现
     *
     * @param parameterName
     *            注解参数名
     * @return 列表中具有给定名称的 {@link AnnotationParameterValue} 对象的值，通过调用
     *         该对象的 {@link AnnotationParameterValue#getValue()} 获取，如果未找到则返回 null
     *
     *         <p>
     *         注解参数值可能为以下类型之一：
     *         <ul>
     *         <li>String —— 字符串常量
     *         <li>String[] —— 字符串数组
     *         <li>包装类型，如 Integer 或 Character —— 基本类型常量
     *         <li>一维基本类型数组(即 int[]、long[]、short[]、char[]、byte[]、boolean[]、
     *         float[] 或 double[])—— 基本类型数组
     *         <li>一维 {@link Object}[] 数组 —— 数组类型(且数组元素类型可能为此列表中的类型之一)
     *         <li>{@link AnnotationEnumValue} —— 枚举常量(包装了枚举类及其常量的字符串名称)
     *         <li>{@link AnnotationClassRef} —— 注解中的 Class 引用(包装了被引用类的名称)
     *         <li>{@link AnnotationInfo} —— 嵌套注解
     *         </ul>
     */
    public Object getValue(final String parameterName) {
        final AnnotationParameterValue apv = get(parameterName);
        return apv == null ? null : apv.getValue();
    }
}
