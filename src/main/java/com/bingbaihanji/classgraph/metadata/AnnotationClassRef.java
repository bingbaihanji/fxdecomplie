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

import com.bingbaihanji.classgraph.type.ParseException;

/**
 * 存储在注解参数值中找到的 {@code Class<?>} 类型描述符
 */
public class AnnotationClassRef extends MetadataNode {
    /** 类型描述符字符串 */
    private String typeDescriptorStr;

    /** 类型签名 */
    private transient TypeSignature typeSignature;

    /** 类名 */
    private transient String className;

    /**
     * 构造函数
     */
    AnnotationClassRef() {
        super();
    }

    /**
     * 构造函数
     *
     * @param typeDescriptorStr
     *            类型描述符字符串
     */
    AnnotationClassRef(final String typeDescriptorStr) {
        super();
        this.typeDescriptorStr = typeDescriptorStr;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取被引用类的名称
     *
     * @return 被引用类的名称
     */
    public String getName() {
        return getClassName();
    }

    /**
     * 获取类型签名
     *
     * @return {@code Class<?>} 引用的类型签名可能是 {@link ClassRef}、
     *         {@link BaseType} 或 {@link ArrayType}
     */
    private TypeSignature getTypeSignature() {
        if (typeSignature == null) {
            try {
                // ClassRef、BaseType 或 ArrayType 中不可能有待解析的类型变量，
                // 所以直接将 definingClassName 设为 null
                typeSignature = TypeSignature.parse(typeDescriptorStr, /* definingClassName = */ null);
                typeSignature.setScanResult(scanResult);
            } catch (final ParseException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return typeSignature;
    }

    /**
     * 加载被引用的类，返回被引用类的 {@code Class<?>} 引用
     *
     * @param ignoreExceptions
     *            如果为 true，则忽略异常，在类无法加载时返回 null
     * @return 被引用类的 {@code Class<?>} 引用
     * @throws IllegalArgumentException
     *             如果类无法加载且 ignoreExceptions 为 false
     */
    @Override
    public Class<?> loadClass(final boolean ignoreExceptions) {
        getTypeSignature();
        if (typeSignature instanceof BaseType) {
            return ((BaseType) typeSignature).getType();
        } else if (typeSignature instanceof ClassRef) {
            return typeSignature.loadClass(ignoreExceptions);
        } else if (typeSignature instanceof ArrayType) {
            return typeSignature.loadClass(ignoreExceptions);
        } else {
            throw new IllegalArgumentException("Got unexpected type " + typeSignature.getClass().getName()
                    + " for ref type signature: " + typeDescriptorStr);
        }
    }

    /**
     * 加载被引用的类，返回被引用类的 {@code Class<?>} 引用
     *
     * @return 被引用类的 {@code Class<?>} 引用
     * @throws IllegalArgumentException
     *             如果类无法加载
     */
    @Override
    public Class<?> loadClass() {
        return loadClass(/* ignoreExceptions = */ false);
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#getClassName()
     */
    @Override
    protected String getClassName() {
        if (className == null) {
            getTypeSignature();
            if (typeSignature instanceof BaseType) {
                className = ((BaseType) typeSignature).getTypeStr();
            } else if (typeSignature instanceof ClassRef) {
                className = ((ClassRef) typeSignature).getFullyQualifiedClassName();
            } else if (typeSignature instanceof ArrayType) {
                className = typeSignature.getClassName();
            } else {
                throw new IllegalArgumentException("Got unexpected type " + typeSignature.getClass().getName()
                        + " for ref type signature: " + typeDescriptorStr);
            }
        }
        return className;
    }

    /**
     * 获取类信息
     *
     * @return 被引用类的 {@link ClassInfo} 对象，如果被引用类在扫描期间未被遇到则返回 null
     *         (即在扫描期间没有为该类创建 ClassInfo 对象)
     *         注意：即使此方法返回 null，{@link #loadClass()} 也可能能够按名称加载被引用类
     */
    @Override
    public ClassInfo getClassInfo() {
        getTypeSignature();
        return typeSignature.getClassInfo();
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (typeSignature != null) {
            typeSignature.setScanResult(scanResult);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getTypeSignature().hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof AnnotationClassRef)) {
            return false;
        }
        return getTypeSignature().equals(((AnnotationClassRef) obj).getTypeSignature());
    }

    @Override
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        // 较新版本的 Annotation::toString() 已经去掉了 "class"/"interface" 前缀，
        // 并在类引用末尾添加了 ".class"(这实际上并不匹配注解源码语法……)

        //        String prefix = "class ";
        //        if (scanResult != null) {
        //            final ClassInfo ci = getClassInfo();
        //            // JDK 在 Annotation::toString 中对接口和注解都使用 "interface"
        //            if (ci != null && ci.isInterfaceOrAnnotation()) {
        //                prefix = "interface ";
        //            }
        //        }

        /* prefix + */
        buf.append(getTypeSignature().toString(useSimpleNames)).append(".class");
    }
}
