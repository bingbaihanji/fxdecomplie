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

import com.bingbaihanji.classgraph.reflection.ReflectionUtils;
import com.bingbaihanji.classgraph.utils.LogNode;

import java.lang.annotation.Annotation;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.annotation.Inherited;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** 保存类、方法、方法参数或字段上特定注解实例的元数据 */
public class AnnotationInfo extends ScanResultObject implements Comparable<AnnotationInfo>, HasName {
    /** 名称 */
    private String name;

    /** 注解参数值 */
    private AnnotationParameterValueList annotationParamValues;

    /**
     * 当 annotationParamValues 中任何包装类型的 Object[] 数组被延迟转换为基本类型数组后，设置为 true
     */
    private transient boolean annotationParamValuesHasBeenConvertedToPrimitive;

    /** 带默认值的注解参数值 */
    private transient volatile AnnotationParameterValueList annotationParamValuesWithDefaults;

    /** 用于反序列化的默认构造函数 */
    AnnotationInfo() {
        super();
    }

    /**
     * 为 ClassGraphWorkspaceAdapter 提供的简单构造函数
     *
     * @param name
     *            注解的名称
     */
    public AnnotationInfo(final String name) {
        super();
        this.name = name;
        this.annotationParamValues = new AnnotationParameterValueList();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param name
     *            注解的名称
     * @param annotationParamValues
     *            注解参数值，如果没有则为 null
     */
    AnnotationInfo(final String name, final AnnotationParameterValueList annotationParamValues) {
        super();
        this.name = name;
        this.annotationParamValues = annotationParamValues;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取名称
     *
     * @return 注解类的名称
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 检查注解是否被继承
     *
     * @return 如果此注解被 {@link Inherited} 元注解修饰，则返回 true
     */
    public boolean isInherited() {
        final ClassInfo classInfo = getClassInfo();
        return classInfo != null && classInfo.isInherited;
    }

    /**
     * 获取默认参数值
     *
     * @return 此注解的默认参数值列表，如果没有则返回空列表
     */
    public AnnotationParameterValueList getDefaultParameterValues() {
        return getClassInfo().getAnnotationDefaultParameterValues();
    }

    /**
     * 获取参数值
     *
     * @param includeDefaultValues
     *            如果为 true，则为任何缺失的注解参数值包含默认值
     * @return 此注解的参数值，包括(如果需要的话)从注解类定义继承的默认参数值，如果没有则返回空列表
     */
    public AnnotationParameterValueList getParameterValues(final boolean includeDefaultValues) {
        final ClassInfo classInfo = getClassInfo();
        if (classInfo == null) {
            // ClassInfo 尚未设置，只返回不带默认值的值
            // (在扫描期间尝试记录 AnnotationInfo 时发生，此时 ScanResult 尚不可用)
            return annotationParamValues == null ? AnnotationParameterValueList.EMPTY_LIST : annotationParamValues;
        }
        // 延迟将任何包装类型的 Object[] 数组转换为基本类型数组
        if (annotationParamValues != null && !annotationParamValuesHasBeenConvertedToPrimitive) {
            annotationParamValues.convertWrapperArraysToPrimitiveArrays(classInfo);
            annotationParamValuesHasBeenConvertedToPrimitive = true;
        }
        if (!includeDefaultValues) {
            // 不包含默认值
            return annotationParamValues == null ? AnnotationParameterValueList.EMPTY_LIST : annotationParamValues;
        }
        AnnotationParameterValueList result = annotationParamValuesWithDefaults;
        if (result == null) {
            synchronized (this) {
                result = annotationParamValuesWithDefaults;
                if (result == null) {
                    result = buildParameterValuesWithDefaults(classInfo);
                    annotationParamValuesWithDefaults = result;
                }
            }
        }
        return result;
    }

    /**
     * 构建包含默认值的注解参数值列表(线程不安全，应在同步块内调用)
     *
     * @param classInfo
     *            注解类信息
     * @return 包含默认值的参数值列表
     */
    private AnnotationParameterValueList buildParameterValuesWithDefaults(final ClassInfo classInfo) {
        if (classInfo.annotationDefaultParamValues != null
                && !classInfo.annotationDefaultParamValuesHasBeenConvertedToPrimitive) {
            classInfo.annotationDefaultParamValues.convertWrapperArraysToPrimitiveArrays(classInfo);
            classInfo.annotationDefaultParamValuesHasBeenConvertedToPrimitive = true;
        }

        // 检查默认值和此注解实例中的值是否有一个或两个为 null(空)
        final AnnotationParameterValueList defaultParamValues = classInfo.annotationDefaultParamValues;
        if (defaultParamValues == null && annotationParamValues == null) {
            return AnnotationParameterValueList.EMPTY_LIST;
        } else if (defaultParamValues == null) {
            return annotationParamValues;
        } else if (annotationParamValues == null) {
            return defaultParamValues;
        }

        // 用非默认值覆盖默认值
        final Map<String, Object> allParamValues = new HashMap<>();
        for (final AnnotationParameterValue defaultParamValue : defaultParamValues) {
            allParamValues.put(defaultParamValue.getName(), defaultParamValue.getValue());
        }
        for (final AnnotationParameterValue annotationParamValue : this.annotationParamValues) {
            allParamValues.put(annotationParamValue.getName(), annotationParamValue.getValue());
        }

        // 将注解值按与注解方法相同的顺序排列(每个注解常量对应一个方法)
        if (classInfo.methodInfo == null) {
            // 不应发生(读取 class 文件时，无论 scanSpec.enableMethodInfo 是否为 true，方法总是会被读取)
            throw new IllegalArgumentException("Could not find methods for annotation " + classInfo.getName());
        }
        final AnnotationParameterValueList result = new AnnotationParameterValueList();
        for (final MethodInfo mi : classInfo.methodInfo) {
            final String paramName = mi.getName();
            switch (paramName) {
                // 这些方法名称不应出现在 @interface 类本身中，它应该只包含注解常量的方法
                // (但为了安全起见还是跳过它们)这些方法只应存在于注解的具体实例中
                case "<init>":
                case "<clinit>":
                case "hashCode":
                case "equals":
                case "toString":
                case "annotationType":
                    // 跳过
                    break;
                default:
                    // 注解常量
                    final Object paramValue = allParamValues.get(paramName);
                    // 注解值不能为 null(或从默认值或注解实例中缺失)
                    if (paramValue != null) {
                        result.add(new AnnotationParameterValue(paramName, paramValue));
                    }
                    break;
            }
        }
        return result;
    }

    /**
     * 获取参数值
     *
     * @return 此注解的参数值，包括从注解类定义继承的默认参数值，如果没有则返回空列表
     */
    public AnnotationParameterValueList getParameterValues() {
        return getParameterValues(true);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取注解类的名称，供 {@link #getClassInfo()} 使用
     *
     * @return 类名
     */
    @Override
    protected String getClassName() {
        return name;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (annotationParamValues != null) {
            for (final AnnotationParameterValue a : annotationParamValues) {
                a.setScanResult(scanResult);
            }
        }
    }

    /**
     * 获取类型描述符或类型签名中引用的任何类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            被引用的类信息
     */
    @Override
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        super.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        if (annotationParamValues != null) {
            for (final AnnotationParameterValue annotationParamValue : annotationParamValues) {
                annotationParamValue.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 返回注解类的 {@link ClassInfo} 对象 */
    @Override
    public ClassInfo getClassInfo() {
        return super.getClassInfo();
    }

    /**
     * 通过调用 {@code getClassInfo().loadClass()} 加载与此 {@link AnnotationInfo} 对象对应的 {@link Annotation} 类，
     * 然后创建一个新的注解实例，注解参数值来自此 {@link AnnotationInfo} 对象，可能会覆盖通过调用
     * {@link AnnotationInfo#getClassInfo()} 然后 {@link ClassInfo#getAnnotationDefaultParameterValues()}
     * 获取的默认注解参数值
     *
     * <p>
     * 注意，返回的 {@link Annotation} 将具有某种 {@link InvocationHandler} 代理类型，
     * 例如 {@code com.bingbaihanji.classgraph.core.features.$Proxy4} 或 {@code com.sun.proxy.$Proxy6}
     * 这是具体 {@link Annotation} 实例无法直接实例化这一事实的不可避免的副作用
     * (ClassGraph 使用了<a href=
     * "http://hg.openjdk.java.net/jdk7/jdk7/jdk/file/9b8c96f96a0f/src/share/classes/sun/reflect/annotation/AnnotationParser.java#l255">
     * 与 JDK 从 map 实例化注解相同的方法</a>)然而，代理实例在类型转换和 {@code instanceof}
     * 方面<a href="https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/reflect/Proxy.html">
     * 会被特殊处理</a>：你可以将返回的代理实例强制转换为注解类型，
     * 并且针对注解类的 {@code instanceof} 检查也会成功
     *
     * <p>
     * 当然，获取具体注解的另一种选择(而不是通过此方法实例化 {@link ClassInfo} 对象上的注解)
     * 是调用 {@link ClassInfo#loadClass()}，并直接从返回的 {@link Class} 对象读取注解
     *
     * @return 新的 {@link Annotation} 实例，作为一个可以强制转换为预期注解类型的动态代理对象
     */
    public Annotation loadClassAndInstantiate() {
        final Class<? extends Annotation> annotationClass = getClassInfo().loadClass(Annotation.class);
        return (Annotation) Proxy.newProxyInstance(annotationClass.getClassLoader(),
                new Class<?>[]{annotationClass}, new AnnotationInvocationHandler(annotationClass, this));
    }

    /**
     * 将包装类型数组转换为基本类型数组
     */
    void convertWrapperArraysToPrimitiveArrays() {
        if (annotationParamValues != null) {
            annotationParamValues.convertWrapperArraysToPrimitiveArrays(getClassInfo());
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final AnnotationInfo o) {
        final int diff = this.name.compareTo(o.name);
        if (diff != 0) {
            return diff;
        }
        if (annotationParamValues == null && o.annotationParamValues == null) {
            return 0;
        } else if (annotationParamValues == null) {
            return -1;
        } else if (o.annotationParamValues == null) {
            return 1;
        } else {
            for (int i = 0,
                 max = Math.max(annotationParamValues.size(), o.annotationParamValues.size()); i < max; i++) {
                if (i >= annotationParamValues.size()) {
                    return -1;
                } else if (i >= o.annotationParamValues.size()) {
                    return 1;
                } else {
                    final int diff2 = annotationParamValues.get(i).compareTo(o.annotationParamValues.get(i));
                    if (diff2 != 0) {
                        return diff2;
                    }
                }
            }
        }
        return 0;
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof AnnotationInfo)) {
            return false;
        }
        final AnnotationInfo other = (AnnotationInfo) obj;
        return this.compareTo(other) == 0;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int h = name.hashCode();
        if (annotationParamValues != null) {
            for (final AnnotationParameterValue e : annotationParamValues) {
                h = h * 7 + e.getName().hashCode() * 3 + e.getValue().hashCode();
            }
        }
        return h;
    }

    @Override
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        buf.append('@').append(useSimpleNames ? ClassInfo.getSimpleName(name) : name);
        final AnnotationParameterValueList paramVals = getParameterValues();
        if (!paramVals.isEmpty()) {
            buf.append('(');
            for (int i = 0; i < paramVals.size(); i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                final AnnotationParameterValue paramVal = paramVals.get(i);
                if (paramVals.size() > 1 || !"value".equals(paramVal.getName())) {
                    paramVal.toString(useSimpleNames, buf);
                } else {
                    paramVal.toStringParamValueOnly(useSimpleNames, buf);
                }
            }
            buf.append(')');
        }
    }

    /** 用于动态实例化 {@link Annotation} 对象的 {@link InvocationHandler} */
    private static class AnnotationInvocationHandler implements InvocationHandler {

        /** 注解类 */
        private final Class<? extends Annotation> annotationClass;

        /** 此注解的 {@link AnnotationInfo} 对象 */
        private final AnnotationInfo annotationInfo;

        /** 已实例化的注解参数值 */
        private final Map<String, Object> annotationParameterValuesInstantiated = new HashMap<>();

        /**
         * 构造函数
         *
         * @param annotationClass
         *            注解类
         * @param annotationInfo
         *            注解信息
         */
        AnnotationInvocationHandler(final Class<? extends Annotation> annotationClass,
                                    final AnnotationInfo annotationInfo) {
            this.annotationClass = annotationClass;
            this.annotationInfo = annotationInfo;

            // 实例化注解参数值(这会加载并获取类字面量、枚举常量等的引用)
            for (final AnnotationParameterValue apv : annotationInfo.getParameterValues()) {
                final Object instantiatedValue = apv.instantiate(annotationInfo.getClassInfo());
                if (instantiatedValue == null) {
                    // 注解不能包含 null 值
                    throw new IllegalArgumentException("Got null value for annotation parameter " + apv.getName()
                            + " of annotation " + annotationInfo.name);
                }
                this.annotationParameterValuesInstantiated.put(apv.getName(), instantiatedValue);
            }
        }

        /* (non-Javadoc)
         * @see java.lang.reflect.InvocationHandler#invoke(java.lang.Object, java.lang.reflect.Method,
         * java.lang.Object[])
         */
        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) {
            final String methodName = method.getName();
            final Class<?>[] paramTypes = method.getParameterTypes();
            if ((args == null ? 0 : args.length) != paramTypes.length) {
                throw new IllegalArgumentException(
                        "Wrong number of arguments for " + annotationClass.getName() + "." + methodName + ": got "
                                + (args == null ? 0 : args.length) + ", expected " + paramTypes.length);
            }
            if (args != null && paramTypes.length == 1) {
                if ("equals".equals(methodName) && paramTypes[0] == Object.class) {
                    // equals() 需要与 JDK 实现行为一致
                    // (参见 JDK 中的 src/share/classes/sun/reflect/annotation/AnnotationInvocationHandler.java)
                    if (this == args[0]) {
                        return true;
                    } else if (!annotationClass.isInstance(args[0])) {
                        return false;
                    }
                    final ReflectionUtils reflectionUtils = annotationInfo.scanResult == null
                            ? new ReflectionUtils()
                            : annotationInfo.scanResult.reflectionUtils;
                    for (final Entry<String, Object> ent : annotationParameterValuesInstantiated.entrySet()) {
                        final String paramName = ent.getKey();
                        final Object paramVal = ent.getValue();
                        final Object otherParamVal = reflectionUtils.invokeMethod(/* throwException = */ false,
                                args[0], paramName);
                        if ((paramVal == null) != (otherParamVal == null)) {
                            // 注解值不应为 null，但为了安全起见
                            return false;
                        } else if (paramVal == null && otherParamVal == null) {
                            return true;
                        } else if (paramVal == null || !paramVal.equals(otherParamVal)) {
                            return false;
                        }
                    }
                    return true;
                } else {
                    // .equals(Object) 是枚举中唯一可以接受一个参数的方法
                    throw new IllegalArgumentException();
                }
            } else if (paramTypes.length == 0) {
                // 处理 .toString()、.hashCode()、.annotationType()
                switch (methodName) {
                    case "toString":
                        return annotationInfo.toString();
                    case "hashCode": {
                        // hashCode() 需要与 JDK 实现行为一致
                        // (参见 JDK 中的 src/share/classes/sun/reflect/annotation/AnnotationInvocationHandler.java)
                        int result = 0;
                        for (final Entry<String, Object> ent : annotationParameterValuesInstantiated.entrySet()) {
                            final String paramName = ent.getKey();
                            final Object paramVal = ent.getValue();
                            int paramValHashCode;
                            if (paramVal == null) {
                                // 注解值不应为 null，但为了安全起见
                                paramValHashCode = 0;
                            } else {
                                final Class<?> type = paramVal.getClass();
                                if (!type.isArray()) {
                                    paramValHashCode = paramVal.hashCode();
                                } else if (type == byte[].class) {
                                    paramValHashCode = Arrays.hashCode((byte[]) paramVal);
                                } else if (type == char[].class) {
                                    paramValHashCode = Arrays.hashCode((char[]) paramVal);
                                } else if (type == double[].class) {
                                    paramValHashCode = Arrays.hashCode((double[]) paramVal);
                                } else if (type == float[].class) {
                                    paramValHashCode = Arrays.hashCode((float[]) paramVal);
                                } else if (type == int[].class) {
                                    paramValHashCode = Arrays.hashCode((int[]) paramVal);
                                } else if (type == long[].class) {
                                    paramValHashCode = Arrays.hashCode((long[]) paramVal);
                                } else if (type == short[].class) {
                                    paramValHashCode = Arrays.hashCode((short[]) paramVal);
                                } else if (type == boolean[].class) {
                                    paramValHashCode = Arrays.hashCode((boolean[]) paramVal);
                                } else {
                                    paramValHashCode = Arrays.hashCode((Object[]) paramVal);
                                }
                            }
                            result += (127 * paramName.hashCode()) ^ paramValHashCode;
                        }
                        return result;
                    }
                    case "annotationType":
                        return annotationClass;
                    default:
                        // 继续向下执行(其他方法名用于返回注解参数值)
                        break;
                }
            } else {
                // 对 2 个或更多参数抛出异常
                throw new IllegalArgumentException();
            }

            // 实例化注解参数值(这会加载并获取类字面量、枚举常量等的引用)
            final Object annotationParameterValue = annotationParameterValuesInstantiated.get(methodName);
            if (annotationParameterValue == null) {
                // 未定义的枚举常量(枚举值不能为 null)
                throw new IncompleteAnnotationException(annotationClass, methodName);
            }

            // 克隆任何数组类型的注解参数值，以符合 Java 注解 API 的规范
            final Class<?> annotationParameterValueClass = annotationParameterValue.getClass();
            if (annotationParameterValueClass.isArray()) {
                // 处理数组类型
                if (annotationParameterValueClass == String[].class) {
                    return ((String[]) annotationParameterValue).clone();
                } else if (annotationParameterValueClass == byte[].class) {
                    return ((byte[]) annotationParameterValue).clone();
                } else if (annotationParameterValueClass == char[].class) {
                    return ((char[]) annotationParameterValue).clone();
                } else if (annotationParameterValueClass == double[].class) {
                    return ((double[]) annotationParameterValue).clone();
                } else if (annotationParameterValueClass == float[].class) {
                    return ((float[]) annotationParameterValue).clone();
                } else if (annotationParameterValueClass == int[].class) {
                    return ((int[]) annotationParameterValue).clone();
                } else if (annotationParameterValueClass == long[].class) {
                    return ((long[]) annotationParameterValue).clone();
                } else if (annotationParameterValueClass == short[].class) {
                    return ((short[]) annotationParameterValue).clone();
                } else if (annotationParameterValueClass == boolean[].class) {
                    return ((boolean[]) annotationParameterValue).clone();
                } else {
                    // 处理嵌套注解类型的数组
                    final Object[] arr = (Object[]) annotationParameterValue;
                    return arr.clone();
                }
            }
            return annotationParameterValue;
        }
    }
}
