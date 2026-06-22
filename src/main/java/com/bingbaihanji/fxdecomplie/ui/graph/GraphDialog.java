package com.bingbaihanji.fxdecomplie.ui.graph;

import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 图形可视化弹窗。
 *
 * <p>默认使用 JavaFX 原生控件渲染应用生成的 DOT 子集，避免 WebView/Viz.js
 * 在部分运行环境中卡在 WASM 初始化阶段。DOT 源码仍可切换查看，便于排查。</p>
 */
public class GraphDialog extends Dialog<Void> {

    private static final Logger logger = LoggerFactory.getLogger(GraphDialog.class);

    private static final int W = 820;
    private static final int H = 640;
    private static final String DOT_STYLE =
            "-fx-control-inner-background:#1e1e1e;-fx-text-fill:#d4d4d4;"
            + "-fx-font-family:'Consolas',monospace;-fx-font-size:13px;"
            + "-fx-highlight-fill:#264f78;-fx-highlight-text-fill:#fff;";

    private StackPane contentStack;
    private TextArea dotArea;
    private Node currentGraphView;
    private ToggleButton viewToggle;
    private Label statusLabel;

    public GraphDialog(javafx.stage.Window owner, String title, String dot) {
        init(owner, title);
        if (dot == null || dot.isBlank()) {
            showMsg(I18nUtil.getString("graph.noData"));
        } else {
            showDot(dot);
        }
    }

    public GraphDialog(javafx.stage.Window owner, String title) {
        init(owner, title);
        showLoading(I18nUtil.getString("graph.loading"));
    }

    private void init(javafx.stage.Window owner, String title) {
        if (owner != null) initOwner(owner);
        setTitle(title);
        initModality(javafx.stage.Modality.NONE);
        setResizable(true);

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefSize(W, H);
        try { pane.getStylesheets().add(AppTheme.darkStylesheet()); } catch (RuntimeException ignored) {}

        contentStack = new StackPane();
        contentStack.setStyle("-fx-background-color:#1e1e1e;");
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        viewToggle = new ToggleButton("DOT");
        viewToggle.getStyleClass().add("code-deck-toggle");
        viewToggle.setMinWidth(50); viewToggle.setMaxHeight(24);
        viewToggle.setVisible(false);
        viewToggle.setOnAction(e -> toggleView());

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill:#808080;-fx-font-size:11px;");

        var spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(6, viewToggle, spacer, statusLabel);
        bar.setStyle("-fx-padding:4px 8px;-fx-background-color:#252526;");
        bar.setMinHeight(28);

        VBox root = new VBox(contentStack, bar);
        VBox.setVgrow(contentStack, Priority.ALWAYS);
        pane.setContent(root);

        setOnShown(e -> {
            var w = pane.getScene().getWindow();
            if (w != null) DefaultWindowTheme.applyWindowDarkMode(w);
            if (w instanceof javafx.stage.Stage s) setIcon(s);
        });
    }

    private void showLoading(String msg) {
        var ind = new javafx.scene.control.ProgressIndicator(36);
        Label l = new Label(msg);
        l.setStyle("-fx-text-fill:#969696;-fx-font-size:13px;");
        VBox b = new VBox(12, ind, l);
        b.setAlignment(javafx.geometry.Pos.CENTER);
        b.setStyle("-fx-background-color:#1e1e1e;");
        contentStack.getChildren().setAll(b);
        viewToggle.setVisible(false);
        statusLabel.setText("");
    }

    // ---- public API ----

    public void showDot(String dot) {
        runOnFx(() -> {
            String d = dot == null ? "" : dot;
            if (d.isBlank()) { showMsg(I18nUtil.getString("graph.noData")); return; }

            showDotSource(d);
            try {
                currentGraphView = DotGraphRenderer.create(DotGraphParser.parse(d));
                contentStack.getChildren().setAll(currentGraphView);
                viewToggle.setVisible(true);
                viewToggle.setSelected(true);
                viewToggle.setText("DOT");
                statusLabel.setText("JavaFX 图形");
            } catch (RuntimeException e) {
                logger.warn("GraphDialog: 原生图形渲染失败，保留 DOT 源码", e);
                currentGraphView = null;
                statusLabel.setText("DOT 源码（图形解析失败）");
            }
        });
    }

    public void showMessage(String msg) {
        runOnFx(() -> showMsg(msg));
    }

    // ---- 内部 ----

    private void showDotSource(String dot) {
        if (dotArea == null) { dotArea = new TextArea(); dotArea.setEditable(false); dotArea.setWrapText(false); }
        dotArea.setStyle(DOT_STYLE);
        dotArea.setText(dot);
        contentStack.getChildren().setAll(dotArea);
        viewToggle.setVisible(false);
        currentGraphView = null;
    }

    private void showMsg(String msg) {
        if (dotArea == null) { dotArea = new TextArea(); dotArea.setEditable(false); dotArea.setWrapText(true); }
        dotArea.setStyle(DOT_STYLE);
        dotArea.setText(msg == null ? "" : msg);
        contentStack.getChildren().setAll(dotArea);
        viewToggle.setVisible(false);
        currentGraphView = null;
    }

    private void toggleView() {
        if (currentGraphView != null && contentStack.getChildren().contains(currentGraphView)) {
            contentStack.getChildren().setAll(dotArea);
            viewToggle.setText("Graph");
            statusLabel.setText("DOT 源码");
        } else if (dotArea != null && currentGraphView != null) {
            contentStack.getChildren().setAll(currentGraphView);
            viewToggle.setText("DOT");
            statusLabel.setText("JavaFX 图形");
        }
    }

    // ---- 静态工具 ----

    public static void show(javafx.stage.Window owner, String title, String dot) {
        Objects.requireNonNull(dot);
        var d = new GraphDialog(owner, title, dot);
        d.show();
        bringToFront(d);
    }

    private static void runOnFx(Runnable a) {
        if (Platform.isFxApplicationThread()) a.run(); else Platform.runLater(a);
    }

    private static void bringToFront(Dialog<?> d) {
        Platform.runLater(() -> {
            var w = d.getDialogPane().getScene() != null ? d.getDialogPane().getScene().getWindow() : null;
            if (w instanceof javafx.stage.Stage s) { s.toFront(); s.requestFocus(); }
        });
    }

    private static void setIcon(javafx.stage.Stage s) {
        try (var is = GraphDialog.class.getResourceAsStream("/icon/logo.png")) {
            if (is != null) s.getIcons().add(new javafx.scene.image.Image(is));
        } catch (Exception ignored) {}
    }
}
