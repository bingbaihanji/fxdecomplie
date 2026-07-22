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
import com.bingbaihanji.classgraph.utils.LogNode;

import java.util.*;

/** 方法类型签名(在 classfile 文档中称为 "MethodSignature") */
public final class MethodTypeSignature extends HierarchicalTypeSignature {
    /** 方法类型参数 */
    final List<TypeParameter> typeParameters;

    /** 方法参数类型签名 */
    private final List<TypeSignature> parameterTypeSignatures;

    /** 方法结果类型 */
    private final TypeSignature resultType;

    /** throws 类型签名 */
    private final List<ClassRefOrTypeVariableSignature> throwsSignatures;

    /** 显式接收器参数上的任何类型注解 */
    private AnnotationInfoList receiverTypeAnnotationInfo;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param typeParameters
     *            方法的类型参数
     * @param paramTypes
     *            方法的参数类型
     * @param resultType
     *            方法的返回类型
     * @param throwsSignatures
     *            方法的 throws 签名
     */
    private MethodTypeSignature(final List<TypeParameter> typeParameters, final List<TypeSignature> paramTypes,
                                final TypeSignature resultType, final List<ClassRefOrTypeVariableSignature> throwsSignatures) {
        super();
        this.typeParameters = typeParameters;
        this.parameterTypeSignatures = paramTypes;
        this.resultType = resultType;
        this.throwsSignatures = throwsSignatures;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 解析方法签名
     *
     * @param typeDescriptor
     *            方法的类型描述符
     * @param definingClassName
     *            定义类的名称(用于解析类型变量)
     * @return 解析后的方法类型签名
     * @throws ParseException
     *             如果方法类型签名无法解析
     */
    static MethodTypeSignature parse(final String typeDescriptor, final String definingClassName)
            throws ParseException {
        if ("<init>".equals(typeDescriptor)) {
            // 特殊情况：CONSTANT_NameAndType_info 结构中的实例初始化方法签名：
            // https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.4.2
            return new MethodTypeSignature(Collections.<TypeParameter>emptyList(),
                    Collections.<TypeSignature>emptyList(), /* void */ new BaseTypeSignature('V'),
                    Collections.<ClassRefOrTypeVariableSignature>emptyList());
        }
        final Parser parser = new Parser(typeDescriptor);
        final List<TypeParameter> typeParameters = TypeParameter.parseList(parser, definingClassName);
        parser.expect('(');
        final List<TypeSignature> paramTypes = new ArrayList<>();
        while (parser.peek() != ')') {
            if (!parser.hasMore()) {
                throw new ParseException(parser, "解析方法签名时输入耗尽");
            }
            final TypeSignature paramType = TypeSignature.parse(parser, definingClassName);
            if (paramType == null) {
                throw new ParseException(parser, "缺少方法参数类型签名");
            }
            paramTypes.add(paramType);
        }
        parser.expect(')');
        final TypeSignature resultType = TypeSignature.parse(parser, definingClassName);
        if (resultType == null) {
            throw new ParseException(parser, "缺少方法结果类型签名");
        }
        List<ClassRefOrTypeVariableSignature> throwsSignatures;
        if (parser.peek() == '^') {
            throwsSignatures = new ArrayList<>();
            while (parser.peek() == '^') {
                parser.expect('^');
                final ClassRefTypeSignature classTypeSignature = ClassRefTypeSignature.parse(parser,
                        definingClassName);
                if (classTypeSignature != null) {
                    throwsSignatures.add(classTypeSignature);
                } else {
                    final TypeVariableSignature typeVariableSignature = TypeVariableSignature.parse(parser,
                            definingClassName);
                    if (typeVariableSignature != null) {
                        throwsSignatures.add(typeVariableSignature);
                    } else {
                        throw new ParseException(parser, "缺少类型变量签名");
                    }
                }
            }
        } else {
            throwsSignatures = Collections.emptyList();
        }
        if (parser.hasMore()) {
            throw new ParseException(parser, "类型描述符末尾有多余字符");
        }
        final MethodTypeSignature methodSignature = new MethodTypeSignature(typeParameters, paramTypes, resultType,
                throwsSignatures);
        // 添加从类型变量签名到其所属方法签名以及封闭类类型签名的反向链接
        @SuppressWarnings("unchecked") final List<TypeVariableSignature> typeVariableSignatures = (List<TypeVariableSignature>) parser.getState();
        if (typeVariableSignatures != null) {
            for (final TypeVariableSignature typeVariableSignature : typeVariableSignatures) {
                typeVariableSignature.containingMethodSignature = methodSignature;
            }
        }
        return methodSignature;
    }

    /**
     * 获取方法的类型参数(如果这是一个
     * <a href="https://docs.oracle.com/javase/tutorial/extra/generics/methods.html">泛型方法</a>)
     *
     * @return 方法的类型参数(如果有)，否则为 null
     */
    public List<TypeParameter> getTypeParameters() {
        return typeParameters;
    }

    /**
     * 获取方法参数的类型签名注意：此方法不公开，因为类型需要与其他参数元数据对齐
     * 参数的类型可以在对齐后从参数的 {@link MethodParameterInfo} 对象中获取
     *
     * @return 方法的参数类型，作为 {@link TypeSignature} 解析类型对象
     */
    List<TypeSignature> getParameterTypeSignatures() {
        return parameterTypeSignatures;
    }

    /**
     * 获取方法的结果类型
     *
     * @return 方法的结果类型，作为 {@link TypeSignature} 解析类型对象
     */
    public TypeSignature getResultType() {
        return resultType;
    }

    /**
     * 获取方法的 throws 类型
     *
     * @return 方法的 throws 类型，作为 {@link TypeSignature} 解析类型对象
     */
    public List<ClassRefOrTypeVariableSignature> getThrowsSignatures() {
        return throwsSignatures;
    }

    @Override
    protected void addTypeAnnotation(final List<TypePathNode> typePath, final AnnotationInfo annotationInfo) {
        // 类的各个类型部分各自有其自己的 addTypeAnnotation 方法
        throw new IllegalArgumentException(
                "不能对 " + MethodTypeSignature.class.getSimpleName() + " 调用此方法");
    }

    /**
     * 为显式接收器参数添加类型注解
     *
     * @param annotationInfo
     *            接收器类型注解
     */
    void addRecieverTypeAnnotation(final AnnotationInfo annotationInfo) {
        if (receiverTypeAnnotationInfo == null) {
            receiverTypeAnnotationInfo = new AnnotationInfoList(1);
        }
        receiverTypeAnnotationInfo.add(annotationInfo);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取显式接收器参数上的类型注解，如果没有则返回 null
     *
     * @return 显式接收器参数上的类型注解，如果没有则返回 null
     */
    public AnnotationInfoList getReceiverTypeAnnotationInfo() {
        return receiverTypeAnnotationInfo;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#getClassName()
     */
    @Override
    protected String getClassName() {
        // getClassInfo() 对此类型无效，因此 getClassName() 不需要实现
        throw new IllegalArgumentException("getClassName() 不能在此处调用");
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#getClassInfo()
     */
    @Override
    protected ClassInfo getClassInfo() {
        throw new IllegalArgumentException("getClassInfo() 不能在此处调用");
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (typeParameters != null) {
            for (final TypeParameter typeParameter : typeParameters) {
                typeParameter.setScanResult(scanResult);
            }
        }
        if (this.parameterTypeSignatures != null) {
            for (final TypeSignature typeParameter : parameterTypeSignatures) {
                typeParameter.setScanResult(scanResult);
            }
        }
        if (this.resultType != null) {
            this.resultType.setScanResult(scanResult);
        }
        if (throwsSignatures != null) {
            for (final ClassRefOrTypeVariableSignature throwsSignature : throwsSignatures) {
                throwsSignature.setScanResult(scanResult);
            }
        }
    }

    /**
     * 获取类型签名中引用的所有类的名称
     *
     * @param refdClassNames
     *            引用的类名集合
     */
    protected void findReferencedClassNames(final Set<String> refdClassNames) {
        for (final TypeParameter typeParameter : typeParameters) {
            if (typeParameter != null) {
                typeParameter.findReferencedClassNames(refdClassNames);
            }
        }
        for (final TypeSignature typeSignature : parameterTypeSignatures) {
            if (typeSignature != null) {
                typeSignature.findReferencedClassNames(refdClassNames);
            }
        }
        resultType.findReferencedClassNames(refdClassNames);
        for (final ClassRefOrTypeVariableSignature typeSignature : throwsSignatures) {
            if (typeSignature != null) {
                typeSignature.findReferencedClassNames(refdClassNames);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取类型描述符或类型签名中引用的所有类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            引用的类信息集合
     */
    @Override
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        final Set<String> refdClassNames = new HashSet<>();
        findReferencedClassNames(refdClassNames);
        for (final String refdClassName : refdClassNames) {
            final ClassInfo classInfo = ClassInfo.getOrCreateClassInfo(refdClassName, classNameToClassInfo);
            classInfo.scanResult = scanResult;
            refdClassInfo.add(classInfo);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return typeParameters.hashCode() + parameterTypeSignatures.hashCode() * 7 + resultType.hashCode() * 15
                + throwsSignatures.hashCode() * 31;
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof MethodTypeSignature)) {
            return false;
        }
        final MethodTypeSignature o = (MethodTypeSignature) obj;
        return o.typeParameters.equals(this.typeParameters)
                && o.parameterTypeSignatures.equals(this.parameterTypeSignatures)
                && o.resultType.equals(this.resultType) && o.throwsSignatures.equals(this.throwsSignatures);
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    protected void toStringInternal(final boolean useSimpleNames, final AnnotationInfoList annotationsToExclude,
                                    final StringBuilder buf) {
        if (!typeParameters.isEmpty()) {
            buf.append('<');
            for (int i = 0; i < typeParameters.size(); i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                typeParameters.get(i).toString(useSimpleNames, buf);
            }
            buf.append('>');
        }

        if (buf.length() > 0) {
            buf.append(' ');
        }
        buf.append(resultType.toString());

        buf.append(" (");
        for (int i = 0; i < parameterTypeSignatures.size(); i++) {
            if (i > 0) {
                buf.append(", ");
            }
            parameterTypeSignatures.get(i).toString(useSimpleNames, buf);
        }
        buf.append(')');

        if (!throwsSignatures.isEmpty()) {
            buf.append(" throws ");
            for (int i = 0; i < throwsSignatures.size(); i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                throwsSignatures.get(i).toString(useSimpleNames, buf);
            }
        }
    }
}