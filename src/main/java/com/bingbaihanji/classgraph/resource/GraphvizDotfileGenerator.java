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
package com.bingbaihanji.classgraph.resource;

import com.bingbaihanji.classgraph.metadata.*;
import com.bingbaihanji.classgraph.scan.ClassGraph;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.type.TypeSignature;
import com.bingbaihanji.classgraph.util.CollectionUtils;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

/** 以 Graphviz .dot 文件格式构建类图可视化 */
public final class GraphvizDotfileGenerator {
    /** 标准类的颜色 */
    private static final String STANDARD_CLASS_COLOR = "fff2b6";

    /** 接口的颜色 */
    private static final String INTERFACE_COLOR = "b6e7ff";

    /** 注解的颜色 */
    private static final String ANNOTATION_COLOR = "f3c9ff";

    /** 方法参数的换行宽度 */
    private static final int PARAM_WRAP_WIDTH = 40;

    /** 哪些字符是 Unicode 空白字符 */
    private static final BitSet IS_UNICODE_WHITESPACE = new BitSet(1 << 16);

    static {
        // 有效的 Unicode 空白字符，参见：
        // http://stackoverflow.com/questions/4731055/whitespace-matching-regex-java
        // 另见(关于 \n 和 \r -- 一个 Java 愚蠢行为的真实例子)：
        // https://stackoverflow.com/a/3866219/3950982
        final String wsChars = " " // 空格 (SPACE)
                + "	" // 字符制表符 (CHARACTER TABULATION)
                + "\n" // 换行符 (LINE FEED, LF)
                + "" // 行制表符 (LINE TABULATION)
                + "" // 换页符 (FORM FEED, FF)
                + "\r" // 回车符 (CARRIAGE RETURN, CR)
                + "" // 下一行 (NEXT LINE, NEL)
                + " " // 不换行空格 (NO-BREAK SPACE)
                + " " // 欧甘文空格标记 (OGHAM SPACE MARK)
                + "᠎" // 蒙古文元音分隔符 (MONGOLIAN VOWEL SEPARATOR)
                + " " // 半身空铅 (EN QUAD)
                + " " // 全身空铅 (EM QUAD)
                + " " // 半身空格 (EN SPACE)
                + " " // 全身空格 (EM SPACE)
                + " " // 三分之一全身空格 (THREE-PER-EM SPACE)
                + " " // 四分之一全身空格 (FOUR-PER-EM SPACE)
                + " " // 六分之一全身空格 (SIX-PER-EM SPACE)
                + " " // 数字空格 (FIGURE SPACE)
                + " " // 标点空格 (PUNCTUATION SPACE)
                + " " // 细空格 (THIN SPACE)
                + " " // 极细空格 (HAIR SPACE)
                + " " // 行分隔符 (LINE SEPARATOR)
                + " " // 段落分隔符 (PARAGRAPH SEPARATOR)
                + " " // 窄不换行空格 (NARROW NO-BREAK SPACE)
                + " " // 中等数学空格 (MEDIUM MATHEMATICAL SPACE)
                + "　"; // 表意空格 (IDEOGRAPHIC SPACE)
        for (int i = 0; i < wsChars.length(); i++) {
            IS_UNICODE_WHITESPACE.set(wsChars.charAt(i));
        }
    }

    /**
     * 构造函数
     */
    private GraphvizDotfileGenerator() {
        // 不可构造
    }

    /**
     * 检查字符是否为 Unicode 空白字符
     *
     * @param c
     *            字符
     * @return 如果字符是 Unicode 空白字符则返回 true
     */
    private static boolean isUnicodeWhitespace(final char c) {
        return IS_UNICODE_WHITESPACE.get(c);
    }

    /**
     * 将 HTML 不安全的字符编码为 HTML 实体
     *
     * @param unsafeStr
     *            要进行转义以使其 HTML 安全的字符串
     * @param turnNewlineIntoBreak
     *            如果为 true，将 '\n' 转换为输出中的换行元素
     * @param buf
     *            字符串构建器
     */
    private static void htmlEncode(final CharSequence unsafeStr, final boolean turnNewlineIntoBreak,
                                   final StringBuilder buf) {
        for (int i = 0, n = unsafeStr.length(); i < n; i++) {
            final char c = unsafeStr.charAt(i);
            switch (c) {
                case '&':
                    buf.append("&amp;");
                    break;
                case '<':
                    buf.append("&lt;");
                    break;
                case '>':
                    buf.append("&gt;");
                    break;
                case '"':
                    buf.append("&quot;");
                    break;
                case '\'':
                    buf.append("&#x27;"); // 参见 http://goo.gl/FzoP6m
                    break;
                case '\\':
                    buf.append("&lsol;");
                    break;
                case '/':
                    buf.append("&#x2F;"); // 如果属性值未加引号，'/' 可能是危险字符
                    break;
                // 编码一些常见的在某些字符集/浏览器变体中容易出问题的字符
                case '—':
                    buf.append("&mdash;");
                    break;
                case '–':
                    buf.append("&ndash;");
                    break;
                case '“':
                    buf.append("&ldquo;");
                    break;
                case '”':
                    buf.append("&rdquo;");
                    break;
                case '‘':
                    buf.append("&lsquo;");
                    break;
                case '’':
                    buf.append("&rsquo;");
                    break;
                case '«':
                    buf.append("&laquo;");
                    break;
                case '»':
                    buf.append("&raquo;");
                    break;
                case '£':
                    buf.append("&pound;");
                    break;
                case '©':
                    buf.append("&copy;");
                    break;
                case '®':
                    buf.append("&reg;");
                    break;
                case (char) 0x00A0:
                    buf.append("&nbsp;");
                    break;
                case '\n':
                    if (turnNewlineIntoBreak) {
                        buf.append("<br>");
                    } else {
                        buf.append(' '); // 在 HTML 文本中，换行符作为空白字符起作用
                    }
                    break;
                default:
                    if (c <= 32 || isUnicodeWhitespace(c)) {
                        buf.append(' ');
                    } else {
                        buf.append(c);
                    }
                    break;
            }
        }
    }

    /**
     * 将 HTML 不安全的字符编码为 HTML 实体
     *
     * @param unsafeStr
     *            要进行转义以使其 HTML 安全的字符串
     * @param buf
     *            字符串构建器
     */
    private static void htmlEncode(final CharSequence unsafeStr, final StringBuilder buf) {
        htmlEncode(unsafeStr, /* turnNewlineIntoBreak = */ false, buf);
    }

    /**
     * 为类节点生成 HTML 标签
     *
     * @param ci
     *            类信息
     * @param shape
     *            要使用的形状
     * @param boxBgColor
     *            框背景颜色
     * @param showFields
     *            是否显示字段
     * @param showMethods
     *            是否显示方法
     * @param useSimpleNames
     *            是否对类型签名中的类使用简单名称
     * @param ScanConfig
     *            扫描规格
     * @param buf
     *            字符串构建器
     */
    private static void labelClassNodeHTML(final ClassInfo ci, final String shape, final String boxBgColor,
                                           final boolean showFields, final boolean showMethods, final boolean useSimpleNames,
                                           final ScanConfig ScanConfig, final StringBuilder buf) {
        buf.append("[shape=").append(shape).append(",style=filled,fillcolor=\"#").append(boxBgColor)
                .append("\",label=");
        buf.append('<');
        buf.append("<table border='0' cellborder='0' cellspacing='1'>");

        // 类修饰符
        buf.append("<tr><td><font point-size='12'>").append(ci.getModifiersStr()).append(' ')
                .append(ci.isEnum() ? "enum"
                        : ci.isAnnotation() ? "@interface" : ci.isInterface() ? "interface" : "class")
                .append("</font></td></tr>");

        if (ci.getName().contains(".")) {
            buf.append("<tr><td><font point-size='14'><b>");
            htmlEncode(ci.getPackageName() + ".", buf);
            buf.append("</b></font></td></tr>");
        }

        // 类名
        buf.append("<tr><td><font point-size='20'><b>");
        htmlEncode(ci.getSimpleName(), buf);
        buf.append("</b></font></td></tr>");

        // 创建一个与框背景颜色匹配但更暗的颜色
        final float darkness = 0.8f;
        final int r = (int) (Integer.parseInt(boxBgColor.substring(0, 2), 16) * darkness);
        final int g = (int) (Integer.parseInt(boxBgColor.substring(2, 4), 16) * darkness);
        final int b = (int) (Integer.parseInt(boxBgColor.substring(4, 6), 16) * darkness);
        final String darkerColor = String.format("#%s%s%s%s%s%s", Integer.toString(r >> 4, 16),
                Integer.toString(r & 0xf, 16), Integer.toString(g >> 4, 16), Integer.toString(g & 0xf, 16),
                Integer.toString(b >> 4, 16), Integer.toString(b & 0xf, 16));

        // 类注解
        final AnnotationInfoList annotationInfo = ci.annotationInfo;
        if (annotationInfo != null && !annotationInfo.isEmpty()) {
            buf.append("<tr><td colspan='3' bgcolor='").append(darkerColor)
                    .append("'><font point-size='12'><b>ANNOTATIONS</b></font></td></tr>");
            final AnnotationInfoList annotationInfoSorted = new AnnotationInfoList(annotationInfo);
            CollectionUtils.sortIfNotEmpty(annotationInfoSorted);
            for (final AnnotationInfo ai : annotationInfoSorted) {
                final String annotationName = ai.getName();
                if (!annotationName.startsWith("java.lang.annotation.")) {
                    buf.append("<tr>");
                    buf.append("<td align='center' valign='top'>");
                    htmlEncode(ai.toString(), buf);
                    buf.append("</td></tr>");
                }
            }
        }

        // 字段
        final FieldInfoList fieldInfo = ci.fieldInfo;
        if (showFields && fieldInfo != null && !fieldInfo.isEmpty()) {
            final FieldInfoList fieldInfoSorted = new FieldInfoList(fieldInfo);
            CollectionUtils.sortIfNotEmpty(fieldInfoSorted);
            for (int i = fieldInfoSorted.size() - 1; i >= 0; --i) {
                // 移除 serialVersionUID 字段
                if ("serialVersionUID".equals(fieldInfoSorted.get(i).getName())) {
                    fieldInfoSorted.remove(i);
                }
            }
            if (!fieldInfoSorted.isEmpty()) {
                buf.append("<tr><td colspan='3' bgcolor='").append(darkerColor)
                        .append("'><font point-size='12'><b>")
                        .append(ScanConfig.ignoreFieldVisibility ? "" : "PUBLIC ")
                        .append("FIELDS</b></font></td></tr>");
                buf.append("<tr><td cellpadding='0'>");
                buf.append("<table border='0' cellborder='0'>");
                for (final FieldInfo fi : fieldInfoSorted) {
                    buf.append("<tr>");
                    buf.append("<td align='right' valign='top'>");

                    // 字段注解
                    final AnnotationInfoList fieldAnnotationInfo = fi.annotationInfo;
                    if (fieldAnnotationInfo != null) {
                        for (final AnnotationInfo ai : fieldAnnotationInfo) {
                            if (buf.charAt(buf.length() - 1) != ' ') {
                                buf.append(' ');
                            }
                            htmlEncode(ai.toString(), buf);
                        }
                    }

                    // 字段修饰符
                    if (ScanConfig.ignoreFieldVisibility) {
                        if (buf.charAt(buf.length() - 1) != ' ') {
                            buf.append(' ');
                        }
                        buf.append(fi.getModifiersStr());
                    }

                    // 字段类型
                    if (buf.charAt(buf.length() - 1) != ' ') {
                        buf.append(' ');
                    }
                    final TypeSignature typeSig = fi.getTypeSignatureOrTypeDescriptor();
                    htmlEncode(useSimpleNames ? typeSig.toStringWithSimpleNames() : typeSig.toString(), buf);
                    buf.append("</td>");

                    // 字段名
                    buf.append("<td align='left' valign='top'><b>");
                    final String fieldName = fi.getName();
                    htmlEncode(fieldName, buf);
                    buf.append("</b></td></tr>");
                }
                buf.append("</table>");
                buf.append("</td></tr>");
            }
        }

        // 方法
        final MethodInfoList methodInfo = ci.methodInfo;
        if (showMethods && methodInfo != null) {
            final MethodInfoList methodInfoSorted = new MethodInfoList(methodInfo);
            CollectionUtils.sortIfNotEmpty(methodInfoSorted);
            for (int i = methodInfoSorted.size() - 1; i >= 0; --i) {
                // 不列出静态初始化块或 Object 的方法
                final MethodInfo mi = methodInfoSorted.get(i);
                final String name = mi.getName();
                final int numParam = mi.getParameterInfo().length;
                if ("<clinit>".equals(name) || "hashCode".equals(name) && numParam == 0
                        || "toString".equals(name) && numParam == 0 || "equals".equals(name) && numParam == 1
                        && "boolean (java.lang.Object)".equals(mi.getTypeDescriptor().toString())) {
                    methodInfoSorted.remove(i);
                }
            }
            if (!methodInfoSorted.isEmpty()) {
                buf.append("<tr><td cellpadding='0'>");
                buf.append("<table border='0' cellborder='0'>");
                buf.append("<tr><td colspan='3' bgcolor='").append(darkerColor)
                        .append("'><font point-size='12'><b>")
                        .append(ScanConfig.ignoreMethodVisibility ? "" : "PUBLIC ")
                        .append("METHODS</b></font></td></tr>");
                for (final MethodInfo mi : methodInfoSorted) {
                    buf.append("<tr>");

                    // 方法注解
                    // TODO: 如果内容过长，对此单元格进行换行
                    buf.append("<td align='right' valign='top'>");
                    final AnnotationInfoList methodAnnotationInfo = mi.annotationInfo;
                    if (methodAnnotationInfo != null) {
                        for (final AnnotationInfo ai : methodAnnotationInfo) {
                            if (buf.charAt(buf.length() - 1) != ' ') {
                                buf.append(' ');
                            }
                            htmlEncode(ai.toString(), buf);
                        }
                    }

                    // 方法修饰符
                    if (ScanConfig.ignoreMethodVisibility) {
                        if (buf.charAt(buf.length() - 1) != ' ') {
                            buf.append(' ');
                        }
                        buf.append(mi.getModifiersStr());
                    }

                    // 方法返回类型
                    if (buf.charAt(buf.length() - 1) != ' ') {
                        buf.append(' ');
                    }
                    if (!"<init>".equals(mi.getName())) {
                        // 不为构造函数列出返回类型
                        final TypeSignature resultTypeSig = mi.getTypeSignatureOrTypeDescriptor().getResultType();
                        htmlEncode(
                                useSimpleNames ? resultTypeSig.toStringWithSimpleNames() : resultTypeSig.toString(),
                                buf);
                    } else {
                        buf.append("<b>&lt;constructor&gt;</b>");
                    }
                    buf.append("</td>");

                    // 方法名
                    buf.append("<td align='left' valign='top'>");
                    buf.append("<b>");
                    if ("<init>".equals(mi.getName())) {
                        // 为构造函数显示类名
                        htmlEncode(ci.getSimpleName(), buf);
                    } else {
                        htmlEncode(mi.getName(), buf);
                    }
                    buf.append("</b>&nbsp;");
                    buf.append("</td>");

                    // 方法参数
                    buf.append("<td align='left' valign='top'>");
                    buf.append('(');
                    final MethodParam[] paramInfo = mi.getParameterInfo();
                    if (paramInfo.length != 0) {
                        for (int i = 0, wrapPos = 0; i < paramInfo.length; i++) {
                            if (i > 0) {
                                buf.append(", ");
                                wrapPos += 2;
                            }
                            if (wrapPos > PARAM_WRAP_WIDTH) {
                                buf.append("</td></tr><tr><td></td><td></td><td align='left' valign='top'>");
                                wrapPos = 0;
                            }

                            // 参数注解
                            final AnnotationInfo[] paramAnnotationInfo = paramInfo[i].annotationInfo;
                            if (paramAnnotationInfo != null) {
                                for (final AnnotationInfo ai : paramAnnotationInfo) {
                                    final String ais = ai.toString();
                                    if (!ais.isEmpty()) {
                                        if (buf.charAt(buf.length() - 1) != ' ') {
                                            buf.append(' ');
                                        }
                                        htmlEncode(ais, buf);
                                        wrapPos += 1 + ais.length();
                                        if (wrapPos > PARAM_WRAP_WIDTH) {
                                            buf.append("</td></tr><tr><td></td><td></td>"
                                                    + "<td align='left' valign='top'>");
                                            wrapPos = 0;
                                        }
                                    }
                                }
                            }

                            // 参数类型
                            final TypeSignature paramTypeSig = paramInfo[i].getTypeSignatureOrTypeDescriptor();
                            final String paramTypeStr = useSimpleNames ? paramTypeSig.toStringWithSimpleNames()
                                    : paramTypeSig.toString();
                            htmlEncode(paramTypeStr, buf);
                            wrapPos += paramTypeStr.length();

                            // 参数名
                            final String paramName = paramInfo[i].getName();
                            if (paramName != null) {
                                buf.append(" <B>");
                                htmlEncode(paramName, buf);
                                wrapPos += 1 + paramName.length();
                                buf.append("</B>");
                            }
                        }
                    }
                    buf.append(')');
                    buf.append("</td></tr>");
                }
                buf.append("</table>");
                buf.append("</td></tr>");
            }
        }
        buf.append("</table>");
        buf.append(">]");
    }

    /**
     * 生成一个 .dot 文件，可输入到 GraphViz 中进行类图的布局和可视化
     * sizeX 和 sizeY 参数是让 GraphViz 渲染 .dot 文件时使用的图像输出尺寸(以英寸为单位)
     *
     * @param classInfoList
     *            类信息列表
     * @param sizeX
     *            尺寸 X
     * @param sizeY
     *            尺寸 Y
     * @param showFields
     *            是否显示字段
     * @param showFieldTypeDependencyEdges
     *            是否显示字段类型依赖边
     * @param showMethods
     *            是否显示方法
     * @param showMethodTypeDependencyEdges
     *            是否显示方法类型依赖边
     * @param showAnnotations
     *            是否显示注解
     * @param useSimpleNames
     *            是否对类使用简单名称
     * @param ScanConfig
     *            扫描规格
     * @return GraphViz dot 文件内容字符串
     */
    public static String generateGraphVizDotFile(final ClassInfoList classInfoList, final float sizeX, final float sizeY,
                                                 final boolean showFields, final boolean showFieldTypeDependencyEdges, final boolean showMethods,
                                                 final boolean showMethodTypeDependencyEdges, final boolean showAnnotations,
                                                 final boolean useSimpleNames, final ScanConfig ScanConfig) {
        final StringBuilder buf = new StringBuilder(1024 * 1024);
        buf.append("digraph {\n");
        buf.append("size=\"").append(sizeX).append(',').append(sizeY).append("\";\n");
        buf.append("layout=dot;\n");
        buf.append("rankdir=\"BT\";\n");
        buf.append("overlap=false;\n");
        buf.append("splines=true;\n");
        buf.append("pack=true;\n");
        buf.append("graph [fontname = \"Courier, Regular\"]\n");
        buf.append("node [fontname = \"Courier, Regular\"]\n");
        buf.append("edge [fontname = \"Courier, Regular\"]\n");

        final ClassInfoList standardClassNodes = classInfoList.getStandardClasses();
        final ClassInfoList interfaceNodes = classInfoList.getInterfaces();
        final ClassInfoList annotationNodes = classInfoList.getAnnotations();

        for (final ClassInfo node : standardClassNodes) {
            buf.append('"').append(node.getName()).append('"');
            labelClassNodeHTML(node, "box", STANDARD_CLASS_COLOR, showFields, showMethods, useSimpleNames, ScanConfig,
                    buf);
            buf.append(";\n");
        }

        for (final ClassInfo node : interfaceNodes) {
            buf.append('"').append(node.getName()).append('"');
            labelClassNodeHTML(node, "diamond", INTERFACE_COLOR, showFields, showMethods, useSimpleNames, ScanConfig,
                    buf);
            buf.append(";\n");
        }

        for (final ClassInfo node : annotationNodes) {
            buf.append('"').append(node.getName()).append('"');
            labelClassNodeHTML(node, "oval", ANNOTATION_COLOR, showFields, showMethods, useSimpleNames, ScanConfig,
                    buf);
            buf.append(";\n");
        }

        final Set<String> allVisibleNodes = new HashSet<>();
        allVisibleNodes.addAll(standardClassNodes.getNames());
        allVisibleNodes.addAll(interfaceNodes.getNames());
        allVisibleNodes.addAll(annotationNodes.getNames());

        buf.append('\n');
        for (final ClassInfo classNode : standardClassNodes) {
            for (final ClassInfo directSuperclassNode : classNode.getSuperclasses().directOnly()) {
                if (directSuperclassNode != null && allVisibleNodes.contains(directSuperclassNode.getName())
                        && !"java.lang.Object".equals(directSuperclassNode.getName())) {
                    // 类 --> 超类
                    buf.append("  \"").append(classNode.getName()).append("\" -> \"")
                            .append(directSuperclassNode.getName()).append("\" [arrowsize=2.5]\n");
                }
            }

            for (final ClassInfo implementedInterfaceNode : classNode.getInterfaces().directOnly()) {
                if (allVisibleNodes.contains(implementedInterfaceNode.getName())) {
                    // 类 --<> 实现的接口
                    buf.append("  \"").append(classNode.getName()).append("\" -> \"")
                            .append(implementedInterfaceNode.getName())
                            .append("\" [arrowhead=diamond, arrowsize=2.5]\n");
                }
            }

            if (showFieldTypeDependencyEdges && classNode.fieldInfo != null) {
                for (final FieldInfo fi : classNode.fieldInfo) {
                    for (final ClassInfo referencedFieldType : fi.findReferencedClassInfo(/* log = */ null)) {
                        if (allVisibleNodes.contains(referencedFieldType.getName())) {
                            // 类 --[ ] 字段类型(空心框)
                            buf.append("  \"").append(referencedFieldType.getName()).append("\" -> \"")
                                    .append(classNode.getName())
                                    .append("\" [arrowtail=obox, arrowsize=2.5, dir=back]\n");
                        }
                    }
                }
            }

            if (showMethodTypeDependencyEdges && classNode.methodInfo != null) {
                for (final MethodInfo mi : classNode.methodInfo) {
                    for (final ClassInfo referencedMethodType : mi.findReferencedClassInfo(/* log = */ null)) {
                        if (allVisibleNodes.contains(referencedMethodType.getName())) {
                            // 类 --[#] 字段类型(实心框)
                            buf.append("  \"").append(referencedMethodType.getName()).append("\" -> \"")
                                    .append(classNode.getName())
                                    .append("\" [arrowtail=box, arrowsize=2.5, dir=back]\n");
                        }
                    }
                }
            }
        }
        for (final ClassInfo interfaceNode : interfaceNodes) {
            for (final ClassInfo superinterfaceNode : interfaceNode.getInterfaces().directOnly()) {
                if (allVisibleNodes.contains(superinterfaceNode.getName())) {
                    // 接口 --<> 超接口
                    buf.append("  \"").append(interfaceNode.getName()).append("\" -> \"")
                            .append(superinterfaceNode.getName()).append("\" [arrowhead=diamond, arrowsize=2.5]\n");
                }
            }
        }
        if (showAnnotations) {
            for (final ClassInfo annotationNode : annotationNodes) {
                for (final ClassInfo annotatedClassNode : annotationNode.getClassesWithAnnotationDirectOnly()) {
                    if (allVisibleNodes.contains(annotatedClassNode.getName())) {
                        // 被注解的类 --o 注解
                        buf.append("  \"").append(annotatedClassNode.getName()).append("\" -> \"")
                                .append(annotationNode.getName()).append("\" [arrowhead=dot, arrowsize=2.5]\n");
                    }
                }
                for (final ClassInfo classWithMethodAnnotationNode : annotationNode
                        .getClassesWithMethodAnnotationDirectOnly()) {
                    if (allVisibleNodes.contains(classWithMethodAnnotationNode.getName())) {
                        // 具有方法注解的类 --o 方法注解
                        buf.append("  \"").append(classWithMethodAnnotationNode.getName()).append("\" -> \"")
                                .append(annotationNode.getName()).append("\" [arrowhead=odot, arrowsize=2.5]\n");
                    }
                }
                for (final ClassInfo classWithMethodAnnotationNode : annotationNode
                        .getClassesWithFieldAnnotationDirectOnly()) {
                    if (allVisibleNodes.contains(classWithMethodAnnotationNode.getName())) {
                        // 具有字段注解的类 --o 方法注解
                        buf.append("  \"").append(classWithMethodAnnotationNode.getName()).append("\" -> \"")
                                .append(annotationNode.getName()).append("\" [arrowhead=odot, arrowsize=2.5]\n");
                    }
                }
            }
        }
        buf.append('}');
        return buf.toString();
    }

    /**
     * 生成一个 .dot 文件，可输入到 GraphViz 中进行类图的布局和可视化
     * 返回的图仅显示类间依赖关系sizeX 和 sizeY 参数是让 GraphViz 渲染 .dot 文件时使用的
     * 图像输出尺寸(以英寸为单位)使用此方法前必须调用
     * {@link ClassGraph#withInterClassDependencies()}
     *
     * @param classInfoList
     *            其依赖关系需要绘制在图中的节点列表
     * @param sizeX
     *            GraphViz 布局宽度(以英寸为单位)
     * @param sizeY
     *            GraphViz 布局宽度(以英寸为单位)
     * @param includeExternalClasses
     *            如果为 true，在图中包含不在 classInfoList 中的任何依赖节点
     * @return GraphViz 文件内容
     * @throws IllegalArgumentException
     *             如果此 {@link ClassInfoList} 为空，或者在扫描前未调用
     *             {@link ClassGraph#withInterClassDependencies()}(因为将没有可绘制的内容)
     */
    public static String generateGraphVizDotFileFromInterClassDependencies(final ClassInfoList classInfoList,
                                                                           final float sizeX, final float sizeY, final boolean includeExternalClasses) {

        final StringBuilder buf = new StringBuilder(1024 * 1024);
        buf.append("digraph {\n");
        buf.append("size=\"").append(sizeX).append(',').append(sizeY).append("\";\n");
        buf.append("layout=dot;\n");
        buf.append("rankdir=\"BT\";\n");
        buf.append("overlap=false;\n");
        buf.append("splines=true;\n");
        buf.append("pack=true;\n");
        buf.append("graph [fontname = \"Courier, Regular\"]\n");
        buf.append("node [fontname = \"Courier, Regular\"]\n");
        buf.append("edge [fontname = \"Courier, Regular\"]\n");

        final Set<ClassInfo> allVisibleNodes = new HashSet<>(classInfoList);
        if (includeExternalClasses) {
            for (final ClassInfo ci : classInfoList) {
                allVisibleNodes.addAll(ci.getClassDependencies());
            }
        }

        for (final ClassInfo ci : allVisibleNodes) {
            buf.append('"').append(ci.getName()).append('"');
            buf.append("[shape=").append(ci.isAnnotation() ? "oval" : ci.isInterface() ? "diamond" : "box")
                    .append(",style=filled,fillcolor=\"#").append(ci.isAnnotation() ? ANNOTATION_COLOR
                            : ci.isInterface() ? INTERFACE_COLOR : STANDARD_CLASS_COLOR)
                    .append("\",label=");
            buf.append('<');
            buf.append("<table border='0' cellborder='0' cellspacing='1'>");

            // 类修饰符
            buf.append("<tr><td><font point-size='12'>").append(ci.getModifiersStr()).append(' ')
                    .append(ci.isEnum() ? "enum"
                            : ci.isAnnotation() ? "@interface" : ci.isInterface() ? "interface" : "class")
                    .append("</font></td></tr>");

            if (ci.getName().contains(".")) {
                buf.append("<tr><td><font point-size='14'><b>");
                htmlEncode(ci.getPackageName(), buf);
                buf.append("</b></font></td></tr>");
            }

            // 类名
            buf.append("<tr><td><font point-size='20'><b>");
            htmlEncode(ci.getSimpleName(), buf);
            buf.append("</b></font></td></tr>");
            buf.append("</table>");
            buf.append(">];\n");
        }

        buf.append('\n');
        for (final ClassInfo ci : classInfoList) {
            for (final ClassInfo dep : ci.getClassDependencies()) {
                if (includeExternalClasses || allVisibleNodes.contains(dep)) {
                    // 类 --> 依赖
                    buf.append("  \"").append(ci.getName()).append("\" -> \"").append(dep.getName())
                            .append("\" [arrowsize=2.5]\n");
                }
            }
        }

        buf.append('}');
        return buf.toString();
    }
}
