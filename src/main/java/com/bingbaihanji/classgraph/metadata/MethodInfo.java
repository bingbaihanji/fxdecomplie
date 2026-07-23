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

import com.bingbaihanji.classgraph.bytecode.ClassParser.MethodTypeAnnotationDecorator;
import com.bingbaihanji.classgraph.metadata.ClassHierarchy.RelType;
import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.type.*;
import com.bingbaihanji.classgraph.type.TypeUtils.ModifierType;
import com.bingbaihanji.classgraph.util.Assert;
import com.bingbaihanji.classgraph.util.LogNode;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 保存扫描过程中遇到的类的方法元数据所有值均直接取自类的 ClassParser
 */
public class MethodInfo extends ClassMemberInfo implements Comparable<MethodInfo> {
    /** 未对齐的参数注解 */
    public AnnotationInfo[][] parameterAnnotationInfo;
    /** 解析后的类型描述符 */
    private transient MethodType typeDescriptor;
    /** 解析后的类型签名(如果没有则为 null)方法参数类型未对齐 */
    private transient MethodType typeSignature;
    /**
     * 未对齐的参数名称仅在 JDK8+ 中产生，且仅当编译时提供命令行开关 `-parameters`
     */
    private String[] parameterNames;
    /**
     * 未对齐的参数修饰符仅在 JDK8+ 中产生，且仅当编译时提供命令行开关 `-parameters`
     */
    private int[] parameterModifiers;
    /** 对齐后的方法参数信息 */
    private transient MethodParam[] parameterInfo;

    /** 如果此方法有方法体则为 true */
    private boolean hasBody;

    /** 此方法体的最小行号，如果未知则为 0 */
    private int minLineNum;

    /** 此方法体的最大行号，如果未知则为 0 */
    private int maxLineNum;

    /** {@link MethodType} 实例的类型注解装饰器 */
    private transient List<MethodTypeAnnotationDecorator> typeAnnotationDecorators;

    private String[] thrownExceptionNames;

    private transient ClassInfoList thrownExceptions;

    // -------------------------------------------------------------------------------------------------------------

    /** 反序列化的默认构造函数 */
    MethodInfo() {
        super();
    }

    /**
     * 构造函数
     *
     * @param definingClassName
     *            封闭类的名称
     * @param methodName
     *            方法名称
     * @param methodAnnotationInfo
     *            方法上注解的 {@link AnnotationInfo} 对象列表
     * @param modifiers
     *            方法修饰符位
     * @param typeDescriptorStr
     *            内部方法类型描述符字符串
     * @param typeSignatureStr
     *            内部方法类型签名字符串，如果没有则为 null
     * @param parameterNames
     *            参数名称
     * @param parameterModifiers
     *            参数修饰符
     * @param parameterAnnotationInfo
     *            参数 {@link AnnotationInfo}
     * @param hasBody
     *            如果此方法有方法体则为 true
     * @param minLineNum
     *            此方法体的最小行号，如果未知则为 0
     * @param maxLineNum
     *            此方法体的最大行号，如果未知则为 0
     * @param methodTypeAnnotationDecorators
     *            方法类型注解的装饰器 lambda
     * @param thrownExceptionNames
     *            此方法抛出的异常
     */
    public MethodInfo(final String definingClassName, final String methodName,
                      final AnnotationInfoList methodAnnotationInfo, final int modifiers, final String typeDescriptorStr,
                      final String typeSignatureStr, final String[] parameterNames, final int[] parameterModifiers,
                      final AnnotationInfo[][] parameterAnnotationInfo, final boolean hasBody, final int minLineNum,
                      final int maxLineNum, final List<MethodTypeAnnotationDecorator> methodTypeAnnotationDecorators,
                      final String[] thrownExceptionNames) {
        super(definingClassName, methodName, modifiers, typeDescriptorStr, typeSignatureStr, methodAnnotationInfo);
        this.parameterNames = parameterNames;
        this.parameterModifiers = parameterModifiers;
        this.parameterAnnotationInfo = parameterAnnotationInfo;
        this.hasBody = hasBody;
        this.minLineNum = minLineNum;
        this.maxLineNum = maxLineNum;
        this.typeAnnotationDecorators = methodTypeAnnotationDecorators;
        this.thrownExceptionNames = thrownExceptionNames;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 返回方法名称注意，构造函数的名称为 {@code "<init>"}，私有静态类初始化块的名称为
     * {@code "<clinit>"}
     *
     * @return 方法名称
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 以字符串形式获取方法修饰符，例如 "public static final"要获取修饰符位，请调用
     * {@link #getModifiers()}
     *
     * @return 方法的修饰符(字符串形式)
     */
    @Override
    public String getModifiersStr() {
        final StringBuilder buf = new StringBuilder();
        TypeUtils.modifiersToString(modifiers, ModifierType.METHOD, isDefault(), buf);
        return buf.toString();
    }

    /**
     * 返回方法的解析后类型描述符，不包含类型参数如果需要泛型类型参数，请改用
     * {@link #getTypeSignature()}
     *
     * @return 方法的解析后类型描述符
     */
    @Override
    public MethodType getTypeDescriptor() {
        synchronized (this) {
            if (typeDescriptor == null) {
                try {
                    typeDescriptor = MethodType.parse(typeDescriptorStr, declaringClassName);
                    typeDescriptor.setScanResult(scanResult);
                    if (typeAnnotationDecorators != null) {
                        // 类型注解从源代码中声明的第一个参数开始对形参进行索引
                        // 然而，方法类型描述符可能以额外的隐式(编译器合成的)参数开头，
                        // 这些参数不计入 formal_parameter_index —— 例如，非静态内部类构造函数的
                        // 前导封闭实例参数，或枚举构造函数的 (String name, int ordinal) 前导参数
                        // 确定存在多少个这样的隐式前缀参数，在运行装饰器时将它们从描述符中剥离，
                        // 以使 formal_parameter_index 对齐，然后再恢复它们另见 getParameterInfo()，
                        // 出于同样的原因对参数元数据进行"右对齐"(#897)
                        final int descNumParam = typeDescriptor.getParameterTypeSignatures().size();
                        int numImplicitPrefixParams;
                        final MethodType sig = getTypeSignature();
                        if (sig != null) {
                            // 泛型类型签名省略了隐式前缀参数，因此参数数量的差异揭示了多少个
                            // (规范认可的关系)
                            numImplicitPrefixParams = descNumParam - sig.getParameterTypeSignatures().size();
                        } else {
                            // 没有泛型类型签名(例如非泛型内部类或枚举构造函数)，
                            // 因此从结构上确定隐式前缀参数的数量
                            numImplicitPrefixParams = getNumImplicitPrefixParams();
                        }
                        // 限制在合理范围内，以防编译器错误或格式错误的 ClassParser
                        if (numImplicitPrefixParams < 0) {
                            numImplicitPrefixParams = 0;
                        } else if (numImplicitPrefixParams > descNumParam) {
                            numImplicitPrefixParams = descNumParam;
                        }
                        decorateMethodType(typeDescriptor, numImplicitPrefixParams);
                    }
                } catch (final ParseException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            return typeDescriptor;
        }
    }

    /**
     * 确定此方法参数列表开头处不被类型注解的 {@code formal_parameter_index} 计数的隐式
     * (编译器合成的)参数的数量这仅在无法与泛型类型签名对比时使用目前处理两种标准
     * Java 情况：非静态内部类构造函数的前导封闭实例参数，以及枚举构造函数的前导
     * {@code (String name, int ordinal)} 参数(局部类和匿名类可能添加不同数量的合成参数，
     * 此处有意不进行特殊处理 —— 任何由此导致的不匹配都会被 {@link #decorateMethodType} 优雅处理)
     * (#897)
     *
     * @return 隐式前缀参数的数量(如果没有或无法确定则为 0)
     */
    private int getNumImplicitPrefixParams() {
        if ("<init>".equals(name)) {
            final ClassInfo declaringClassInfo = getClassInfo();
            if (declaringClassInfo != null) {
                if (declaringClassInfo.isEnum()) {
                    // 枚举构造函数有两个前导合成参数：(String name, int ordinal)
                    return 2;
                } else if (declaringClassInfo.isInnerClass() && !declaringClassInfo.isStatic()) {
                    // 非静态内部类构造函数有一个前导封闭实例参数
                    return 1;
                }
            }
        }
        return 0;
    }

    /**
     * 在给定的解析后方法类型上运行方法类型注解装饰器，暂时剥离给定数量的隐式前缀参数，
     * 以使 {@code formal_parameter_index} 值与源代码声明的参数对齐任何无法与参数类型
     * 匹配的单个类型注解(例如由于编译器特定的参数索引方式，如 Kotlin、局部或匿名类，
     * 或编译器错误)将被跳过，而不是导致整个方法类型解析中止(#897)
     *
     * @param methodType
     *            要装饰的解析后方法类型签名或描述符
     * @param numImplicitPrefixParams
     *            装饰期间要剥离的隐式前缀参数数量(如果无则为 0)
     */
    private void decorateMethodType(final MethodType methodType, final int numImplicitPrefixParams) {
        final List<TypeSignature> paramSigs = methodType.getParameterTypeSignatures();
        // 在移除隐式前缀参数之前先复制它们 —— 不要使用 List.subList() 返回的实时视图，
        // 因为下面的 paramSigs 结构修改会使其失效
        final List<TypeSignature> implicitPrefixParams = numImplicitPrefixParams <= 0 ? null
                : new ArrayList<>(paramSigs.subList(0, numImplicitPrefixParams));
        for (int i = 0; i < numImplicitPrefixParams; i++) {
            paramSigs.remove(0);
        }
        for (final MethodTypeAnnotationDecorator decorator : typeAnnotationDecorators) {
            try {
                decorator.decorate(methodType);
            } catch (final IllegalArgumentException e) {
                // 跳过无法与参数类型匹配的类型注解，而不是无法产生整个方法类型(尽最大努力)(#897)
            }
        }
        if (implicitPrefixParams != null) {
            for (int i = numImplicitPrefixParams - 1; i >= 0; --i) {
                paramSigs.add(0, implicitPrefixParams.get(i));
            }
        }
    }

    /**
     * 返回方法的解析后类型签名，可能包含类型参数如果返回 null，表示此方法没有类型签名
     * 信息可用，请改用 {@link #getTypeDescriptor()}
     *
     * @return 方法的解析后类型签名，如果不可用则为 null
     * @throws IllegalArgumentException
     *             如果方法类型签名无法解析(这应仅在 ClassParser 损坏或编译器错误导致无效的类型签名
     *             被写入 ClassParser 时抛出)
     */
    @Override
    public MethodType getTypeSignature() {
        synchronized (this) {
            if (typeSignature == null && typeSignatureStr != null) {
                try {
                    typeSignature = MethodType.parse(typeSignatureStr, declaringClassName);
                    typeSignature.setScanResult(scanResult);
                    if (typeAnnotationDecorators != null) {
                        // 泛型类型签名已经省略了所有隐式前缀参数，因此
                        // formal_parameter_index 直接与其对齐(剥离 0 个)(#897)
                        decorateMethodType(typeSignature, 0);
                    }
                } catch (final ParseException e) {
                    throw new IllegalArgumentException(
                            "方法 " + getClassName() + "." + getName() + " 的类型签名无效"
                                    + (getClassInfo() != null
                                    ? "，位于类路径元素 " + getClassInfo().getClasspathURI()
                                    : "")
                                    + " : " + typeSignatureStr,
                            e);
                }
            }
            return typeSignature;
        }
    }

    /**
     * 返回方法的解析后类型签名，可能包含类型参数如果类型签名字符串为 null(表示此方法
     * 没有类型签名信息可用)，则返回解析后的类型描述符
     *
     * @return 方法的解析后类型签名，如果不可用，则返回方法的解析后类型描述符
     */
    @Override
    public MethodType getTypeSignatureOrTypeDescriptor() {
        MethodType typeSig = null;
        try {
            typeSig = getTypeSignature();
            if (typeSig != null) {
                return typeSig;
            }
        } catch (final Exception e) {
            // 忽略
        }
        return getTypeDescriptor();
    }

    /**
     * 返回方法抛出的异常列表，作为 {@link ClassInfoList}
     *
     * @return 方法抛出的异常列表，作为 {@link ClassInfoList}(列表可能为空)
     */
    public ClassInfoList getThrownExceptions() {
        synchronized (this) {
            if (thrownExceptions == null && thrownExceptionNames != null) {
                thrownExceptions = new ClassInfoList(thrownExceptionNames.length);
                for (final String thrownExceptionName : thrownExceptionNames) {
                    final ClassInfo classInfo = scanResult.classes().getClassInfo(thrownExceptionName);
                    if (classInfo != null) {
                        thrownExceptions.add(classInfo);
                        classInfo.setScanResult(scanResult);
                    }
                }
            }
            return thrownExceptions == null ? ClassInfoList.EMPTY_LIST : thrownExceptions;
        }
    }

    /**
     * 返回方法抛出的异常，作为数组
     *
     * @return 方法抛出的异常，作为数组(数组可能为空)
     */
    public String[] getThrownExceptionNames() {
        return thrownExceptionNames == null ? new String[0] : thrownExceptionNames;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 如果此方法是构造函数则返回 true构造函数的方法名为 {@code "<init>"}
     * 对于命名为 {@code "<clinit>"} 的私有静态类初始化块，此方法返回 false
     *
     * @return 如果此方法是构造函数则返回 true
     */
    public boolean isConstructor() {
        return "<init>".equals(name);
    }

    /**
     * 如果此方法是 synchronized 的则返回 true
     *
     * @return 如果此方法是 synchronized 的则返回 true
     */
    public boolean isSynchronized() {
        return Modifier.isSynchronized(modifiers);
    }

    /**
     * 如果此方法是桥接方法则返回 true
     *
     * @return 如果此方法是桥接方法则返回 true
     */
    public boolean isBridge() {
        return (modifiers & 0x0040) != 0;
    }

    /**
     * 如果此方法是可变参数方法则返回 true
     *
     * @return 如果此方法是可变参数方法则返回 true
     */
    public boolean isVarArgs() {
        return (modifiers & 0x0080) != 0;
    }

    /**
     * 如果此方法是 native 方法则返回 true
     *
     * @return 如果此方法是 native 方法则返回 true
     */
    public boolean isNative() {
        return Modifier.isNative(modifiers);
    }

    /**
     * 如果此方法是抽象方法则返回 true
     *
     * @return 如果此方法是抽象方法则返回 true
     */
    public boolean isAbstract() {
        return Modifier.isAbstract(modifiers);
    }

    /**
     * 如果此方法是 strictfp 方法则返回 true
     *
     * @return 如果此方法是 strictfp 方法则返回 true
     */
    public boolean isStrict() {
        return Modifier.isStrict(modifiers);
    }

    /**
     * 如果此方法有方法体(即在包含类中有实现)则返回 true
     *
     * @return 如果此方法有方法体则返回 true
     */
    public boolean hasBody() {
        return hasBody;
    }

    /**
     * 此方法体中第一个非空行的行号，如果未知则为 0
     *
     * @return 此方法体中第一个非空行的行号，如果未知则为 0
     */
    public int getMinLineNum() {
        return minLineNum;
    }

    /**
     * 此方法体中最后一个非空行的行号，如果未知则为 0
     *
     * @return 此方法体中最后一个非空行的行号，如果未知则为 0
     */
    public int getMaxLineNum() {
        return maxLineNum;
    }

    /**
     * 如果这是默认方法(即如果这是接口中的方法且该方法有方法体)则返回 true
     *
     * @return 如果这是默认方法则返回 true
     */
    public boolean isDefault() {
        final ClassInfo classInfo = getClassInfo();
        return classInfo != null && classInfo.isInterface() && hasBody;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取方法参数的可用信息
     *
     * @return 方法参数的 {@link MethodParam} 对象，每个参数一个
     */
    public MethodParam[] getParameterInfo() {
        // Kotlin 对各种参数元数据类型的元数处理非常不一致，参见：
        // https://github.com/classgraph/classgraph/issues/175#issuecomment-363031510
        // 作为变通方案，我们假设任何合成/强制参数必须在参数列表的开头出现(当元数不匹配时)，
        // 并将元数据字段右对齐这在跨 JVM 语言中可能是最安全的假设，尽管此约定绝
        // 不是唯一的可能性(不幸的是，我们不能仅依赖修饰符位来查找合成/强制参数，
        // 因为这些位并不总是可用的，即使可用，它们也不总是给出正确的对齐方式，
        // 至少对于 Kotlin 生成的代码来说是这样)

        // 实际上，Java 规范明确指出："给定方法或构造函数的签名和描述符可能由于
        // 编译器生成的工件而不完全对应特别是，MethodType 中编码形参的
        // TypeSignatures 数量可能少于 MethodDescriptor 中的 ParameterDescriptors 数量"

        // 这也曾被 Guava 28.2 中的一个隐式参数触发 (#660)

        synchronized (this) {
            if (parameterInfo == null) {
                // 从方法的类型签名中获取参数类型签名
                List<TypeSignature> paramTypeSignatures = null;
                final MethodType typeSig = getTypeSignature();
                if (typeSig != null) {
                    paramTypeSignatures = typeSig.getParameterTypeSignatures();
                }

                // 如果没有类型签名(即如果这不是泛型方法)，则回退到类型描述符
                // (注意：类型描述符基本上没什么用，因为编译器可能会自动添加 `synthetic`
                // 和/或 `bridge` 参数，而不提供方法的任何修饰符，因此无法知道在前面添加了
                // 多少参数 —— 参见 #660)
                List<TypeSignature> paramTypeDescriptors = null;
                try {
                    final MethodType typeDesc = getTypeDescriptor();
                    if (typeDesc != null) {
                        paramTypeDescriptors = typeDesc.getParameterTypeSignatures();
                    }
                } catch (final Exception e) {
                    // 忽略当 `synthetic`、`bridge` 或 `mandated` 参数被添加到第一个参数位置时，
                    // 类型注解无法与参数对齐而触发的任何 IllegalArgumentException
                }

                // 找到所有参数信息源的最大长度
                int numParams = paramTypeSignatures == null ? 0 : paramTypeSignatures.size();
                if (paramTypeDescriptors != null && paramTypeDescriptors.size() > numParams) {
                    numParams = paramTypeDescriptors.size();
                }
                if (parameterNames != null && parameterNames.length > numParams) {
                    numParams = parameterNames.length;
                }
                if (parameterModifiers != null && parameterModifiers.length > numParams) {
                    numParams = parameterModifiers.length;
                }
                if (parameterAnnotationInfo != null && parameterAnnotationInfo.length > numParams) {
                    numParams = parameterAnnotationInfo.length;
                }

                // "右对齐"所有参数信息，即假设任何自动添加的隐式参数都是在参数列表开头
                // 添加的，而不是在末尾

                String[] paramNamesAligned = null;
                if (parameterNames != null && parameterNames.length > 0) {
                    if (parameterNames.length == numParams) {
                        // 无需对齐
                        paramNamesAligned = parameterNames;
                    } else {
                        // 长度不匹配时进行右对齐
                        paramNamesAligned = new String[numParams];
                        for (int i = 0,
                             lenDiff = numParams - parameterNames.length; i < parameterNames.length; i++) {
                            paramNamesAligned[lenDiff + i] = parameterNames[i];
                        }
                    }
                }
                int[] paramModifiersAligned = null;
                if (parameterModifiers != null && parameterModifiers.length > 0) {
                    if (parameterModifiers.length == numParams) {
                        // 无需对齐
                        paramModifiersAligned = parameterModifiers;
                    } else {
                        // 长度不匹配时进行右对齐
                        paramModifiersAligned = new int[numParams];
                        for (int i = 0, lenDiff = numParams
                                - parameterModifiers.length; i < parameterModifiers.length; i++) {
                            paramModifiersAligned[lenDiff + i] = parameterModifiers[i];
                        }
                    }
                }
                AnnotationInfo[][] paramAnnotationInfoAligned = null;
                if (parameterAnnotationInfo != null && parameterAnnotationInfo.length > 0) {
                    if (parameterAnnotationInfo.length == numParams) {
                        // 无需对齐
                        paramAnnotationInfoAligned = parameterAnnotationInfo;
                    } else {
                        // 长度不匹配时进行右对齐
                        paramAnnotationInfoAligned = new AnnotationInfo[numParams][];
                        for (int i = 0, lenDiff = numParams
                                - parameterAnnotationInfo.length; i < parameterAnnotationInfo.length; i++) {
                            paramAnnotationInfoAligned[lenDiff + i] = parameterAnnotationInfo[i];
                        }
                    }
                }
                List<TypeSignature> paramTypeSignaturesAligned = null;
                if (paramTypeSignatures != null && paramTypeSignatures.size() > 0) {
                    if (paramTypeSignatures.size() == numParams) {
                        // 无需对齐
                        paramTypeSignaturesAligned = paramTypeSignatures;
                    } else {
                        // 长度不匹配时进行右对齐
                        paramTypeSignaturesAligned = new ArrayList<>(numParams);
                        for (int i = 0, lenDiff = numParams - paramTypeSignatures.size(); i < lenDiff; i++) {
                            // 用 null 左填充
                            paramTypeSignaturesAligned.add(null);
                        }
                        paramTypeSignaturesAligned.addAll(paramTypeSignatures);
                    }
                }
                List<TypeSignature> paramTypeDescriptorsAligned = null;
                if (paramTypeDescriptors != null && paramTypeDescriptors.size() > 0) {
                    if (paramTypeDescriptors.size() == numParams) {
                        // 无需对齐
                        paramTypeDescriptorsAligned = paramTypeDescriptors;
                    } else {
                        // 长度不匹配时进行右对齐
                        paramTypeDescriptorsAligned = new ArrayList<>(numParams);
                        for (int i = 0, lenDiff = numParams - paramTypeDescriptors.size(); i < lenDiff; i++) {
                            // 用 null 左填充
                            paramTypeDescriptorsAligned.add(null);
                        }
                        paramTypeDescriptorsAligned.addAll(paramTypeDescriptors);
                    }
                }

                // 生成 MethodParam 条目
                parameterInfo = new MethodParam[numParams];
                for (int i = 0; i < numParams; i++) {
                    parameterInfo[i] = new MethodParam(this,
                            paramAnnotationInfoAligned == null ? null : paramAnnotationInfoAligned[i],
                            paramModifiersAligned == null ? 0 : paramModifiersAligned[i],
                            paramTypeDescriptorsAligned == null ? null : paramTypeDescriptorsAligned.get(i),
                            paramTypeSignaturesAligned == null ? null : paramTypeSignaturesAligned.get(i),
                            paramNamesAligned == null ? null : paramNamesAligned[i]);
                    parameterInfo[i].setScanResult(scanResult);
                }
            }
            return parameterInfo;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 检查此方法是否有带该注解的参数
     *
     * @param annotation
     *            方法参数注解
     * @return 如果此方法有带该注解的参数则返回 true
     */
    public boolean hasParameterAnnotation(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return hasParameterAnnotation(annotation.getName());
    }

    /**
     * 检查此方法是否有带该命名注解的参数
     *
     * @param annotationName
     *            方法参数注解的名称
     * @return 如果此方法有带该命名注解的参数则返回 true
     */
    public boolean hasParameterAnnotation(final String annotationName) {
        for (final MethodParam MethodParam : getParameterInfo()) {
            if (MethodParam.hasAnnotation(annotationName)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 加载并返回每个方法参数的类
     *
     * @return 每个方法参数的 {@link Class} 引用数组
     */
    private Class<?>[] loadParameterClasses() {
        final MethodParam[] allParameterInfo = getParameterInfo();
        final List<Class<?>> parameterClasses = new ArrayList<>(allParameterInfo.length);
        for (final MethodParam mpi : allParameterInfo) {
            final TypeSignature parameterType = mpi.getTypeSignatureOrTypeDescriptor();
            TypeSignature actualParameterType;
            if (parameterType instanceof TypeVar) {
                final TypeVar tvs = (TypeVar) parameterType;
                final TypeParam t = tvs.resolve();
                if (t.classBound != null) {
                    // 如果可用，使用类型变量的类边界作为具体类型，
                    // 优先于使用第一个接口边界(忽略接口边界，如果存在的话)
                    actualParameterType = t.classBound;
                } else if (t.interfaceBounds != null && !t.interfaceBounds.isEmpty()) {
                    // 使用类型变量的第一个接口边界作为具体类型
                    // (忽略第二个及后续的接口边界，如果存在的话)
                    actualParameterType = t.interfaceBounds.get(0);
                } else {
                    // 健全性检查，不应发生
                    throw new IllegalArgumentException("TypeVar 没有边界");
                }
            } else {
                actualParameterType = parameterType;
            }
            parameterClasses.add(actualParameterType.loadClass());
        }
        return parameterClasses.toArray(new Class<?>[0]);
    }

    /**
     * 加载此方法关联的类，并获取此方法的 {@link Method} 引用仅当 {@link #isConstructor()}
     * 返回 false 时调用此方法，否则将抛出 {@link IllegalArgumentException}
     * 对于构造函数，应调用 {@link #loadClassAndGetConstructor()}
     *
     * @return 此方法的 {@link Method} 引用
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>如果方法的类无法加载</li>
     *             <li>如果方法不存在</li>
     *             <li>如果方法是构造函数</li>
     *             <li>如果方法的某个参数引用了未知类</li>
     *             <li>如果方法的返回类型引用了未知类</li>
     *             </ul>
     */
    public Method loadClassAndGetMethod() throws IllegalArgumentException {
        if (isConstructor()) {
            throw new IllegalArgumentException(
                    "构造函数需要调用 loadClassAndGetConstructor()，而不是 loadClassAndGetMethod()");
        }
        final Class<?>[] parameterClassesArr = loadParameterClasses();
        try {
            return loadClass().getMethod(getName(), parameterClassesArr);
        } catch (final NoSuchMethodException e1) {
            try {
                return loadClass().getDeclaredMethod(getName(), parameterClassesArr);
            } catch (final NoSuchMethodException e2) {
                throw new IllegalArgumentException("方法未找到: " + getClassName() + "." + getName());
            }
        } catch (final NoClassDefFoundError e3) {
            // 方法返回了未知的类
            throw new IllegalArgumentException("无法加载方法: " + getClassName() + "." + getName(), e3);
        }
    }

    /**
     * 加载此构造函数关联的类，并获取此构造函数的 {@link Constructor} 引用仅当
     * {@link #isConstructor()} 返回 true 时调用此方法，否则将抛出
     * {@link IllegalArgumentException}对于非构造函数方法，应调用
     * {@link #loadClassAndGetMethod()}
     *
     * @return 此构造函数的 {@link Constructor} 引用
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>如果方法的类无法加载</li>
     *             <li>如果构造函数不存在</li>
     *             <li>如果方法不是构造函数</li>
     *             <li>如果构造函数的某个参数引用了未知类</li>
     *             </ul>
     */
    public Constructor<?> loadClassAndGetConstructor() throws IllegalArgumentException {
        if (!isConstructor()) {
            throw new IllegalArgumentException(
                    "非构造函数方法需要调用 loadClassAndGetMethod()，而不是 loadClassAndGetConstructor()");
        }
        final Class<?>[] parameterClassesArr = loadParameterClasses();
        try {
            return loadClass().getConstructor(parameterClassesArr);
        } catch (final NoSuchMethodException e1) {
            try {
                return loadClass().getDeclaredConstructor(parameterClassesArr);
            } catch (final NoSuchMethodException e2) {
                throw new IllegalArgumentException("未找到类 " + getClassName() + " 的构造函数");
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 处理 {@link Repeatable} 注解
     *
     * @param allRepeatableAnnotationNames
     *            所有可重复注解的名称集合
     */
    void handleRepeatableAnnotations(final Set<String> allRepeatableAnnotationNames) {
        if (annotationInfo != null) {
            annotationInfo.handleRepeatableAnnotations(allRepeatableAnnotationNames, getClassInfo(),
                    RelType.METHOD_ANNOTATIONS, RelType.CLASSES_WITH_METHOD_ANNOTATION,
                    RelType.CLASSES_WITH_NONPRIVATE_METHOD_ANNOTATION);
        }
        if (parameterAnnotationInfo != null) {
            for (int i = 0; i < parameterAnnotationInfo.length; i++) {
                final AnnotationInfo[] pai = parameterAnnotationInfo[i];
                if (pai != null && pai.length > 0) {
                    boolean hasRepeatableAnnotation = false;
                    for (final AnnotationInfo ai : pai) {
                        if (allRepeatableAnnotationNames.contains(ai.getName())) {
                            hasRepeatableAnnotation = true;
                            break;
                        }
                    }
                    if (hasRepeatableAnnotation) {
                        final AnnotationInfoList aiList = new AnnotationInfoList(pai.length);
                        aiList.addAll(Arrays.asList(pai));
                        aiList.handleRepeatableAnnotations(allRepeatableAnnotationNames, getClassInfo(),
                                RelType.METHOD_PARAMETER_ANNOTATIONS,
                                RelType.CLASSES_WITH_METHOD_PARAMETER_ANNOTATION,
                                RelType.CLASSES_WITH_NONPRIVATE_METHOD_PARAMETER_ANNOTATION);
                        parameterAnnotationInfo[i] = aiList.toArray(new AnnotationInfo[0]);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    public void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (this.typeDescriptor != null) {
            this.typeDescriptor.setScanResult(scanResult);
        }
        if (this.typeSignature != null) {
            this.typeSignature.setScanResult(scanResult);
        }
        if (this.annotationInfo != null) {
            for (final AnnotationInfo ai : this.annotationInfo) {
                ai.setScanResult(scanResult);
            }
        }
        if (this.parameterAnnotationInfo != null) {
            for (final AnnotationInfo[] pai : this.parameterAnnotationInfo) {
                if (pai != null) {
                    for (final AnnotationInfo ai : pai) {
                        ai.setScanResult(scanResult);
                    }
                }
            }
        }
        if (this.parameterInfo != null) {
            for (final MethodParam mpi : parameterInfo) {
                mpi.setScanResult(scanResult);
            }
        }
        if (this.thrownExceptions != null) {
            for (final ClassInfo thrownException : thrownExceptions) {
                if (thrownException.scanResult == null) { // 防止无限循环
                    thrownException.setScanResult(scanResult);
                }
            }
        }
    }

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
        try {
            final MethodType methodSig = getTypeSignature();
            if (methodSig != null) {
                methodSig.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
            }
        } catch (final IllegalArgumentException e) {
            if (log != null) {
                log.log("方法 " + getClassName() + "." + getName() + " 的类型签名非法: "
                        + getTypeSignatureStr());
            }
        }
        try {
            final MethodType methodDesc = getTypeDescriptor();
            if (methodDesc != null) {
                methodDesc.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
            }
        } catch (final IllegalArgumentException e) {
            if (log != null) {
                log.log("方法 " + getClassName() + "." + getName() + " 的类型描述符非法: "
                        + getTypeDescriptorStr());
            }
        }
        if (annotationInfo != null) {
            for (final AnnotationInfo ai : annotationInfo) {
                ai.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
            }
        }
        for (final MethodParam mpi : getParameterInfo()) {
            final AnnotationInfo[] aiArr = mpi.annotationInfo;
            if (aiArr != null) {
                for (final AnnotationInfo ai : aiArr) {
                    ai.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
                }
            }
        }
        if (thrownExceptionNames != null) {
            final ClassInfoList thrownExceptions = getThrownExceptions();
            if (thrownExceptions != null) {
                for (int i = 0; i < thrownExceptions.size(); i++) {
                    classNameToClassInfo.put(thrownExceptionNames[i], thrownExceptions.get(i));
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 使用类名、方法名和类型描述符进行 equals() 比较
     *
     * @param obj
     *            要比较相等的对象
     * @return 如果相等则返回 true
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof MethodInfo)) {
            return false;
        }
        final MethodInfo other = (MethodInfo) obj;
        return declaringClassName.equals(other.declaringClassName)
                && typeDescriptorStr.equals(other.typeDescriptorStr) && name.equals(other.name);
    }

    /**
     * 使用类名、方法名和类型描述符的哈希码
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return name.hashCode() + typeDescriptorStr.hashCode() * 11 + declaringClassName.hashCode() * 57;
    }

    /**
     * 按类名、方法名、然后类型描述符的顺序排序
     *
     * @param other
     *            要比较的另一个 {@link MethodInfo}
     * @return 比较的结果
     */
    @Override
    public int compareTo(final MethodInfo other) {
        final int diff0 = declaringClassName.compareTo(other.declaringClassName);
        if (diff0 != 0) {
            return diff0;
        }
        final int diff1 = name.compareTo(other.name);
        if (diff1 != 0) {
            return diff1;
        }
        return typeDescriptorStr.compareTo(other.typeDescriptorStr);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取方法的字符串表示注意，构造函数的名称为 {@code "<init>"}，私有静态类初始化块的
     * 名称为 {@code "<clinit>"}
     *
     * @param useSimpleNames
     *            是否使用简单名称
     * @param buf
     *            缓冲区
     */
    @Override
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        final MethodType methodType = getTypeSignatureOrTypeDescriptor();

        if (annotationInfo != null) {
            for (final AnnotationInfo annotation : annotationInfo) {
                if (buf.length() > 0) {
                    buf.append(' ');
                }
                annotation.toString(useSimpleNames, buf);
            }
        }

        if (modifiers != 0) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            TypeUtils.modifiersToString(modifiers, ModifierType.METHOD, isDefault(), buf);
        }

        final List<TypeParam> TypeParams = methodType.getTypeParams();
        if (!TypeParams.isEmpty()) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append('<');
            for (int i = 0; i < TypeParams.size(); i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                TypeParams.get(i).toString(useSimpleNames, buf);
            }
            buf.append('>');
        }

        if (!isConstructor()) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            methodType.getResultType().toStringInternal(useSimpleNames, /* annotationsToExclude = */ annotationInfo,
                    buf);
        }

        if (buf.length() > 0) {
            buf.append(' ');
        }
        if (name != null) {
            buf.append(useSimpleNames ? ClassInfo.getSimpleName(name) : name);
        }

        // 如果至少有一个参数被命名，则为未命名的参数使用占位符名称，
        // 否则不为任何参数显示名称
        final MethodParam[] allParamInfo = getParameterInfo();
        boolean hasParamNames = false;
        for (final MethodParam methodParamInfo : allParamInfo) {
            if (methodParamInfo.getName() != null) {
                hasParamNames = true;
                break;
            }
        }

        // 查找可变参数索引(如果存在)—— 对于可变参数方法，这是最后一个不是合成参数
        // 或强制参数的参数(事实证明，对于匿名内部类的变量捕获，Java 编译器可以在
        // 可变参数*之后*添加参数 —— 参见 #260)
        int varArgsParamIndex = -1;
        if (isVarArgs()) {
            for (int i = allParamInfo.length - 1; i >= 0; --i) {
                final int mods = allParamInfo[i].getModifiers();
                if ((mods & /* synthetic */ 0x1000) == 0 && (mods & /* mandated */ 0x8000) == 0) {
                    final TypeSignature paramType = allParamInfo[i].getTypeSignatureOrTypeDescriptor();
                    if (paramType instanceof ArrayType) {
                        varArgsParamIndex = i;
                        break;
                    }
                }
            }
        }

        buf.append('(');
        for (int i = 0, numParams = allParamInfo.length; i < numParams; i++) {
            final MethodParam paramInfo = allParamInfo[i];
            if (i > 0) {
                buf.append(", ");
            }

            if (paramInfo.annotationInfo != null) {
                for (final AnnotationInfo ai : paramInfo.annotationInfo) {
                    ai.toString(useSimpleNames, buf);
                    buf.append(' ');
                }
            }

            MethodParam.modifiersToString(paramInfo.getModifiers(), buf);

            final TypeSignature paramTypeSignature = paramInfo.getTypeSignatureOrTypeDescriptor();
            // 在隐式添加到非泛型方法的 `synthetic`、`bridge` 或 `mandated` 参数的情况下，
            // 参数类型签名可能为 null
            if (paramTypeSignature != null) {
                if (i == varArgsParamIndex) {
                    // 正确显示可变参数 —— 将最后一个 "[]" 替换为 "..."
                    if (!(paramTypeSignature instanceof ArrayType)) {
                        throw new IllegalArgumentException(
                                "可变参数方法 " + name + " 的最后一个参数获得了非数组类型");
                    }
                    final ArrayType arrayType = (ArrayType) paramTypeSignature;
                    if (arrayType.getNumDimensions() == 0) {
                        throw new IllegalArgumentException(
                                "可变参数方法 " + name + " 的最后一个参数获得了零维数组类型");
                    }
                    arrayType.getElementTypeSignature().toString(useSimpleNames, buf);
                    for (int j = 0; j < arrayType.getNumDimensions() - 1; j++) {
                        buf.append("[]");
                    }
                    buf.append("...");
                } else {
                    // 从类型签名顶层的类型注解中排除参数注解，使注解不会被列出两次
                    final AnnotationInfoList annotationsToExclude;
                    if (paramInfo.annotationInfo == null || paramInfo.annotationInfo.length == 0) {
                        annotationsToExclude = null;
                    } else {
                        annotationsToExclude = new AnnotationInfoList(paramInfo.annotationInfo.length);
                        annotationsToExclude.addAll(Arrays.asList(paramInfo.annotationInfo));
                    }
                    paramTypeSignature.toStringInternal(useSimpleNames, annotationsToExclude, buf);
                }
            }

            if (hasParamNames) {
                final String paramName = paramInfo.getName();
                if (paramName != null) {
                    if (buf.charAt(buf.length() - 1) != ' ') {
                        buf.append(' ');
                    }
                    buf.append(paramName);
                }
            }
        }
        buf.append(')');

        // 当 throws 签名存在时，它包括泛型类型变量和类名
        if (!methodType.getThrowsSignatures().isEmpty()) {
            buf.append(" throws ");
            for (int i = 0; i < methodType.getThrowsSignatures().size(); i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                methodType.getThrowsSignatures().get(i).toString(useSimpleNames, buf);
            }
        } else {
            if (thrownExceptionNames != null && thrownExceptionNames.length > 0) {
                buf.append(" throws ");
                for (int i = 0; i < thrownExceptionNames.length; i++) {
                    if (i > 0) {
                        buf.append(", ");
                    }
                    buf.append(useSimpleNames ? ClassInfo.getSimpleName(thrownExceptionNames[i])
                            : thrownExceptionNames[i]);
                }
            }
        }
    }
}
