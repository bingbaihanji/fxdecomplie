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

import com.bingbaihanji.classgraph.metadata.*;
import com.bingbaihanji.classgraph.util.*;

import com.bingbaihanji.classgraph.metadata.ClassHierarchy.RelType;
import com.bingbaihanji.classgraph.util.Assert;
import com.bingbaihanji.classgraph.util.CollectionUtils;
import com.bingbaihanji.classgraph.util.LogNode;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.util.*;

/** {@link AnnotationInfo} 对象的列表 */
public class AnnotationInfoList extends InfoList<AnnotationInfo> {
    /** 一个不可修改的空 {@link AnnotationInfoList} */
    static final AnnotationInfoList EMPTY_LIST = new AnnotationInfoList();
    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    static {
        EMPTY_LIST.makeUnmodifiable();
    }

    /**
     * 与类或方法直接相关、且不是通过元注解继承的注解集合
     * 此字段可为 null，因为注解信息列表是增量构建的参见 {@link #directOnly()}
     */
    private AnnotationInfoList directlyRelatedAnnotations;

    /**
     * 构造一个新的可修改的空 {@link AnnotationInfo} 对象列表
     */
    public AnnotationInfoList() {
        super();
    }

    /**
     * 构造一个新的可修改的空 {@link AnnotationInfo} 对象列表，并给出大小提示
     *
     * @param sizeHint
     *            大小提示
     */
    public AnnotationInfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * 构造一个新的可修改的空 {@link AnnotationInfoList}，并给出初始的 {@link AnnotationInfo} 对象列表
     *
     * @param reachableAnnotations
     *            可达的注解
     */
    public AnnotationInfoList(final AnnotationInfoList reachableAnnotations) {
        // 如果只给出了可达的注解，则将它们全部视为直接注解
        this(reachableAnnotations, reachableAnnotations);
    }

    /**
     * 构造函数
     *
     * @param reachableAnnotations
     *            可达的注解
     * @param directlyRelatedAnnotations
     *            直接相关的注解
     */
    AnnotationInfoList(final AnnotationInfoList reachableAnnotations,
                       final AnnotationInfoList directlyRelatedAnnotations) {
        super(reachableAnnotations);
        this.directlyRelatedAnnotations = directlyRelatedAnnotations;
    }

    /**
     * 返回一个不可修改的空 {@link AnnotationInfoList}
     *
     * @return 不可修改的空 {@link AnnotationInfoList}
     */
    public static AnnotationInfoList emptyList() {
        return EMPTY_LIST;
    }

    /**
     * 通过注解名称获取 {@link AnnotationInfo}
     *
     * @param annotationName 注解名称
     * @return 注解信息，若不存在则返回 null
     */
    public AnnotationInfo get(final String annotationName) {
        for (final AnnotationInfo ai : this) {
            if (ai.getName().equals(annotationName)) {
                return ai;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 查找元注解的传递闭包
     *
     * @param ai
     *            AnnotationInfo 对象
     * @param allAnnotationsOut
     *            输出的注解集合
     * @param visited
     *            已访问的集合
     */
    private static void findMetaAnnotations(final AnnotationInfo ai, final AnnotationInfoList allAnnotationsOut,
                                            final Set<ClassInfo> visited) {
        final ClassInfo annotationClassInfo = ai.getClassInfo();
        if (annotationClassInfo != null && annotationClassInfo.annotationInfo != null
                // 避免循环
                && visited.add(annotationClassInfo)) {
            for (final AnnotationInfo metaAnnotationInfo : annotationClassInfo.annotationInfo) {
                final ClassInfo metaAnnotationClassInfo = metaAnnotationInfo.getClassInfo();
                if (metaAnnotationClassInfo == null) {
                    continue;
                }
                final String metaAnnotationClassName = metaAnnotationClassInfo.getName();
                // 不将 java.lang.annotation 中的注解视为元注解
                if (!metaAnnotationClassName.startsWith("java.lang.annotation.")) {
                    // 将元注解添加到传递闭包中
                    allAnnotationsOut.add(metaAnnotationInfo);
                    // 递归处理元-元注解
                    findMetaAnnotations(metaAnnotationInfo, allAnnotationsOut, visited);
                }
            }
        }
    }

    /**
     * 获取类上的间接注解(元注解和/或继承的注解)
     *
     * @param directAnnotationInfo
     *            类、方法、方法参数或字段上的直接注解
     * @param annotatedClass
     *            对于类注解，这是被注解的类，否则为 null
     * @return 间接注解
     */
    static AnnotationInfoList getIndirectAnnotations(final AnnotationInfoList directAnnotationInfo,
                                                     final ClassInfo annotatedClass) {
        // 添加直接注解
        final Set<ClassInfo> directOrInheritedAnnotationClasses = new HashSet<>();
        final Set<ClassInfo> reachedAnnotationClasses = new HashSet<>();
        final AnnotationInfoList reachableAnnotationInfo = new AnnotationInfoList(
                directAnnotationInfo == null ? 2 : directAnnotationInfo.size());
        if (directAnnotationInfo != null) {
            for (final AnnotationInfo dai : directAnnotationInfo) {
                final ClassInfo daiClassInfo = dai.getClassInfo();
                if (daiClassInfo != null) {
                    directOrInheritedAnnotationClasses.add(daiClassInfo);
                }
                reachableAnnotationInfo.add(dai);
                findMetaAnnotations(dai, reachableAnnotationInfo, reachedAnnotationClasses);
            }
        }
        if (annotatedClass != null) {
            // 添加超类上任何 @Inherited 注解
            for (final ClassInfo superclass : annotatedClass.getSuperclasses()) {
                if (superclass.annotationInfo != null) {
                    for (final AnnotationInfo sai : superclass.annotationInfo) {
                        // 如果继承的超类注解在子类中被覆盖，则不添加
                        if (sai.isInherited() && directOrInheritedAnnotationClasses.add(sai.getClassInfo())) {
                            reachableAnnotationInfo.add(sai);
                            final AnnotationInfoList reachableMetaAnnotationInfo = new AnnotationInfoList(2);
                            findMetaAnnotations(sai, reachableMetaAnnotationInfo, reachedAnnotationClasses);
                            // 元注解也必须具有 @Inherited 才能被继承
                            for (final AnnotationInfo rmai : reachableMetaAnnotationInfo) {
                                if (rmai.isInherited()) {
                                    reachableAnnotationInfo.add(rmai);
                                }
                            }
                        }
                    }
                }
            }
        }
        // 返回排序后的注解列表
        final AnnotationInfoList directAnnotationInfoSorted = directAnnotationInfo == null
                ? AnnotationInfoList.EMPTY_LIST
                : new AnnotationInfoList(directAnnotationInfo);
        CollectionUtils.sortIfNotEmpty(directAnnotationInfoSorted);
        final AnnotationInfoList annotationInfoList = new AnnotationInfoList(reachableAnnotationInfo,
                directAnnotationInfoSorted);
        CollectionUtils.sortIfNotEmpty(annotationInfoList);
        return annotationInfoList;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 查找此列表中满足给定过滤谓词的 {@link AnnotationInfo} 对象子集
     *
     * @param filter
     *            要应用的 {@link AnnotationInfoFilter}
     * @return 此列表中满足给定过滤谓词的 {@link AnnotationInfo} 对象子集
     */
    public AnnotationInfoList filter(final AnnotationInfoFilter filter) {
        final AnnotationInfoList annotationInfoFiltered = new AnnotationInfoList();
        for (final AnnotationInfo resource : this) {
            if (filter.accept(resource)) {
                annotationInfoFiltered.add(resource);
            }
        }
        return annotationInfoFiltered;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 检查此列表中是否包含具有给定名称的 {@link AnnotationInfo} 对象
     *
     * @param annotationName
     *            注解名称
     * @return 如果此列表包含具有给定名称的 {@link AnnotationInfo} 对象，则返回 true
     */
    public boolean containsName(final String annotationName) {
        for (final AnnotationInfo ai : this) {
            if (ai.getName().equals(annotationName)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此列表中引用的任何类的 {@link ClassInfo} 对象
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
        for (final AnnotationInfo ai : this) {
            ai.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 处理 {@link Repeatable} 注解
     *
     * @param allRepeatableAnnotationNames
     *            所有可重复注解的名称
     * @param containingClassInfo
     *            包含的类
     * @param forwardRelType
     *            用于链接的正向关系类型(或 null 表示不链接)
     * @param reverseRelType0
     *            用于链接的第一个反向关系类型(或 null 表示不链接)
     * @param reverseRelType1
     *            用于链接的第二个反向关系类型(或 null 表示不链接)
     */
    void handleRepeatableAnnotations(final Set<String> allRepeatableAnnotationNames,
                                     final ClassInfo containingClassInfo, final RelType forwardRelType, final RelType reverseRelType0,
                                     final RelType reverseRelType1) {
        List<AnnotationInfo> repeatableAnnotations = null;
        for (int i = size() - 1; i >= 0; --i) {
            final AnnotationInfo ai = get(i);
            if (allRepeatableAnnotationNames.contains(ai.getName())) {
                if (repeatableAnnotations == null) {
                    repeatableAnnotations = new ArrayList<>();
                }
                repeatableAnnotations.add(ai);
                // 移除可重复注解
                remove(i);
            }
        }
        // 将每个可重复注解参数中的组件注解添加进来
        if (repeatableAnnotations != null) {
            for (final AnnotationInfo repeatableAnnotation : repeatableAnnotations) {
                final AnnotationParameterValueList values = repeatableAnnotation.getParameterValues();
                if (!values.isEmpty()) {
                    final AnnotationParameterValue apv = values.get("value");
                    if (apv != null) {
                        final Object arr = apv.getValue();
                        if (arr instanceof Object[]) {
                            for (final Object value : (Object[]) arr) {
                                if (value instanceof AnnotationInfo) {
                                    final AnnotationInfo ai = (AnnotationInfo) value;
                                    add(ai);

                                    // 如果需要，链接注解
                                    if (forwardRelType != null
                                            && (reverseRelType0 != null || reverseRelType1 != null)) {
                                        final ClassInfo annotationClass = ai.getClassInfo();
                                        if (annotationClass != null) {
                                            containingClassInfo.hierarchy().addRelation(forwardRelType, annotationClass);
                                            if (reverseRelType0 != null) {
                                                annotationClass.hierarchy().addRelation(reverseRelType0,
                                                        containingClassInfo);
                                            }
                                            if (reverseRelType1 != null) {
                                                annotationClass.hierarchy().addRelation(reverseRelType1,
                                                        containingClassInfo);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 返回直接注解的列表，不包括元注解如果此 {@link AnnotationInfoList} 由类注解组成，
     * 即通过 `ClassInfo#getAnnotationInfo()` 生成，则返回的列表也会排除从超类或实现的接口
     * 继承的注解(这些超类或接口被 {@link java.lang.annotation.Inherited @Inherited} 元注解修饰)
     *
     * @return 直接相关注解的列表
     */
    public AnnotationInfoList directOnly() {
        // 如果 directlyRelatedAnnotations == null，则这已经是一个直接注解列表
        // (即读取 class 文件时创建的 AnnotationInfo 对象列表)
        // 否则，返回一个仅包含直接注解的新列表
        return this.directlyRelatedAnnotations == null ? this
                // 使 .directOnly() 幂等
                : new AnnotationInfoList(directlyRelatedAnnotations, /* directlyRelatedAnnotations = */ null);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取具有给定类的 {@link Repeatable} 注解，如果没有找到则返回空列表
     *
     * @param annotationClass
     *            要搜索的类
     * @return 具有给定类的注解列表，如果没有找到则返回空列表
     */
    public AnnotationInfoList getRepeatable(final Class<? extends Annotation> annotationClass) {
        Assert.isAnnotation(annotationClass);
        return getRepeatable(annotationClass.getName());
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取具有给定名称的 {@link Repeatable} 注解，如果没有找到则返回空列表
     *
     * @param name
     *            要搜索的名称
     * @return 具有给定名称的注解列表，如果没有找到则返回空列表
     */
    public AnnotationInfoList getRepeatable(final String name) {
        final AnnotationInfoList matchingAnnotations = new AnnotationInfoList();
        for (final AnnotationInfo ai : this) {
            if (ai.getName().equals(name)) {
                matchingAnnotations.add(ai);
            }
        }
        if (matchingAnnotations.isEmpty()) {
            return AnnotationInfoList.EMPTY_LIST;
        }
        matchingAnnotations.trimToSize();
        return matchingAnnotations;
    }

    /* (non-Javadoc)
     * @see java.util.ArrayList#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof AnnotationInfoList)) {
            return false;
        }
        final AnnotationInfoList other = (AnnotationInfoList) obj;
        if ((directlyRelatedAnnotations == null) != (other.directlyRelatedAnnotations == null)) {
            return false;
        }
        if (directlyRelatedAnnotations == null) {
            return super.equals(other);
        }
        return super.equals(other) && directlyRelatedAnnotations.equals(other.directlyRelatedAnnotations);
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.util.ArrayList#hashCode()
     */
    @Override
    public int hashCode() {
        return super.hashCode() ^ (directlyRelatedAnnotations == null ? 0 : directlyRelatedAnnotations.hashCode());
    }

    /**
     * 使用一个将 {@link AnnotationInfo} 对象映射为布尔值的谓词来过滤 {@link AnnotationInfoList}，
     * 生成另一个 {@link AnnotationInfoList}，包含列表中所有谓词为 true 的元素
     */
    @FunctionalInterface
    public interface AnnotationInfoFilter {
        /**
         * 是否允许 {@link AnnotationInfo} 列表项通过过滤器
         *
         * @param annotationInfo
         *            要过滤的 {@link AnnotationInfo} 项
         * @return 是否允许该项通过过滤器如果为 true，则该项被复制到输出列表；如果为 false，则被排除
         */
        boolean accept(AnnotationInfo annotationInfo);
    }
}
