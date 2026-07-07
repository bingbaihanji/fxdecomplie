package com.bingbaihanji.fxdecomplie.ui.outline;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从反编译 Java 源码中提取字段、方法、内部类的大纲信息使用正则逐行匹配
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class OutlineParser {

    /**
     * 方法签名匹配模式
     * 使用简单括号计数扫描参数列表,避免正则嵌套量词导致灾难性回溯或
     * 深层嵌套泛型（如 {@code Map<String, Map<String, List<Integer>>>}）匹配失败
     * 匹配策略：定位开括号后逐字符扫描,跳过字符串/注释,括号计数归零时结束
     */
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^\\s*((?:public|protected|private|static|final|synchronized|abstract|native|\\s)*)"
                    + "[\\w<>\\[\\],.\\s]+\\s+(\\w+)\\s*\\(");

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^\\s*((?:public|protected|private|static|final|volatile|transient|\\s)*)"
                    + "[\\w<>\\[\\],.\\s]+\\s+(\\w+)\\s*(?:=\\s*[^;]+)?;");

    private static final Pattern INNER_CLASS_PATTERN = Pattern.compile(
            "^\\s*((?:public|protected|private|static|\\s)*)\\b(class|interface|enum|record)\\s+(\\w+)");

    /** 匹配全限定类引用：包路径（小写段）+ 类名/内部类（大小写均可,混淆后类名常为小写单字母） */
    private static final Pattern CLASS_REF_PATTERN = Pattern.compile(
            "\\b([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+\\.[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\b");

    private OutlineParser() {
        throw new AssertionError("utility class");
    }

    /**
     * 从反编译 Java 源码提取大纲成员（字段、方法、内部类）
     *
     * <p>逐行扫描源码,用大括号计数跟踪嵌套深度方法匹配采用两步策略：
     * 先用 {@link #METHOD_PATTERN} 匹配方法名前缀,再从开括号位置逐字符扫描
     * 找到匹配的闭括号（跳过字符串和注释）,避免正则嵌套量词的深度限制和
     * 灾难性回溯问题</p>
     *
     * @param sourceCode 反编译后的 Java 源码
     * @return 大纲成员列表,按源码行号排序
     */
    public static List<OutlineMember> parse(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return List.of();
        }
        List<OutlineMember> members = new ArrayList<>();
        String[] lines = sourceCode.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        int depth = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            depth += count(line, '{') - count(line, '}');

            Matcher m;
            if (depth == 1 && !line.contains(" class ") && !line.contains(" interface ")
                    && !line.contains(" enum ") && !line.contains(" record ")) {
                if ((m = METHOD_PATTERN.matcher(line)).find()) {
                    // 从开括号位置扫描匹配闭括号,处理深层嵌套泛型参数
                    int openParen = m.end() - 1; // pattern 以 "\(" 结尾,开括号在最后一字符
                    if (openParen >= 0 && hasMatchingCloseParen(line, openParen)) {
                        members.add(new OutlineMember(m.group(2), OutlineMember.MemberType.METHOD,
                                extractModifiers(m.group(1)), i + 1));
                    }
                } else if ((m = FIELD_PATTERN.matcher(line)).find() && !line.contains("(")) {
                    members.add(new OutlineMember(m.group(2), OutlineMember.MemberType.FIELD,
                            extractModifiers(m.group(1)), i + 1));
                }
            }
            if ((m = INNER_CLASS_PATTERN.matcher(line)).find()
                    && !line.contains("new ") && depth >= 1 && depth <= 2) {
                members.add(new OutlineMember(m.group(3), OutlineMember.MemberType.INNER_CLASS,
                        extractModifiers(m.group(1)), i + 1));
            }
        }
        return members;
    }

    /**
     * 从指定位置的开括号开始,逐字符扫描找到匹配的闭括号
     * 跳过字符串字面量和注释,支持任意深度的泛型嵌套
     *
     * @param line      当前行文本
     * @param openParen 开括号 '(' 的索引
     * @return 找到匹配闭括号且后跟 '{' 或 ';' 时返回 true
     */
    private static boolean hasMatchingCloseParen(String line, int openParen) {
        int depth = 0;
        for (int j = openParen; j < line.length(); j++) {
            char c = line.charAt(j);
            // 跳过字符串字面量
            if (c == '"' || c == '\'') {
                j = skipQuoted(line, j, c) - 1;
                continue;
            }
            // 跳过单行注释
            if (c == '/' && j + 1 < line.length() && line.charAt(j + 1) == '/') {
                return false; // 注释到行尾,不可能有闭括号和 {/;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    // 闭括号后应跟 { 或 ; 或 throws
                    int after = j + 1;
                    while (after < line.length() && Character.isWhitespace(line.charAt(after))) {
                        after++;
                    }
                    if (after < line.length()) {
                        char next = line.charAt(after);
                        if (next == '{' || next == ';') {
                            return true;
                        }
                        // 检查 throws 子句
                        if (after + 5 < line.length()
                                && line.substring(after, after + 6).equals("throws")) {
                            // 继续扫描到 { 或 ;
                            for (int k = after + 6; k < line.length(); k++) {
                                if (line.charAt(k) == '{' || line.charAt(k) == ';') {
                                    return true;
                                }
                            }
                            return false;
                        }
                    }
                    return false;
                }
            }
        }
        return false;
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

    /**
     * 统计字符在行中的出现次数,跳过字符串字面量和注释
     * 避免 {@code "hello { world }"} 或 {@code // { } } 中的括号被误计
     */
    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // 跳过字符串/字符字面量
            if (ch == '"' || ch == '\'') {
                i = skipQuoted(s, i, ch) - 1;
                continue;
            }
            // 跳过单行注释
            if (ch == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                break; // 注释到行尾,本行后续不再有代码级括号
            }
            if (ch == c) {
                n++;
            }
        }
        return n;
    }

    /**
     * 从反编译源码中提取代码元数据,用于 Ctrl+Click 导航
     * 逐行扫描类引用、方法调用和字段访问
     */
    public static CodeMetadata extractMetadata(String sourceCode) {
        Map<Integer, List<CodeMetadata.Reference>> refsByLine = new HashMap<>();
        if (sourceCode == null || sourceCode.isEmpty()) {
            return new CodeMetadata(refsByLine);
        }

        String[] lines = sourceCode.replace("\r\n", "\n").replace("\r", "\n").split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            List<CodeMetadata.Reference> refs = new ArrayList<>();

            // 跳过纯注释行
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("* ")) {
                continue;
            }

            Matcher m = CLASS_REF_PATTERN.matcher(line);
            while (m.find()) {
                String match = m.group(1);
                // 包含 '.' 且最后一段是有效 Java 标识符则视为类引用
                // 混淆后的类名通常为小写单字母（如 a, b, c）,不能再依赖首字母大写判断
                if (match.contains(".") && looksLikeClassReference(match)) {
                    refs.add(new CodeMetadata.Reference(
                            CodeMetadata.RefType.CLASS_REF, match, null, lineNum));
                }
            }

            if (!refs.isEmpty()) {
                refsByLine.put(lineNum, refs);
            }
        }

        return new CodeMetadata(refsByLine);
    }

    /**
     * 判断匹配片段是否为有效的类引用
     *
     * <p>混淆后的类名通常为小写单字母（如 a/b/c）,若只按首字母大写过滤
     * 会漏掉所有混淆类引用,导致 Ctrl+Click 导航失效</p>
     *
     * <p>简单规则：最后一个 '.' 后面的简单类名必须是有效 Java 标识符,
     * 且至少包含两个 '.' 段（至少一级包 + 类名）,避免误匹配单个变量</p>
     */
    private static boolean looksLikeClassReference(String match) {
        if (match == null || match.isBlank()) {
            return false;
        }
        int lastDot = match.lastIndexOf('.');
        if (lastDot < 0) {
            return false;
        }
        String simpleName = match.substring(lastDot + 1);
        if (simpleName.isEmpty() || !Character.isJavaIdentifierStart(simpleName.charAt(0))) {
            return false;
        }
        for (int i = 1; i < simpleName.length(); i++) {
            if (!Character.isJavaIdentifierPart(simpleName.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String extractModifiers(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }
}
