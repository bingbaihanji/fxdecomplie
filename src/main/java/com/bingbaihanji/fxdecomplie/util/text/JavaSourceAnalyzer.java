package com.bingbaihanji.fxdecomplie.util.text;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;

import java.util.regex.Pattern;

/**
 * Java 源码静态分析工具:声明行识别、令牌规范化、类引用判定、偏移换算等
 * 从 {@code MainWindow} 抽取的纯静态方法集合
 */
public final class JavaSourceAnalyzer {

    /** 用于解析 Java 源码中 import 语句的正则 */
    public static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?([\\w.$]+|[\\w.]+\\.\\*)\\s*;\\s*$");
    /** 预编译: 判断前缀是否以类/接口/枚举/record 关键字结尾 */
    public static final Pattern DECL_KEYWORD_END_PATTERN =
            Pattern.compile("(^|\\s)(class|interface|enum|record)\\s*$");
    /** 预编译: 判断前缀是否以 @interface 结尾 */
    public static final Pattern ANN_INTERFACE_PATTERN =
            Pattern.compile("(^|\\s)@\\s*interface\\s*$");

    private JavaSourceAnalyzer() {
        throw new AssertionError("utility class");
    }

    /** 将点分隔的类名转换为内部类路径(首段大写后用 $ 替代 .) */
    public static String toInnerClassPath(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }
        String[] parts = className.split("\\.");
        StringBuilder sb = new StringBuilder();
        boolean classSegmentSeen = false;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            boolean classLike = Character.isUpperCase(part.charAt(0)) || classSegmentSeen;
            if (sb.length() > 0) {
                sb.append(classLike ? '$' : '/');
            }
            sb.append(part);
            if (classLike) {
                classSegmentSeen = true;
            }
        }
        return sb.toString();
    }

    /** 判断树节点名是否与简单类令牌匹配(含内部类 $ 后缀匹配) */
    public static boolean matchesSimpleClassName(FileTreeNode node, String simpleToken,
                                                 String expectedClassFile) {
        if (node == null || simpleToken == null || simpleToken.isBlank()) {
            return false;
        }
        String name = node.getName();
        if (expectedClassFile.equals(name)) {
            return true;
        }
        String path = normalizeInternalClassName(node.getFullPath());
        int slash = path.lastIndexOf('/');
        String simpleName = slash >= 0 ? path.substring(slash + 1) : path;
        return simpleName.equals(simpleToken) || simpleName.endsWith("$" + simpleToken);
    }

    /** 在源码中查找匹配 token 的声明行,优先返回离点击行最近的匹配 */
    public static int findDeclarationLine(String sourceCode, String token, int clickedLine) {
        if (sourceCode == null || sourceCode.isBlank() || token == null || token.isBlank()) {
            return -1;
        }
        String simpleToken = tokenSimpleName(token);
        simpleToken = simpleToken.endsWith(".class") ? simpleToken.substring(0, simpleToken.length() - 6) : simpleToken;
        if (simpleToken.isBlank()) {
            return -1;
        }
        String[] lines = sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int bestLine = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < lines.length; i++) {
            String line = stripLineComment(lines[i]);
            if (line.isBlank() || !line.contains(simpleToken)) {
                continue;
            }
            if (looksLikeDeclarationLine(line, simpleToken)) {
                int lineNumber = i + 1;
                int distance = clickedLine > 0 ? Math.abs(lineNumber - clickedLine) : lineNumber;
                if (lineNumber == clickedLine) {
                    return lineNumber;
                }
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestLine = lineNumber;
                }
            }
        }
        return bestLine;
    }

    /** 判断源码行是否看起来是声明行(类/接口/枚举/方法/字段声明) */
    public static boolean looksLikeDeclarationLine(String line, String simpleToken) {
        String trimmed = line == null ? "" : line.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        // 跳过行首注解(如 @Override、@Deprecated)
        while (trimmed.startsWith("@")) {
            int end = trimmed.indexOf(' ');
            if (end < 0) {
                end = trimmed.indexOf('(');
            }
            if (end < 0) {
                break;
            }
            if (trimmed.charAt(end) == '(') {
                int depth = 1;
                int k = end + 1;
                while (k < trimmed.length() && depth > 0) {
                    if (trimmed.charAt(k) == '(') {
                        depth++;
                    } else if (trimmed.charAt(k) == ')') {
                        depth--;
                    }
                    k++;
                }
                trimmed = trimmed.substring(k).strip();
            } else {
                trimmed = trimmed.substring(end + 1).strip();
            }
        }
        if (trimmed.isEmpty()) {
            return false;
        }
        // 快速路径: 使用 indexOf 检查声明关键字,避免正则编译
        if (containsKeywordBeforeToken(trimmed, simpleToken,
                "class ", "interface ", "enum ", "record ")) {
            return true;
        }
        String quoted = Pattern.quote(simpleToken);
        Pattern methodPattern = Pattern.compile(
                "^(?:[\\w@$]+\\s+)*(?:[\\w.$<>\\[\\],?]+\\s+)+" + quoted
                        + "\\s*\\([^;]*\\)\\s*(?:throws\\s+[\\w.$,\\s]+)?\\s*(?:\\{|;)?\\s*$");
        if (methodPattern.matcher(trimmed).find()) {
            return true;
        }
        Pattern fieldPattern = Pattern.compile(
                "^(?:[\\w@$]+\\s+)*(?:[\\w.$<>\\[\\],?]+\\s+)+" + quoted
                        + "\\s*(?:=|;|,).*$");
        return fieldPattern.matcher(trimmed).find();
    }

    /** 去除行尾的 // 注释部分,返回纯代码(正确处理字符串字面量内的 // ) */
    public static String stripLineComment(String line) {
        if (line == null) {
            return "";
        }
        boolean inString = false;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inString) {
                // 跳过字符字面量(如 '"'、'\''、'\\')
                i++;
                if (i < line.length() && line.charAt(i) == '\\') {
                    i++; // 跳过转义字符
                }
                if (i < line.length()) {
                    i++; // 跳过字符内容到关闭引号
                }
                continue;
            }
            if (c == '"') {
                // 统计引号前连续反斜杠数量,偶数个表示引号未被转义
                int bs = 0;
                for (int j = i - 1; j >= 0 && line.charAt(j) == '\\'; j--) {
                    bs++;
                }
                if (bs % 2 == 0) {
                    inString = !inString;
                }
            } else if (c == '/' && line.charAt(i + 1) == '/' && !inString) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    /**
     * 使用 indexOf 快速检查行中是否包含指定关键字后跟 token (替代正则编译)
     *
     * @param line     被检查的行
     * @param token    要匹配的标识符
     * @param keywords 关键字数组(含尾部空格,如 "class ")
     * @return true 若行中某关键字后(经空白分隔)紧跟 token 且两侧满足词边界
     */
    public static boolean containsKeywordBeforeToken(String line, String token, String... keywords) {
        for (String kw : keywords) {
            int idx = line.indexOf(kw);
            while (idx >= 0) {
                if (idx > 0 && Character.isJavaIdentifierPart(line.charAt(idx - 1))) {
                    idx = line.indexOf(kw, idx + 1);
                    continue;
                }
                int afterKw = idx + kw.length();
                String rest = line.substring(afterKw).stripLeading();
                if (rest.startsWith(token)) {
                    int afterToken = token.length();
                    if (afterToken >= rest.length()
                            || !Character.isJavaIdentifierPart(rest.charAt(afterToken))) {
                        return true;
                    }
                }
                idx = line.indexOf(kw, idx + 1);
            }
        }
        return false;
    }

    /** 清理声明 token,去除首尾非 Java 名称/路径字符 */
    public static String sanitizeDeclarationToken(String token) {
        if (token == null) {
            return "";
        }
        String result = token.strip();
        while (!result.isEmpty() && !isDeclarationTokenChar(result.charAt(0))) {
            result = result.substring(1);
        }
        while (!result.isEmpty() && !isDeclarationTokenChar(result.charAt(result.length() - 1))) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /** @return true 若字符是声明 token 的有效字符(Java 标识符、点号、美元符、斜杠) */
    public static boolean isDeclarationTokenChar(char ch) {
        return Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '$' || ch == '/';
    }

    /** 从限定名中提取简单名(最后一段) */
    public static String tokenSimpleName(String token) {
        String normalized = token.replace('.', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    /** @return true 若 token 首字母大写、下划线或美元符号开头(相对类引用) */
    public static boolean isRelativeClassToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String trimmed = token.strip();
        while (!trimmed.isEmpty() && !isDeclarationTokenChar(trimmed.charAt(0))) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            return false;
        }
        char first = trimmed.charAt(0);
        return Character.isUpperCase(first) || first == '_' || first == '$';
    }

    /** @return true 若 token 应优先作为类引用导航(含点号或首字母大写) */
    public static boolean shouldPreferClassNavigation(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String simpleToken = tokenSimpleName(token);
        simpleToken = simpleToken.endsWith(".class")
                ? simpleToken.substring(0, simpleToken.length() - 6) : simpleToken;
        return token.contains(".") || isRelativeClassToken(simpleToken);
    }

    /** @return true 若 token 应在工作区中进行类查找(限定名、相对类引用或混淆短名) */
    public static boolean shouldSearchWorkspaceForClassToken(String token) {
        if (shouldPreferClassNavigation(token)) {
            return true;
        }
        String simpleToken = tokenSimpleName(token);
        simpleToken = simpleToken.endsWith(".class")
                ? simpleToken.substring(0, simpleToken.length() - 6) : simpleToken;
        return isShortObfuscatedClassToken(simpleToken);
    }

    /** @return true 若 token 是可能的混淆类名(短名、非常见词汇、仅含合法字符) */
    public static boolean isShortObfuscatedClassToken(String token) {
        if (token == null || token.isBlank() || token.length() > 2) {
            return false;
        }
        if (com.bingbaihanji.fxdecomplie.rename.AutoDeobfuscator.isCommonShortName(token)) {
            return false;
        }
        return token.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '$');
    }

    /** 判断指定行是否看起来使用了 token 指代的类(new/extends/import/类型声明等语境) */
    public static boolean looksLikeClassUsageAtLine(String sourceCode, int lineNumber, String token) {
        if (sourceCode == null || sourceCode.isBlank() || token == null || token.isBlank()
                || lineNumber <= 0) {
            return false;
        }
        String simpleToken = tokenSimpleName(token);
        simpleToken = simpleToken.endsWith(".class")
                ? simpleToken.substring(0, simpleToken.length() - 6) : simpleToken;
        if (simpleToken.isBlank()) {
            return false;
        }
        String[] lines = sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (lineNumber > lines.length) {
            return false;
        }
        String line = stripLineComment(lines[lineNumber - 1]).strip();
        if (line.isBlank() || line.startsWith("package ")) {
            return false;
        }
        // 快速路径: 使用 indexOf 替代正则编译检查关键字+token 模式
        if (containsKeywordBeforeToken(line, simpleToken,
                "class ", "interface ", "enum ", "record ",
                "new ", "extends ", "implements ", "throws ", "instanceof ")) {
            return true;
        }
        String quoted = Pattern.quote(simpleToken);
        if (Pattern.compile("^import\\s+(?:static\\s+)?[\\w.$]*\\." + quoted
                + "\\s*;\\s*$").matcher(line).find()) {
            return true;
        }
        return Pattern.compile("(^|[\\s(<,])" + quoted
                + "\\s*(?:<[^;{}()]*>)?(?:\\s*\\[\\s*\\])*\\s+"
                + "[a-zA-Z_$][a-zA-Z0-9_$]*\\b").matcher(line).find();
    }

    /** @return true 若两个内部类名处于同一包下 */
    public static boolean samePackage(String leftInternalName, String rightInternalName) {
        return packageName(leftInternalName).equals(packageName(rightInternalName));
    }

    /** 标准化内部类名(委托 ClassNameUtil) */
    public static String normalizeInternalClassName(String className) {
        return ClassNameUtil.normalizeInternalName(className);
    }

    /** 获取内部类名的包名部分(委托 ClassNameUtil) */
    public static String packageName(String internalName) {
        return ClassNameUtil.packageName(internalName);
    }

    /** 将 TextPos(行号+列偏移)转换为文本在原始字符串中的平坦偏移量 */
    public static int flatOffset(String text,
                                 jfx.incubator.scene.control.richtext.TextPos pos) {
        int line = pos.index();
        int col = pos.offset();
        int off = 0;
        int ls = 0;
        for (int i = 0; i < line && ls < text.length(); i++) {
            int nl = text.indexOf('\n', ls);
            if (nl < 0) {
                break;
            }
            off += (nl - ls) + 1;
            ls = nl + 1;
        }
        return Math.min(text.length(), off + col);
    }

    /** 判断 offset 位置附近的标识符是否是类/接口/枚举声明的类型名 */
    public static boolean isDeclaredTypeNameAt(String sourceCode, int offset, String caretName) {
        if (sourceCode == null || sourceCode.isBlank() || caretName == null || caretName.isBlank()
                || offset < 0 || offset > sourceCode.length()) {
            return false;
        }
        int probe = Math.min(offset, sourceCode.length() - 1);
        if (probe > 0 && (probe >= sourceCode.length()
                || !Character.isJavaIdentifierPart(sourceCode.charAt(probe)))) {
            probe--;
        }
        if (probe < 0 || probe >= sourceCode.length()
                || !Character.isJavaIdentifierPart(sourceCode.charAt(probe))) {
            return false;
        }
        int start = probe;
        while (start > 0 && Character.isJavaIdentifierPart(sourceCode.charAt(start - 1))) {
            start--;
        }
        int end = probe + 1;
        while (end < sourceCode.length() && Character.isJavaIdentifierPart(sourceCode.charAt(end))) {
            end++;
        }
        if (!caretName.equals(sourceCode.substring(start, end))) {
            return false;
        }
        int lineStart = sourceCode.lastIndexOf('\n', start);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        String prefix = sourceCode.substring(lineStart, start).stripTrailing();
        return DECL_KEYWORD_END_PATTERN.matcher(prefix).find()
                || ANN_INTERFACE_PATTERN.matcher(prefix).find();
    }

    /** 获取类的叶子名(去除包名后的最后一段,跳过内部类 $ 前缀) */
    public static String classLeafName(String className) {
        String simple = tokenSimpleName(normalizeInternalClassName(className));
        int dollar = simple.lastIndexOf('$');
        return dollar >= 0 ? simple.substring(dollar + 1) : simple;
    }
}
