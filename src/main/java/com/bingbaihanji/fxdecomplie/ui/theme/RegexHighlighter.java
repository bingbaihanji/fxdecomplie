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
 * 基于正则表达式的 Java 语法高亮器,实现 SyntaxDecorator 接口
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

    /** 默认/通用样式 */
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

    /** 使用内置默认暗色主题构造高亮器 */
    public RegexHighlighter() {
        this(VsCodeThemeLoader.defaultDark());
    }

    /**
     * 使用指定 VS Code 主题构造高亮器
     *
     * @param theme VS Code 主题数据
     */
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

    /** 检查标识符是否以大写开头（类名/接口名/枚举名等类型标识） */
    private static boolean isType(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        return Character.isUpperCase(identifier.charAt(0));
    }

    /** 检查标识符后是否紧跟 ((方法调用/声明) */
    private static boolean isMethod(String line, int endPos) {
        if (endPos >= line.length()) {
            return false;
        }
        int lineEnd = line.indexOf('\n', endPos);
        if (lineEnd < 0) {
            lineEnd = line.length();
        }
        String rest = line.substring(endPos, Math.min(lineEnd, endPos + 30)).trim();
        return rest.startsWith("(");
    }

    /** 检查标识符后是否紧跟 = 或 ;(字段声明) */
    private static boolean isField(String line, int endPos) {
        if (endPos >= line.length()) {
            return false;
        }
        int lineEnd = line.indexOf('\n', endPos);
        if (lineEnd < 0) {
            lineEnd = line.length();
        }
        String rest = line.substring(endPos, Math.min(lineEnd, endPos + 30)).trim();
        return rest.startsWith("=") || rest.startsWith(";");
    }

    /** 检查标识符是否在方法参数列表中(同一行内的括号之间) */
    private static boolean isParameter(String line, int startPos, int endPos) {
        int lineStart = line.lastIndexOf('\n', startPos);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        int lineEnd = line.indexOf('\n', endPos);
        if (lineEnd < 0) {
            lineEnd = line.length();
        }
        String currentLine = line.substring(lineStart, lineEnd);
        int openParen = currentLine.indexOf('(');
        int closeParen = currentLine.indexOf(')');
        if (openParen < 0 || closeParen < 0 || openParen >= closeParen) {
            return false;
        }
        int posInLine = startPos - lineStart;
        return posInLine > openParen && posInLine < closeParen;
    }

    /**
     * 根据 token 文本和上下文确定其样式,供外部（如 BracketHighlighter）复用
     *
     * @param matched   token 文本
     * @param groupName 正则组名（"MULTICOMMENT","SINGLECOMMENT","STRING","ANNOTATION","NUMBER","IDENTIFIER"或null）
     * @param fullLine  段落完整文本（用于上下文判断）
     * @param endPos    token 结束位置
     * @param startPos  token 起始位置
     * @return 对应样式属性
     */
    public StyleAttributeMap classifyToken(String matched, String groupName,
                                           String fullLine, int endPos, int startPos) {
        if (groupName == null) {
            return styleDefault;
        }
        return switch (groupName) {
            case "MULTICOMMENT", "SINGLECOMMENT" -> styleComment;
            case "STRING" -> styleString;
            case "ANNOTATION" -> styleAnnotation;
            case "NUMBER" -> styleNumber;
            case "IDENTIFIER" -> {
                if (KEYWORDS.contains(matched)) {
                    yield styleKeyword;
                }
                if (isMethod(fullLine, endPos)) {
                    yield styleMethod;
                }
                if (isField(fullLine, endPos)) {
                    yield styleField;
                }
                if (isType(matched)) {
                    yield styleType;
                }
                if (isParameter(fullLine, startPos, endPos)) {
                    yield styleParameter;
                }
                yield styleDefault;
            }
            default -> styleDefault;
        };
    }

    /** 根据 scope 列表解析样式(优先精确匹配 → 前缀匹配 → 回退) */
    private StyleAttributeMap resolveStyle(Map<String, StyleAttributeMap> styles, List<String> scopes) {
        for (String scope : scopes) {
            if (styles.containsKey(scope)) {
                return styles.get(scope);
            }
            for (var entry : styles.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(scope + ".")) {
                    return entry.getValue();
                }
            }
        }
        return styles.getOrDefault("default",
                StyleAttributeMap.builder().setTextColor(Color.web("#d4d4d4")).build());
    }

    /**
     * 为正则分词后的段落创建带样式片段的 RichParagraph
     * <p>逐个匹配 token,token 之间的文本应用默认样式,其余文本根据正则组名分类着色</p>
     */
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
            // ---- 此 token 之前的普通文本: 应用默认样式 ----
            if (matcher.start() > lastEnd) {
                builder.addSegment(text.substring(lastEnd, matcher.start()), styleDefault);
            }

            String matched = matcher.group();
            StyleAttributeMap style;

            // ---- 注释处理: 多行(/* */)和单行(//) ----
            if (matcher.group("MULTICOMMENT") != null || matcher.group("SINGLECOMMENT") != null) {
                style = styleComment;
                // ---- 字符串字面量: 双引号带转义序列 ----
            } else if (matcher.group("STRING") != null) {
                style = styleString;
                // ---- 注解: @Override, @Deprecated 等 ----
            } else if (matcher.group("ANNOTATION") != null) {
                style = styleAnnotation;
                // ---- 数字字面量: 整数、浮点数,可带后缀(L, F, D) ----
            } else if (matcher.group("NUMBER") != null) {
                style = styleNumber;
                // ---- 标识符: 词边界 token,需根据上下文消歧 ----
            } else if (matcher.group("IDENTIFIER") != null) {
                if (KEYWORDS.contains(matched)) {
                    // ---- Java 关键字: if, class, public 等 ----
                    style = styleKeyword;
                } else if (isMethod(text, matcher.end())) {
                    // ---- 方法名: 标识符后跟 '(' ----
                    style = styleMethod;
                } else if (isField(text, matcher.end())) {
                    // ---- 字段名: 标识符后跟 '=' 或 ';' ----
                    style = styleField;
                } else if (isType(matched)) {
                    // ---- 类型名: 标识符以大写字母开头 ----
                    style = styleType;
                } else if (isParameter(text, matcher.start(), matcher.end())) {
                    // ---- 参数名: 方法括号内的标识符 ----
                    style = styleParameter;
                } else {
                    // ---- 回退: 通用标识符 ----
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

    /** 文本变更时重新高亮的回调（当前不做处理,由 createRichParagraph 全量重建） */
    @Override
    public void handleChange(CodeTextModel model, TextPos start, TextPos end,
                             int linesRemoved, int linesAdded, int charIndex) {
    }
}
