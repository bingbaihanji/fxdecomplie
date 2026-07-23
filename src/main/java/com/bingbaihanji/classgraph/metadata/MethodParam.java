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

import com.bingbaihanji.classgraph.util.Assert;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * 方法参数的信息
 *
 * @author lukehutch
 */
public class MethodParam {
    /** 注解信息 */
    final AnnotationInfo[] annotationInfo;
    /** 包含此参数的方法 */
    private final MethodInfo methodInfo;
    /** 修饰符 */
    private final int modifiers;

    /** 类型描述符 */
    private final TypeSignature typeDescriptor;

    /** 类型签名 */
    private final TypeSignature typeSignature;

    /** 参数名称 */
    private final String name;

    /** 扫描结果 */
    private ScanResult scanResult;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param methodInfo
     *            定义方法的 {@link MethodInfo}
     * @param annotationInfo
     *            此方法参数上注解的 {@link AnnotationInfo}
     * @param modifiers
     *            方法参数修饰符
     * @param typeDescriptor
     *            方法参数类型描述符
     * @param typeSignature
     *            方法参数类型签名
     * @param name
     *            方法参数名称
     */
    MethodParam(final MethodInfo methodInfo, final AnnotationInfo[] annotationInfo, final int modifiers,
                        final TypeSignature typeDescriptor, final TypeSignature typeSignature, final String name) {
        this.methodInfo = methodInfo;
        this.name = name;
        this.modifiers = modifiers;
        this.typeDescriptor = typeDescriptor;
        this.typeSignature = typeSignature;
        this.annotationInfo = annotationInfo;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 将修饰符转换为字符串表示，例如 "public static final"
     *
     * @param modifiers
     *            字段或方法修饰符
     * @param buf
     *            用于写入结果的缓冲区
     */
    static void modifiersToString(final int modifiers, final StringBuilder buf) {
        if ((modifiers & Modifier.FINAL) != 0) {
            buf.append("final ");
        }
        if ((modifiers & 0x1000) != 0) {
            buf.append("synthetic ");
        }
        if ((modifiers & 0x8000) != 0) {
            buf.append("mandated ");
        }
    }

    /**
     * 获取定义方法的 {@link MethodInfo}
     *
     * @return 定义方法的 {@link MethodInfo}
     */
    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    /**
     * 方法参数名称对于未命名的参数(例如合成参数)，或者如果编译 JDK 版本低于 8，或者如果使用 JDK 8+
     * 编译但未使用命令行开关 `-parameters`，则可能为 null
     *
     * @return 方法参数名称
     */
    public String getName() {
        return name;
    }

    /**
     * 方法参数修饰符如果未设置修饰符位，或者如果编译 JDK 版本低于 8，或者如果使用 JDK 8+
     * 编译但未使用命令行开关 `-parameters`，则可能为零
     *
     * @return 方法参数修饰符
     */
    public int getModifiers() {
        return modifiers;
    }

    /**
     * 以字符串形式获取方法参数修饰符，例如 "final"要获取修饰符位，请调用 {@link #getModifiers()}
     *
     * @return 方法参数的修饰符(字符串形式)
     */
    public String getModifiersStr() {
        final StringBuilder buf = new StringBuilder();
        modifiersToString(modifiers, buf);
        return buf.toString();
    }

    /**
     * 方法参数类型签名，可能包含泛型类型信息(如果此参数没有类型签名信息可用，则为 null)
     *
     * @return 方法类型签名(如果可用)，否则为 null
     */
    public TypeSignature getTypeSignature() {
        return typeSignature;
    }

    /**
     * 方法参数类型描述符
     *
     * @return 方法类型描述符
     */
    public TypeSignature getTypeDescriptor() {
        return typeDescriptor;
    }

    /**
     * 方法参数类型签名，如果不可用，则返回方法类型描述符
     *
     * @return 方法类型签名(如果存在)，否则返回方法类型描述符
     */
    public TypeSignature getTypeSignatureOrTypeDescriptor() {
        return typeSignature != null ? typeSignature : typeDescriptor;
    }

    /**
     * 方法参数注解信息(如果没有注解则为 null)
     *
     * @return 此方法参数上注解的 {@link AnnotationInfo}
     */
    public AnnotationInfoList getAnnotationInfo() {
        if (!scanResult.ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("请在调用 #scan() 之前调用 ClassGraph#enableAnnotationInfo()");
        }
        if (annotationInfo == null || annotationInfo.length == 0) {
            return AnnotationInfoList.EMPTY_LIST;
        } else {
            final AnnotationInfoList annotationInfoList = new AnnotationInfoList(annotationInfo.length);
            Collections.addAll(annotationInfoList, annotationInfo);
            return AnnotationInfoList.getIndirectAnnotations(annotationInfoList, /* annotatedClass = */ null);
        }
    }

    /**
     * 获取此方法上的非 {@link Repeatable} 注解，如果方法参数没有该注解则返回 null
     * (对于 {@link Repeatable} 注解，请使用 {@link #getAnnotationInfoRepeatable(Class)})
     *
     * @param annotation
     *            注解类
     * @return 表示此方法参数上该注解的 {@link AnnotationInfo} 对象，如果方法参数没有该注解则返回 null
     */
    public AnnotationInfo getAnnotationInfo(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getAnnotationInfo(annotation.getName());
    }

    /**
     * 获取此方法上的命名非 {@link Repeatable} 注解，如果方法参数没有该命名注解则返回 null
     * (对于 {@link Repeatable} 注解，请使用 {@link #getAnnotationInfoRepeatable(String)})
     *
     * @param annotationName
     *            注解名称
     * @return 表示此方法参数上该命名注解的 {@link AnnotationInfo} 对象，如果方法参数没有该命名注解则返回 null
     */
    public AnnotationInfo getAnnotationInfo(final String annotationName) {
        return getAnnotationInfo().get(annotationName);
    }

    /**
     * 获取此方法上的 {@link Repeatable} 注解，如果方法参数没有该注解则返回空列表
     *
     * @param annotation
     *            注解类
     * @return 包含此方法参数上该注解所有实例的 {@link AnnotationInfoList}，如果方法参数没有该注解则返回空列表
     */
    public AnnotationInfoList getAnnotationInfoRepeatable(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getAnnotationInfoRepeatable(annotation.getName());
    }

    /**
     * 获取此方法上的命名 {@link Repeatable} 注解，如果方法参数没有该命名注解则返回空列表
     *
     * @param annotationName
     *            注解名称
     * @return 包含此方法参数上该命名注解所有实例的 {@link AnnotationInfoList}，如果方法参数没有该命名注解则返回空列表
     */
    public AnnotationInfoList getAnnotationInfoRepeatable(final String annotationName) {
        return getAnnotationInfo().getRepeatable(annotationName);
    }

    /**
     * 检查此方法参数是否有该注解
     *
     * @param annotation
     *            注解类
     * @return 如果此方法参数有该注解则返回 true
     */
    public boolean hasAnnotation(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return hasAnnotation(annotation.getName());
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 检查此方法参数是否有该命名注解
     *
     * @param annotationName
     *            注解名称
     * @return 如果此方法参数有该命名注解则返回 true
     */
    public boolean hasAnnotation(final String annotationName) {
        return getAnnotationInfo().containsName(annotationName);
    }

    /**
     * 设置扫描结果
     *
     * @param scanResult
     *            新的扫描结果
     */
    protected void setScanResult(final ScanResult scanResult) {
        this.scanResult = scanResult;
        if (this.annotationInfo != null) {
            for (final AnnotationInfo ai : annotationInfo) {
                ai.setScanResult(scanResult);
            }
        }
        if (this.typeDescriptor != null) {
            this.typeDescriptor.setScanResult(scanResult);
        }
        if (this.typeSignature != null) {
            this.typeSignature.setScanResult(scanResult);
        }
    }

    /**
     * 如果此方法参数是 final 的则返回 true
     *
     * @return 如果此方法参数是 final 的则返回 true
     */
    public boolean isFinal() {
        return Modifier.isFinal(modifiers);
    }

    /**
     * 如果此方法参数是合成的则返回 true
     *
     * @return 如果此方法参数是合成的则返回 true
     */
    public boolean isSynthetic() {
        return (modifiers & 0x1000) != 0;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 如果此方法参数是强制的则返回 true
     *
     * @return 如果此方法参数是强制的则返回 true
     */
    public boolean isMandated() {
        return (modifiers & 0x8000) != 0;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof MethodParam)) {
            return false;
        }
        final MethodParam other = (MethodParam) obj;
        return Objects.equals(methodInfo, other.methodInfo)
                && Objects.deepEquals(annotationInfo, other.annotationInfo) && modifiers == other.modifiers
                && Objects.equals(typeDescriptor, other.typeDescriptor)
                && Objects.equals(typeSignature, other.typeSignature) && Objects.equals(name, other.name);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(methodInfo, Arrays.hashCode(annotationInfo), typeDescriptor, typeSignature, name)
                + modifiers;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 渲染为字符串
     *
     * @param useSimpleNames
     *            如果为 true，则仅使用每个类的简单名称
     * @param buf
     *            缓冲区
     */
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        if (annotationInfo != null) {
            for (final AnnotationInfo anAnnotationInfo : annotationInfo) {
                anAnnotationInfo.toString(useSimpleNames, buf);
                buf.append(' ');
            }
        }

        modifiersToString(modifiers, buf);

        getTypeSignatureOrTypeDescriptor().toString(useSimpleNames, buf);

        buf.append(' ');
        buf.append(name == null ? "_unnamed_param" : name);
    }

    /**
     * 使用类的简单名称渲染为字符串
     *
     * @return 字符串表示
     */
    public String toStringWithSimpleNames() {
        final StringBuilder buf = new StringBuilder();
        toString(/* useSimpleNames = */ true, buf);
        return buf.toString();
    }

    /**
     * 渲染为字符串
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        toString(/* useSimpleNames = */ false, buf);
        return buf.toString();
    }
}
