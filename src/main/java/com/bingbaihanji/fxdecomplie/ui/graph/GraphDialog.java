package com.bingbaihanji.fxdecomplie.ui.graph;

import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 图形可视化弹窗，使用 WebView + Viz.js 渲染 DOT 格式的图形
 *
 * <p>需要 viz.js 放在 src/main/resources/web/viz.js，许可文件一并保留</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class GraphDialog extends Dialog<Void> {

    private static final int DEFAULT_WIDTH = 820;
    private static final int DEFAULT_HEIGHT = 640;
    private static final String TEMPLATE_PATH = "/web/graph-template.html";

    private final WebView webView;

    public GraphDialog(Window owner, String title, String dot) {
        initOwner(owner);
        setTitle(title);
        initModality(Modality.NONE);
        setResizable(true);

        webView = new WebView();
        webView.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        webView.setZoom(1.0);

        DialogPane dialogPane = getDialogPane();
        dialogPane.setContent(webView);
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        dialogPane.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        setOnShown(e -> {
            DefaultWindowTheme.applyWindowDarkMode(dialogPane.getScene().getWindow());
            loadGraph(dot);
        });

        setOnCloseRequest(e -> webView.getEngine().loadContent(""));
    }

    private void loadGraph(String dot) {
        try {
            String template = loadTemplate();
            String escaped = dot.replace("\\", "\\\\")
                    .replace("`", "\\`")
                    .replace("$", "\\$");
            String html = template.replace("__DOT_CONTENT__", escaped);
            webView.getEngine().loadContent(html);
        } catch (Exception e) {
            webView.getEngine().loadContent(
                    "<html><body style='background:#1e1e1e;color:#ccc;font-family:Consolas;'>"
                            + "<p>" + I18nUtil.getString("graph.renderFailed") + "</p>"
                            + "<pre>" + escapeHtml(dot) + "</pre></body></html>"
            );
        }
    }

    private String loadTemplate() throws Exception {
        InputStream is = getClass().getResourceAsStream(TEMPLATE_PATH);
        if (is == null) {
            return fallbackTemplate();
        }
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    /** 无外部模板时的回退 */
    private String fallbackTemplate() {
        return """
                <html><head><meta charset="UTF-8"><style>
                body{background:#1e1e1e;margin:0;overflow:hidden;}
                #graph{width:100vw;height:100vh;}
                </style></head><body>
                <div id="graph"></div>
                <script>
                try {
                    var dot = `__DOT_CONTENT__`;
                    var viz = new Viz();
                    viz.renderSVGElement(dot).then(function(el){
                        document.getElementById('graph').appendChild(el);
                    }).catch(function(err){
                        document.getElementById('graph').innerHTML =
                            '<pre style="color:#ccc;padding:20px;">'
                            + err.toString() + '</pre>';
                    });
                } catch(e) {
                    document.getElementById('graph').innerHTML =
                        '<pre style="color:#ccc;padding:20px;">'
                        + 'viz.js not loaded. Please place viz.js in resources/web/'
                        + '</pre>';
                }
                </script></body></html>
                """;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /** 显示图形弹窗 */
    public static void show(Window owner, String title, String dot) {
        Objects.requireNonNull(dot, "dot");
        new GraphDialog(owner, title, dot).show();
    }
}
