package com.bingbaihanji.fxdecomplie.ui.theme;

import javafx.scene.paint.Color;
import jfx.incubator.scene.control.richtext.SyntaxDecorator;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.RichParagraph;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于正则表达式的 Java 语法高亮器，实现 SyntaxDecorator 接口。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class RegexHighlighter implements SyntaxDecorator {

    /** Java 关键字集合 */
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "var", "record", "sealed",
            "permits", "yield", "module", "requires", "exports", "opens", "to", "uses",
            "provides", "with", "transitive", "non-sealed"
    );

    /** Token 类型 → 主题 scope 映射 */
    private static final Map<String, List<String>> TOKEN_TO_SCOPES = Map.of(
            "KEYWORD", List.of("keyword", "keyword.control", "storage.type"),
            "STRING", List.of("string", "string.quoted"),
            "COMMENT", List.of("comment", "comment.line", "comment.block"),
            "ANNOTATION", List.of("keyword.other.annotation", "storage.type.annotation"),
            "NUMBER", List.of("constant.numeric", "constant.numeric.decimal"),
            "FIELD", List.of("variable.other.object", "entity.name.field"),
            "PARAMETER", List.of("variable.parameter", "entity.name.parameter"),
            "METHOD", List.of("entity.name.function", "support.function"),
            "TYPE", List.of("entity.name.type", "storage.type.class", "support.class"),
            "DEFAULT", List.of("variable", "source", "meta")
    );

    /** 预编译的正则分词模式 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?<MULTICOMMENT>/\\*[\\s\\S]*?\\*/)"
                    + "|(?<SINGLECOMMENT>//[^\n]*)"
                    + "|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\")"
                    + "|(?<ANNOTATION>@[a-zA-Z_][a-zA-Z0-9_.]*)"
                    + "|(?<NUMBER>\\b\\d+\\.?\\d*[fFlLdD]?\\b)"
                    + "|(?<IDENTIFIER>\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)"
    );

    /** 各 token 类型的样式属性 */
    private final StyleAttributeMap styleDefault;
    /** 关键字样式 */
    private final StyleAttributeMap styleKeyword;
    /** 字符串样式 */
    private final StyleAttributeMap styleString;
    /** 注释样式 */
    private final StyleAttributeMap styleComment;
    /** 注解样式 */
    private final StyleAttributeMap styleAnnotation;
    /** 数字样式 */
    private final StyleAttributeMap styleNumber;
    /** 字段/属性样式 */
    private final StyleAttributeMap styleField;
    /** 方法参数样式 */
    private final StyleAttributeMap styleParameter;
    /** 方法名样式 */
    private final StyleAttributeMap styleMethod;
    /** 类型/类名样式 */
    private final StyleAttributeMap styleType;

    public RegexHighlighter() {
        this(VsCodeThemeLoader.defaultDark());
    }

    public RegexHighlighter(VsCodeThemeLoader.ThemeData theme) {
        Map<String, StyleAttributeMap> styles = theme.tokenStyles();
        this.styleDefault = resolveStyle(styles, TOKEN_TO_SCOPES.get("DEFAULT"));
        this.styleKeyword = resolveStyle(styles, TOKEN_TO_SCOPES.get("KEYWORD"));
        this.styleString = resolveStyle(styles, TOKEN_TO_SCOPES.get("STRING"));
        this.styleComment = resolveStyle(styles, TOKEN_TO_SCOPES.get("COMMENT"));
        this.styleAnnotation = resolveStyle(styles, TOKEN_TO_SCOPES.get("ANNOTATION"));
        this.styleNumber = resolveStyle(styles, TOKEN_TO_SCOPES.get("NUMBER"));
        this.styleField = resolveStyle(styles, TOKEN_TO_SCOPES.get("FIELD"));
        this.styleParameter = resolveStyle(styles, TOKEN_TO_SCOPES.get("PARAMETER"));
        this.styleMethod = resolveStyle(styles, TOKEN_TO_SCOPES.get("METHOD"));
        this.styleType = resolveStyle(styles, TOKEN_TO_SCOPES.get("TYPE"));
    }

    /** 检查标识符是否以大写开头（类/接口/枚举名） */
    private static boolean isType(String identifier) {
        return Character.isUpperCase(identifier.charAt(0));
    }

    /** 检查标识符后是否紧跟 (（方法调用/声明） */
    private static boolean isMethod(String line, int endPos) {
        int lineEnd = line.indexOf('\n', endPos);
        if (lineEnd < 0) lineEnd = line.length();
        String rest = line.substring(endPos, Math.min(lineEnd, endPos + 30)).trim();
        return rest.startsWith("(");
    }

    /** 检查标识符后是否紧跟 = 或 ;（字段声明） */
    private static boolean isField(String line, int endPos) {
        int lineEnd = line.indexOf('\n', endPos);
        if (lineEnd < 0) lineEnd = line.length();
        String rest = line.substring(endPos, Math.min(lineEnd, endPos + 30)).trim();
        return rest.startsWith("=") || rest.startsWith(";");
    }

    /** 检查标识符是否在方法参数列表中（同一行内的括号之间） */
    private static boolean isParameter(String line, int startPos, int endPos) {
        int lineStart = line.lastIndexOf('\n', startPos);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        int lineEnd = line.indexOf('\n', endPos);
        if (lineEnd < 0) lineEnd = line.length();
        String currentLine = line.substring(lineStart, lineEnd);
        int openParen = currentLine.indexOf('(');
        int closeParen = currentLine.indexOf(')');
        if (openParen < 0 || closeParen < 0 || openParen >= closeParen) return false;
        int posInLine = startPos - lineStart;
        return posInLine > openParen && posInLine < closeParen;
    }

    /** 根据 scope 列表解析样式（优先精确匹配 → 前缀匹配 → 回退） */
    private StyleAttributeMap resolveStyle(Map<String, StyleAttributeMap> styles, List<String> scopes) {
        for (String scope : scopes) {
            if (styles.containsKey(scope)) return styles.get(scope);
            for (var entry : styles.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(scope + ".")) return entry.getValue();
            }
        }
        return styles.getOrDefault("default",
                StyleAttributeMap.builder().setTextColor(Color.web("#d4d4d4")).build());
    }

    @Override
    public RichParagraph createRichParagraph(CodeTextModel model, int paragraphIndex) {
        String text = model.getPlainText(paragraphIndex);
        if (text == null || text.isEmpty()) {
            return RichParagraph.builder().build();
        }

        RichParagraph.Builder builder = RichParagraph.builder();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            // ---- Plain text before this token: apply default style ----
            if (matcher.start() > lastEnd) {
                builder.addSegment(text.substring(lastEnd, matcher.start()), styleDefault);
            }

            String matched = matcher.group();
            StyleAttributeMap style;

            // ---- Comment handling: multi-line (/* */) and single-line (//) ----
            if (matcher.group("MULTICOMMENT") != null || matcher.group("SINGLECOMMENT") != null) {
                style = styleComment;
                // ---- String literal: double-quoted with escape sequences ----
            } else if (matcher.group("STRING") != null) {
                style = styleString;
                // ---- Annotation: @Override, @Deprecated, etc. ----
            } else if (matcher.group("ANNOTATION") != null) {
                style = styleAnnotation;
                // ---- Number literal: integers, floats, with suffixes (L, F, D) ----
            } else if (matcher.group("NUMBER") != null) {
                style = styleNumber;
                // ---- Identifier: word boundary token, needs context-based disambiguation ----
            } else if (matcher.group("IDENTIFIER") != null) {
                if (KEYWORDS.contains(matched)) {
                    // ---- Java keyword: if, class, public, etc. ----
                    style = styleKeyword;
                } else if (isMethod(text, matcher.end())) {
                    // ---- Method name: identifier followed by '(' ----
                    style = styleMethod;
                } else if (isField(text, matcher.end())) {
                    // ---- Field name: identifier followed by '=' or ';' ----
                    style = styleField;
                } else if (isType(matched)) {
                    // ---- Type name: identifier starting with uppercase letter ----
                    style = styleType;
                } else if (isParameter(text, matcher.start(), matcher.end())) {
                    // ---- Parameter name: identifier inside method parens ----
                    style = styleParameter;
                } else {
                    // ---- Fallback: generic identifier ----
                    style = styleDefault;
                }
            } else {
                style = styleDefault;
            }

            builder.addSegment(matched, style);
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            builder.addSegment(text.substring(lastEnd), styleDefault);
        }

        return builder.build();
    }

    @Override
    public void handleChange(CodeTextModel model, TextPos start, TextPos end,
                             int linesRemoved, int linesAdded, int charIndex) {
    }
}
