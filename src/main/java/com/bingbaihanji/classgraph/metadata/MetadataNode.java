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

import com.bingbaihanji.classgraph.util.LogNode;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 可从 {@link ScanResult} 中访问且与一个 {@link ClassInfo} 对象相关联的对象的超类
 */
abstract class MetadataNode {
    /** 扫描结果 */
    transient protected ScanResult scanResult;
    /** 类引用，在类被加载后设置 */
    protected transient Class<?> classRef;
    /** 关联的 {@link ClassInfo} 对象 */
    private transient ClassInfo classInfo;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 在扫描完成后，在信息对象中设置 {@link ScanResult} 的反向引用
     *
     * @param scanResult
     *            扫描结果
     */
    void setScanResult(final ScanResult scanResult) {
        this.scanResult = scanResult;
    }

    /**
     * 获取此对象引用的任何类对应的 {@link ClassInfo} 对象
     *
     * @param log
     *            日志
     * @return 引用的类信息
     */
    final Set<ClassInfo> findReferencedClassInfo(final LogNode log) {
        final Set<ClassInfo> refdClassInfo = new LinkedHashSet<>();
        if (scanResult != null) {
            findReferencedClassInfo(scanResult.classNameToClassInfo, refdClassInfo, log);
        }
        return refdClassInfo;
    }

    /**
     * 获取此对象引用的任何类对应的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            引用的类信息
     * @param log
     *            日志
     */
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        final ClassInfo ci = getClassInfo();
        if (ci != null) {
            refdClassInfo.add(ci);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 类的名称(由 {@link #getClassInfo()} 使用，用于获取该类的 {@link ClassInfo} 对象)
     *
     * @return 类名
     */
    protected abstract String getClassName();

    /**
     * 获取被引用类的 {@link ClassInfo} 对象，如果在扫描期间未遇到被引用的类(即在扫描期间未为该类创建
     * ClassInfo 对象)，则返回 null注意，即使此方法返回 null，{@link #loadClass()} 也可能能够通过名称加载被引用的类
     *
     * @return 被引用类的 {@link ClassInfo} 对象
     */
    ClassInfo getClassInfo() {
        if (classInfo == null) {
            if (scanResult == null) {
                return null;
            }
            final String className = getClassName();
            if (className == null) {
                throw new IllegalArgumentException("Class name is not set");
            }
            classInfo = scanResult.getClassInfo(className);
        }
        return classInfo;
    }

    /**
     * 通过调用 getClassInfo().getName() 获取类名，或者作为回退，通过调用 getClassName() 获取
     *
     * @return 类名
     */
    private String getClassInfoNameOrClassName() {
        String className;
        ClassInfo ci = null;
        try {
            ci = getClassInfo();
        } catch (final IllegalArgumentException e) {
            // 忽略对数组 classInfo 的错误访问
        }
        if (ci == null) {
            ci = classInfo;
        }
        if (ci != null) {
            // 从 getClassInfo().getName() 获取类名
            className = ci.getName();
        } else {
            // 从 getClassName() 获取类名
            className = getClassName();
        }
        if (className == null) {
            throw new IllegalArgumentException("Class name is not set");
        }
        return className;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 加载由 {@link #getClassInfo()} 返回名称的类，如果返回 null，则加载由 {@link #getClassName()} 返回名称的类
     * 返回该类的 {@code Class<?>} 引用，并将其转换为请求的超类或接口类型
     *
     * @param <T>
     *            超类或接口类型
     * @param superclassOrInterfaceType
     *            要将结果类引用转换到的类型
     * @param ignoreExceptions
     *            如果为 true，则忽略类加载异常并在失败时返回 null
     * @return 被引用类的 {@code Class<?>} 引用，如果类无法加载(或转换失败)且 ignoreExceptions 为 true，则返回 null
     * @throws IllegalArgumentException
     *             如果类无法加载或转换，且 ignoreExceptions 为 false
     */
    <T> Class<T> loadClass(final Class<T> superclassOrInterfaceType, final boolean ignoreExceptions) {
        synchronized (this) {
            // 如果类尚未加载，尝试加载类
            if (classRef == null) {
                final String className = getClassInfoNameOrClassName();
                try {
                    classRef = scanResult != null
                            ? scanResult.loadClass(className, superclassOrInterfaceType, ignoreExceptions)
                            // 回退，如果 scanResult 未设置
                            : Class.forName(className);
                    if (classRef == null && !ignoreExceptions) {
                        throw new IllegalArgumentException("Could not load class " + className);
                    }
                } catch (final Throwable t) {
                    if (!ignoreExceptions) {
                        throw new IllegalArgumentException("Could not load class " + className, t);
                    }
                }
            }
            @SuppressWarnings("unchecked") final Class<T> classT = (Class<T>) classRef;
            return classT;
        }
    }

    /**
     * 加载由 {@link #getClassInfo()} 返回名称的类，如果返回 null，则加载由 {@link #getClassName()} 返回名称的类
     * 返回该类的 {@code Class<?>} 引用，并将其转换为请求的超类或接口类型
     *
     * @param <T>
     *            超类或接口类型
     * @param superclassOrInterfaceType
     *            要将结果类引用转换到的类型
     * @return 被引用类的 {@code Class<?>} 引用，如果类无法加载(或转换失败)且 ignoreExceptions 为 true，则返回 null
     * @throws IllegalArgumentException
     *             如果类无法加载或转换，且 ignoreExceptions 为 false
     */
    <T> Class<T> loadClass(final Class<T> superclassOrInterfaceType) {
        return loadClass(superclassOrInterfaceType, /* ignoreExceptions = */ false);
    }

    /**
     * 加载由 {@link #getClassInfo()} 返回名称的类，如果返回 null，则加载由 {@link #getClassName()} 返回名称的类
     * 返回该类的 {@code Class<?>} 引用
     *
     * @param ignoreExceptions
     *            如果为 true，则忽略类加载异常并在失败时返回 null
     * @return 被引用类的 {@code Class<?>} 引用，如果类无法加载且 ignoreExceptions 为 true，则返回 null
     * @throws IllegalArgumentException
     *             如果类无法加载且 ignoreExceptions 为 false
     */
    Class<?> loadClass(final boolean ignoreExceptions) {
        if (classRef == null) {
            final String className = getClassInfoNameOrClassName();
            if (scanResult != null) {
                classRef = scanResult.loadClass(className, ignoreExceptions);
            } else {
                // 回退，如果 scanResult 未设置
                try {
                    classRef = Class.forName(className);
                } catch (final Throwable t) {
                    if (!ignoreExceptions) {
                        throw new IllegalArgumentException("Could not load class " + className, t);
                    }
                }
            }
        }
        return classRef;
    }

    /**
     * 加载由 {@link #getClassInfo()} 返回名称的类，如果返回 null，则加载由 {@link #getClassName()} 返回名称的类
     * 返回该类的 {@code Class<?>} 引用
     *
     * @return 被引用类的 {@code Class<?>} 引用
     * @throws IllegalArgumentException
     *             如果类无法加载
     */
    Class<?> loadClass() {
        return loadClass(/* ignoreExceptions = */ false);
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
    protected abstract void toString(final boolean useSimpleNames, StringBuilder buf);

    /**
     * 渲染为字符串，如果 useSimpleNames 为 true，则使用类的简单名称
     *
     * @param useSimpleNames
     *            如果为 true，则仅使用每个类的简单名称
     * @return 字符串表示
     */
    String toString(final boolean useSimpleNames) {
        final StringBuilder buf = new StringBuilder();
        toString(useSimpleNames, buf);
        return buf.toString();
    }

    /**
     * 渲染为字符串，仅使用类的<a href=
     * "https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/lang/Class.html#getSimpleName()">简单名称</a>
     *
     * @return 字符串表示，使用类的简单名称
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