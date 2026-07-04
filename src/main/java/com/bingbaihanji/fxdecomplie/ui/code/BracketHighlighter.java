package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.ui.theme.RegexHighlighter;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.SyntaxDecorator;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.RichParagraph;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 括号匹配高亮协调器。监听 CodeArea 光标位置，查找匹配括号并应用高亮样式。
 *
 * <p>实现策略：
 * <ol>
 *   <li>通过 {@code caretPositionProperty} 监听光标变化，150ms 防抖</li>
 *   <li>光标位于括号字符上时，向前/后扫描查找匹配括号</li>
 *   <li>匹配结果变化时创建新的 {@link BracketSyntaxDecorator} 实例，
 *       通过 {@code setSyntaxDecorator} 触发 CodeArea 全量重渲染</li>
 *   <li>包含括号的段落使用 {@link RegexHighlighter#classifyToken} 正确分词，
 *       仅在括号字符位置覆盖高亮样式</li>
 * </ol>
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class BracketHighlighter {

    /** 防抖延迟（毫秒） */
    private static final int DEBOUNCE_MS = 150;
    /** 超大文件跳过括号匹配的字节阈值 */
    private static final int SKIP_LENGTH_THRESHOLD = 500_000;

    /** 所有括号字符集合 */
    private static final Set<Character> BRACKETS = Set.of('(', ')', '{', '}', '[', ']', '<', '>');
    /** 括号对映射（开 → 闭） */
    private static final Map<Character, Character> PAIRS = Map.of(
            '(', ')', '{', '}', '[', ']', '<', '>');

    /** 匹配成功括号高亮色（金色） */
    private static final Color COLOR_MATCHED = Color.rgb(255, 215, 0);
    /** 不匹配括号高亮色（红色） */
    private static final Color COLOR_UNMATCHED = Color.rgb(255, 80, 80);

    /** 分词正则（与 RegexHighlighter.TOKEN_PATTERN 保持完全一致） */
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?<MC>/\\*[\\s\\S]*?\\*/)|(?<SC>//[^\n]*)|(?<STR>\"(?:\\\\.|[^\"\\\\])*\")"
                    + "|(?<ANN>@[a-zA-Z_][a-zA-Z0-9_.]*)|(?<NUM>\\b\\d+\\.?\\d*[fFlLdD]?\\b)"
                    + "|(?<ID>\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)");

    private final CodeArea codeArea;
    private final RegexHighlighter regexHighlighter;
    private final PauseTransition debounce;

    /** 当前匹配括号的文档偏移量（-1 表示无匹配） */
    private volatile int matchedOpenPos = -1;
    private volatile int matchedClosePos = -1;
    private volatile boolean isMatched;

    /**
     * @param codeArea         目标 CodeArea
     * @param regexHighlighter 底层语法高亮器（用于获取 token 样式）
     */
    public BracketHighlighter(CodeArea codeArea, RegexHighlighter regexHighlighter) {
        this.codeArea = codeArea;
        this.regexHighlighter = regexHighlighter;
        this.debounce = new PauseTransition(Duration.millis(DEBOUNCE_MS));
        this.debounce.setOnFinished(e -> scan());
    }

    /**
     * 在光标附近查找括号字符。依次检查光标前一个字符、光标所在字符。
     *
     * @param text     完整文本
     * @param docOff   光标文档偏移量
     * @return 括号字符的文档偏移量，未找到返回 -1
     */
    private static int findBracketNearCaret(String text, int docOff) {
        // 优先检查光标前一个字符（光标通常在括号之后）
        if (docOff > 0 && BRACKETS.contains(text.charAt(docOff - 1))) {
            return docOff - 1;
        }
        // 其次检查光标所在字符
        if (docOff < text.length() && BRACKETS.contains(text.charAt(docOff))) {
            return docOff;
        }
        return -1;
    }

    /**
     * 查找与给定位置括号匹配的另一半位置。
     *
     * @param text   完整文本
     * @param start  括号字符位置
     * @param isOpen 是否为开括号
     * @param ch     括号字符
     * @return 匹配位置，未找到返回 -1
     */
    private static int findMatchingBracket(String text, int start, boolean isOpen, char ch) {
        if (isOpen) {
            char close = PAIRS.get(ch);
            return scanForward(text, start, ch, close);
        }
        char open = findOpenForClose(ch);
        if (open == '\0') {
            return -1;
        }
        return scanBackward(text, start, open, ch);
    }

    // ---- 核心扫描逻辑 ----

    /** 向前扫描匹配闭括号，跳过字符串字面量和注释中的括号 */
    private static int scanForward(String text, int start, char open, char close) {
        int depth = 1;
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            // 跳过字符串和字符字面量中的括号
            if (c == '"' || c == '\'') {
                i = skipQuoted(text, i, c) - 1;
                continue;
            }
            // 跳过单行注释
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                int end = text.indexOf('\n', i + 2);
                i = end < 0 ? text.length() : end;
                continue;
            }
            // 跳过多行注释
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? text.length() : end + 1;
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 向后扫描匹配开括号，跳过字符串字面量和注释中的括号 */
    private static int scanBackward(String text, int start, char open, char close) {
        int depth = 1;
        for (int i = start - 1; i >= 0; i--) {
            char c = text.charAt(i);
            // 向后扫描时检测字符串结束引号
            if (c == '"' || c == '\'') {
                i = skipQuotedBackward(text, i, c) + 1;
                continue;
            }
            // 向后跳过多行注释结束标记
            if (c == '/' && i - 1 >= 0 && text.charAt(i - 1) == '*') {
                int end = text.lastIndexOf("/*", i - 2);
                i = end < 0 ? -1 : end;
                continue;
            }
            // 向后跳过单行注释（从行首到当前位置）
            if (c == '\n') {
                int lineStart = i + 1;
                int commentStart = text.indexOf("//", lineStart);
                if (commentStart >= 0 && commentStart < start) {
                    i = lineStart;
                    continue;
                }
            }
            if (c == close) {
                depth++;
            } else if (c == open) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 向前跳过字符串/字符字面量 */
    private static int skipQuoted(String text, int start, char quote) {
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            i++;
            if (c == quote) {
                break;
            }
        }
        return Math.min(i, text.length());
    }

    /** 向后跳过字符串/字符字面量 */
    private static int skipQuotedBackward(String text, int end, char quote) {
        int i = end - 1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (c == quote && (i == 0 || text.charAt(i - 1) != '\\')) {
                break;
            }
            i--;
        }
        return Math.max(i, -1);
    }

    /** 从闭括号反推开括号字符 */
    private static char findOpenForClose(char close) {
        for (Map.Entry<Character, Character> entry : PAIRS.entrySet()) {
            if (entry.getValue() == close) {
                return entry.getKey();
            }
        }
        return '\0';
    }

    // ---- 括号匹配算法 ----

    /** 将 TextPos（段落索引 + 列偏移）转换为文档绝对字符偏移 */
    private static int textPosToDocOffset(String text, TextPos pos) {
        int paragraphIndex = pos.index();
        int columnOffset = pos.offset();
        if (paragraphIndex < 0 || columnOffset < 0) {
            return -1;
        }
        int offset = 0;
        int lineStart = 0;
        for (int i = 0; i < paragraphIndex && lineStart < text.length(); i++) {
            int newline = text.indexOf('\n', lineStart);
            if (newline < 0) {
                break;
            }
            offset += (newline - lineStart) + 1;
            lineStart = newline + 1;
        }
        return offset + columnOffset;
    }

    /** 安装光标监听器 */
    public void install() {
        codeArea.caretPositionProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                debounce.playFromStart();
            }
        });
    }

    /** 停止监听并清除高亮 */
    public void dispose() {
        debounce.stop();
        clearHighlight();
    }

    /** 扫描光标位置，查找并高亮匹配括号 */
    private void scan() {
        String text = codeArea.getText();
        if (text == null || text.length() > SKIP_LENGTH_THRESHOLD) {
            clearHighlight();
            return;
        }
        TextPos pos = codeArea.getCaretPosition();
        if (pos == null) {
            clearHighlight();
            return;
        }
        int docOffset = textPosToDocOffset(text, pos);
        if (docOffset < 0 || docOffset > text.length()) {
            clearHighlight();
            return;
        }

        // 检查光标附近是否有括号字符
        int bracketPos = findBracketNearCaret(text, docOffset);
        if (bracketPos < 0) {
            clearHighlight();
            return;
        }

        // 查找匹配括号
        char ch = text.charAt(bracketPos);
        boolean isOpen = PAIRS.containsKey(ch);
        int matchPos = findMatchingBracket(text, bracketPos, isOpen, ch);

        // 规范化 open/close 位置
        int newOpenPos;
        int newClosePos;
        boolean newMatched;
        if (isOpen) {
            newOpenPos = bracketPos;
            newClosePos = (matchPos >= 0) ? matchPos : -1;
        } else {
            newOpenPos = (matchPos >= 0) ? matchPos : -1;
            newClosePos = bracketPos;
        }
        newMatched = (matchPos >= 0);

        // 仅在结果变化时触发重渲染
        if (newOpenPos == matchedOpenPos && newClosePos == matchedClosePos
                && newMatched == isMatched) {
            return;
        }
        matchedOpenPos = newOpenPos;
        matchedClosePos = newClosePos;
        isMatched = newMatched;

        applyHighlight(newOpenPos, newClosePos, newMatched);
    }

    /** 清除高亮，恢复为原始 RegexHighlighter */
    private void clearHighlight() {
        if (matchedOpenPos < 0 && matchedClosePos < 0) {
            return;
        }
        matchedOpenPos = -1;
        matchedClosePos = -1;
        isMatched = false;
        Platform.runLater(() -> codeArea.setSyntaxDecorator(regexHighlighter));
    }

    /** 应用括号高亮 */
    private void applyHighlight(int openPos, int closePos, boolean matched) {
        Color color = matched ? COLOR_MATCHED : COLOR_UNMATCHED;
        StyleAttributeMap bracketStyle = StyleAttributeMap.builder()
                .setTextColor(color).setBold(true).build();

        boolean hasBracket = (openPos >= 0 || closePos >= 0);
        SyntaxDecorator decorator;
        if (hasBracket) {
            decorator = new BracketSyntaxDecorator(regexHighlighter, openPos, closePos, bracketStyle);
        } else {
            decorator = regexHighlighter;
        }
        Platform.runLater(() -> codeArea.setSyntaxDecorator(decorator));
    }

    // ---- 内部 SyntaxDecorator：在 RegexHighlighter 样式上叠加括号高亮 ----

    /**
     * 组合括号高亮的 {@link SyntaxDecorator} 实现。
     *
     * <p>对于不包含括号的段落完整委托给 {@link RegexHighlighter}；
     * 对于包含括号的段落，使用相同的分词策略重新构建 RichParagraph，
     * 通过 {@link RegexHighlighter#classifyToken} 获取每个 token 的正确样式，
     * 仅在括号字符位置覆盖为高亮样式。</p>
     */
    private static final class BracketSyntaxDecorator implements SyntaxDecorator {

        private final RegexHighlighter highlighter;
        private final int bracketPos1;
        private final int bracketPos2;
        private final StyleAttributeMap bracketStyle;

        BracketSyntaxDecorator(RegexHighlighter highlighter, int pos1, int pos2,
                               StyleAttributeMap bracketStyle) {
            this.highlighter = highlighter;
            this.bracketPos1 = pos1;
            this.bracketPos2 = pos2;
            this.bracketStyle = bracketStyle;
        }

        /**
         * 将一段文本追加到 builder。若该段包含括号位置，则拆分为
         * [前缀(tokenStyle)] [括号(bracketStyle)] [后缀(tokenStyle)] 三段。
         */
        private static void appendWithBracketOverride(RichParagraph.Builder builder,
                                                      String segment, int segmentStart,
                                                      int local1, int local2,
                                                      StyleAttributeMap bracketStyle,
                                                      StyleAttributeMap tokenStyle) {
            int segmentEnd = segmentStart + segment.length();
            int bracket1 = toLocal(local1, segmentStart, segmentEnd);
            int bracket2 = toLocal(local2, segmentStart, segmentEnd);

            if (bracket1 < 0 && bracket2 < 0) {
                builder.addSegment(segment, tokenStyle);
                return;
            }

            // 收集本段内的括号位置（去重 + 排序）
            List<Integer> positions = new ArrayList<>(2);
            if (bracket1 >= 0) {
                positions.add(bracket1);
            }
            if (bracket2 >= 0 && bracket2 != bracket1) {
                positions.add(bracket2);
            }
            positions.sort(null);

            int prev = 0;
            for (int bp : positions) {
                if (bp > prev) {
                    builder.addSegment(segment.substring(prev, bp), tokenStyle);
                }
                // 括号字符本身（长度固定为 1）
                builder.addSegment(segment.substring(bp, bp + 1), bracketStyle);
                prev = bp + 1;
            }
            if (prev < segment.length()) {
                builder.addSegment(segment.substring(prev), tokenStyle);
            }
        }

        /** 确定当前 matcher 匹配到的捕获组名 */
        private static String extractGroupName(Matcher matcher) {
            for (String name : new String[]{"MC", "SC", "STR", "ANN", "NUM", "ID"}) {
                if (matcher.group(name) != null) {
                    return name;
                }
            }
            return null;
        }

        /** 计算段落起始位置的文档偏移量 */
        private static int computeParagraphOffset(CodeTextModel model, int paragraphIndex) {
            int offset = 0;
            for (int i = 0; i < paragraphIndex; i++) {
                String paragraphText = model.getPlainText(i);
                offset += (paragraphText != null ? paragraphText.length() : 0) + 1;
            }
            return offset;
        }

        /** 若文档偏移 p 在 [rangeStart, rangeEnd) 内，返回局部偏移；否则返回 -1 */
        private static int toLocal(int p, int rangeStart, int rangeEnd) {
            if (p >= rangeStart && p < rangeEnd) {
                return p - rangeStart;
            }
            return -1;
        }

        @Override
        public RichParagraph createRichParagraph(CodeTextModel model, int paragraphIndex) {
            int paragraphOffset = computeParagraphOffset(model, paragraphIndex);
            String text = model.getPlainText(paragraphIndex);
            if (text == null || text.isEmpty()) {
                return RichParagraph.builder().build();
            }
            int paragraphEnd = paragraphOffset + text.length();
            int localPos1 = toLocal(bracketPos1, paragraphOffset, paragraphEnd);
            int localPos2 = toLocal(bracketPos2, paragraphOffset, paragraphEnd);

            // 本段落不含括号 → 完整委托
            if (localPos1 < 0 && localPos2 < 0) {
                return highlighter.createRichParagraph(model, paragraphIndex);
            }

            // 本段落含括号 → 重新分词，正确应用 token 样式 + 括号高亮
            return buildParagraphWithBrackets(text, localPos1, localPos2, bracketStyle);
        }

        /** 分词并构建带括号高亮的段落 */
        private RichParagraph buildParagraphWithBrackets(String text, int local1, int local2,
                                                         StyleAttributeMap bracketStyle) {
            RichParagraph.Builder builder = RichParagraph.builder();
            Matcher matcher = TOKEN_PATTERN.matcher(text);
            int lastEnd = 0;

            while (matcher.find()) {
                if (matcher.start() > lastEnd) {
                    String before = text.substring(lastEnd, matcher.start());
                    StyleAttributeMap tokenStyle = highlighter.classifyToken(
                            before, null, text, matcher.start(), lastEnd);
                    appendWithBracketOverride(builder, before, lastEnd,
                            local1, local2, bracketStyle, tokenStyle);
                }

                String token = matcher.group();
                String groupName = extractGroupName(matcher);
                int tokenStart = matcher.start();
                StyleAttributeMap tokenStyle = highlighter.classifyToken(
                        token, groupName, text, matcher.end(), tokenStart);
                appendWithBracketOverride(builder, token, tokenStart,
                        local1, local2, bracketStyle, tokenStyle);
                lastEnd = matcher.end();
            }

            if (lastEnd < text.length()) {
                String remaining = text.substring(lastEnd);
                StyleAttributeMap tokenStyle = highlighter.classifyToken(
                        remaining, null, text, text.length(), lastEnd);
                appendWithBracketOverride(builder, remaining, lastEnd,
                        local1, local2, bracketStyle, tokenStyle);
            }

            return builder.build();
        }

        @Override
        public void handleChange(CodeTextModel model, TextPos start, TextPos end,
                                 int linesRemoved, int linesAdded, int charIndex) {
            // 只读编辑器，不处理变更
        }
    }
}
