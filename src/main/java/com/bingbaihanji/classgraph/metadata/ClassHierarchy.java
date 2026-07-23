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

import com.bingbaihanji.classgraph.metadata.ClassInfo.ClassType;
import com.bingbaihanji.classgraph.metadata.MethodInfoList;
import com.bingbaihanji.classgraph.scan.ScanConfig;

import java.lang.annotation.Inherited;
import java.util.*;

/** 管理类的层次结构关系：超类、子类、接口、注解等关系图 */
public class ClassHierarchy {

    /** 当没有任何类可达时返回的空常量值 */
    private static final ReachableAndDirectlyRelatedClasses NO_REACHABLE_CLASSES =
            new ReachableAndDirectlyRelatedClasses(Collections.<ClassInfo>emptySet(),
                    Collections.<ClassInfo>emptySet());

    /** 所属的 ClassInfo 对象 */
    private final ClassInfo owner;
    /** 与该类相关联的类集合 */
    private final Map<RelType, Set<ClassInfo>> relatedClasses;
    /** 注解列表，加载后的缓存 */
    private ClassInfoList annotationsRef;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param owner 所属的 ClassInfo 对象
     */
    ClassHierarchy(final ClassInfo owner) {
        this.owner = owner;
        this.relatedClasses = new EnumMap<>(RelType.class);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取关联关系 Map（包级私有，仅供 ClassInfo 内部桥接方法使用）
     *
     * @return relatedClasses 映射
     */
    Map<RelType, Set<ClassInfo>> getRelatedClasses() {
        return relatedClasses;
    }

    /**
     * 添加具有给定关系类型的类，返回调用是否导致集合发生变化
     *
     * @param relType   {@link RelType} 关系类型
     * @param classInfo {@link ClassInfo} 对象
     * @return 如果成功添加则返回 true
     */
    void addRelation(final RelType relType, final ClassInfo classInfo) {
        Set<ClassInfo> classInfoSet = relatedClasses.get(relType);
        if (classInfoSet == null) {
            relatedClasses.put(relType, classInfoSet = new LinkedHashSet<>(4));
        }
        classInfoSet.add(classInfo);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取与给定关系类型相关联的类（传递闭包）以及直接关联的类
     *
     * @param relType      关系类型
     * @param strictAccept 如果为 true，则当外部类未启用时排除外部类
     * @param classTypes   要接受的类类型
     * @return 可达的类和直接关联的类
     */
    ClassHierarchy.ReachableAndDirectlyRelatedClasses filterClassInfo(final RelType relType, final boolean strictAccept,
                                                                      final ClassType... classTypes) {
        Set<ClassInfo> directlyRelatedClasses = this.relatedClasses.get(relType);
        if (directlyRelatedClasses == null) {
            return NO_REACHABLE_CLASSES;
        } else {
            // 克隆集合以防止用户意外或故意修改内容
            directlyRelatedClasses = new LinkedHashSet<>(directlyRelatedClasses);
        }
        final Set<ClassInfo> reachableClasses = new LinkedHashSet<>(directlyRelatedClasses);
        if (relType == RelType.METHOD_ANNOTATIONS || relType == RelType.METHOD_PARAMETER_ANNOTATIONS
                || relType == RelType.FIELD_ANNOTATIONS) {
            // 对于方法和字段注解，在查找元注解时需要更改 RelType
            for (final ClassInfo annotation : directlyRelatedClasses) {
                reachableClasses.addAll(
                        annotation.hierarchy().filterClassInfo(RelType.CLASS_ANNOTATIONS, strictAccept).reachableClasses);
            }
        } else if (relType == RelType.CLASSES_WITH_METHOD_ANNOTATION
                || relType == RelType.CLASSES_WITH_NONPRIVATE_METHOD_ANNOTATION
                || relType == RelType.CLASSES_WITH_METHOD_PARAMETER_ANNOTATION
                || relType == RelType.CLASSES_WITH_NONPRIVATE_METHOD_PARAMETER_ANNOTATION
                || relType == RelType.CLASSES_WITH_FIELD_ANNOTATION
                || relType == RelType.CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION) {
            // 如果查找元注解标注的方法或字段，需要找到所有元注解的注解，然后
            // 查找它们标注的方法或字段
            for (final ClassInfo subAnnotation : this.filterClassInfo(RelType.CLASSES_WITH_ANNOTATION, strictAccept,
                    ClassType.ANNOTATION).reachableClasses) {
                final Set<ClassInfo> annotatedClasses = subAnnotation.hierarchy().relatedClasses.get(relType);
                if (annotatedClasses != null) {
                    reachableClasses.addAll(annotatedClasses);
                }
            }
        } else {
            // 对于其他关系类型，可达类型在传递闭包中保持不变查找
            // 传递闭包，在必要时打破循环
            final LinkedList<ClassInfo> queue = new LinkedList<>(directlyRelatedClasses);
            while (!queue.isEmpty()) {
                final ClassInfo head = queue.removeFirst();
                final Set<ClassInfo> headRelatedClasses = head.hierarchy().relatedClasses.get(relType);
                if (headRelatedClasses != null) {
                    for (final ClassInfo directlyReachableFromHead : headRelatedClasses) {
                        // 避免循环
                        if (reachableClasses.add(directlyReachableFromHead)) {
                            queue.add(directlyReachableFromHead);
                        }
                    }
                }
            }
        }
        if (reachableClasses.isEmpty()) {
            return NO_REACHABLE_CLASSES;
        }

        if (relType == RelType.CLASS_ANNOTATIONS || relType == RelType.METHOD_ANNOTATIONS
                || relType == RelType.METHOD_PARAMETER_ANNOTATIONS || relType == RelType.FIELD_ANNOTATIONS) {
            // 特殊情况——不将 java.lang.annotation.* 元注解作为相关元注解继承
            // （但仍将它们作为注解类上的直接元注解返回）
            Set<ClassInfo> reachableClassesToRemove = null;
            for (final ClassInfo reachableClassInfo : reachableClasses) {
                // 移除所有不直接与此类关联的 java.lang.annotation 注解
                if (reachableClassInfo.getName().startsWith("java.lang.annotation.")
                        && !directlyRelatedClasses.contains(reachableClassInfo)) {
                    if (reachableClassesToRemove == null) {
                        reachableClassesToRemove = new LinkedHashSet<>();
                    }
                    reachableClassesToRemove.add(reachableClassInfo);
                }
            }
            if (reachableClassesToRemove != null) {
                reachableClasses.removeAll(reachableClassesToRemove);
            }
        }

        return new ReachableAndDirectlyRelatedClasses(
                ClassInfo.filterClassInfo(reachableClasses, owner.scanResult.ScanConfig, strictAccept, classTypes),
                ClassInfo.filterClassInfo(directlyRelatedClasses, owner.scanResult.ScanConfig, strictAccept, classTypes));
    }

    // -------------------------------------------------------------------------------------------------------------
    // 子类 / 超类

    /**
     * 获取此类的子类，按名称排序调用 {@link ClassInfoList#directOnly()} 可获取直接子类
     *
     * 如果此类表示 {@link Object}，则只返回标准类而非接口，因为接口不继承 {@link Object}
     *
     * @return 此类子类的列表，如果没有则返回空列表
     */
    public ClassInfoList getSubclasses() {
        if ("java.lang.Object".equals(owner.getName())) {
            // 对查询 java.lang.Object 的所有子类做特殊处理
            return owner.scanResult.classes().getAllStandardClasses();
        } else {
            return new ClassInfoList(
                    this.filterClassInfo(RelType.SUBCLASSES, /* strictAccept = */ !owner.isExternalClass()),
                    /* sortByName = */ true);
        }
    }

    /**
     * 获取此类的所有超类，按类层次结构升序排列，为简单起见不包括 {@link Object}，
     * 因为它是所有类的超类
     *
     * 也不包括超接口，如果这是接口的话（使用 {@link #getInterfaces()} 获取接口的超接口）
     *
     * @return 此类所有超类的列表，如果没有则返回空列表
     */
    public ClassInfoList getSuperclasses() {
        return new ClassInfoList(this.filterClassInfo(RelType.SUPERCLASSES, /* strictAccept = */ false),
                /* sortByName = */ false);
    }

    /**
     * 获取此类的单个直接超类，如果没有则返回 null不返回超接口，如果这是接口的话
     * （使用 {@link #getInterfaces()} 获取接口的超接口）
     *
     * @return 此类的超类，如果没有则返回 null
     */
    public ClassInfo getSuperclass() {
        final Set<ClassInfo> superClasses = relatedClasses.get(RelType.SUPERCLASSES);
        if (superClasses == null || superClasses.isEmpty()) {
            return null;
        } else if (superClasses.size() > 2) {
            throw new IllegalArgumentException("More than one superclass: " + superClasses);
        } else {
            ClassInfo nonObjectSuperclass = null;
            for (final ClassInfo superclass : superClasses) {
                if (!"java.lang.Object".equals(superclass.getName())) {
                    nonObjectSuperclass = superclass;
                }
            }
            return nonObjectSuperclass;
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 接口

    /**
     * 获取此类或其超类实现的接口，如果这是标准类的话；或者此接口扩展的超接口，如果这是接口的话
     *
     * @return 此类或其超类实现的接口列表，如果这是标准类的话；或者此接口扩展的超接口，
     *         如果这是接口的话如果没有则返回空列表
     */
    public ClassInfoList getInterfaces() {
        // 类也会继承其超类实现的接口
        final ReachableAndDirectlyRelatedClasses implementedInterfaces = this
                .filterClassInfo(RelType.IMPLEMENTED_INTERFACES, /* strictAccept = */ false);
        final Set<ClassInfo> allInterfaces = new LinkedHashSet<>(implementedInterfaces.reachableClasses);
        for (final ClassInfo superclass : this.filterClassInfo(RelType.SUPERCLASSES,
                /* strictAccept = */ false).reachableClasses) {
            final Set<ClassInfo> superclassImplementedInterfaces = superclass.hierarchy()
                    .filterClassInfo(RelType.IMPLEMENTED_INTERFACES, /* strictAccept = */ false).reachableClasses;
            allInterfaces.addAll(superclassImplementedInterfaces);
        }
        // 不能按名称排序接口，因为它们在继承定义中的顺序是重要的
        return new ClassInfoList(allInterfaces, implementedInterfaces.directlyRelatedClasses,
                /* sortByName = */ false);
    }

    /**
     * 获取实现此接口的类（及其子类），如果这是接口的话
     *
     * @return 实现此接口的类（及其子类）的列表，如果这是接口的话；否则返回空列表
     */
    public ClassInfoList getClassesImplementing() {
        // 实现类的子类也会实现该接口
        final ReachableAndDirectlyRelatedClasses implementingClasses = this
                .filterClassInfo(RelType.CLASSES_IMPLEMENTING, /* strictAccept = */ !owner.isExternalClass());
        final Set<ClassInfo> allImplementingClasses = new LinkedHashSet<>(implementingClasses.reachableClasses);
        for (final ClassInfo implementingClass : implementingClasses.reachableClasses) {
            final Set<ClassInfo> implementingSubclasses = implementingClass.hierarchy().filterClassInfo(RelType.SUBCLASSES,
                    /* strictAccept = */ !implementingClass.isExternalClass()).reachableClasses;
            allImplementingClasses.addAll(implementingSubclasses);
        }
        return new ClassInfoList(allImplementingClasses, implementingClasses.directlyRelatedClasses,
                /* sortByName = */ true);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 内部类 / 外部类

    /**
     * 获取包含此外部类，如果这是内部类的话
     *
     * @return 包含外部类的列表，如果这是内部类的话；否则返回空列表注意，返回所有
     *         包含外部类，而不仅仅是最内层的包含外部类
     */
    public ClassInfoList getOuterClasses() {
        return new ClassInfoList(
                this.filterClassInfo(RelType.CONTAINED_WITHIN_OUTER_CLASS, /* strictAccept = */ false),
                /* sortByName = */ false);
    }

    /**
     * 获取包含在此类中的内部类，如果这是外部类的话
     *
     * @return 包含在此类中的内部类列表，如果没有则返回空列表
     */
    public ClassInfoList getInnerClasses() {
        return new ClassInfoList(this.filterClassInfo(RelType.CONTAINS_INNER_CLASS, /* strictAccept = */ false),
                /* sortByName = */ true);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 注解

    /**
     * 获取此类上的注解和元注解（如果需要注解的参数值而不仅仅是注解类，
     * 请调用 {@link ClassInfo#getAnnotationInfo()} 替代此方法）
     *
     * <p>
     * 还处理 {@link Inherited} 元注解，该元注解使得注解可以标注一个类及其所有子类
     *
     * <p>
     * 过滤掉 {@code java.lang.annotation} 包中的元注解
     *
     * @return 此类上的注解和元注解列表
     */
    public ClassInfoList getAnnotations() {
        if (annotationsRef != null) {
            return annotationsRef;
        }

        if (!owner.scanResult.ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableAnnotationInfo() before #scan()");
        }

        // 获取此类上的所有注解
        final ReachableAndDirectlyRelatedClasses annotationClasses = this
                .filterClassInfo(RelType.CLASS_ANNOTATIONS, /* strictAccept = */ false);
        // 检查超类上是否有任何 @Inherited 注解
        Set<ClassInfo> inheritedSuperclassAnnotations = null;
        for (final ClassInfo superclass : getSuperclasses()) {
            for (final ClassInfo superclassAnnotation : superclass.hierarchy().filterClassInfo(RelType.CLASS_ANNOTATIONS,
                    /* strictAccept = */ false).reachableClasses) {
                // 检查此注解上的任何元注解是否包含 @Inherited，
                // @Inherited 元注解使得注解可以标注一个类及其所有子类
                if (superclassAnnotation != null && superclassAnnotation.isInherited) {
                    // superclassAnnotation 具有 @Inherited 元注解
                    if (inheritedSuperclassAnnotations == null) {
                        inheritedSuperclassAnnotations = new LinkedHashSet<>();
                    }
                    inheritedSuperclassAnnotations.add(superclassAnnotation);
                }
            }
        }

        if (inheritedSuperclassAnnotations == null) {
            // 没有可继承的超类注解
            annotationsRef = new ClassInfoList(annotationClasses, /* sortByName = */ true);
        } else {
            // 合并可继承的超类注解和此类上的注解
            inheritedSuperclassAnnotations.addAll(annotationClasses.reachableClasses);
            annotationsRef = new ClassInfoList(inheritedSuperclassAnnotations,
                    annotationClasses.directlyRelatedClasses, /* sortByName = */ true);
        }
        return annotationsRef;
    }

    /**
     * 获取以此类作为注解的类
     *
     * @return 被此类标注的标准类和非注解接口的列表，如果这是注解类的话；如果没有则返回空列表
     *         还处理 {@link Inherited} 元注解，该元注解使得类上的注解可以被其所有子类继承
     */
    public ClassInfoList getClassesWithAnnotation() {
        if (!owner.scanResult.ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableAnnotationInfo() before #scan()");
        }

        // 获取拥有此注解的类
        final ReachableAndDirectlyRelatedClasses classesWithAnnotation = this
                .filterClassInfo(RelType.CLASSES_WITH_ANNOTATION, /* strictAccept = */ !owner.isExternalClass());

        if (owner.isInherited) {
            // 如果这是可继承的注解，将所有被注解类的子类也加入结果中
            final Set<ClassInfo> classesWithAnnotationAndTheirSubclasses = new LinkedHashSet<>(
                    classesWithAnnotation.reachableClasses);
            for (final ClassInfo classWithAnnotation : classesWithAnnotation.reachableClasses) {
                classesWithAnnotationAndTheirSubclasses.addAll(classWithAnnotation.hierarchy().getSubclasses());
            }
            return new ClassInfoList(classesWithAnnotationAndTheirSubclasses,
                    classesWithAnnotation.directlyRelatedClasses, /* sortByName = */ true);
        } else {
            // 如果不可继承，只返回被注解的类
            return new ClassInfoList(classesWithAnnotation, /* sortByName = */ true);
        }
    }

    /**
     * 获取以此类作为直接注解的类
     *
     * @return 被请求的注解直接标注（即非元注解标注）的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithAnnotationDirectOnly() {
        return new ClassInfoList(
                this.filterClassInfo(RelType.CLASSES_WITH_ANNOTATION, /* strictAccept = */ !owner.isExternalClass()),
                /* sortByName = */ true);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 字段/方法注解的关系查询（内部辅助方法）

    /**
     * 获取此类声明的字段、方法或方法参数上的注解或元注解
     * （不包括此类的接口或超类声明的字段、方法或方法参数）
     *
     * @param relType {@link RelType#FIELD_ANNOTATIONS}、{@link RelType#METHOD_ANNOTATIONS} 或
     *                {@link RelType#METHOD_PARAMETER_ANNOTATIONS} 之一
     * @return 此类声明的字段或方法上的注解或元注解列表
     */
    private ClassInfoList getFieldOrMethodAnnotations(final RelType relType) {
        final boolean isField = relType == RelType.FIELD_ANNOTATIONS;
        if (!(isField ? owner.scanResult.ScanConfig.enableFieldInfo : owner.scanResult.ScanConfig.enableMethodInfo)
                || !owner.scanResult.ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enable" + (isField ? "Field" : "Method")
                    + "Info() and " + "#enableAnnotationInfo() before #scan()");
        }
        final ReachableAndDirectlyRelatedClasses fieldOrMethodAnnotations = this.filterClassInfo(relType,
                /* strictAccept = */ false, ClassType.ANNOTATION);
        final Set<ClassInfo> fieldOrMethodAnnotationsAndMetaAnnotations = new LinkedHashSet<>(
                fieldOrMethodAnnotations.reachableClasses);
        return new ClassInfoList(fieldOrMethodAnnotationsAndMetaAnnotations,
                fieldOrMethodAnnotations.directlyRelatedClasses, /* sortByName = */ true);
    }

    /**
     * 获取以此类作为字段、方法或方法参数注解的类
     *
     * @param relType {@link RelType#CLASSES_WITH_FIELD_ANNOTATION}、
     *                {@link RelType#CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION}、
     *                {@link RelType#CLASSES_WITH_METHOD_ANNOTATION}、
     *                {@link RelType#CLASSES_WITH_NONPRIVATE_METHOD_ANNOTATION}、
     *                {@link RelType#CLASSES_WITH_METHOD_PARAMETER_ANNOTATION} 或
     *                {@link RelType#CLASSES_WITH_NONPRIVATE_METHOD_PARAMETER_ANNOTATION} 之一
     * @return 声明了带有此注解或元注解的方法或字段的类的列表
     */
    private ClassInfoList getClassesWithFieldOrMethodAnnotation(final RelType relType) {
        final boolean isField = relType == RelType.CLASSES_WITH_FIELD_ANNOTATION
                || relType == RelType.CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION;
        if (!(isField ? owner.scanResult.ScanConfig.enableFieldInfo : owner.scanResult.ScanConfig.enableMethodInfo)
                || !owner.scanResult.ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enable" + (isField ? "Field" : "Method")
                    + "Info() and " + "#enableAnnotationInfo() before #scan()");
        }
        final ReachableAndDirectlyRelatedClasses classesWithDirectlyAnnotatedFieldsOrMethods = this
                .filterClassInfo(relType, /* strictAccept = */ !owner.isExternalClass());
        final ReachableAndDirectlyRelatedClasses annotationsWithThisMetaAnnotation = this.filterClassInfo(
                RelType.CLASSES_WITH_ANNOTATION, /* strictAccept = */ !owner.isExternalClass(), ClassType.ANNOTATION);
        if (annotationsWithThisMetaAnnotation.reachableClasses.isEmpty()) {
            // 此注解未元标注其他标注了方法的注解
            return new ClassInfoList(classesWithDirectlyAnnotatedFieldsOrMethods, /* sortByName = */ true);
        } else {
            // 取所有被此注解直接标注了字段或方法的类的并集，
            // 以及被此注解元标注了字段或方法的类
            final Set<ClassInfo> allClassesWithAnnotatedOrMetaAnnotatedFieldsOrMethods = new LinkedHashSet<>(
                    classesWithDirectlyAnnotatedFieldsOrMethods.reachableClasses);
            for (final ClassInfo metaAnnotatedAnnotation : annotationsWithThisMetaAnnotation.reachableClasses) {
                allClassesWithAnnotatedOrMetaAnnotatedFieldsOrMethods
                        .addAll(metaAnnotatedAnnotation.hierarchy().filterClassInfo(relType,
                                /* strictAccept = */ !metaAnnotatedAnnotation.isExternalClass()).reachableClasses);
            }
            return new ClassInfoList(allClassesWithAnnotatedOrMetaAnnotatedFieldsOrMethods,
                    classesWithDirectlyAnnotatedFieldsOrMethods.directlyRelatedClasses, /* sortByName = */ true);
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 字段注解

    /**
     * 获取所有字段注解
     *
     * @return 此类字段上的所有注解列表，如果没有则返回空列表
     *         注意：这些注解不包含具体的注解参数——调用 {@link FieldInfo#getAnnotationInfo()}
     *         获取具体字段注解实例的详细信息
     */
    public ClassInfoList getFieldAnnotations() {
        return getFieldOrMethodAnnotations(RelType.FIELD_ANNOTATIONS);
    }

    /**
     * 获取以此类作为字段注解或元注解的类
     *
     * @return 具有带有此注解或元注解的字段的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithFieldAnnotation() {
        // 获取所有具有被此注解标注或元标注的字段的类
        final Set<ClassInfo> classesWithMethodAnnotation = new HashSet<>(
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_FIELD_ANNOTATION));
        // 添加所有具有非私有的被此注解标注或元标注的字段的类的子类
        // （非私有字段会被继承）
        for (final ClassInfo classWithNonprivateMethodAnnotationOrMetaAnnotation :
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION)) {
            classesWithMethodAnnotation.addAll(classWithNonprivateMethodAnnotationOrMetaAnnotation.hierarchy().getSubclasses());
        }
        return new ClassInfoList(classesWithMethodAnnotation,
                new HashSet<>(getClassesWithFieldAnnotationDirectOnly()), /* sortByName = */ true);
    }

    /**
     * 获取以此类作为直接字段注解的类
     *
     * @return 声明了被请求的方法注解直接标注（即非元注解标注）的字段的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithFieldAnnotationDirectOnly() {
        return new ClassInfoList(
                this.filterClassInfo(RelType.CLASSES_WITH_FIELD_ANNOTATION, /* strictAccept = */ !owner.isExternalClass()),
                /* sortByName = */ true);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 方法注解

    /**
     * 获取所有方法注解
     *
     * @return 此类声明的方法上的所有注解或元注解列表（不包括此类的接口或超类声明的方法），
     *         作为 {@link ClassInfo} 对象列表，如果没有则返回空列表
     *         注意：这些注解不包含具体的注解参数——调用 {@link MethodInfo#getAnnotationInfo()}
     *         获取具体方法注解实例的详细信息
     */
    public ClassInfoList getMethodAnnotations() {
        return getFieldOrMethodAnnotations(RelType.METHOD_ANNOTATIONS);
    }

    /**
     * 获取所有方法参数注解
     *
     * @return 此类声明的方法上的所有注解或元注解列表（不包括此类的接口或超类声明的方法），
     *         作为 {@link ClassInfo} 对象列表，如果没有则返回空列表
     *         注意：这些注解不包含具体的注解参数——调用 {@link MethodInfo#getAnnotationInfo()}
     *         获取具体方法注解实例的详细信息
     */
    public ClassInfoList getMethodParameterAnnotations() {
        return getFieldOrMethodAnnotations(RelType.METHOD_PARAMETER_ANNOTATIONS);
    }

    /**
     * 获取以此类作为方法注解的所有类及其子类（如果方法是非私有的）
     *
     * @return 声明了带有此注解或元注解的方法的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodAnnotation() {
        // 获取所有具有被此注解标注或元标注的方法的类
        final Set<ClassInfo> classesWithMethodAnnotation = new HashSet<>(
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_METHOD_ANNOTATION));
        // 添加所有具有非私有的被此注解标注或元标注的方法的类的子类
        // （非私有方法会被继承）
        for (final ClassInfo classWithNonprivateMethodAnnotationOrMetaAnnotation :
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_NONPRIVATE_METHOD_ANNOTATION)) {
            classesWithMethodAnnotation.addAll(classWithNonprivateMethodAnnotationOrMetaAnnotation.hierarchy().getSubclasses());
        }
        return new ClassInfoList(classesWithMethodAnnotation,
                new HashSet<>(getClassesWithMethodAnnotationDirectOnly()), /* sortByName = */ true);
    }

    /**
     * 获取以此类作为方法参数注解的所有类及其子类（如果方法是非私有的）
     *
     * @return 声明了带有被此注解或元注解标注的参数的方法的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodParameterAnnotation() {
        // 获取所有具有被此注解标注或元标注的方法的类
        final Set<ClassInfo> classesWithMethodParameterAnnotation = new HashSet<>(
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_METHOD_PARAMETER_ANNOTATION));
        // 添加所有具有非私有的被此注解标注或元标注的方法的类的子类
        // （非私有方法会被继承）
        for (final ClassInfo classWithNonprivateMethodParameterAnnotationOrMetaAnnotation :
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_NONPRIVATE_METHOD_PARAMETER_ANNOTATION)) {
            classesWithMethodParameterAnnotation
                    .addAll(classWithNonprivateMethodParameterAnnotationOrMetaAnnotation.hierarchy().getSubclasses());
        }
        return new ClassInfoList(classesWithMethodParameterAnnotation,
                new HashSet<>(getClassesWithMethodParameterAnnotationDirectOnly()), /* sortByName = */ true);
    }

    /**
     * 获取以此类作为直接方法注解的类
     *
     * @return 声明了被请求的方法注解直接标注（即非元注解标注）的方法的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodAnnotationDirectOnly() {
        return new ClassInfoList(
                this.filterClassInfo(RelType.CLASSES_WITH_METHOD_ANNOTATION, /* strictAccept = */ !owner.isExternalClass()),
                /* sortByName = */ true);
    }

    /**
     * 获取以此类作为直接方法参数注解的类
     *
     * @return 声明了被请求的方法注解直接标注（即非元注解标注）的方法参数的类的列表，如果没有则返回空列表
     */
    ClassInfoList getClassesWithMethodParameterAnnotationDirectOnly() {
        return new ClassInfoList(this.filterClassInfo(RelType.CLASSES_WITH_METHOD_PARAMETER_ANNOTATION,
                /* strictAccept = */ !owner.isExternalClass()), /* sortByName = */ true);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 可重复注解处理

    /**
     * 处理 {@link java.lang.annotation.Repeatable} 注解
     *
     * @param allRepeatableAnnotationNames 所有可重复注解的名称集合
     */
    public void handleRepeatableAnnotations(final Set<String> allRepeatableAnnotationNames) {
        if (owner.annotationInfo != null) {
            owner.annotationInfo.handleRepeatableAnnotations(allRepeatableAnnotationNames, owner,
                    RelType.CLASS_ANNOTATIONS, RelType.CLASSES_WITH_ANNOTATION, null);
        }
        if (owner.fieldInfo != null) {
            for (final FieldInfo fi : owner.fieldInfo) {
                fi.handleRepeatableAnnotations(allRepeatableAnnotationNames);
            }
        }
        if (owner.methodInfo != null) {
            for (final MethodInfo mi : owner.methodInfo) {
                mi.handleRepeatableAnnotations(allRepeatableAnnotationNames);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 字段/方法注解的交叉链接（来自 addFieldInfo / addMethodInfo）

    /**
     * 添加字段或方法注解的交叉链接
     *
     * @param annotationInfoList 注解信息列表
     * @param isField            是否为字段
     * @param modifiers          字段或方法修饰符
     * @param classNameToClassInfo 从类名到 ClassInfo 的映射
     */
    void addFieldOrMethodAnnotationInfo(final AnnotationInfoList annotationInfoList, final boolean isField,
                                        final int modifiers, final Map<String, ClassInfo> classNameToClassInfo) {
        if (annotationInfoList != null) {
            for (final AnnotationInfo fieldAnnotationInfo : annotationInfoList) {
                final ClassInfo annotationClassInfo = ClassInfo.getOrCreateClassInfo(fieldAnnotationInfo.getName(),
                        classNameToClassInfo);
                annotationClassInfo.setModifiers(ClassInfo.ANNOTATION_CLASS_MODIFIER);
                // 将此类标记为具有带有此注解的字段或方法
                this.addRelation(isField ? RelType.FIELD_ANNOTATIONS : RelType.METHOD_ANNOTATIONS,
                        annotationClassInfo);
                annotationClassInfo.hierarchy().addRelation(
                        isField ? RelType.CLASSES_WITH_FIELD_ANNOTATION : RelType.CLASSES_WITH_METHOD_ANNOTATION,
                        owner);
                // 对于非私有方法/字段，也添加到非私有（可继承）映射中
                if (!java.lang.reflect.Modifier.isPrivate(modifiers)) {
                    annotationClassInfo.hierarchy().addRelation(isField ? RelType.CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION
                            : RelType.CLASSES_WITH_NONPRIVATE_METHOD_ANNOTATION, owner);
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 内部类型

    /** 类之间的关系类型 */
    public enum RelType {

        // 类关系：

        /**
         * 此类的超类，如果这是普通类的话
         *
         * <p>
         * （应该只包含一个条目，如果超类是 java.lang.Object 或未知则为 null）
         */
        SUPERCLASSES,

        /** 此类的子类，如果这是普通类的话 */
        SUBCLASSES,

        /** 表示一个内部类包含在此类中 */
        CONTAINS_INNER_CLASS,

        /** 表示一个外部类包含此类（应该只有零个或一个条目） */
        CONTAINED_WITHIN_OUTER_CLASS,

        // 接口关系：

        /**
         * 此类实现的接口（如果这是普通类的话），或超接口（如果这是接口的话）
         *
         * <p>
         * （也可能包含注解，因为注解是接口，所以可以实现一个注解）
         */
        IMPLEMENTED_INTERFACES,

        /**
         * 实现此接口的类（包括子接口），如果这是接口的话
         */
        CLASSES_IMPLEMENTING,

        // 类注解关系：

        /**
         * 此类上的注解（如果这是普通类的话），或此注解上的元注解（如果这是注解的话）
         */
        CLASS_ANNOTATIONS,

        /** 被此注解标注的类，如果这是注解的话 */
        CLASSES_WITH_ANNOTATION,

        // 方法注解关系：

        /** 此类一个或多个方法上的注解 */
        METHOD_ANNOTATIONS,

        /**
         * 具有一个或多个被此注解标注的方法的类，如果这是注解的话
         */
        CLASSES_WITH_METHOD_ANNOTATION,

        /**
         * 具有一个或多个被此注解标注的非私有（可继承）方法的类，如果这是注解的话
         */
        CLASSES_WITH_NONPRIVATE_METHOD_ANNOTATION,

        /** 此类一个或多个方法参数上的注解 */
        METHOD_PARAMETER_ANNOTATIONS,

        /**
         * 具有一个或多个方法，其一个或多个参数被此注解标注的类，如果这是注解的话
         */
        CLASSES_WITH_METHOD_PARAMETER_ANNOTATION,

        /**
         * 具有一个或多个非私有（可继承）方法，其一个或多个参数被此注解标注的类，
         * 如果这是注解的话
         */
        CLASSES_WITH_NONPRIVATE_METHOD_PARAMETER_ANNOTATION,

        // 字段注解关系：

        /** 此类一个或多个字段上的注解 */
        FIELD_ANNOTATIONS,

        /**
         * 具有一个或多个被此注解标注的字段的类，如果这是注解的话
         */
        CLASSES_WITH_FIELD_ANNOTATION,

        /**
         * 具有一个或多个被此注解标注的非私有（可继承）字段的类，如果这是注解的话
         */
        CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION,
    }

    /** 对于给定关系类型，通过有向路径间接可达的类集合，以及直接关联（仅一步关系距离）的类集合 */
    public static class ReachableAndDirectlyRelatedClasses {

        /** 可达的类 */
        public final Set<ClassInfo> reachableClasses;

        /** 直接关联的类 */
        public final Set<ClassInfo> directlyRelatedClasses;

        /**
         * 构造函数
         *
         * @param reachableClasses     可达的类
         * @param directlyRelatedClasses 直接关联的类
         */
        public ReachableAndDirectlyRelatedClasses(final Set<ClassInfo> reachableClasses,
                                                   final Set<ClassInfo> directlyRelatedClasses) {
            this.reachableClasses = reachableClasses;
            this.directlyRelatedClasses = directlyRelatedClasses;
        }
    }
}
