package com.bingbaihanji.fxdecomplie.ui.graph;

import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import javafx.stage.Window;
import jfx.incubator.scene.control.richtext.CodeArea;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 图形可视化弹窗，展示 DOT 格式图形描述
 *
 * <p>首版使用 CodeArea 文本显示，无需外部依赖。
 * WebView + viz.js 渲染路径保留，当 standalone viz.js 可用时启用</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class GraphDialog extends Dialog<Void> {

    private static final int DEFAULT_WIDTH = 820;
    private static final int DEFAULT_HEIGHT = 640;

    public GraphDialog(Window owner, String title, String dot) {
        initOwner(owner);
        setTitle(title);
        initModality(Modality.NONE);
        setResizable(true);

        CodeArea codeArea = new CodeArea();
        codeArea.setEditable(false);
        codeArea.getStyleClass().add("code-editor");
        codeArea.setStyle("""
                -fx-font-feature-settings: "calt" 1, "liga" 1;
                -fx-font-family: "Consolas", "Fira Code", monospace;
                -fx-font-size: 13px;
                """);
        codeArea.setText(dot == null ? "" : dot);

        DialogPane dialogPane = getDialogPane();
        dialogPane.setContent(codeArea);
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        dialogPane.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        setOnShown(e -> {
            var win = dialogPane.getScene().getWindow();
            DefaultWindowTheme.applyWindowDarkMode(win);
            if (win instanceof javafx.stage.Stage s) setDialogIcon(s);
        });
    }

    /** 显示图形弹窗 */
    public static void show(Window owner, String title, String dot) {
        Objects.requireNonNull(dot, "dot");
        new GraphDialog(owner, title, dot).show();
    }

    private static void setDialogIcon(javafx.stage.Stage stage) {
        try {
            var stream = GraphDialog.class.getResourceAsStream("/icon/logo.png");
            if (stream != null) {
                stage.getIcons().add(new javafx.scene.image.Image(stream));
            }
        } catch (Exception ignored) {
        }
    }
}
