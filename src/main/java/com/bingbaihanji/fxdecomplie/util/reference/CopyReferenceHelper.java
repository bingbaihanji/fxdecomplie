package com.bingbaihanji.fxdecomplie.util.reference;

import com.bingbaihanji.fxdecomplie.model.OpenFile;
import com.bingbaihanji.fxdecomplie.ui.outline.OutlineMember;
import com.bingbaihanji.fxdecomplie.ui.outline.OutlineParser;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 复制引用工具类 — 实现类似 IntelliJ IDEA "Copy Reference"(Ctrl+Alt+Shift+C)功能 
 *
 * <p>根据光标位置智能生成引用字符串：
 * <ul>
 *   <li>光标在类名上 → 全限定类名,如 {@code com.example.MyClass}</li>
 *   <li>光标在字段上 → 完整字段引用,如 {@code com.example.MyClass#myField}</li>
 *   <li>光标在方法上 → 完整方法引用(参数类型自动解析为全限定名),
 *       如 {@code com.example.MyClass#myMethod(java.lang.String, int)}</li>
 *   <li>其他位置 → 文件路径 + 行号,如 {@code com/example/MyClass.java:43}</li>
 * </ul>
 *
 * @author bingbaihanji
 * @since 2026-07-07
 */
public final class CopyReferenceHelper {

    /** 匹配 import 语句：import 可选 static,包路径.类名; */
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\.[A-Z_$][\\w$]*)*)\\s*(?:\\.[A-Z_$][\\w$]*)?\\s*;");


    /**
     * 匹配源文件后缀名，用于识别 .class 或 .java 文件
     */
    private static final Pattern SOURCE_SUFFIX_PATTERN = Pattern.compile("\\.(class|java)$");

    /**
     * 匹配注解使用语法(单行)，包括注解名及可选的括号内参数
     * 例如：@Override、@SuppressWarnings("unchecked") 等
     * 不处理嵌套注解或复杂表达式
     */
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("@\\w+(?:\\s*\\([^)]*\\))?\\s*");

    /**
     * 构造函数声明的模板模式，用于匹配给定类名的构造函数定义
     * 实际使用时需通过 String.format(CONSTRUCTOR_PATTERN_TEMPLATE, className) 生成
     * 模式匹配：可选的访问修饰符(public/protected/private)+ 类名 + 左括号及后续内容
     * 例如："public MyClass(" 或 "MyClass("
     */
    private static final Pattern CONSTRUCTOR_PATTERN_TEMPLATE = Pattern.compile(
            "(?:(?:public|protected|private)\\s+)?%s\\s*\\(.*");

    /**
     * 包含 Java 所有原始类型(primitive type)的名称
     * 用于判断一个类型名称是否为基本类型，从而避免不必要的导入处理
     */
    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "boolean", "byte", "short", "int", "long", "float", "double", "char", "void");

    /**
     * 包含 java.lang 包中常用的类、接口、异常、错误等类型的简单名称
     * 这些类型默认由编译器自动导入，因此在分析导入依赖时可忽略它们
     * 集合覆盖了核心类型(String, Object)、包装类、常用工具类以及常见异常和错误
     */
    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "String", "Object", "Integer", "Long", "Boolean", "Double", "Float",
            "Byte", "Short", "Character", "Class", "Enum", "Number", "Void",
            "StringBuilder", "StringBuffer", "Math", "System", "Thread", "Runnable",
            "Throwable", "Exception", "Error", "RuntimeException", "Override",
            "Deprecated", "SuppressWarnings", "SafeVarargs", "Iterable",
            "AutoCloseable", "Comparable", "CharSequence", "Appendable",
            "Cloneable", "Readable", "Process", "StackTraceElement",
            "IllegalArgumentException", "IllegalStateException",
            "NullPointerException", "IndexOutOfBoundsException",
            "UnsupportedOperationException", "NoSuchElementException",
            "ArithmeticException", "ClassCastException", "ArrayStoreException",
            "EnumConstantNotPresentException", "TypeNotPresentException",
            "NegativeArraySizeException", "NumberFormatException",
            "SecurityException", "AssertionError", "ExceptionInInitializerError",
            "StackOverflowError", "OutOfMemoryError", "NoClassDefFoundError",
            "AbstractMethodError", "LinkageError", "VirtualMachineError",
            "InternalError", "UnknownError", "IncompatibleClassChangeError",
            "NoSuchFieldError", "NoSuchMethodError", "UnsatisfiedLinkError",
            "VerifyError", "ClassCircularityError", "ClassFormatError",
            "UnsupportedClassVersionError", "InstantiationError",
            "IllegalAccessError");

    private CopyReferenceHelper() {
        throw new AssertionError("utility class");
    }

    /**
     * 根据光标位置生成引用字符串 
     *
     * @param openFile 当前打开的文件元数据(提供源码和全路径)
     * @param codeArea 代码编辑器(提供光标位置和文本)
     * @return 引用字符串,无法确定时返回路径:行号格式
     */
    public static String getReferenceString(OpenFile openFile, CodeArea codeArea) {
        if (openFile == null || codeArea == null) {
            return "";
        }

        String sourceCode = openFile.sourceCode();
        String fqn = extractFqn(openFile);
        String internalPath = extractInternalPath(openFile);
        String className = openFile.className();
        TextPos caret = codeArea.getCaretPosition();
        if (caret == null) {
            return fqn;
        }

        int lineIndex = caret.index(); // 0-based
        int lineNumber = lineIndex + 1; // 1-based
        int column = caret.offset();
        List<String> lines = splitLines(sourceCode);
        String currentLine = getLine(lines, lineIndex);

        // 预解析 import 映射表,用于参数类型全限定名解析
        Map<String, String> importMap = parseImports(lines);
        String currentPackage = extractPackage(fqn);

        // 1. 解析大纲,匹配光标行
        List<OutlineMember> members = OutlineParser.parse(sourceCode);
        for (OutlineMember member : members) {
            if (member.lineNumber() != lineNumber) {
                continue;
            }
            if (currentLine == null) {
                continue;
            }

            switch (member.type()) {
                case METHOD -> {
                    if (isCursorOnToken(currentLine, column, member.name())) {
                        String params = extractParams(currentLine, importMap, currentPackage);
                        return fqn + "#" + member.name() + params;
                    }
                }
                case FIELD -> {
                    if (isCursorOnToken(currentLine, column, member.name())) {
                        return fqn + "#" + member.name();
                    }
                }
                case INNER_CLASS -> {
                    if (isCursorOnToken(currentLine, column, member.name())) {
                        return fqn + "." + member.name();
                    }
                }
            }
        }

        // 2. 检查光标是否在构造方法声明行上
        if (isConstructorLine(currentLine, className)
                && isCursorOnToken(currentLine, column, className)) {
            String params = extractParams(currentLine, importMap, currentPackage);
            return fqn + "#" + className + params;
        }

        // 3. 检查光标是否在类声明行上
        if (isCursorOnToken(currentLine, column, className)) {
            return fqn;
        }

        // 4. 其他位置：路径:行号
        return internalPath + ".java:" + lineNumber;
    }

    // ─── 全限定名/路径工具 ─────────────────────────────────

    /** 从 OpenFile.fullPath 提取点分全限定类名(去掉 .class/.java 后缀) */
    private static String extractFqn(OpenFile openFile) {
        String path = openFile.fullPath();
        if (path == null || path.isBlank()) {
            return "";
        }
        return SOURCE_SUFFIX_PATTERN.matcher(path).replaceFirst("").replace('/', '.');
    }

    /** 从 OpenFile.fullPath 提取内部路径(保留 / 分隔,去掉后缀) */
    private static String extractInternalPath(OpenFile openFile) {
        String path = openFile.fullPath();
        if (path == null || path.isBlank()) {
            return "";
        }
        return SOURCE_SUFFIX_PATTERN.matcher(path).replaceFirst("");
    }

    /** 从全限定类名中提取包名,如 {@code com.example.Foo} → {@code com.example} */
    private static String extractPackage(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(0, lastDot) : "";
    }

    // ─── Import 解析 ──────────────────────────────────────

    /**
     * 解析源码中的 import 语句,构建简单类名 → 全限定名的映射 
     * 对于 {@code import com.foo.Bar;} 将 {@code Bar} 映射到 {@code com.foo.Bar}
     * 对于静态 import 和通配符 import,提取最后的类名部分 
     */
    static Map<String, String> parseImports(String sourceCode) {
        return parseImports(splitLines(sourceCode));
    }

    private static Map<String, String> parseImports(List<String> lines) {
        Map<String, String> map = new LinkedHashMap<>();
        if (lines.isEmpty()) {
            return map;
        }
        for (String line : lines) {
            Matcher m = IMPORT_PATTERN.matcher(line);
            if (m.find()) {
                String fullImport = m.group(1);
                String simpleName = simpleNameOf(fullImport);
                if (!simpleName.isEmpty()) {
                    map.putIfAbsent(simpleName, fullImport);
                }
            }
        }
        return map;
    }

    /** 从全限定名中提取最后的简单名 */
    private static String simpleNameOf(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }

    // ─── 参数类型解析 ─────────────────────────────────────

    /**
     * 从方法声明行提取参数列表,将简单类型名解析为全限定名 
     *
     * @param line           方法声明行
     * @param importMap      import 映射(简单名 → 全限定名)
     * @param currentPackage 当前类所在包名
     * @return 解析后的参数列表字符串,如 {@code (java.lang.String, int, com.foo.Bar)}
     */
    static String extractParams(String line, Map<String, String> importMap, String currentPackage) {
        int openIdx = line.indexOf('(');
        if (openIdx < 0) {
            return "()";
        }
        int closeIdx = findClosingParen(line, openIdx);
        if (closeIdx < 0 || closeIdx <= openIdx + 1) {
            return "()";
        }
        String rawParams = line.substring(openIdx + 1, closeIdx).trim();
        if (rawParams.isEmpty()) {
            return "()";
        }

        List<String> parts = splitByComma(rawParams);
        List<String> resolved = new ArrayList<>();
        for (String part : parts) {
            resolved.add(resolveOneParam(part.trim(), importMap, currentPackage));
        }
        return "(" + String.join(", ", resolved) + ")";
    }

    /**
     * 解析单个参数文本(如 {@code @RequestBody AjglXzajVO xzajVO})为全限定类型名 
     * 去除注解和参数名,将类型部分解析为全限定名 
     */
    private static String resolveOneParam(String paramText, Map<String, String> importMap,
                                          String currentPackage) {
        // 去除注解：@Foo、@Foo("bar")、@Foo(value = "bar")
        String cleaned = ANNOTATION_PATTERN.matcher(paramText).replaceAll("").trim();
        // 去除参数名(最后一个空格后的单词),处理 varargs "..."
        int lastSpace = cleaned.lastIndexOf(' ');
        String typeStr;
        if (lastSpace >= 0) {
            typeStr = cleaned.substring(0, lastSpace).trim();
        } else {
            typeStr = cleaned;
        }
        // varargs "String..." → "String[]"
        if (typeStr.endsWith("...")) {
            typeStr = typeStr.substring(0, typeStr.length() - 3) + "[]";
        }
        return resolveTypeString(typeStr, importMap, currentPackage);
    }

    /**
     * 将类型字符串中的每个简单类名解析为全限定名 
     * 支持泛型语法(如 {@code List<Map<String, Foo>>})和数组(如 {@code String[]}) 
     */
    static String resolveTypeString(String typeStr, Map<String, String> importMap,
                                    String currentPackage) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < typeStr.length()) {
            char c = typeStr.charAt(i);
            // 非标识符字符直接附加
            if (!Character.isJavaIdentifierStart(c)) {
                result.append(c);
                i++;
                continue;
            }
            // 读取完整的标识符(可能包含 . 作为包分隔符)
            int start = i;
            while (i < typeStr.length()) {
                char nc = typeStr.charAt(i);
                if (Character.isJavaIdentifierPart(nc) || nc == '.') {
                    i++;
                } else {
                    break;
                }
            }
            String token = typeStr.substring(start, i);
            // 如果 token 已包含 '.',说明已经是全限定名,不解析
            if (token.contains(".")) {
                result.append(token);
            } else {
                result.append(resolveSimpleName(token, importMap, currentPackage));
            }
        }
        return result.toString();
    }

    /**
     * 将单个简单类名解析为全限定名 
     * 优先级：import 映射 > java.lang 自动导入 > 当前包 > 原始名称 
     */
    private static String resolveSimpleName(String simpleName, Map<String, String> importMap,
                                            String currentPackage) {
        // 原始类型：不改动
        if (isPrimitive(simpleName)) {
            return simpleName;
        }
        // import 映射
        String resolved = importMap.get(simpleName);
        if (resolved != null) {
            return resolved;
        }
        // java.lang 自动导入
        if (isJavaLangClass(simpleName)) {
            return "java.lang." + simpleName;
        }
        // 同包类
        if (!currentPackage.isEmpty()) {
            return currentPackage + "." + simpleName;
        }
        return simpleName;
    }

    /** 原始类型和 void 关键字 */
    private static boolean isPrimitive(String name) {
        return PRIMITIVE_TYPES.contains(name);
    }

    /** 常用 java.lang 包中的类(无需显式 import) */
    private static boolean isJavaLangClass(String name) {
        return JAVA_LANG_TYPES.contains(name);
    }

    /** 逗号分隔(尊重泛型嵌套括号),如 {@code Map<String, Integer>, List<Foo>} → 2 部分 */
    private static List<String> splitByComma(String params) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                result.add(params.substring(start, i));
                start = i + 1;
            }
        }
        result.add(params.substring(start));
        return result;
    }

    // ─── 括号/行/光标工具 ─────────────────────────────────

    /** 获取指定行文本(0-based index),越界返回 null */
    private static List<String> splitLines(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
    }

    /** 获取指定行文本(0-based index),越界返回 null */
    private static String getLine(List<String> lines, int lineIndex) {
        return lineIndex >= 0 && lineIndex < lines.size() ? lines.get(lineIndex) : null;
    }

    /** 判断光标列位置是否在指定 token 上 */
    static boolean isCursorOnToken(String line, int column, String token) {
        if (line == null || token == null || token.isEmpty()) {
            return false;
        }
        int idx = 0;
        while ((idx = line.indexOf(token, idx)) != -1) {
            int end = idx + token.length();
            if (column >= idx && column <= end) {
                return true;
            }
            idx = end;
        }
        return false;
    }

    /**
     * 从指定开括号位置逐字符扫描找到匹配的闭括号 
     * 跳过字符串字面量和单行注释 
     */
    static int findClosingParen(String line, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipQuoted(line, i, c) - 1;
                continue;
            }
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                return -1;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 判断该行是否为构造方法声明 
     * 构造方法格式：{@code (public|protected|private)? ClassName(},无返回类型 
     */
    private static boolean isConstructorLine(String line, String className) {
        if (line == null || className == null || className.isEmpty()) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.contains(" class ") || trimmed.contains(" interface ")
                || trimmed.contains(" enum ") || trimmed.contains(" record ")) {
            return false;
        }
        if (trimmed.contains("new " + className)) {
            return false;
        }
        Pattern constructorPattern = Pattern.compile(CONSTRUCTOR_PATTERN_TEMPLATE.pattern()
                .formatted(Pattern.quote(className)));
        return constructorPattern.matcher(trimmed).matches();
    }

    /** 跳过字符串/字符字面量,处理转义字符 */
    private static int skipQuoted(String s, int start, char quote) {
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            i++;
            if (c == quote) {
                break;
            }
        }
        return Math.min(i, s.length());
    }
}
