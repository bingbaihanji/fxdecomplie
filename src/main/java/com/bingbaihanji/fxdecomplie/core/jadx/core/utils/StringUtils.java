package com.bingbaihanji.fxdecomplie.core.jadx.core.utils;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.args.IntegerFormat;
import com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.NameMapper;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 字符串工具类，提供字符串转义、反转义、格式化等功能
 * 主要用于反编译过程中的字符串处理和数字格式化
 */
public class StringUtils {
    private static final StringUtils DEFAULT_INSTANCE = new StringUtils(new JadxArgs());
    /** 空白字符集合 */
    private static final String WHITES = " \t\r\n\f\b";
    /** 单词分隔符集合，包含空白字符和其他标点符号 */
    private static final String WORD_SEPARATORS = WHITES + "(\")<,>{}=+-*/|[]\\:;'.`~!#^&";
    private final boolean escapeUnicode;
    private final IntegerFormat integerFormat;
    /**
     * 构造函数
     * @param args Jadx 参数配置
     */
    public StringUtils(JadxArgs args) {
        this.escapeUnicode = args.isEscapeUnicode();
        this.integerFormat = args.getIntegerFormat();
    }

    /**
     * 获取单例实例
     * @return StringUtils 实例
     */
    public static StringUtils getInstance() {
        return DEFAULT_INSTANCE;
    }

    /**
     * 遍历字符串的所有码点
     * @param str 输入字符串
     * @param visitor 码点访问器
     */
    public static void visitCodePoints(String str, IntConsumer visitor) {
        int len = str.length();
        int offset = 0;
        while (offset < len) {
            int codePoint = str.codePointAt(offset);
            visitor.accept(codePoint);
            offset += Character.charCount(codePoint);
        }
    }

    /**
     * 转义字符串，将特殊字符替换为下划线
     * @param str 输入字符串
     * @return 转义后的字符串
     */
    public static String escape(String str) {
        int len = str.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '.':
                case '/':
                case ';':
                case '$':
                case ' ':
                case ',':
                case '<':
                    sb.append('_');
                    break;

                case '[':
                    sb.append('A');
                    break;

                case ']':
                case '>':
                case '?':
                case '*':
                    break;

                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * 转义 XML 特殊字符
     * @param str 输入字符串
     * @return XML 转义后的字符串
     */
    public static String escapeXML(String str) {
        int len = str.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            String replace = escapeXmlChar(c);
            if (replace != null) {
                sb.append(replace);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 转义资源值字符串
     * @param str 输入字符串
     * @return 转义后的资源值字符串
     */
    public static String escapeResValue(String str) {
        int len = str.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            commonEscapeAndAppend(sb, c);
        }
        return sb.toString();
    }

    /**
     * 转义资源字符串值，在通用资源值转义的基础上额外转义单引号和双引号
     *
     * @param str 输入字符串
     * @return 转义后的资源字符串值
     */
    public static String escapeResStrValue(String str) {
        int len = str.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\'':
                    sb.append("\\'");
                    break;
                default:
                    commonEscapeAndAppend(sb, c);
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * 将 XML 特殊字符转义为对应的实体或转义序列
     *
     * @param c 待转义的字符
     * @return 转义后的字符串，无需转义时返回 null
     */
    private static @Nullable String escapeXmlChar(char c) {
        if (c <= 0x1F) {
            return "\\" + (int) c;
        }
        switch (c) {
            case '&':
                return "&amp;";
            case '<':
                return "&lt;";
            case '>':
                return "&gt;";
            case '"':
                return "&quot;";
            case '\'':
                return "&apos;";
            case '\\':
                return "\\\\";
            default:
                return null;
        }
    }

    /**
     * 将空白字符转义为对应的转义序列
     *
     * @param c 待转义的字符
     * @return 转义后的字符串，非空白字符时返回 null
     */
    private static @Nullable String escapeWhiteSpaceChar(char c) {
        switch (c) {
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\t':
                return "\\t";
            case '\b':
                return "\\b";
            case '\f':
                return "\\f";
            default:
                return null;
        }
    }

    /**
     * 通用的字符转义并追加逻辑：优先按空白字符转义，其次按 XML 字符转义，否则原样追加
     *
     * @param sb 结果构建器
     * @param c  待处理的字符
     */
    private static void commonEscapeAndAppend(StringBuilder sb, char c) {
        String replace = escapeWhiteSpaceChar(c);
        if (replace == null) {
            replace = escapeXmlChar(c);
        }
        if (replace != null) {
            sb.append(replace);
        } else {
            sb.append(c);
        }
    }

    /**
     * 判断字符串是否不为 null 且非空
     *
     * @param str 待判断的字符串
     * @return 若非空则返回 true
     */
    public static boolean notEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    /**
     * 判断字符串是否为 null 或空
     *
     * @param str 待判断的字符串
     * @return 若为 null 或空则返回 true
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否非空白（不为 null、非空且去除首尾空白后非空）
     *
     * @param str 待判断的字符串
     * @return 若非空白则返回 true
     */
    public static boolean notBlank(String str) {
        return notEmpty(str) && !str.trim().isEmpty();
    }

    /**
     * 统计子串在字符串中出现的次数（不重叠匹配）
     *
     * @param str    源字符串
     * @param subStr 子串
     * @return 出现次数
     */
    public static int countMatches(String str, String subStr) {
        if (str == null || str.isEmpty() || subStr == null || subStr.isEmpty()) {
            return 0;
        }
        int subStrLen = subStr.length();
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(subStr, idx)) != -1) {
            count++;
            idx += subStrLen;
        }
        return count;
    }

    /**
     * 判断字符串是否包含指定字符
     *
     * @param str 源字符串
     * @param ch  目标字符
     * @return 若包含则返回 true
     */
    public static boolean containsChar(String str, char ch) {
        return str.indexOf(ch) != -1;
    }

    /**
     * 移除字符串中所有的指定字符
     *
     * @param str 源字符串
     * @param ch  待移除的字符
     * @return 移除后的字符串
     */
    public static String removeChar(String str, char ch) {
        int pos = str.indexOf(ch);
        if (pos == -1) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str.length());
        int cur = 0;
        int next = pos;
        while (true) {
            sb.append(str, cur, next);
            cur = next + 1;
            next = str.indexOf(ch, cur);
            if (next == -1) {
                sb.append(str, cur, str.length());
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 计算内容中从 start 到 pos 之间包含多少行（换行符数量）
     *
     * @param content 文本内容
     * @param pos     结束位置
     * @param start   起始位置
     * @return 两个位置之间的行数
     */
    public static int countLinesByPos(String content, int pos, int start) {
        if (start >= pos) {
            return 0;
        }
        int count = 0;
        int tempPos = start;
        do {
            tempPos = content.indexOf("\n", tempPos);
            if (tempPos == -1) {
                break;
            }
            if (tempPos >= pos) {
                break;
            }
            count += 1;
            tempPos += 1;
        } while (tempPos < content.length());
        return count;
    }

    /**
     * 返回包含 pos 到 end 位置的完整行内容（当 end 不为 -1 时）
     *
     * @param content 文本内容
     * @param pos     起始位置
     * @param end     结束位置，为 -1 时仅返回 pos 所在行
     * @return 包含指定位置区间的完整行文本
     */
    public static String getLine(String content, int pos, int end) {
        if (pos >= content.length()) {
            return "";
        }
        if (end != -1) {
            if (end > content.length()) {
                end = content.length() - 1;
            }
        } else {
            end = pos + 1;
        }
        // 定位到行首
        int headPos = content.lastIndexOf("\n", pos);
        if (headPos == -1) {
            headPos = 0;
        }
        // 定位到行尾
        int endPos = content.indexOf("\n", end);
        if (endPos == -1) {
            endPos = content.length();
        }
        return content.substring(headPos, endPos);
    }

    /**
     * 判断字符是否为空白字符
     *
     * @param chr 待判断的字符
     * @return 若为空白字符则返回 true
     */
    public static boolean isWhite(char chr) {
        return WHITES.indexOf(chr) != -1;
    }

    /**
     * 判断字符是否为单词分隔符
     *
     * @param chr 待判断的字符
     * @return 若为单词分隔符则返回 true
     */
    public static boolean isWordSeparator(char chr) {
        return WORD_SEPARATORS.indexOf(chr) != -1;
    }

    /**
     * 按固定分隔字符串拆分内容
     *
     * @param content  待拆分的内容
     * @param splitStr 分隔字符串
     * @return 拆分后的字符串列表，内容为空时返回空列表
     */
    public static List<String> splitByFixedString(String content, String splitStr) {
        if (isEmpty(content)) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        int splitLen = splitStr.length();
        int pos = 0;
        while (true) {
            int split = content.indexOf(splitStr, pos);
            if (split == -1) {
                parts.add(content.substring(pos));
                return parts;
            }
            parts.add(content.substring(pos, split));
            pos = split + splitLen;
        }
    }

    /**
     * 移除字符串末尾的指定后缀（若存在）
     *
     * @param str    源字符串
     * @param suffix 待移除的后缀
     * @return 移除后缀后的字符串
     */
    public static String removeSuffix(String str, String suffix) {
        if (str.endsWith(suffix)) {
            return str.substring(0, str.length() - suffix.length());
        }
        return str;
    }

    /**
     * 获取字符串中首个分隔符之前的前缀部分
     *
     * @param str   源字符串
     * @param delim 分隔符
     * @return 前缀字符串，未找到分隔符时返回 null
     */
    public static @Nullable String getPrefix(String str, String delim) {
        int idx = str.indexOf(delim);
        if (idx != -1) {
            return str.substring(0, idx);
        }
        return null;
    }

    /**
     * 获取当前时间的文本表示（格式：HH:mm:ss）
     *
     * @return 当前时间字符串
     */
    public static String getDateText() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    /**
     * 根据字节长度获取对应的类型转换前缀字符串
     *
     * @param bytesLen 数字类型的字节长度（1/2/4/8）
     * @return 类型转换前缀，如 "(byte) "
     * @throws JadxRuntimeException 当字节长度不受支持时抛出
     */
    private static String getCastStr(int bytesLen) {
        switch (bytesLen) {
            case 1:
                return "(byte) ";
            case 2:
                return "(short) ";
            case 4:
                return "(int) ";
            case 8:
                return "(long) ";
            default:
                throw new JadxRuntimeException("Unexpected number type length: " + bytesLen);
        }
    }

    /**
     * 将 double 值格式化为 Java 字面量表示，识别 NaN、无穷大和特殊常量
     *
     * @param d 待格式化的 double 值
     * @return 格式化后的字符串
     */
    public static String formatDouble(double d) {
        if (Double.isNaN(d)) {
            return "Double.NaN";
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return "Double.NEGATIVE_INFINITY";
        }
        if (d == Double.POSITIVE_INFINITY) {
            return "Double.POSITIVE_INFINITY";
        }
        if (d == Double.MIN_VALUE) {
            return "Double.MIN_VALUE";
        }
        if (d == Double.MAX_VALUE) {
            return "Double.MAX_VALUE";
        }
        if (d == Double.MIN_NORMAL) {
            return "Double.MIN_NORMAL";
        }
        return Double.toString(d) + 'd';
    }

    /**
     * 将 float 值格式化为 Java 字面量表示，识别 NaN、无穷大和特殊常量
     *
     * @param f 待格式化的 float 值
     * @return 格式化后的字符串
     */
    public static String formatFloat(float f) {
        if (Float.isNaN(f)) {
            return "Float.NaN";
        }
        if (f == Float.NEGATIVE_INFINITY) {
            return "Float.NEGATIVE_INFINITY";
        }
        if (f == Float.POSITIVE_INFINITY) {
            return "Float.POSITIVE_INFINITY";
        }
        if (f == Float.MIN_VALUE) {
            return "Float.MIN_VALUE";
        }
        if (f == Float.MAX_VALUE) {
            return "Float.MAX_VALUE";
        }
        if (f == Float.MIN_NORMAL) {
            return "Float.MIN_NORMAL";
        }
        return Float.toString(f) + 'f';
    }

    /**
     * 将字符串首字母转换为大写
     *
     * @param str 源字符串
     * @return 首字母大写后的字符串，空字符串时原样返回
     */
    public static String capitalizeFirstChar(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * 获取整数格式配置
     * @return 整数格式枚举
     */
    public IntegerFormat getIntegerFormat() {
        return integerFormat;
    }

    /**
     * 反转义字符串，将特殊字符转换为可读的转义序列
     * @param str 输入字符串
     * @return 转义后的字符串（带双引号）
     */
    public String unescapeString(String str) {
        int len = str.length();
        if (len == 0) {
            return "\"\"";
        }
        StringBuilder res = new StringBuilder();
        res.append('"');
        visitCodePoints(str, codePoint -> processCodePoint(codePoint, res));
        res.append('"');
        return res.toString();
    }

    /**
     * 处理单个码点，将其转换为转义序列
     * @param codePoint 码点值
     * @param res 结果构建器
     */
    private void processCodePoint(int codePoint, StringBuilder res) {
        String str = getSpecialStringForCodePoint(codePoint);
        if (str != null) {
            res.append(str);
            return;
        }
        if (isEscapeNeededForCodePoint(codePoint)) {
            res.append("\\u").append(String.format("%04x", codePoint));
        } else {
            res.appendCodePoint(codePoint);
        }
    }

    /**
     * 判断码点是否需要转义
     * @param codePoint 码点值
     * @return 如果需要转义返回 true
     */
    private boolean isEscapeNeededForCodePoint(int codePoint) {
        if (codePoint < 32) {
            return true;
        }
        if (codePoint < 127) {
            return false;
        }
        if (escapeUnicode) {
            return true;
        }
        return !NameMapper.isPrintableCodePoint(codePoint);
    }

    /**
     * 以最佳方式表示单个字符
     * @param c 字符
     * @param explicitCast 是否显式类型转换
     * @return 字符的字符串表示
     */
    public String unescapeChar(char c, boolean explicitCast) {
        if (c == '\'') {
            return "'\\''";
        }
        String str = getSpecialStringForCodePoint(c);
        if (str != null) {
            return '\'' + str + '\'';
        }
        if (c >= 127 && escapeUnicode) {
            return String.format("'\\u%04x'", (int) c);
        }
        if (NameMapper.isPrintableChar(c)) {
            return "'" + c + '\'';
        }
        String intStr = Integer.toString(c);
        return explicitCast ? "(char) " + intStr : intStr;
    }

    /**
     * 反转义字符（不使用显式类型转换）
     * @param ch 字符
     * @return 字符的字符串表示
     */
    public String unescapeChar(char ch) {
        return unescapeChar(ch, false);
    }

    /**
     * 获取码点对应的特殊转义字符串
     * @param c 码点值
     * @return 转义字符串，如果不需要特殊转义则返回 null
     */
    @Nullable
    private String getSpecialStringForCodePoint(int c) {
        switch (c) {
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\t':
                return "\\t";
            case '\b':
                return "\\b";
            case '\f':
                return "\\f";
            case '\'':
                return "'";
            case '"':
                return "\\\"";
            case '\\':
                return "\\\\";

            default:
                return null;
        }
    }

    /**
     * 根据整数格式配置格式化数字，可选择是否进行显式类型转换
     *
     * @param number   待格式化的数字
     * @param bytesLen 数字类型的字节长度（1/2/4/8）
     * @param cast     是否强制显式类型转换
     * @return 格式化后的数字字符串
     */
    private String formatNumber(long number, int bytesLen, boolean cast) {
        String numStr;
        if (integerFormat.isHexadecimal()) {
            String hexStr = Long.toHexString(number);
            if (number < 0) {
                // 截去负数前导的 'f'，使其与数字类型的长度匹配
                int len = hexStr.length();
                numStr = "0x" + hexStr.substring(len - bytesLen * 2, len);
                // 强制类型转换，因为无符号的负数比编译器允许的有符号最大值更大
                cast = true;
            } else {
                numStr = "0x" + hexStr;
            }
        } else {
            numStr = Long.toString(number);
        }
        if (bytesLen == 8 && (number == Long.MIN_VALUE || Math.abs(number) >= Integer.MAX_VALUE)) {
            // 对超过 int 最小/最大值的 long 值强制类型转换，
            // 以解决编译错误："integer number too large"
            cast = true;
        }
        if (cast) {
            if (bytesLen == 8) {
                return numStr + 'L';
            }
            return getCastStr(bytesLen) + numStr;
        }
        return numStr;
    }

    /**
     * 将数值格式化为 byte 类型字面量表示
     *
     * @param l    数值
     * @param cast 是否强制显式类型转换
     * @return 格式化后的字符串
     */
    public String formatByte(long l, boolean cast) {
        return formatNumber(l, 1, cast);
    }

    /**
     * 将数值格式化为 short 类型字面量表示，AUTO 格式下识别 Short 的最大/最小值常量
     *
     * @param l    数值
     * @param cast 是否强制显式类型转换
     * @return 格式化后的字符串
     */
    public String formatShort(long l, boolean cast) {
        if (integerFormat == IntegerFormat.AUTO) {
            switch ((short) l) {
                case Short.MAX_VALUE:
                    return "Short.MAX_VALUE";
                case Short.MIN_VALUE:
                    return "Short.MIN_VALUE";
            }
        }
        return formatNumber(l, 2, cast);
    }

    /**
     * 将数值格式化为 int 类型字面量表示，AUTO 格式下识别 Integer 的最大/最小值常量
     *
     * @param l    数值
     * @param cast 是否强制显式类型转换
     * @return 格式化后的字符串
     */
    public String formatInteger(long l, boolean cast) {
        if (integerFormat == IntegerFormat.AUTO) {
            switch ((int) l) {
                case Integer.MAX_VALUE:
                    return "Integer.MAX_VALUE";
                case Integer.MIN_VALUE:
                    return "Integer.MIN_VALUE";
            }
        }
        return formatNumber(l, 4, cast);
    }

    /**
     * 将数值格式化为 long 类型字面量表示，AUTO 格式下识别 Long 的最大/最小值常量
     *
     * @param l    数值
     * @param cast 是否强制显式类型转换
     * @return 格式化后的字符串
     */
    public String formatLong(long l, boolean cast) {
        if (integerFormat == IntegerFormat.AUTO) {
            if (l == Long.MAX_VALUE) {
                return "Long.MAX_VALUE";
            }
            if (l == Long.MIN_VALUE) {
                return "Long.MIN_VALUE";
            }
        }
        return formatNumber(l, 8, cast);
    }
}
