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

    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^\\s*((?:public|protected|private|static|final|synchronized|abstract|native|\\s)*)"
                    + "[\\w<>\\[\\],.\\s]+\\s+(\\w+)\\s*\\([^)]*(?:\\([^)]*\\)[^)]*)*+\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\s*[{;]");

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^\\s*((?:public|protected|private|static|final|volatile|transient|\\s)*)"
                    + "[\\w<>\\[\\],.\\s]+\\s+(\\w+)\\s*(?:=\\s*[^;]+)?;");

    private static final Pattern INNER_CLASS_PATTERN = Pattern.compile(
            "^\\s*((?:public|protected|private|static|\\s)*)\\b(class|interface|enum|record)\\s+(\\w+)");

    private static final Pattern CLASS_REF_PATTERN = Pattern.compile(
            "\\b([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+(?:\\.[A-Z][a-zA-Z0-9_]*)*)\\b");

    private OutlineParser() {
        throw new AssertionError("utility class");
    }

    /** @param sourceCode 反编译后的 Java 源码 */
    public static List<OutlineMember> parse(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) return List.of();
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
                    members.add(new OutlineMember(m.group(2), OutlineMember.MemberType.METHOD,
                            extractModifiers(m.group(1)), i + 1));
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

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) n++;
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
                // 仅当匹配片段看似包路径时才视为类引用
                if (match.contains(".") && Character.isUpperCase(match.charAt(match.lastIndexOf('.') + 1))) {
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

    private static String extractModifiers(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }
}
