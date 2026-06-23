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
 * 字节码视图语法高亮器，用于 jadx 风格的字节码输出。
 *
 * <p>高亮元素：标题行、结构头（magic/flags/this_class/Constant pool 等）、
 * hex 地址、hex 字节、指令偏移（|xxxx:）、操作码、常量池引用（#N）、
 * 注释、字符串</p>
 *
 * @author bingbaihanji
 * @date 2026-06-23
 */
final class BytecodeHighlighter implements SyntaxDecorator {

    private static final Color DEFAULT_TEXT = Color.web("#9aa7b0");
    private static final Color HEADER = Color.web("#4ec9b0");
    private static final Color DIRECTIVE = Color.web("#c586c0");
    private static final Color OPCODE = Color.web("#569cd6");
    private static final Color HEX_ADDR = Color.web("#dcdcaa");
    private static final Color HEX_BYTES = Color.web("#9cdcfe");
    private static final Color COMMENT = Color.web("#6a9955");
    private static final Color STRING = Color.web("#ce9178");
    private static final Color CP_REF = Color.web("#b5cea8");
    private static final Color INSN_OFF = Color.web("#d7ba7d");
    private static final Color NUMBER = Color.web("#b5cea8");
    private static final Color LABEL = Color.web("#e0e0e0");

    private static final StyleAttributeMap S_DEFAULT = builder(DEFAULT_TEXT);
    private static final StyleAttributeMap S_HEADER = builder(HEADER);
    private static final StyleAttributeMap S_DIRECTIVE = builder(DIRECTIVE);
    private static final StyleAttributeMap S_OPCODE = builder(OPCODE);
    private static final StyleAttributeMap S_HEX_ADDR = builder(HEX_ADDR);
    private static final StyleAttributeMap S_HEX_BYTES = builder(HEX_BYTES);
    private static final StyleAttributeMap S_COMMENT = builder(COMMENT);
    private static final StyleAttributeMap S_STRING = builder(STRING);
    private static final StyleAttributeMap S_CP_REF = builder(CP_REF);
    private static final StyleAttributeMap S_INSN_OFF = builder(INSN_OFF);
    private static final StyleAttributeMap S_NUMBER = builder(NUMBER);
    private static final StyleAttributeMap S_LABEL = builder(LABEL);

    /** 结构头标签 */
    private static final Pattern RE_HEADER = Pattern.compile(
            "^(?:######|magic|minor\\s+version|major\\s+version|flags|"
                    + "this_class|super_class|interfaces|"
                    + "Constant\\s+pool|Fields\\s+count|Methods\\s+count|Attributes|"
                    + "ConstantPool):");

    /** .method / .field / .end method 指令 */
    private static final Pattern RE_DIRECTIVE = Pattern.compile(
            "\\.(?:method|end\\s+method|field|end\\s+field|line|local|registers|"
                    + "param|max|class|super|source|implements|prologue|epilogue|"
                    + "catch|catchall)");

    /** JVM 操作码（小写） */
    private static final Pattern RE_OPCODE = Pattern.compile(
            "\\b(?:aaload|aastore|aconst_null|aload|aload_0|aload_1|aload_2|aload_3|"
                    + "anewarray|areturn|arraylength|astore|astore_0|astore_1|astore_2|astore_3|"
                    + "athrow|baload|bastore|bipush|breakpoint|caload|castore|"
                    + "checkcast|d2f|d2i|d2l|dadd|daload|dastore|dcmpg|dcmpl|"
                    + "dconst_0|dconst_1|ddiv|dload|dload_0|dload_1|dload_2|dload_3|"
                    + "dmul|dneg|drem|dreturn|dstore|dstore_0|dstore_1|dstore_2|dstore_3|"
                    + "dup|dup_x1|dup_x2|dup2|dup2_x1|dup2_x2|"
                    + "f2d|f2i|f2l|fadd|faload|fastore|fcmpg|fcmpl|"
                    + "fconst_0|fconst_1|fconst_2|fdiv|fload|fload_0|fload_1|fload_2|fload_3|"
                    + "fmul|fneg|frem|freturn|fstore|fstore_0|fstore_1|fstore_2|fstore_3|"
                    + "getfield|getstatic|goto|goto_w|"
                    + "i2b|i2c|i2d|i2f|i2l|i2s|iadd|iaload|iand|iastore|iconst_0|iconst_1|"
                    + "iconst_2|iconst_3|iconst_4|iconst_5|iconst_m1|idiv|"
                    + "if_acmpeq|if_acmpne|if_icmpeq|if_icmpge|if_icmpgt|if_icmple|if_icmplt|if_icmpne|"
                    + "ifeq|ifge|ifgt|ifle|iflt|ifne|ifnonnull|ifnull|"
                    + "iinc|iload|iload_0|iload_1|iload_2|iload_3|"
                    + "impdep1|impdep2|imul|ineg|instanceof|invokedynamic|invokeinterface|invokespecial|"
                    + "invokestatic|invokevirtual|ior|irem|ireturn|ishl|ishr|istore|"
                    + "istore_0|istore_1|istore_2|istore_3|isub|iushr|ixor|"
                    + "jsr|jsr_w|l2d|l2f|l2i|ladd|laload|land|lastore|lcmp|lconst_0|lconst_1|"
                    + "ldc|ldc_w|ldc2_w|ldiv|lload|lload_0|lload_1|lload_2|lload_3|"
                    + "lmul|lneg|lookupswitch|lor|lrem|lreturn|lshl|lshr|lstore|"
                    + "lstore_0|lstore_1|lstore_2|lstore_3|lsub|lushr|lxor|"
                    + "monitorenter|monitorexit|multianewarray|new|newarray|nop|pop|pop2|"
                    + "putfield|putstatic|ret|return|saload|sastore|sipush|swap|"
                    + "tableswitch|wide|unknown_\\d+)\\b");

    /** 文件 hex 偏移地址: 8hex: */
    private static final Pattern RE_HEX_ADDR = Pattern.compile(
            "^\\s{4,}([0-9a-fA-F]{8}):");

    /** hex 字节序列: 2hex空格2hex... */
    private static final Pattern RE_HEX_BYTES = Pattern.compile(
            "\\b([0-9a-fA-F]{2}(?: [0-9a-fA-F]{2})*)");

    /** 指令 PC 偏移: |xxxx: */
    private static final Pattern RE_INSN_OFF = Pattern.compile(
            "\\|[0-9a-fA-F]{4}:");

    /** 常量池引用: #N */
    private static final Pattern RE_CP_REF = Pattern.compile("#\\d+");

    /** 数字（独立数字） */
    private static final Pattern RE_NUMBER = Pattern.compile(
            "(?<![.#\\w-])(?:0x[0-9a-fA-F]+|-?\\d+(?:\\.\\d+)?[fFLl]?)(?![.#\\w])");

    /** ACC_ 标志 */
    private static final Pattern RE_FLAG = Pattern.compile(
            "ACC_[A-Z_]+");

    private static StyleAttributeMap builder(Color c) {
        return StyleAttributeMap.builder().setTextColor(c).build();
    }

    private static StyleAttributeMap styleFor(Kind kind) {
        return switch (kind) {
            case HEADER -> S_HEADER;
            case DIRECTIVE -> S_DIRECTIVE;
            case OPCODE -> S_OPCODE;
            case HEX_ADDR -> S_HEX_ADDR;
            case HEX_BYTES -> S_HEX_BYTES;
            case INSN_OFF -> S_INSN_OFF;
            case COMMENT -> S_COMMENT;
            case STRING -> S_STRING;
            case CP_REF -> S_CP_REF;
            case NUMBER -> S_NUMBER;
            case LABEL -> S_LABEL;
            default -> S_DEFAULT;
        };
    }

    @Override
    public RichParagraph createRichParagraph(CodeTextModel model, int paragraphIndex) {
        String line = model.getPlainText(paragraphIndex);
        if (line == null || line.isEmpty()) return RichParagraph.builder().build();

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

    private List<Token> tokenize(String line) {
        List<Token> tokens = new ArrayList<>();
        int pos = 0;
        int len = line.length();

        // 先检查结构头行（整行判定）
        Matcher hm = RE_HEADER.matcher(line);
        if (hm.find() && hm.start() <= 4) {
            tokens.add(new Token(Kind.HEADER, line.substring(0, hm.end())));
            pos = hm.end();
        }

        // 标题行（######）
        if (pos == 0 && line.startsWith("######")) {
            tokens.add(new Token(Kind.HEADER, line));
            return tokens;
        }

        // 注释行（行首 #）
        if (pos < len && line.charAt(pos) == '#' && (pos == 0 || Character.isWhitespace(line.charAt(pos - 1)))) {
            tokens.add(new Token(Kind.COMMENT, line.substring(pos)));
            return tokens;
        }

        // 整行注释（只有缩进 + #）
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("#") || trimmed.startsWith(";") || trimmed.startsWith("//")) {
            tokens.add(new Token(Kind.COMMENT, line));
            return tokens;
        }

        while (pos < len) {
            // 空白
            if (Character.isWhitespace(line.charAt(pos))) {
                int end = pos + 1;
                while (end < len && Character.isWhitespace(line.charAt(end))) end++;
                tokens.add(new Token(Kind.DEFAULT, line.substring(pos, end)));
                pos = end;
                continue;
            }

            // 管道分隔符 |xxxx:
            Matcher io = RE_INSN_OFF.matcher(line);
            if (io.find(pos) && io.start() == pos) {
                tokens.add(new Token(Kind.INSN_OFF, io.group()));
                pos = io.end();
                continue;
            }

            // hex 地址（缩进后的 8hex:）
            Matcher ha = RE_HEX_ADDR.matcher(line);
            if (ha.find(pos) && ha.start() == pos) {
                tokens.add(new Token(Kind.HEX_ADDR, ha.group()));
                pos = ha.end();
                continue;
            }

            // hex 字节
            Matcher hb = RE_HEX_BYTES.matcher(line);
            if (hb.find(pos) && hb.start() == pos && Character.isLetterOrDigit(line.charAt(pos))) {
                // 确保匹配的是 hex 字节序列
                String match = hb.group();
                if (match.matches("^[0-9a-fA-F]{2}( [0-9a-fA-F]{2})*$")) {
                    tokens.add(new Token(Kind.HEX_BYTES, match));
                    pos = hb.end();
                    continue;
                }
            }

            // .directive
            Matcher dm = RE_DIRECTIVE.matcher(line);
            if (dm.find(pos) && dm.start() == pos) {
                tokens.add(new Token(Kind.DIRECTIVE, dm.group()));
                pos = dm.end();
                continue;
            }

            // 操作码
            Matcher om = RE_OPCODE.matcher(line);
            if (om.find(pos) && om.start() == pos) {
                tokens.add(new Token(Kind.OPCODE, om.group()));
                pos = om.end();
                continue;
            }

            // ACC_ 标志
            Matcher fm = RE_FLAG.matcher(line);
            if (fm.find(pos) && fm.start() == pos) {
                tokens.add(new Token(Kind.DEFAULT, fm.group()));
                pos = fm.end();
                continue;
            }

            // 常量池引用
            Matcher cr = RE_CP_REF.matcher(line);
            if (cr.find(pos) && cr.start() == pos) {
                tokens.add(new Token(Kind.CP_REF, cr.group()));
                pos = cr.end();
                continue;
            }

            // 数字
            Matcher nm = RE_NUMBER.matcher(line);
            if (nm.find(pos) && nm.start() == pos) {
                tokens.add(new Token(Kind.NUMBER, nm.group()));
                pos = nm.end();
                continue;
            }

            // 字符串
            if (line.charAt(pos) == '"') {
                int end = pos + 1;
                while (end < len && line.charAt(end) != '"') {
                    if (line.charAt(end) == '\\') end++;
                    end++;
                }
                if (end < len) end++;
                tokens.add(new Token(Kind.STRING, line.substring(pos, end)));
                pos = end;
                continue;
            }

            // 行内注释
            if (line.charAt(pos) == '#' || line.charAt(pos) == ';') {
                tokens.add(new Token(Kind.COMMENT, line.substring(pos)));
                return tokens;
            }

            // 标签引用
            if (line.charAt(pos) == '→') {
                int end = pos + 1;
                while (end < len && !Character.isWhitespace(line.charAt(end))) end++;
                tokens.add(new Token(Kind.LABEL, line.substring(pos, end)));
                pos = end;
                continue;
            }

            // 默认文本块
            int end = pos + 1;
            while (end < len && !isSpecial(line, end)) {
                end++;
            }
            tokens.add(new Token(Kind.DEFAULT, line.substring(pos, end)));
            pos = end;
        }

        return tokens;
    }

    private boolean isSpecial(String line, int pos) {
        if (pos >= line.length()) return false;
        char c = line.charAt(pos);
        return c == '#' || c == '"' || c == '.' || c == ';' || c == '|'
                || c == '→' || Character.isWhitespace(c)
                || Character.isDigit(c) || Character.isLetter(c);
    }

    @Override
    public void handleChange(CodeTextModel model, TextPos start, TextPos end,
                             int linesRemoved, int linesAdded, int charIndex) {
    }

    private enum Kind {
        HEADER, DIRECTIVE, OPCODE, HEX_ADDR, HEX_BYTES, INSN_OFF,
        COMMENT, STRING, CP_REF, NUMBER, LABEL, DEFAULT
    }

    private record Token(Kind kind, String text) {
    }
}
