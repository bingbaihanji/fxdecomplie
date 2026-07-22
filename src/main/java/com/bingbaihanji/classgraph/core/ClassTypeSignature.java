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
import com.bingbaihanji.classgraph.types.TypeUtils;
import com.bingbaihanji.classgraph.types.TypeUtils.ModifierType;
import com.bingbaihanji.classgraph.utils.LogNode;

import java.util.*;

/** 类类型签名(在 classfile 文档中称为"ClassSignature") */
public final class ClassTypeSignature extends HierarchicalTypeSignature {

    /** 类类型参数 */
    final List<TypeParameter> typeParameters;
    /** 类信息 */
    private final ClassInfo classInfo;
    /** 超类类型 */
    private final ClassRefTypeSignature superclassSignature;

    /** 超接口签名 */
    private final List<ClassRefTypeSignature> superinterfaceSignatures;

    /**
     * throws 签名(通常为 null)这些仅出现在 Scala 类中，当类标记了 {@code @throws} 时出现，
     * 它们违反了 classfile 规范(#495)，但我们仍然解析它们
     */
    private final List<ClassRefOrTypeVariableSignature> throwsSignatures;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param classInfo
     *            类的 {@link ClassInfo} 对象
     * @param typeParameters
     *            类类型参数
     * @param superclassSignature
     *            超类签名
     * @param superinterfaceSignatures
     *            超接口签名
     * @param throwsSignatures
     *            throws 签名(这些实际上是无效的，但可能由 Scala 添加：#495)通常为 null
     */
    private ClassTypeSignature(final ClassInfo classInfo, final List<TypeParameter> typeParameters,
                               final ClassRefTypeSignature superclassSignature,
                               final List<ClassRefTypeSignature> superinterfaceSignatures,
                               final List<ClassRefOrTypeVariableSignature> throwsSignatures) {
        super();
        this.classInfo = classInfo;
        this.typeParameters = typeParameters;
        this.superclassSignature = superclassSignature;
        this.superinterfaceSignatures = superinterfaceSignatures;
        this.throwsSignatures = throwsSignatures;
    }

    /**
     * 用于创建合成类类型描述符的构造函数(#662)
     *
     * @param classInfo
     *            类
     * @param superclass
     *            超类
     * @param interfaces
     *            实现的接口
     */
    ClassTypeSignature(final ClassInfo classInfo, final ClassInfo superclass, final ClassInfoList interfaces) {
        super();
        this.classInfo = classInfo;
        this.typeParameters = Collections.emptyList();
        ClassRefTypeSignature superclassSignature = null;
        try {
            superclassSignature = superclass == null ? null
                    : (ClassRefTypeSignature) TypeSignature
                    .parse("L" + superclass.getName().replace('.', '/') + ";", classInfo.getName());
        } catch (final ParseException e) {
            // 静默失败(不应发生)
        }
        this.superclassSignature = superclassSignature;
        this.superinterfaceSignatures = interfaces == null || interfaces.isEmpty()
                ? Collections.<ClassRefTypeSignature>emptyList()
                : new ArrayList<ClassRefTypeSignature>(interfaces.size());
        if (interfaces != null) {
            for (final ClassInfo iface : interfaces) {
                try {
                    final ClassRefTypeSignature ifaceSignature = (ClassRefTypeSignature) TypeSignature
                            .parse("L" + iface.getName().replace('.', '/') + ";", classInfo.getName());
                    this.superinterfaceSignatures.add(ifaceSignature);
                } catch (final ParseException e) {
                    // 静默失败(不应发生)
                }
            }
        }
        this.throwsSignatures = null;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 解析类类型签名或类类型描述符
     *
     * @param typeDescriptor
     *            要解析的类类型签名或类类型描述符
     * @param classInfo
     *            类信息
     * @return 解析后的类类型签名或类类型描述符
     * @throws ParseException
     *             如果类类型签名无法解析
     */
    static ClassTypeSignature parse(final String typeDescriptor, final ClassInfo classInfo) throws ParseException {
        final Parser parser = new Parser(typeDescriptor);
        // 定义类名用于使用定义类的类型描述符解析类型变量
        // 但这里我们正在解析定义类的类型描述符，所以它不可能包含指向自身的变量 => 直接使用 null 作为定义类名
        final String definingClassNameNull = null;
        final List<TypeParameter> typeParameters = TypeParameter.parseList(parser, definingClassNameNull);
        final ClassRefTypeSignature superclassSignature = ClassRefTypeSignature.parse(parser,
                definingClassNameNull);
        List<ClassRefTypeSignature> superinterfaceSignatures;
        if (parser.hasMore()) {
            superinterfaceSignatures = new ArrayList<>();
            while (parser.hasMore()) {
                if (parser.peek() == '^') {
                    // 类类型签名中存在非法的 "throws" 后缀 -- 穿透处理
                    break;
                }
                final ClassRefTypeSignature superinterfaceSignature = ClassRefTypeSignature.parse(parser,
                        definingClassNameNull);
                if (superinterfaceSignature == null) {
                    throw new ParseException(parser, "Could not parse superinterface signature");
                }
                superinterfaceSignatures.add(superinterfaceSignature);
            }
        } else {
            superinterfaceSignatures = Collections.emptyList();
        }
        List<ClassRefOrTypeVariableSignature> throwsSignatures;
        if (parser.peek() == '^') {
            // 此类型签名的末尾存在非法的 "throws" 后缀
            // Scala 在将某个类标记为 "@throws" 时会添加这些后缀(#495)
            // 具有此类类型签名的类会被 javac 和 javap 拒绝，如果在子类上调用
            // getClass().getGenericSuperclass() 会抛出 GenericSignatureFormatError
            // 但由于类型擦除，JVM 会忽略类型签名，而 Scala 似乎依赖这一点 --
            // 或者至少 Scala 团队从未注意到这个问题，因为这些类在纯 Scala 环境中可以正常运行
            // 由于此问题在 Scala 领域可能广泛存在，因此接受这些无效的类型签名并实际解析出
            // 所有 "throws" 后缀，比抛出异常并拒绝解析类型签名更好
            throwsSignatures = new ArrayList<>();
            while (parser.peek() == '^') {
                parser.expect('^');
                final ClassRefTypeSignature classTypeSignature = ClassRefTypeSignature.parse(parser,
                        classInfo.getName());
                if (classTypeSignature != null) {
                    throwsSignatures.add(classTypeSignature);
                } else {
                    final TypeVariableSignature typeVariableSignature = TypeVariableSignature.parse(parser,
                            classInfo.getName());
                    if (typeVariableSignature != null) {
                        throwsSignatures.add(typeVariableSignature);
                    } else {
                        throw new ParseException(parser, "Missing type variable signature");
                    }
                }
            }
        } else {
            throwsSignatures = null;
        }
        if (parser.hasMore()) {
            throw new ParseException(parser, "Extra characters at end of type descriptor");
        }
        return new ClassTypeSignature(classInfo, typeParameters, superclassSignature, superinterfaceSignatures,
                throwsSignatures);
    }

    /**
     * 获取类的类型参数
     *
     * @return 类的类型参数
     */
    public List<TypeParameter> getTypeParameters() {
        return typeParameters;
    }

    /**
     * 获取超类的类型签名(对于 {@link Object} 可能为 null，因为它没有超类)
     *
     * @return 超类的类型签名，如果无超类则返回 null(即对于 {@link Object})
     */
    public ClassRefTypeSignature getSuperclassSignature() {
        return superclassSignature;
    }

    /**
     * 获取所有超接口的类型签名
     *
     * @return 所有超接口的类型签名
     */
    public List<ClassRefTypeSignature> getSuperinterfaceSignatures() {
        return superinterfaceSignatures;
    }

    /**
     * 获取 throws 签名根据 classfile 规范，这些是无效的(因此此方法目前是非公开的)，
     * 但可能由 Scala 编译器添加(参见 bug #495)
     *
     * @return throws 签名
     */
    List<ClassRefOrTypeVariableSignature> getThrowsSignatures() {
        return throwsSignatures;
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    protected void addTypeAnnotation(final List<TypePathNode> typePath, final AnnotationInfo annotationInfo) {
        // 类类型的各个部分各自具有其自己的 addTypeAnnotation 方法
        throw new IllegalArgumentException(
                "Cannot call this method on " + ClassTypeSignature.class.getSimpleName());
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#getClassName()
     */
    @Override
    protected String getClassName() {
        return classInfo != null ? classInfo.getName() : null;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#getClassInfo()
     */
    @Override
    protected ClassInfo getClassInfo() {
        return classInfo;
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
        if (this.superclassSignature != null) {
            this.superclassSignature.setScanResult(scanResult);
        }
        if (superinterfaceSignatures != null) {
            for (final ClassRefTypeSignature classRefTypeSignature : superinterfaceSignatures) {
                classRefTypeSignature.setScanResult(scanResult);
            }
        }
    }

    /**
     * 获取类型签名中引用的所有类名
     *
     * @param refdClassNames
     *            被引用的类名集合
     */
    protected void findReferencedClassNames(final Set<String> refdClassNames) {
        for (final TypeParameter typeParameter : typeParameters) {
            typeParameter.findReferencedClassNames(refdClassNames);
        }
        if (superclassSignature != null) {
            superclassSignature.findReferencedClassNames(refdClassNames);
        }
        if (superinterfaceSignatures != null) {
            for (final ClassRefTypeSignature typeSignature : superinterfaceSignatures) {
                typeSignature.findReferencedClassNames(refdClassNames);
            }
        }
        if (throwsSignatures != null) {
            for (final ClassRefOrTypeVariableSignature typeSignature : throwsSignatures) {
                typeSignature.findReferencedClassNames(refdClassNames);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取类型描述符或类型签名中引用的所有类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            被引用的类信息集合
     */
    @Override
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        final Set<String> refdClassNames = new HashSet<>();
        findReferencedClassNames(refdClassNames);
        for (final String refdClassName : refdClassNames) {
            final ClassInfo clsInfo = ClassInfo.getOrCreateClassInfo(refdClassName, classNameToClassInfo);
            clsInfo.scanResult = scanResult;
            refdClassInfo.add(clsInfo);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return typeParameters.hashCode() + (superclassSignature == null ? 1 : superclassSignature.hashCode()) * 7
                + (superinterfaceSignatures == null ? 1 : superinterfaceSignatures.hashCode()) * 15;
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof ClassTypeSignature)) {
            return false;
        }
        final ClassTypeSignature o = (ClassTypeSignature) obj;
        return Objects.equals(o.typeParameters, this.typeParameters)
                && Objects.equals(o.superclassSignature, this.superclassSignature)
                && Objects.equals(o.superinterfaceSignatures, this.superinterfaceSignatures);
    }

    /**
     * 渲染为字符串形式
     *
     * @param className
     *            类名
     * @param useSimpleNames
     *            是否使用简单名称
     * @param modifiers
     *            类修饰符
     * @param isAnnotation
     *            如果类是注解则为 true
     * @param isInterface
     *            如果类是接口则为 true
     * @param annotationsToExclude
     *            要排除的注解
     * @param buf
     *            字符串构建器
     */
    void toStringInternal(final String className, final boolean useSimpleNames, final int modifiers,
                          final boolean isAnnotation, final boolean isInterface, final AnnotationInfoList annotationsToExclude,
                          final StringBuilder buf) {
        if (throwsSignatures != null) {
            for (final ClassRefOrTypeVariableSignature throwsSignature : throwsSignatures) {
                if (buf.length() > 0) {
                    buf.append(' ');
                }
                buf.append("@throws(").append(throwsSignature).append(")");
            }
        }
        if (modifiers != 0) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            TypeUtils.modifiersToString(modifiers, ModifierType.CLASS, /* ignored */ false, buf);
        }
        if (buf.length() > 0) {
            buf.append(' ');
        }
        buf.append(isAnnotation ? "@interface"
                : isInterface ? "interface" : (modifiers & 0x4000) != 0 ? "enum" : "class");
        buf.append(' ');
        if (className != null) {
            buf.append(useSimpleNames ? ClassInfo.getSimpleName(className) : className);
        }
        if (!typeParameters.isEmpty()) {
            buf.append('<');
            for (int i = 0; i < typeParameters.size(); i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                typeParameters.get(i).toStringInternal(useSimpleNames, null, buf);
            }
            buf.append('>');
        }
        if (superclassSignature != null) {
            final String superSig = superclassSignature.toString(useSimpleNames);
            // 即使超类是 Object，superSig 也可能带有类类型注解
            if (!"java.lang.Object".equals(superSig)
                    && !("Object".equals(superSig) && "java.lang.Object".equals(superclassSignature.className))) {
                buf.append(" extends ");
                buf.append(superSig);
            }
        }
        if (superinterfaceSignatures != null && !superinterfaceSignatures.isEmpty()) {
            buf.append(isInterface ? " extends " : " implements ");
            for (int i = 0; i < superinterfaceSignatures.size(); i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                superinterfaceSignatures.get(i).toStringInternal(useSimpleNames, null, buf);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 内部转换为字符串
     *
     * @param useSimpleNames
     *            是否使用简单名称
     * @param annotationsToExclude
     *            要排除的注解
     * @param buf
     *            字符串构建器
     */
    @Override
    protected void toStringInternal(final boolean useSimpleNames, final AnnotationInfoList annotationsToExclude,
                                    final StringBuilder buf) {
        toStringInternal(classInfo.getName(), useSimpleNames, classInfo.getModifiers(), classInfo.isAnnotation(),
                classInfo.isInterface(), annotationsToExclude, buf);
    }
}