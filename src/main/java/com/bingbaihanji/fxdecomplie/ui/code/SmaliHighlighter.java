package com.bingbaihanji.fxdecomplie.ui.code;

import javafx.scene.paint.Color;
import jfx.incubator.scene.control.richtext.SyntaxDecorator;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.RichParagraph;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Smali 视图语法高亮器,用于 jadx 风格的 smali 输出
 *
 * <p>高亮元素：标题行(######)、注释(# 开头)、smali 指令关键字
 * (.class/.method/.field/.registers 等)、smali 风格操作码
 * (invoke-virtual/const/4/return-void 等)、字符串和数字</p>
 *
 * @author bingbaihanji
 * @date 2026-06-23
 */
final class SmaliHighlighter implements SyntaxDecorator {

    private static final Color DEFAULT_TEXT = Color.web("#9aa7b0");
    private static final Color DIRECTIVE = Color.web("#c586c0");
    private static final Color OPCODE = Color.web("#569cd6");
    private static final Color COMMENT = Color.web("#6a9955");
    private static final Color STRING = Color.web("#ce9178");
    private static final Color HEADER = Color.web("#4ec9b0");
    private static final Color NUMBER = Color.web("#b5cea8");
    private static final Color LABEL = Color.web("#dcdcaa");

    private static final StyleAttributeMap S_DEFAULT = builder(DEFAULT_TEXT);
    private static final StyleAttributeMap S_DIRECTIVE = builder(DIRECTIVE);
    private static final StyleAttributeMap S_OPCODE = builder(OPCODE);
    private static final StyleAttributeMap S_COMMENT = builder(COMMENT);
    private static final StyleAttributeMap S_STRING = builder(STRING);
    private static final StyleAttributeMap S_HEADER = builder(HEADER);
    private static final StyleAttributeMap S_NUMBER = builder(NUMBER);
    private static final StyleAttributeMap S_LABEL = builder(LABEL);

    /** smali 指令关键字(.class / .method / .field 等) */
    private static final Pattern RE_DIRECTIVE = Pattern.compile(
            "\\.(?:class|super|source|implements|method|end\\s+method|field|end\\s+field|"
                    + "registers|line|local|end\\s+local|prologue|epilogue|annotation|"
                    + "end\\s+annotation|param|catch|catchall|restart\\s+local|array-data|"
                    + "end\\s+array-data|packed-switch|end\\s+packed-switch|"
                    + "sparse-switch|end\\s+sparse-switch|ver)");

    /** 标签引用(:label_?? 或 :cond_? 格式) */
    private static final Pattern RE_LABEL = Pattern.compile(":[a-zA-Z_]\\w*");

    /** smali 风格操作码(带斜杠变体和连字符变体) */
    private static final Pattern RE_OPCODE = Pattern.compile(
            "\\b(?:const/4|const/16|const/high16|const-wide/16|const-wide/32|const-wide/high16|"
                    + "const-string|const-string/jumbo|const-class|const|const-wide|"
                    + "move|move-wide|move-object|move/16|move-wide/16|move-object/16|"
                    + "move/from16|move-wide/from16|move-object/from16|"
                    + "move-result|move-result-wide|move-result-object|move-exception|"
                    + "return-void|return|return-wide|return-object|"
                    + "invoke-virtual|invoke-super|invoke-direct|invoke-static|invoke-interface|"
                    + "invoke-virtual/range|invoke-super/range|invoke-direct/range|"
                    + "invoke-static/range|invoke-interface/range|invoke-dynamic|invoke-dynamic/range|"
                    + "filled-new-array|filled-new-array/range|"
                    + "new-instance|new-array|"
                    + "check-cast|instance-of|"
                    + "array-length|fill-array-data|"
                    + "throw|"
                    + "monitor-enter|monitor-exit|"
                    + "neg-int|not-int|neg-long|not-long|neg-float|neg-double|"
                    + "int-to-long|int-to-float|int-to-double|"
                    + "long-to-int|long-to-float|long-to-double|"
                    + "float-to-int|float-to-long|float-to-double|"
                    + "double-to-int|double-to-long|double-to-float|"
                    + "int-to-byte|int-to-char|int-to-short|"
                    + "add-int|add-long|add-float|add-double|"
                    + "sub-int|sub-long|sub-float|sub-double|"
                    + "mul-int|mul-long|mul-float|mul-double|"
                    + "div-int|div-long|div-float|div-double|"
                    + "rem-int|rem-long|rem-float|rem-double|"
                    + "and-int|and-long|or-int|or-long|xor-int|xor-long|"
                    + "shl-int|shl-long|shr-int|shr-long|ushr-int|ushr-long|"
                    + "add-int/2addr|add-long/2addr|add-float/2addr|add-double/2addr|"
                    + "sub-int/2addr|sub-long/2addr|sub-float/2addr|sub-double/2addr|"
                    + "mul-int/2addr|mul-long/2addr|mul-float/2addr|mul-double/2addr|"
                    + "div-int/2addr|div-long/2addr|div-float/2addr|div-double/2addr|"
                    + "rem-int/2addr|rem-long/2addr|rem-float/2addr|rem-double/2addr|"
                    + "and-int/2addr|and-long/2addr|or-int/2addr|or-long/2addr|"
                    + "xor-int/2addr|xor-long/2addr|"
                    + "shl-int/2addr|shl-long/2addr|shr-int/2addr|shr-long/2addr|"
                    + "ushr-int/2addr|ushr-long/2addr|"
                    + "add-int/lit8|add-int/lit16|add-int/lit32|"
                    + "rsub-int|rsub-int/lit8|"
                    + "mul-int/lit8|mul-int/lit16|"
                    + "div-int/lit8|div-int/lit16|"
                    + "rem-int/lit8|rem-int/lit16|"
                    + "and-int/lit8|and-int/lit16|"
                    + "or-int/lit8|or-int/lit16|"
                    + "xor-int/lit8|xor-int/lit16|"
                    + "shl-int/lit8|shr-int/lit8|ushr-int/lit8|"
                    + "if-eq|if-ne|if-lt|if-ge|if-gt|if-le|"
                    + "if-eqz|if-nez|if-ltz|if-gez|if-gtz|if-lez|"
                    + "goto|goto/16|goto/32|"
                    + "nop|aget|aget-wide|aget-object|aget-boolean|aget-byte|aget-char|aget-short|"
                    + "aput|aput-wide|aput-object|aput-boolean|aput-byte|aput-char|aput-short|"
                    + "iget|iget-wide|iget-object|iget-boolean|iget-byte|iget-char|iget-short|"
                    + "iput|iput-wide|iput-object|iput-boolean|iput-byte|iput-char|iput-short|"
                    + "sget|sget-wide|sget-object|sget-boolean|sget-byte|sget-char|sget-short|"
                    + "sput|sput-wide|sput-object|sput-boolean|sput-byte|sput-char|sput-short|"
                    + "cmp-long|cmpg-float|cmpg-double|cmpl-float|cmpl-double|"
                    + "sparse-switch|packed-switch|"
                    + "iget-quick|iget-wide-quick|iget-object-quick|"
                    + "iput-quick|iput-wide-quick|iput-object-quick|"
                    + "execute-inline|invoke-object-init/range|invoke-direct-empty|"
                    + "throw-verification-error)\\b");

    /** 数字(hex 和十进制) */
    private static final Pattern RE_NUMBER = Pattern.compile(
            "(?<![.#\\w-])(?:0x[0-9a-fA-F]+|-?\\d+(?:\\.\\d+)?[fFLl]?)(?![.#\\w])");

    /** 寄存器/参数引用 v0-vN, p0-pN */
    private static final Pattern RE_REGISTER = Pattern.compile(
            "\\b[vp]\\d+\\b");

    /** 根据颜色创建 StyleAttributeMap */
    private static StyleAttributeMap builder(Color c) {
        return StyleAttributeMap.builder().setTextColor(c).build();
    }

    /** 根据标记类型返回对应的样式属性 */
    private static StyleAttributeMap styleFor(Kind kind) {
        return switch (kind) {
            case DIRECTIVE -> S_DIRECTIVE;
            case OPCODE -> S_OPCODE;
            case COMMENT -> S_COMMENT;
            case STRING -> S_STRING;
            case HEADER -> S_HEADER;
            case NUMBER -> S_NUMBER;
            case LABEL -> S_LABEL;
            default -> S_DEFAULT;
        };
    }

    /** 根据语法标记列表构建富文本段落；标题行整体高亮,其他行按 token 着色 */
    @Override
    public RichParagraph createRichParagraph(CodeTextModel model, int paragraphIndex) {
        String line = model.getPlainText(paragraphIndex);
        if (line == null || line.isEmpty()) {
            return RichParagraph.builder().build();
        }

        // 标题行(######)
        if (line.startsWith("######")) {
            return RichParagraph.builder().addSegment(line, S_HEADER).build();
        }

        List<Token> tokens = tokenize(line);
        if (tokens.isEmpty()) {
            return RichParagraph.builder().addSegment(line, S_DEFAULT).build();
        }

        RichParagraph.Builder builder = RichParagraph.builder();
        for (Token t : tokens) {
            builder.addSegment(t.text, styleFor(t.kind));
        }
        return builder.build();
    }

    /**
     * 对单行进行词法分析,按优先级匹配：注释、字符串、标签、指令、寄存器、数字、操作码、默认文本
     */
    private List<Token> tokenize(String line) {
        List<Token> tokens = new ArrayList<>();
        int pos = 0;
        int len = line.length();

        while (pos < len) {
            // 注释(# 开头,前面无 .xxx 指令)
            if (line.charAt(pos) == '#' && (pos == 0 || Character.isWhitespace(line.charAt(pos - 1))
                    || line.charAt(pos - 1) == ';')) {
                tokens.add(new Token(Kind.COMMENT, line.substring(pos)));
                return tokens;
            }

            // 字符串
            if (line.charAt(pos) == '"') {
                int end = pos + 1;
                while (end < len && line.charAt(end) != '"') {
                    if (line.charAt(end) == '\\') {
                        end++;
                    }
                    end++;
                }
                if (end < len) {
                    end++;
                }
                tokens.add(new Token(Kind.STRING, line.substring(pos, end)));
                pos = end;
                continue;
            }

            // 标签引用 :label_name
            Matcher lm = RE_LABEL.matcher(line);
            if (lm.find(pos) && lm.start() == pos) {
                tokens.add(new Token(Kind.LABEL, lm.group()));
                pos = lm.end();
                continue;
            }

            // smali 指令(.xxx)
            if (line.charAt(pos) == '.') {
                Matcher dm = RE_DIRECTIVE.matcher(line);
                if (dm.find(pos) && dm.start() == pos) {
                    tokens.add(new Token(Kind.DIRECTIVE, dm.group()));
                    pos = dm.end();
                    continue;
                }
                // 未识别的 .xxx 仍作为指令高亮
                int end = pos + 1;
                while (end < len && Character.isLetter(line.charAt(end))) {
                    end++;
                }
                tokens.add(new Token(Kind.DIRECTIVE, line.substring(pos, end)));
                pos = end;
                continue;
            }

            // 寄存器引用
            Matcher rm = RE_REGISTER.matcher(line);
            if (rm.find(pos) && rm.start() == pos) {
                tokens.add(new Token(Kind.DEFAULT, rm.group()));
                pos = rm.end();
                continue;
            }

            // 数字(hex 或十进制)
            Matcher nm = RE_NUMBER.matcher(line);
            if (nm.find(pos) && nm.start() == pos) {
                tokens.add(new Token(Kind.NUMBER, nm.group()));
                pos = nm.end();
                continue;
            }

            // smali 操作码
            Matcher om = RE_OPCODE.matcher(line);
            if (om.find(pos) && om.start() == pos) {
                tokens.add(new Token(Kind.OPCODE, om.group()));
                pos = om.end();
                continue;
            }

            // 默认文本块(一个连续的非特殊字符序列)
            int end = pos + 1;
            while (end < len && !isTokenStart(line, end)) {
                end++;
            }
            tokens.add(new Token(Kind.DEFAULT, line.substring(pos, end)));
            pos = end;
        }

        return tokens;
    }

    /** 判断指定位置是否为新 token 的起始字符 */
    private boolean isTokenStart(String line, int pos) {
        if (pos >= line.length()) {
            return false;
        }
        char c = line.charAt(pos);
        return c == '#' || c == '"' || c == '.' || c == ':'
                || c == 'v' || c == 'p' || c == 'L'
                || Character.isWhitespace(c);
    }

    /** 文本变更回调(Smali 视图不追踪增量变更,留空) */
    @Override
    public void handleChange(CodeTextModel model, TextPos start, TextPos end,
                             int linesRemoved, int linesAdded, int charIndex) {
    }

    /** 词法标记类型 */
    private enum Kind {DIRECTIVE, OPCODE, COMMENT, STRING, HEADER, NUMBER, LABEL, DEFAULT}

    /** 词法标记：类型 + 文本片段 */
    private record Token(Kind kind, String text) {
    }
}
