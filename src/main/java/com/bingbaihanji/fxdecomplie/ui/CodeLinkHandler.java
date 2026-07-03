package com.bingbaihanji.fxdecomplie.ui;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;

import java.util.List;
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

    /**
     * 在 CodeArea 上安装 Ctrl+Click 导航。
     *
     * <p>优先把点击处的 token 交给上层解析，上层可以结合 import、当前包和 workspace
     * 文件树完成项目内 class 跳转；没有 token 导航器时退回到行级 metadata。</p>
     */
    private static final String LINK_HANDLER_KEY = "CODE_LINK_HANDLER";

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
                String token = tokenAt(codeArea.getText(), pos);
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

    private static CodeMetadata.Reference selectReference(List<CodeMetadata.Reference> refs,
                                                          String token) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        String cleanToken = sanitizeToken(token);
        if (!cleanToken.isBlank()) {
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
            return refs.getFirst();
        }
        return null;
    }

    private static String tokenAt(String text, TextPos pos) {
        if (text == null || text.isEmpty() || pos == null) {
            return "";
        }
        int offset = flatOffset(text, pos);
        if (offset < 0 || offset > text.length()) {
            return "";
        }
        int start = offset;
        while (start > 0 && isJavaNameChar(text.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < text.length() && isJavaNameChar(text.charAt(end))) {
            end++;
        }
        return start < end ? text.substring(start, end) : "";
    }

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

    private static boolean isJavaNameChar(char ch) {
        return Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '$';
    }

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

    private static String simpleName(String token) {
        String normalized = token.replace('.', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }
}
