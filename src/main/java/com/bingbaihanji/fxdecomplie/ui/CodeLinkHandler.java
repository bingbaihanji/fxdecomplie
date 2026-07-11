package com.bingbaihanji.fxdecomplie.ui;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Ctrl+Click 代码导航处理器在 CodeArea 上注册鼠标事件,
 * 按下 Ctrl 并点击时查找当前行引用并导航到目标
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class CodeLinkHandler {

    private static final String LINK_HANDLER_KEY = "CODE_LINK_HANDLER";
    private static final Set<String> NON_NAVIGABLE_TOKENS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "false", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "null", "package", "private", "protected", "public", "return",
            "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "true", "try", "void", "volatile", "while",
            "var", "record", "sealed", "permits", "yield", "module", "requires",
            "exports", "opens", "to", "uses", "provides", "with", "transitive",
            "non-sealed"
    );

    private CodeLinkHandler() {
        throw new AssertionError("utility class");
    }

    /**
     * 在 CodeArea 上安装 Ctrl+Click 导航
     *
     * @param codeArea   代码编辑区域
     * @param metadata   代码元数据(行 → 引用映射)
     * @param onNavigate 导航回调：接收点击处的第一个引用
     */
    public static void install(CodeArea codeArea, CodeMetadata metadata,
                               Consumer<CodeMetadata.Reference> onNavigate) {
        install(codeArea, metadata, null, onNavigate);
    }

    /**
     * 在 CodeArea 上安装 Ctrl+Click 导航,优先通过 token 导航器解析,回退到行级 metadata
     *
     * @param codeArea        代码编辑区域
     * @param metadata        代码元数据(行到引用的映射)
     * @param onTokenNavigate token 级导航回调,接收行号和标识符；传入 null 则仅使用 metadata
     * @param onNavigate      行级导航回调,接收点击处的引用
     */
    public static void install(CodeArea codeArea, CodeMetadata metadata,
                               BiConsumer<Integer, String> onTokenNavigate,
                               Consumer<CodeMetadata.Reference> onNavigate) {
        if (codeArea == null) {
            return;
        }
        // 先移除旧处理器
        uninstall(codeArea);
        javafx.event.EventHandler<javafx.scene.input.MouseEvent> handler = event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.isControlDown()) {
                var pos = codeArea.getTextPosition(event.getScreenX(), event.getScreenY());
                int line = pos == null ? codeArea.getCaretPosition().index() + 1 : pos.index() + 1;
                String token = navigationTokenAt(codeArea.getText(), pos);
                if (onTokenNavigate != null && !token.isBlank()) {
                    onTokenNavigate.accept(line, token);
                    event.consume();
                    return;
                }

                var refs = metadata == null ? List.<CodeMetadata.Reference>of()
                        : metadata.getRefsAtLine(line);
                CodeMetadata.Reference ref = selectReference(refs, token);
                if (ref != null && onNavigate != null) {
                    onNavigate.accept(ref);
                    event.consume();
                }
            }
        };
        codeArea.getProperties().put(LINK_HANDLER_KEY, handler);
        codeArea.addEventHandler(MouseEvent.MOUSE_CLICKED, handler);
    }

    /** 移除之前在 CodeArea 上安装的导航处理器 */
    @SuppressWarnings("unchecked")
    public static void uninstall(CodeArea codeArea) {
        if (codeArea == null) {
            return;
        }
        var handler = (javafx.event.EventHandler<javafx.scene.input.MouseEvent>)
                codeArea.getProperties().remove(LINK_HANDLER_KEY);
        if (handler != null) {
            codeArea.removeEventHandler(MouseEvent.MOUSE_CLICKED, handler);
        }
    }

    /**
     * 从行引用列表中选择与点击 token 匹配的引用
     *
     * <p>匹配策略(按优先级)：
     * <ol>
     *   <li>完全限定名匹配(含内部类 $ 分隔)</li>
     *   <li>简单类名匹配(仅最后一段)</li>
     *   <li>无匹配时返回 null,避免导航到无关引用</li>
     * </ol>
     *
     * @param refs  行上的所有类引用
     * @param token 点击处的标识符(可能为 package.ClassName 形式)
     * @return 匹配的引用,无匹配时返回 null
     */
    private static CodeMetadata.Reference selectReference(List<CodeMetadata.Reference> refs,
                                                          String token) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        String cleanToken = sanitizeToken(token);
        if (cleanToken.isBlank()) {
            return null;
        }
        String simpleToken = simpleName(cleanToken);
        for (CodeMetadata.Reference ref : refs) {
            if (ref.targetClass() == null) {
                continue;
            }
            String normalized = ref.targetClass().replace('.', '/');
            if (ref.targetClass().equals(cleanToken)
                    || normalized.equals(cleanToken.replace('.', '/'))
                    || simpleName(normalized).equals(simpleToken)) {
                return ref;
            }
        }
        // 无精确匹配时不回退到第一个引用,避免导航到错误位置
        return null;
    }

    /** 从指定文本位置提取可导航的 Java 标识符(类名或成员引用) */
    public static String navigationTokenAt(String text, TextPos pos) {
        NavigationToken token = navigationTokenRangeAt(text, pos);
        return token == null ? "" : token.token();
    }

    /** 返回光标处可导航 token 及其在文档中的字符范围,用于 Ctrl 悬停高亮  */
    public static NavigationToken navigationTokenRangeAt(String text, TextPos pos) {
        if (text == null || text.isEmpty() || pos == null) {
            return null;
        }
        int offset = flatOffset(text, pos);
        if (offset < 0 || offset > text.length()) {
            return null;
        }
        int probe = offset;
        if (probe >= text.length() || !isJavaIdentifierSegmentChar(text.charAt(probe))) {
            if (probe > 0 && isJavaIdentifierSegmentChar(text.charAt(probe - 1))) {
                probe--;
            } else {
                return null;
            }
        }
        int segmentStart = probe;
        while (segmentStart > 0 && isJavaIdentifierSegmentChar(text.charAt(segmentStart - 1))) {
            segmentStart--;
        }
        int segmentEnd = probe + 1;
        while (segmentEnd < text.length() && isJavaIdentifierSegmentChar(text.charAt(segmentEnd))) {
            segmentEnd++;
        }

        int qualifiedStart = segmentStart;
        while (qualifiedStart > 0 && isJavaNameChar(text.charAt(qualifiedStart - 1))) {
            qualifiedStart--;
        }
        int qualifiedEnd = segmentEnd;
        while (qualifiedEnd < text.length() && isJavaNameChar(text.charAt(qualifiedEnd))) {
            qualifiedEnd++;
        }

        String qualified = text.substring(qualifiedStart, qualifiedEnd);
        int localSegmentStart = segmentStart - qualifiedStart;
        int localSegmentEnd = segmentEnd - qualifiedStart;
        String token = selectNavigationToken(qualified, localSegmentStart, localSegmentEnd);
        if (!isNavigableToken(token)) {
            return null;
        }
        int tokenStart = segmentStart;
        int tokenEnd = segmentEnd;
        if (token.indexOf('.') >= 0) {
            int tokenLocalStart = qualified.indexOf(token);
            if (tokenLocalStart >= 0) {
                tokenStart = qualifiedStart + tokenLocalStart;
                tokenEnd = tokenStart + token.length();
            }
        }
        return new NavigationToken(token, tokenStart, tokenEnd);
    }

    /** 将 TextPos(行号+列偏移)转换为文本在原始字符串中的平坦偏移量 */
    private static int flatOffset(String text, TextPos pos) {
        int line = Math.max(0, pos.index());
        int offset = Math.max(0, pos.offset());
        int currentLine = 0;
        int lineStart = 0;
        for (int i = 0; i < text.length() && currentLine < line; i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                continue;
            }
            if (c == '\n') {
                currentLine++;
                lineStart = i + 1;
            }
        }
        return Math.min(text.length(), lineStart + offset);
    }

    /** @return true 若字符是 Java 名称字符(标识符字符、点号或美元符号) */
    private static boolean isJavaNameChar(char ch) {
        return Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '$';
    }

    /** @return true 若字符属于 Java 标识符段字符(标识符字符或美元符号,不含点号) */
    private static boolean isJavaIdentifierSegmentChar(char ch) {
        return Character.isJavaIdentifierPart(ch) || ch == '$';
    }

    /**
     * 从限定名中选择适合导航的 token
     * <p>处理 this./super. 前缀、静态成员访问、混淆类名等情况,返回最佳导航目标
     *
     * @param qualified    包含包名和类名的完整限定字符串
     * @param segmentStart 标识符段在 qualified 中的起始位置
     * @param segmentEnd   标识符段在 qualified 中的结束位置
     * @return 最适合导航的类引用字符串
     */
    private static String selectNavigationToken(String qualified, int segmentStart,
                                                int segmentEnd) {
        if (qualified == null || segmentStart < 0 || segmentEnd <= segmentStart
                || segmentEnd > qualified.length()) {
            return "";
        }
        String segment = qualified.substring(segmentStart, segmentEnd);
        if (qualified.indexOf('.') < 0) {
            return segment;
        }
        String ownerPrefix = qualified.substring(0, segmentStart);
        if (ownerPrefix.endsWith("this.") || ownerPrefix.endsWith("super.")) {
            return segment;
        }
        if (isClassNameSegment(segment)) {
            String ownerClass = ownerClassReference(ownerPrefix);
            if (!ownerClass.isBlank() && isLikelyStaticMemberSegment(segment)) {
                return ownerClass;
            }
            String classRef = qualified.substring(0, segmentEnd);
            if (looksLikeQualifiedClassReference(classRef)) {
                return classRef;
            }
        }
        // 混淆后的类名可能为小写单字母(如 com.pig4cloud.service.a),
        // isClassNameSegment 会因首字母小写而拒绝此时若该段是 qualified 最后一段
        // 且其前的所有段都是包名风格(全小写/数字),则将整个 qualified 作为类引用
        if (isLastSegment(qualified, segmentEnd) && isPackagePrefix(ownerPrefix)) {
            String classRef = qualified.substring(0, segmentEnd);
            if (isValidJavaIdentifier(segment)) {
                return classRef;
            }
        }
        return segment;
    }

    /** @return true 若 segmentEnd 已到达 qualified 末尾(该段之后无更多字符) */
    private static boolean isLastSegment(String qualified, int segmentEnd) {
        return segmentEnd >= qualified.length();
    }

    /** @return true 若所有点分隔的段都是包名风格(小写开头),即没有明显的类名段 */
    private static boolean isPackagePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return false;
        }
        // 去除末尾的 '.'
        String trimmed = prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix;
        if (trimmed.isEmpty()) {
            return false;
        }
        for (String part : trimmed.split("\\.")) {
            if (part.isEmpty()) {
                return false;
            }
            // 包名段全小写或纯数字开头(如 com、pig4cloud、service)
            if (!isPackageNameSegment(part) && !part.chars().allMatch(c -> Character.isDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

    /** @return true 若名称为有效 Java 标识符 */
    private static boolean isValidJavaIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** @return true 若 token 的每个点分隔段中至少有一个类名段(首字母大写)且之后全为类名段 */
    private static boolean looksLikeQualifiedClassReference(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String[] parts = token.split("\\.");
        boolean classSegmentSeen = false;
        for (String part : parts) {
            if (part.isBlank()) {
                return false;
            }
            if (!classSegmentSeen) {
                if (isClassNameSegment(part)) {
                    classSegmentSeen = true;
                } else if (!isPackageNameSegment(part)) {
                    return false;
                }
            } else if (!isClassNameSegment(part)) {
                return false;
            }
        }
        return classSegmentSeen;
    }

    /** 从点前缀中提取所属类的引用,仅当前缀本身看起来是合法的限定类名时返回 */
    private static String ownerClassReference(String ownerPrefix) {
        if (ownerPrefix == null || ownerPrefix.isBlank()) {
            return "";
        }
        String owner = ownerPrefix.endsWith(".")
                ? ownerPrefix.substring(0, ownerPrefix.length() - 1) : ownerPrefix;
        return looksLikeQualifiedClassReference(owner) ? owner : "";
    }

    /** @return true 若段首字母大写或以下划线/美元符号开头(类名风格) */
    private static boolean isClassNameSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return false;
        }
        char first = segment.charAt(0);
        return Character.isUpperCase(first) || first == '_' || first == '$';
    }

    /** @return true 若段首字母小写(包名风格) */
    private static boolean isPackageNameSegment(String segment) {
        return segment != null && !segment.isBlank()
                && Character.isLowerCase(segment.charAt(0));
    }

    /** @return true 若段看起来是静态成员引用(全大写或含数字但无小写字母) */
    private static boolean isLikelyStaticMemberSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < segment.length(); i++) {
            char ch = segment.charAt(i);
            if (Character.isLetter(ch)) {
                hasLetter = true;
                if (Character.isLowerCase(ch)) {
                    return false;
                }
                continue;
            }
            if (!Character.isDigit(ch) && ch != '_' && ch != '$') {
                return false;
            }
        }
        return hasLetter;
    }

    /** 清理 token,去除首尾非 Java 名称字符 */
    private static String sanitizeToken(String token) {
        if (token == null) {
            return "";
        }
        String result = token.strip();
        while (!result.isEmpty() && !isJavaNameChar(result.charAt(0))) {
            result = result.substring(1);
        }
        while (!result.isEmpty() && !isJavaNameChar(result.charAt(result.length() - 1))) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static boolean isNavigableToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String simple = simpleName(token);
        return !simple.isBlank() && !NON_NAVIGABLE_TOKENS.contains(simple);
    }

    /** 从限定名中提取简单类名(最后一段,/ 或 . 之后的部分) */
    private static String simpleName(String token) {
        String normalized = token.replace('.', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    public record NavigationToken(String token, int startOffset, int endOffset) {
    }
}
