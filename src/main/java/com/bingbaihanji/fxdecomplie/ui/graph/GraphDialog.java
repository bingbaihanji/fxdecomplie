package com.bingbaihanji.fxdecomplie.ui.graph;

import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Window;
import jfx.incubator.scene.control.richtext.CodeArea;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 图形可视化弹窗，使用 WebView + Viz.js 渲染 DOT → SVG 图形
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class GraphDialog extends Dialog<Void> {

    private static final int DEFAULT_WIDTH = 820;
    private static final int DEFAULT_HEIGHT = 640;
    private static final String TEMPLATE_PATH = "/web/graph-template.html";
    private static final String VIZ_PATH = "/web/viz-standalone.js";

    public GraphDialog(Window owner, String title, String dot) {
        initOwner(owner);
        setTitle(title);
        initModality(Modality.NONE);
        setResizable(true);

        DialogPane dialogPane = getDialogPane();
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        dialogPane.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        if (tryWebView(dialogPane, dot)) {
            return;
        }
        // 回退：CodeArea 文本显示
        CodeArea codeArea = new CodeArea();
        codeArea.setEditable(false);
        codeArea.getStyleClass().add("code-editor");
        codeArea.setText(dot == null ? "" : dot);
        dialogPane.setContent(codeArea);

        setOnShown(e -> {
            var win = dialogPane.getScene().getWindow();
            DefaultWindowTheme.applyWindowDarkMode(win);
            if (win instanceof javafx.stage.Stage s) setDialogIcon(s);
        });
    }

    /** 尝试 WebView + viz.js 渲染，失败返回 false */
    private boolean tryWebView(DialogPane pane, String dot) {
        try {
            String template = loadResource(TEMPLATE_PATH);
            String vizJs = loadResource(VIZ_PATH);
            if (template == null || vizJs == null) return false;

            String escaped = dot.replace("\\", "\\\\")
                    .replace("`", "\\`");
            String html = template.replace("__DOT_CONTENT__", escaped);
            // 将 viz.js 内联到 HTML 中
            html = html.replace("<script>",
                    "<script>\n" + vizJs + "\n");

            WebView webView = new WebView();
            webView.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            webView.getEngine().loadContent(html);
            pane.setContent(webView);

            setOnShown(e -> {
                var win = pane.getScene().getWindow();
                DefaultWindowTheme.applyWindowDarkMode(win);
                if (win instanceof javafx.stage.Stage s) setDialogIcon(s);
            });
            setOnCloseRequest(e -> webView.getEngine().loadContent(""));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String loadResource(String path) {
        try (InputStream is = GraphDialog.class.getResourceAsStream(path)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
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
