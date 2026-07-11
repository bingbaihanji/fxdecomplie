package com.bingbaihanji.fxdecomplie.core.jadx.core.deobf;

import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils.notEmpty;

/**
 * 名称映射工具类，提供 Java 标识符验证、可打印字符检查以及无效字符移除等功能。
 */
public class NameMapper {

    /** 匹配单个有效的 Java 标识符（不含点号） */
    public static final Pattern VALID_JAVA_IDENTIFIER = Pattern.compile(
            "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");

    /** 匹配有效的 Java 全限定标识符（以点号分隔的多段标识符） */
    private static final Pattern VALID_JAVA_FULL_IDENTIFIER = Pattern.compile(
            "(" + VALID_JAVA_IDENTIFIER + "\\.)*" + VALID_JAVA_IDENTIFIER);

    /** Java 语言保留字（关键字）集合，不能用作标识符 */
    private static final Set<String> RESERVED_NAMES = new HashSet<>(
            Arrays.asList(
                    "_",
                    "abstract",
                    "assert",
                    "boolean",
                    "break",
                    "byte",
                    "case",
                    "catch",
                    "char",
                    "class",
                    "const",
                    "continue",
                    "default",
                    "do",
                    "double",
                    "else",
                    "enum",
                    "extends",
                    "false",
                    "final",
                    "finally",
                    "float",
                    "for",
                    "goto",
                    "if",
                    "implements",
                    "import",
                    "instanceof",
                    "int",
                    "interface",
                    "long",
                    "native",
                    "new",
                    "null",
                    "package",
                    "private",
                    "protected",
                    "public",
                    "return",
                    "short",
                    "static",
                    "strictfp",
                    "super",
                    "switch",
                    "synchronized",
                    "this",
                    "throw",
                    "throws",
                    "transient",
                    "true",
                    "try",
                    "void",
                    "volatile",
                    "while"));

    /** 私有构造方法，禁止实例化（本类为工具类，仅提供静态方法） */
    private NameMapper() {
    }

    /**
     * 判断给定字符串是否为 Java 保留字（关键字）。
     *
     * @param str 待检查的字符串
     * @return 若为保留字则返回 true
     */
    public static boolean isReserved(String str) {
        return RESERVED_NAMES.contains(str);
    }

    /**
     * 判断给定字符串是否为有效的 Java 标识符（非空、非保留字且符合标识符规则）。
     *
     * @param str 待检查的字符串
     * @return 若为有效标识符则返回 true
     */
    public static boolean isValidIdentifier(String str) {
        return notEmpty(str)
                && !isReserved(str)
                && VALID_JAVA_IDENTIFIER.matcher(str).matches();
    }

    /**
     * 判断给定字符串是否为有效的 Java 全限定标识符（以点号分隔的多段标识符）。
     *
     * @param str 待检查的字符串
     * @return 若为有效的全限定标识符则返回 true
     */
    public static boolean isValidFullIdentifier(String str) {
        return notEmpty(str)
                && !isReserved(str)
                && VALID_JAVA_FULL_IDENTIFIER.matcher(str).matches();
    }

    /**
     * 判断给定字符串是否既是有效标识符又全部由可打印字符组成。
     *
     * @param str 待检查的字符串
     * @return 若有效且全部可打印则返回 true
     */
    public static boolean isValidAndPrintable(String str) {
        return isValidIdentifier(str) && isAllCharsPrintable(str);
    }

    /**
     * 判断给定码点是否可作为 Java 标识符的起始字符。
     *
     * @param codePoint Unicode 码点
     * @return 若可作为标识符起始字符则返回 true
     */
    public static boolean isValidIdentifierStart(int codePoint) {
        return Character.isJavaIdentifierStart(codePoint);
    }

    /**
     * 判断给定码点是否可作为 Java 标识符的组成部分。
     *
     * @param codePoint Unicode 码点
     * @return 若可作为标识符组成部分则返回 true
     */
    public static boolean isValidIdentifierPart(int codePoint) {
        return Character.isJavaIdentifierPart(codePoint);
    }

    /**
     * 判断给定字符是否为可打印的 ASCII 字符（范围 32~126）。
     *
     * @param c 待检查的字符
     * @return 若为可打印字符则返回 true
     */
    public static boolean isPrintableChar(char c) {
        return 32 <= c && c <= 126;
    }

    /**
     * 判断给定码点是否为可打印的 ASCII 码点（范围 32~126）。
     *
     * @param c 待检查的码点
     * @return 若为可打印 ASCII 码点则返回 true
     */
    public static boolean isPrintableAsciiCodePoint(int c) {
        return 32 <= c && c <= 126;
    }

    /**
     * 判断给定码点是否可打印（排除控制字符、格式字符、私有区、代理区及未分配字符，
     * 空白字符中仅允许标准空格）。
     *
     * @param codePoint Unicode 码点
     * @return 若可打印则返回 true
     */
    public static boolean isPrintableCodePoint(int codePoint) {
        if (Character.isISOControl(codePoint)) {
            return false;
        }
        if (Character.isWhitespace(codePoint)) {
            // 除标准空格外，不打印其他空白字符
            return codePoint == ' ';
        }
        switch (Character.getType(codePoint)) {
            case Character.CONTROL:
            case Character.FORMAT:
            case Character.PRIVATE_USE:
            case Character.SURROGATE:
            case Character.UNASSIGNED:
                return false;
        }
        return true;
    }

    /**
     * 判断给定字符串是否全部由可打印的 ASCII 字符组成。
     *
     * @param str 待检查的字符串
     * @return 若全部字符可打印则返回 true
     */
    public static boolean isAllCharsPrintable(String str) {
        int len = str.length();
        int offset = 0;
        while (offset < len) {
            int codePoint = str.codePointAt(offset);
            if (!isPrintableAsciiCodePoint(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    /**
     * 返回移除以下字符后的字符串：
     * <ul>
     * <li>不可打印字符（包括 Unicode 字符）
     * <li>不能作为 Java 标识符组成部分的字符
     * </ul>
     * 注意：此“中间段”方法必须与带前缀的字符串配合使用：
     * <ul>
     * <li>可能保留对 Java 标识符起始位置无效的字符（例如数字）
     * <li>结果不会检查是否为保留字
     * </ul>
     */
    public static String removeInvalidCharsMiddle(String name) {
        if (isValidIdentifier(name) && isAllCharsPrintable(name)) {
            return name;
        }
        int len = name.length();
        StringBuilder sb = new StringBuilder(len);
        CodegenEscapeUtils.visitCodePoints(name, codePoint -> {
            if (isPrintableAsciiCodePoint(codePoint) && isValidIdentifierPart(codePoint)) {
                sb.appendCodePoint(codePoint);
            }
        });
        return sb.toString();
    }

    /**
     * 返回移除无效字符后的字符串，参见 {@link #removeInvalidCharsMiddle}。
     * <p>
     * 若首字符不能作为 Java 标识符的起始字符，则在前面加上前缀。
     */
    public static String removeInvalidChars(String name, String prefix) {
        String result = removeInvalidCharsMiddle(name);
        if (!result.isEmpty()) {
            int codePoint = result.codePointAt(0);
            if (!isValidIdentifierStart(codePoint)) {
                return prefix + result;
            }
        }
        return result;
    }

    /**
     * 返回移除所有不可打印字符后的字符串（仅保留可打印的 ASCII 字符）。
     *
     * @param name 原始字符串
     * @return 移除不可打印字符后的字符串
     */
    public static String removeNonPrintableCharacters(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        CodegenEscapeUtils.visitCodePoints(name, codePoint -> {
            if (isPrintableAsciiCodePoint(codePoint)) {
                sb.appendCodePoint(codePoint);
            }
        });
        return sb.toString();
    }
}
