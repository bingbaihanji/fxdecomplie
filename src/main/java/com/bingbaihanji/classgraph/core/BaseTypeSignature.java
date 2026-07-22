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
import com.bingbaihanji.classgraph.types.Parser;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 基本类型(byte、char、double、float、int、long、short、boolean 或 void)的类型签名 */
public class BaseTypeSignature extends TypeSignature {
    /** 用于表示基本类型的类型签名字符 */
    private final char typeSignatureChar;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     */
    BaseTypeSignature(final char typeSignatureChar) {
        super();
        switch (typeSignatureChar) {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 'V':
                this.typeSignatureChar = typeSignatureChar;
                break;
            default:
                throw new IllegalArgumentException(
                        "Illegal " + BaseTypeSignature.class.getSimpleName() + " type: '" + typeSignatureChar + "'");
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 以字符串形式获取类型名称
     *
     * @param typeChar
     *            类型字符，例如 'I'
     * @return 类型名称，例如 "int"，如果没有匹配则返回 null
     */
    static String getTypeStr(final char typeChar) {
        switch (typeChar) {
            case 'B':
                return "byte";
            case 'C':
                return "char";
            case 'D':
                return "double";
            case 'F':
                return "float";
            case 'I':
                return "int";
            case 'J':
                return "long";
            case 'S':
                return "short";
            case 'Z':
                return "boolean";
            case 'V':
                return "void";
            default:
                return null;
        }
    }

    /**
     * 以字符串形式获取类型名称
     *
     * @param typeStr
     *            类型字符，例如 "int"
     * @return 类型字符，例如 'I'，如果没有匹配则返回 '\0'
     */
    static char getTypeChar(final String typeStr) {
        switch (typeStr) {
            case "byte":
                return 'B';
            case "char":
                return 'C';
            case "double":
                return 'D';
            case "float":
                return 'F';
            case "int":
                return 'I';
            case "long":
                return 'J';
            case "short":
                return 'S';
            case "boolean":
                return 'Z';
            case "void":
                return 'V';
            default:
                return '\0';
        }
    }

    /**
     * 根据类型字符获取对应的类型
     *
     * @param typeChar
     *            类型字符，例如 'I'
     * @return 类型类，例如 int.class，如果没有匹配则返回 null
     */
    static Class<?> getType(final char typeChar) {
        switch (typeChar) {
            case 'B':
                return byte.class;
            case 'C':
                return char.class;
            case 'D':
                return double.class;
            case 'F':
                return float.class;
            case 'I':
                return int.class;
            case 'J':
                return long.class;
            case 'S':
                return short.class;
            case 'Z':
                return boolean.class;
            case 'V':
                return void.class;
            default:
                return null;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 解析基本类型
     *
     * @param parser
     *            解析器
     * @return 基本类型签名
     */
    static BaseTypeSignature parse(final Parser parser) {
        switch (parser.peek()) {
            case 'B':
                parser.next();
                return new BaseTypeSignature('B');
            case 'C':
                parser.next();
                return new BaseTypeSignature('C');
            case 'D':
                parser.next();
                return new BaseTypeSignature('D');
            case 'F':
                parser.next();
                return new BaseTypeSignature('F');
            case 'I':
                parser.next();
                return new BaseTypeSignature('I');
            case 'J':
                parser.next();
                return new BaseTypeSignature('J');
            case 'S':
                parser.next();
                return new BaseTypeSignature('S');
            case 'Z':
                parser.next();
                return new BaseTypeSignature('Z');
            case 'V':
                parser.next();
                return new BaseTypeSignature('V');
            default:
                return null;
        }
    }

    /**
     * 获取用于表示类型的类型签名字符，例如 'I' 表示 int
     *
     * @return 类型签名字符，作为一个单字符 String
     */
    public char getTypeSignatureChar() {
        return typeSignatureChar;
    }

    /**
     * 以字符串形式获取类型名称
     *
     * @return 类型名称，例如 "int"、"float" 或 "void"
     */
    public String getTypeStr() {
        return getTypeStr(typeSignatureChar);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取类型
     *
     * @return 基本类型的类，例如 int.class、float.class 或 void.class
     */
    public Class<?> getType() {
        return getType(typeSignatureChar);
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    protected void addTypeAnnotation(final List<TypePathNode> typePath, final AnnotationInfo annotationInfo) {
        addTypeAnnotation(annotationInfo);
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#loadClass()
     */
    @Override
    Class<?> loadClass() {
        return getType();
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#loadClass(java.lang.Class)
     */
    @Override
    <T> Class<T> loadClass(final Class<T> superclassOrInterfaceType) {
        final Class<?> type = getType();
        if (!superclassOrInterfaceType.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Primitive class " + getTypeStr() + " cannot be cast to "
                    + superclassOrInterfaceType.getName());
        }
        @SuppressWarnings("unchecked") final Class<T> classT = (Class<T>) type;
        return classT;
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#getClassName()
     */
    @Override
    protected String getClassName() {
        return getTypeStr();
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#getClassInfo()
     */
    @Override
    protected ClassInfo getClassInfo() {
        return null;
    }

    /**
     * 获取类型签名中引用的所有类名称
     *
     * @param refdClassNames
     *            被引用的类名称集合
     */
    @Override
    protected void findReferencedClassNames(final Set<String> refdClassNames) {
        // 不添加 byte.class、int.class 等
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#setScanResult(ScanResult)
     */
    @Override
    void setScanResult(final ScanResult scanResult) {
        // 不为 BaseTypeSignature 对象设置 ScanResult(#419)
        // 不需要 ScanResult，因为此类不通过 ScanResult 进行类加载
        // 此外，每个基本类型的 BaseTypeSignature 特定实例被赋值给此类中的静态字段，
        // 这些字段在此类的所有使用中共享，因此不应包含任何特定于某个 ScanResult 的值
        // 从不同的扫描过程设置 ScanResult 将导致 scanResult 字段仅反映最近一次扫描的结果，
        // 并且对该扫描的引用将阻止垃圾回收
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return typeSignatureChar;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof BaseTypeSignature)) {
            return false;
        }
        final BaseTypeSignature other = (BaseTypeSignature) obj;
        return Objects.equals(this.typeAnnotationInfo, other.typeAnnotationInfo)
                && other.typeSignatureChar == this.typeSignatureChar;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.TypeSignature#equalsIgnoringTypeParams(com.bingbaihanji.classgraph.core.TypeSignature)
     */
    @Override
    public boolean equalsIgnoringTypeParams(final TypeSignature other) {
        if (!(other instanceof BaseTypeSignature)) {
            return false;
        }
        return typeSignatureChar == ((BaseTypeSignature) other).typeSignatureChar;
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    protected void toStringInternal(final boolean useSimpleNames, final AnnotationInfoList annotationsToExclude,
                                    final StringBuilder buf) {
        if (typeAnnotationInfo != null) {
            for (final AnnotationInfo annotationInfo : typeAnnotationInfo) {
                if (annotationsToExclude == null || !annotationsToExclude.contains(annotationInfo)) {
                    annotationInfo.toString(useSimpleNames, buf);
                    buf.append(' ');
                }
            }
        }
        buf.append(getTypeStr());
    }
}