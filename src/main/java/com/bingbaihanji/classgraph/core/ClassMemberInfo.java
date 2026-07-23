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

import com.bingbaihanji.classgraph.utils.Assert;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Modifier;

/**
 * 保存扫描过程中遇到的类成员的元数据所有值均直接从类的 class 文件中提取
 */
public abstract class ClassMemberInfo extends ScanResultObject implements HasName {
    /** 定义该成员的类名 */
    protected String declaringClassName;

    /** 类成员的名称 */
    protected String name;

    /** 类成员的修饰符 */
    protected int modifiers;

    /**
     * JVM 内部的类型描述符(不含类型参数，但包含合成参数和强制参数的
     * 类成员参数类型)
     */
    protected String typeDescriptorStr;

    /**
     * 类型签名(如果存在且可用，可能包含类型参数信息)类成员
     * 参数类型是未对齐的
     */
    protected String typeSignatureStr;

    /** 类成员上的注解(如果有) */
    protected AnnotationInfoList annotationInfo;

    /** 注解信息，在加载后缓存 */
    private AnnotationInfoList annotationInfoRef;

    /** 用于反序列化的默认构造器 */
    ClassMemberInfo() {
        super();
    }

    /**
     * 构造器
     *
     * @param definingClassName
     *            定义该成员的类
     * @param memberName
     *            类成员的名称
     * @param modifiers
     *            类成员的修饰符
     * @param typeDescriptorStr
     *            类成员的类型描述符
     * @param typeSignatureStr
     *            类成员的类型签名
     * @param annotationInfo
     *            类成员上所有注解的 {@link AnnotationInfo}
     */
    public ClassMemberInfo(final String definingClassName, final String memberName, final int modifiers,
                           final String typeDescriptorStr, final String typeSignatureStr,
                           final AnnotationInfoList annotationInfo) {
        super();
        this.declaringClassName = definingClassName;
        this.name = memberName;
        this.modifiers = modifiers;
        this.typeDescriptorStr = typeDescriptorStr;
        this.typeSignatureStr = typeSignatureStr;
        this.annotationInfo = annotationInfo == null || annotationInfo.isEmpty() ? null : annotationInfo;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取声明此类成员的类的 {@link ClassInfo} 对象
     *
     * @return 声明类的 {@link ClassInfo} 对象
     *
     * @see #getClassName()
     */
    @Override
    public ClassInfo getClassInfo() {
        return super.getClassInfo();
    }

    /**
     * 获取声明此成员的类的名称
     *
     * @return 声明类的名称
     *
     * @see #getClassInfo()
     */
    @Override
    public String getClassName() {
        return declaringClassName;
    }

    /**
     * 获取类成员的名称
     *
     * @return 类成员的名称
     */
    @Override
    public String getName() {
        return name;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 返回类成员的修饰符位掩码
     *
     * @return 类成员的修饰符位掩码
     */
    public int getModifiers() {
        return modifiers;
    }

    /**
     * 以字符串形式获取修饰符，例如 "public static final"如需获取修饰符位掩码，请调用 getModifiers()
     *
     * @return 修饰符的字符串表示
     */
    public abstract String getModifiersStr();

    /**
     * 如果此类成员是 public 的，则返回 true
     *
     * @return 如果类成员是 public 的，则返回 true
     */
    public boolean isPublic() {
        return Modifier.isPublic(modifiers);
    }

    /**
     * 如果此类成员是 private 的，则返回 true
     *
     * @return 如果类成员是 private 的，则返回 true
     */
    public boolean isPrivate() {
        return Modifier.isPrivate(modifiers);
    }

    /**
     * 如果此类成员是 protected 的，则返回 true
     *
     * @return 如果类成员是 protected 的，则返回 true
     */
    public boolean isProtected() {
        return Modifier.isProtected(modifiers);
    }

    /**
     * 如果此类成员是 static 的，则返回 true
     *
     * @return 如果类成员是 static 的，则返回 true
     */
    public boolean isStatic() {
        return Modifier.isStatic(modifiers);
    }

    /**
     * 如果此类成员是 final 的，则返回 true
     *
     * @return 如果类成员是 final 的，则返回 true
     */
    public boolean isFinal() {
        return Modifier.isFinal(modifiers);
    }

    /**
     * 如果此类成员是合成的，则返回 true
     *
     * @return 如果类成员是合成的，则返回 true
     */
    public boolean isSynthetic() {
        return (modifiers & 0x1000) != 0;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 返回类成员解析后的类型描述符，不包含类型参数如果需要泛型类型参数，请改用 {@link #getTypeSignature()}
     *
     * @return 类成员解析后的类型描述符字符串
     */
    public abstract HierarchicalType getTypeDescriptor();

    /**
     * 返回类成员的类型描述符字符串，不包含类型参数如果需要泛型类型参数，请改用 {@link #getTypeSignatureStr()}
     *
     * @return 类成员的类型描述符字符串
     */
    public String getTypeDescriptorStr() {
        return typeDescriptorStr;
    }

    /**
     * 返回类成员解析后的类型签名，可能包含类型参数如果返回 null，表示此类成员没有可用的类型签名信息，
     * 请改用 {@link #getTypeDescriptor()}
     *
     * @return 类成员解析后的类型签名，如果不可用则返回 null
     * @throws IllegalArgumentException
     *             如果类成员的类型签名无法解析(仅当 class 文件损坏或编译器 bug 导致向 class 文件
     *             写入了无效的类型签名时才会抛出此异常)
     */
    public abstract HierarchicalType getTypeSignature();

    /**
     * 返回类成员的类型签名字符串，可能包含类型参数如果返回 null，表示此类成员没有可用的类型签名信息，
     * 请改用 {@link #getTypeDescriptorStr()}
     *
     * @return 类成员的类型签名字符串，如果不可用则返回 null
     */
    public String getTypeSignatureStr() {
        return typeSignatureStr;
    }

    /**
     * 返回类成员的类型签名，可能包含类型参数如果类型签名为 null(表示此类成员没有可用的类型签名信息)，
     * 则返回类型描述符
     *
     * @return 类成员解析后的类型签名；如果不可用，则返回类成员解析后的类型描述符
     */
    public abstract HierarchicalType getTypeSignatureOrTypeDescriptor();

    /**
     * 返回类成员的类型签名字符串，可能包含类型参数如果类型签名字符串为 null(表示此类成员没有可用的类型签名信息)，
     * 则返回类型描述符字符串
     *
     * @return 类成员的类型签名字符串；如果不可用，则返回类成员的类型描述符字符串
     */
    public String getTypeSignatureOrTypeDescriptorStr() {
        if (typeSignatureStr != null) {
            return typeSignatureStr;
        }
        return typeDescriptorStr;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此类成员上的注解列表及其注解参数值，封装在 {@link AnnotationInfo} 对象中
     *
     * @return 此类成员上的注解列表及其注解参数值，封装在 {@link AnnotationInfo} 对象中；如果没有则返回空列表
     */
    public AnnotationInfoList getAnnotationInfo() {
        synchronized (this) {
            if (annotationInfoRef != null) {
                return annotationInfoRef;
            }

            if (!scanResult.scanSpec.enableAnnotationInfo) {
                throw new IllegalArgumentException("请在调用 scan() 之前先调用 ClassGraph#enableAnnotationInfo()");
            }

            annotationInfoRef = annotationInfo == null ? AnnotationInfoList.EMPTY_LIST
                    : AnnotationInfoList.getIndirectAnnotations(annotationInfo, /* annotatedClass = */ null);
            return annotationInfoRef;
        }
    }

    /**
     * 获取此类成员上指定的非 {@link Repeatable} 注解，如果类成员没有该注解则返回 null
     * (对于 {@link Repeatable} 注解，请使用 {@link #getAnnotationInfoRepeatable(Class)})
     *
     * @param annotation
     *            注解类
     * @return 表示此类成员上该注解的 {@link AnnotationInfo} 对象，如果类成员没有该注解则返回 null
     */
    public AnnotationInfo getAnnotationInfo(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getAnnotationInfo(annotation.getName());
    }

    /**
     * 获取此类成员上指定名称的非 {@link Repeatable} 注解，如果类成员没有该名称的注解则返回 null
     * (对于 {@link Repeatable} 注解，请使用 {@link #getAnnotationInfoRepeatable(String)})
     *
     * @param annotationName
     *            注解名称
     * @return 表示此类成员上指定名称注解的 {@link AnnotationInfo} 对象，如果类成员没有该名称的注解则返回 null
     */
    public AnnotationInfo getAnnotationInfo(final String annotationName) {
        return getAnnotationInfo().get(annotationName);
    }

    /**
     * 获取此类成员上指定的 {@link Repeatable} 注解，如果类成员没有该注解则返回空列表
     *
     * @param annotation
     *            注解类
     * @return 此类成员上该注解所有实例的 {@link AnnotationInfoList}，如果类成员没有该注解则返回空列表
     */
    public AnnotationInfoList getAnnotationInfoRepeatable(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getAnnotationInfoRepeatable(annotation.getName());
    }

    /**
     * 获取此类成员上指定名称的 {@link Repeatable} 注解，如果类成员没有该名称的注解则返回空列表
     *
     * @param annotationName
     *            注解名称
     * @return 此类成员上指定名称注解所有实例的 {@link AnnotationInfoList}，如果类成员没有该名称的注解则返回空列表
     */
    public AnnotationInfoList getAnnotationInfoRepeatable(final String annotationName) {
        return getAnnotationInfo().getRepeatable(annotationName);
    }

    /**
     * 检查类成员是否具有给定的注解
     *
     * @param annotation
     *            注解类
     * @return 如果此类成员具有该注解，则返回 true
     */
    public boolean hasAnnotation(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return hasAnnotation(annotation.getName());
    }

    /**
     * 检查类成员是否具有给定名称的注解
     *
     * @param annotationName
     *            注解名称
     * @return 如果此类成员具有该名称的注解，则返回 true
     */
    public boolean hasAnnotation(final String annotationName) {
        return getAnnotationInfo().containsName(annotationName);
    }
}
