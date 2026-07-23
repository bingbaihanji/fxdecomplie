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
package com.bingbaihanji.classgraph.type;

import com.bingbaihanji.classgraph.core.ClassFile.TypePathNode;
import com.bingbaihanji.classgraph.type.ParseException;
import com.bingbaihanji.classgraph.type.TypeParser;
import com.bingbaihanji.classgraph.type.TypeUtils;

import java.util.*;

/** 类引用类型签名(在 classfile 文档中称为"ClassType") */
public final class ClassRef extends ClassRefOrTypeVar {
    /** 类名 */
    final String className;

    /** 类类型参数 */
    private final List<TypeArg> TypeArgs;

    /** 类型后缀 */
    private final List<String> suffixes;

    /** 后缀类型参数 */
    private final List<List<TypeArg>> suffixTypeArgs;

    /** 后缀类型注解 */
    private List<AnnotationInfoList> suffixTypeAnnotations;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param className
     *            类名
     * @param TypeArgs
     *            类类型参数
     * @param suffixes
     *            类后缀(用于内部类)
     * @param suffixTypeArgs
     *            后缀类型参数
     */
    private ClassRef(final String className, final List<TypeArg> TypeArgs,
                                  final List<String> suffixes, final List<List<TypeArg>> suffixTypeArgs) {
        super();
        this.className = className;
        this.TypeArgs = TypeArgs;
        this.suffixes = suffixes;
        this.suffixTypeArgs = suffixTypeArgs;
    }

    // -------------------------------------------------------------------------------------------------------------

    private static boolean suffixesMatch(final ClassRef a, final ClassRef b) {
        return a.suffixes.equals(b.suffixes) //
                && a.suffixTypeArgs.equals(b.suffixTypeArgs) //
                && Objects.equals(a.suffixTypeAnnotations, b.suffixTypeAnnotations);
    }

    /**
     * 解析类类型签名
     *
     * @param TypeParser
     *            解析器
     * @param definingClassName
     *            定义类的名称(用于解析类型变量)
     * @return 类类型签名
     * @throws ParseException
     *             如果类型签名无法解析
     */
    static ClassRef parse(final TypeParser TypeParser, final String definingClassName) throws ParseException {
        if (TypeParser.peek() == 'L') {
            TypeParser.next();
            final int startParserPosition = TypeParser.getPosition();
            if (!TypeUtils.getIdentifierToken(TypeParser, /* stopAtDollarSign = */ true, /* stopAtDot = */ true)) {
                throw new ParseException(TypeParser, "Could not parse identifier token");
            }
            String className = TypeParser.currToken();
            final List<TypeArg> TypeArgs = TypeArg.parseList(TypeParser, definingClassName);
            List<String> suffixes;
            List<List<TypeArg>> suffixTypeArgs;
            boolean dropSuffixes = false;
            if (TypeParser.peek() == '.' || TypeParser.peek() == '$') {
                suffixes = new ArrayList<>();
                suffixTypeArgs = new ArrayList<>();
                while (TypeParser.peek() == '.' || TypeParser.peek() == '$') {
                    TypeParser.advance(1);
                    if (!TypeUtils.getIdentifierToken(TypeParser, /* stopAtDollarSign = */ true,
                            /* stopAtDot = */ true)) {
                        // 获取到了 '$' 之后的下一个 token 为空字符串，即找到了一个空后缀
                        suffixes.add("");
                        suffixTypeArgs.add(Collections.<TypeArg>emptyList());
                        dropSuffixes = true;
                    } else {
                        suffixes.add(TypeParser.currToken());
                        suffixTypeArgs.add(TypeArg.parseList(TypeParser, definingClassName));
                    }
                }
                if (dropSuffixes) {
                    // 获取到了空后缀 -- 要么是 "$$"，要么是类名以 '$' 结尾(Scala 会这样用)
                    // 在这种情况下，将整个类引用作为不带后缀的单个类名处理
                    className = TypeParser.getSubstring(startParserPosition, TypeParser.getPosition()).replace('/', '.');
                    suffixes = Collections.emptyList();
                    suffixTypeArgs = Collections.emptyList();
                }
            } else {
                suffixes = Collections.emptyList();
                suffixTypeArgs = Collections.emptyList();
            }
            TypeParser.expect(';');
            return new ClassRef(className, TypeArgs, suffixes, suffixTypeArgs);
        } else {
            return null;
        }
    }

    /**
     * 获取类名，不带任何后缀
     *
     * @see #getFullyQualifiedClassName()
     * @return 类名
     */
    public String getBaseClassName() {
        return className;
    }

    /**
     * 获取类名，由基名和后缀组成(后缀用于内部类嵌套，以 '$' 分隔)，但不包含任何类型参数
     * 例如，{@code "xyz.Cls<String>.InnerCls<Integer>"} 返回 {@code "xyz.Cls$InnerCls"}
     * 此方法的意图是：如果将 '.' 替换为 '/'，然后加上后缀 ".class"，就能得到相对于包根目录的 classfile 路径
     *
     * <p>
     * 对比而言，{@link #toString()} 使用 '.' 分隔后缀，并包含类型参数；而此方法使用 '$' 分隔后缀，不包含类型参数
     *
     * @return 类的完全限定名，包含后缀但不含类型参数
     */
    public String getFullyQualifiedClassName() {
        if (suffixes.isEmpty()) {
            return className;
        } else {
            final StringBuilder buf = new StringBuilder();
            buf.append(className);
            for (final String suffix : suffixes) {
                buf.append('$');
                buf.append(suffix);
            }
            return buf.toString();
        }
    }

    /**
     * 获取基类的类型参数
     *
     * @return 基类的类型参数
     */
    public List<TypeArg> getTypeArgs() {
        return TypeArgs;
    }

    /**
     * 获取类的所有嵌套后缀(通常为嵌套内部类名称)
     *
     * @return 类后缀(用于内部类)，如果无则返回空列表
     */
    public List<String> getSuffixes() {
        return suffixes;
    }

    /**
     * 获取类所有嵌套后缀的类型参数列表，每个后缀对应一个列表
     *
     * @return 后缀(嵌套内部类)的类型参数列表，每个后缀一个列表，如果无则返回空列表
     */
    public List<List<TypeArg>> getSuffixTypeArgs() {
        return suffixTypeArgs;
    }

    /**
     * 获取类所有嵌套后缀的类型注解列表的列表，每个后缀对应一个列表
     *
     * @return 后缀(嵌套内部类)的类型注解列表的列表，每个后缀一个列表，如果无则返回 null
     */
    public List<AnnotationInfoList> getSuffixTypeAnnotationInfo() {
        return suffixTypeAnnotations;
    }

    // -------------------------------------------------------------------------------------------------------------

    private void addSuffixTypeAnnotation(final int suffixIdx, final AnnotationInfo annotationInfo) {
        if (suffixTypeAnnotations == null) {
            suffixTypeAnnotations = new ArrayList<>(suffixes.size());
            for (int i = 0; i < suffixes.size(); i++) {
                suffixTypeAnnotations.add(new AnnotationInfoList(1));
            }
        }
        suffixTypeAnnotations.get(suffixIdx).add(annotationInfo);
    }

    @Override
    protected void addTypeAnnotation(final List<TypePathNode> typePath, final AnnotationInfo annotationInfo) {
        // 计算需要向下下降多少层嵌套
        int numDeeperNestedLevels = 0;
        int nextTypeArgIdx = -1;
        for (final TypePathNode typePathNode : typePath) {
            if (typePathNode.typePathKind == 1) {
                // 注解在更深层的嵌套类型中
                // (可以迭代处理)
                numDeeperNestedLevels++;
            } else if (typePathNode.typePathKind == 3) {
                // 注解在参数化类型的类型参数上
                // (需要递归处理)
                nextTypeArgIdx = typePathNode.TypeArgIdx;
                break;
            } else {
                // 这里无效：
                // 0 => 注解在更深层的数组类型中
                // 2 => 注解在参数化类型的通配符类型参数边界上
                throw new IllegalArgumentException("Bad typePathKind: " + typePathNode.typePathKind);
            }
        }

        // 确定是索引基类型还是后缀，跳过非嵌套的类对
        int suffixIdx = -1;
        int nestingLevel = -1;
        String typePrefix = className;
        for (; ; ) {
            boolean skipSuffix;
            if (suffixIdx >= suffixes.size()) {
                throw new IllegalArgumentException("Ran out of nested types while trying to add type annotation");
            } else if (suffixIdx == suffixes.size() - 1) {
                // 右侧没有后缀，因此右侧后缀不可能是静态的，无需跳过此后缀
                skipSuffix = false;
            } else {
                // 对于后缀路径 X.Y，如果 Y 是静态的，则类不是嵌套的
                final ClassInfo outerClassInfo = scanResult.getClassInfo(typePrefix);
                typePrefix = typePrefix + '$' + suffixes.get(suffixIdx + 1);
                final ClassInfo innerClassInfo = scanResult.getClassInfo(typePrefix);
                skipSuffix = outerClassInfo == null || innerClassInfo == null
                        || outerClassInfo.isInterfaceOrAnnotation() //
                        || innerClassInfo.isInterfaceOrAnnotation() //
                        || innerClassInfo.isStatic() //
                        || !outerClassInfo.getInnerClasses().contains(innerClassInfo);
            }
            if (!skipSuffix) {
                // 找到了嵌套类
                nestingLevel++;
                if (nestingLevel >= numDeeperNestedLevels) {
                    break;
                }
            }
            suffixIdx++;
        }

        if (nextTypeArgIdx == -1) {
            // 到达路径末尾 -- 添加类型注解
            if (suffixIdx == -1) {
                // 将类型注解添加到基类型
                addTypeAnnotation(annotationInfo);
            } else {
                // 将类型注解添加到后缀类型
                addSuffixTypeAnnotation(suffixIdx, annotationInfo);
            }
        } else {
            final List<TypeArg> TypeArgList = suffixIdx == -1 ? TypeArgs
                    : suffixTypeArgs.get(suffixIdx);
            // 对于类型描述符(而非类型签名)，TypeArgs 是空列表，因此需要对 nextTypeArgIdx 进行边界检查
            if (nextTypeArgIdx < TypeArgList.size()) {
                // type_path_kind == 3 可以被 type_path_kind == 2 跟随，用于嵌套类型边界上的注解，
                // 这需要对类型路径的剩余部分进行递归处理
                final List<TypePathNode> remainingTypePath = typePath.subList(numDeeperNestedLevels + 1,
                        typePath.size());
                // 将类型注解添加到类型参数
                TypeArgList.get(nextTypeArgIdx).addTypeAnnotation(remainingTypePath, annotationInfo);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 如果尚未加载，则加载引用的类，返回被引用类的 {@code Class<?>} 引用
     * (由 {@link AnnotationClassRef#loadClass()} 调用)
     *
     * @param ignoreExceptions
     *            如果为 true，则忽略异常，在无法加载类时返回 null
     * @return 被引用类的 {@code Class<?>} 引用
     * @throws IllegalArgumentException
     *             如果类无法加载且 ignoreExceptions 为 false
     */
    @Override
    public Class<?> loadClass(final boolean ignoreExceptions) {
        return super.loadClass(ignoreExceptions);
    }

    /**
     * 如果尚未加载，则加载引用的类，返回被引用类的 {@code Class<?>} 引用
     * (由 {@link AnnotationClassRef#loadClass()} 调用)
     *
     * @return 被引用类的 {@code Class<?>} 引用
     * @throws IllegalArgumentException
     *             如果类无法加载
     */
    @Override
    public Class<?> loadClass() {
        return super.loadClass();
    }

    /** @return 用于类加载的完全限定类名 */
    @Override
    protected String getClassName() {
        return getFullyQualifiedClassName();
    }

    /**
     * 获取被引用类的 {@link ClassInfo} 对象
     *
     * @return 被引用类的 {@link ClassInfo} 对象，如果在扫描期间未遇到被引用类(即扫描期间未为该类创建 ClassInfo 对象)则返回 null
     *         注意：即使此方法返回 null，{@link #loadClass()} 仍可能通过名称加载被引用类
     */
    @Override
    public ClassInfo getClassInfo() {
        return super.getClassInfo();
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        for (final TypeArg TypeArg : TypeArgs) {
            TypeArg.setScanResult(scanResult);
        }
        for (final List<TypeArg> TypeArgList : suffixTypeArgs) {
            for (final TypeArg TypeArg : TypeArgList) {
                TypeArg.setScanResult(scanResult);
            }
        }
    }

    /**
     * 获取类型签名中引用的所有类名
     *
     * @param refdClassNames
     *            被引用的类名集合
     */
    @Override
    protected void findReferencedClassNames(final Set<String> refdClassNames) {
        refdClassNames.add(getFullyQualifiedClassName());
        for (final TypeArg TypeArg : TypeArgs) {
            TypeArg.findReferencedClassNames(refdClassNames);
        }
        for (final List<TypeArg> TypeArgList : suffixTypeArgs) {
            for (final TypeArg TypeArg : TypeArgList) {
                TypeArg.findReferencedClassNames(refdClassNames);
            }
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return className.hashCode() + 7 * TypeArgs.hashCode() + 15 * suffixTypeArgs.hashCode()
                + 31 * (typeAnnotationInfo == null ? 0 : typeAnnotationInfo.hashCode())
                + 64 * (suffixTypeAnnotations == null ? 0 : suffixTypeAnnotations.hashCode());
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof ClassRef)) {
            return false;
        }
        final ClassRef o = (ClassRef) obj;
        return o.className.equals(this.className) && o.TypeArgs.equals(this.TypeArgs)
                && Objects.equals(this.typeAnnotationInfo, o.typeAnnotationInfo) && suffixesMatch(o, this);
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.TypeSignature#equalsIgnoringTypeParams(com.bingbaihanji.classgraph.core.TypeSignature)
     */
    @Override
    public boolean equalsIgnoringTypeParams(final TypeSignature other) {
        if (other instanceof TypeVar) {
            // 将类类型签名与类型变量进行比较 -- 此逻辑在 TypeVar 中实现，此处不重复
            return other.equalsIgnoringTypeParams(this);
        }
        if (!(other instanceof ClassRef)) {
            return false;
        }
        final ClassRef o = (ClassRef) other;
        return o.className.equals(this.className) && Objects.equals(this.typeAnnotationInfo, o.typeAnnotationInfo)
                && suffixesMatch(o, this);
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    protected void toStringInternal(final boolean useSimpleNames, final AnnotationInfoList annotationsToExclude,
                                    final StringBuilder buf) {
        // 仅在不使用简单名称或没有后缀时渲染基类
        if (!useSimpleNames || suffixes.isEmpty()) {
            // 追加类型注解
            if (typeAnnotationInfo != null) {
                for (final AnnotationInfo annotationInfo : typeAnnotationInfo) {
                    if (annotationsToExclude == null || !annotationsToExclude.contains(annotationInfo)) {
                        annotationInfo.toString(useSimpleNames, buf);
                        buf.append(' ');
                    }
                }
            }
            // 追加基类名
            buf.append(useSimpleNames ? ClassInfo.getSimpleName(className) : className);
            // 追加基类类型参数
            if (!TypeArgs.isEmpty()) {
                buf.append('<');
                for (int i = 0; i < TypeArgs.size(); i++) {
                    if (i > 0) {
                        buf.append(", ");
                    }
                    TypeArgs.get(i).toString(useSimpleNames, buf);
                }
                buf.append('>');
            }
        }

        // 追加后缀
        if (!suffixes.isEmpty()) {
            for (int i = useSimpleNames ? suffixes.size() - 1 : 0; i < suffixes.size(); i++) {
                if (!useSimpleNames) {
                    // 使用 '$' 而非 '.' 作为后缀分隔符，因为这是 Class.getName() 的行为
                    buf.append('$');
                }
                final AnnotationInfoList typeAnnotations = suffixTypeAnnotations == null ? null
                        : suffixTypeAnnotations.get(i);
                // 追加此后缀的类型注解
                if (typeAnnotations != null && !typeAnnotations.isEmpty()) {
                    for (final AnnotationInfo annotationInfo : typeAnnotations) {
                        annotationInfo.toString(useSimpleNames, buf);
                        buf.append(' ');
                    }
                }
                // 追加后缀名
                buf.append(suffixes.get(i));
                // 追加后缀类型参数
                final List<TypeArg> suffixTypeArgsList = suffixTypeArgs.get(i);
                if (!suffixTypeArgsList.isEmpty()) {
                    buf.append('<');
                    for (int j = 0; j < suffixTypeArgsList.size(); j++) {
                        if (j > 0) {
                            buf.append(", ");
                        }
                        suffixTypeArgsList.get(j).toString(useSimpleNames, buf);
                    }
                    buf.append('>');
                }
            }
        }
    }
}
