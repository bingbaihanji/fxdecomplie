package com.bingbihanji.fxdecomplie.utils;

import com.bingbihanji.fxdecomplie.model.CodeMetadata;
import javafx.scene.input.MouseButton;
import jfx.incubator.scene.control.richtext.CodeArea;

import java.util.function.Consumer;

/**
 * Ctrl+Click 代码导航处理器。在 CodeArea 上注册鼠标事件，
 * 按下 Ctrl 并点击时查找当前行引用并导航到目标。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class CodeLinkHandler {

    private CodeLinkHandler() {
        throw new AssertionError("utility class");
    }

    /**
     * 在 CodeArea 上安装 Ctrl+Click 导航。
     *
     * @param codeArea   代码编辑区域
     * @param metadata   代码元数据（行 → 引用映射）
     * @param onNavigate 导航回调：接收点击处的第一个引用
     */
    public static void install(CodeArea codeArea, CodeMetadata metadata,
                               Consumer<CodeMetadata.Reference> onNavigate) {
        codeArea.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.isControlDown()) {
                int line = codeArea.getCaretPosition().index();
                var refs = metadata.getRefsAtLine(line);
                if (!refs.isEmpty()) {
                    onNavigate.accept(refs.get(0));
                    event.consume();
                }
            }
        });
    }
}
