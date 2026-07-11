package com.bingbaihanji.fxdecomplie.ui.graph;

import com.bingbaihanji.fxdecomplie.ui.IconHelper;
import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.util.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 图形可视化弹窗
 *
 * <p>使用 {@link DotGraphParser} + {@link DotGraphRenderer} 纯 JavaFX 渲染
 * 继承图和方法图,无需 WebView / WASM / 外部 JavaScript,避免在部分 JDK
 * 环境下因缺少 jfxwebkit 原生库或 WASM 安全策略导致渲染失败</p>
 *
 * <p>弹窗底部工具栏提供 Graph / DOT 切换按钮,可在图形视图和 DOT 源码之间
 * 来回切换,便于排查问题和复制 DOT 文本</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 继承图 / 方法图(背景线程生成 DOT 后切换到图形)
 * GraphDialog dialog = new GraphDialog(stage, "继承图 - MyClass");
 * dialog.show();
 * BackgroundTasks.run("BuildGraph", () -> {
 *     String dot = GraphService.toInheritanceDOT(tree);
 *     Platform.runLater(() -> dialog.showDot(dot));
 * });
 *
 * // 或直接展示已有 DOT
 * GraphDialog.show(stage, "标题", dot);
 * }</pre>
 *
 * @author bingbaihanji
 * @date 2026-06-22
 */
public class GraphDialog extends Dialog<Void> {

    private static final Logger log = LoggerFactory.getLogger(GraphDialog.class);

    /** 弹窗默认宽度 */
    private static final int DEFAULT_WIDTH = 820;
    /** 弹窗默认高度 */
    private static final int DEFAULT_HEIGHT = 640;
    /** DOT 源码 TextArea 的暗色主题样式 */
    private static final String DOT_STYLE =
            "-fx-control-inner-background:#1e1e1e;-fx-text-fill:#d4d4d4;"
                    + "-fx-font-family:'Consolas',monospace;-fx-font-size:13px;"
                    + "-fx-highlight-fill:#264f78;-fx-highlight-text-fill:#fff;";

    /** 内容区域(StackPane),承载 TextArea / 图形 / 加载提示 */
    private StackPane contentStack;
    /** DOT 源码文本区(懒初始化) */
    private TextArea dotArea;
    /** 当前图形视图节点(DotGraphRenderer 返回的 ScrollPane 或空图提示) */
    private Node currentGraphView;
    /** Graph / DOT 切换按钮 */
    private ToggleButton viewToggle;
    /** 底部状态标签(DOT 源码 / JavaFX 图形 / 错误信息) */
    private Label statusLabel;

    /**
     * 创建弹窗并立即渲染 DOT 为图形
     *
     * @param owner 父窗口
     * @param title 弹窗标题
     * @param dot   DOT 格式字符串
     */
    public GraphDialog(javafx.stage.Window owner, String title, String dot) {
        init(owner, title);
        if (dot == null || dot.isBlank()) {
            showMsg(I18nUtil.getString("graph.noData"));
        } else {
            showDot(dot);
        }
    }

    /**
     * 创建加载态弹窗,后续通过 {@link #showDot(String)} 更新为图形
     *
     * @param owner 父窗口
     * @param title 弹窗标题
     */
    public GraphDialog(javafx.stage.Window owner, String title) {
        init(owner, title);
        showLoading(I18nUtil.getString("graph.loading"));
    }

    /**
     * 便捷方法：创建并立即显示带 DOT 内容的弹窗
     *
     * @param owner 父窗口
     * @param title 弹窗标题
     * @param dot   DOT 格式字符串,不可为 null
     */
    public static void show(javafx.stage.Window owner, String title, String dot) {
        Objects.requireNonNull(dot, "dot 不可为 null");
        var dialog = new GraphDialog(owner, title, dot);
        dialog.show();
        bringToFront(dialog);
    }

    /**
     * 确保在 JavaFX 应用线程上执行操作
     * 若当前已在 FX 线程则直接执行,否则通过 {@link Platform#runLater(Runnable)} 调度
     *
     * @param action 需要在 FX 线程上执行的操作
     */
    private static void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    // ======================== 公开 API ========================

    /**
     * 将弹窗提升到 Z 轴最前并请求焦点
     *
     * @param dialog 目标弹窗
     */
    private static void bringToFront(Dialog<?> dialog) {
        Platform.runLater(() -> {
            var window = dialog.getDialogPane().getScene() != null
                    ? dialog.getDialogPane().getScene().getWindow() : null;
            if (window instanceof javafx.stage.Stage stage) {
                stage.toFront();
                stage.requestFocus();
            }
        });
    }

    // ======================== 内部视图切换 ========================

    /** 弹窗共享初始化：布局、样式、事件绑定 */
    private void init(javafx.stage.Window owner, String title) {
        if (owner != null) {
            initOwner(owner);
        }
        setTitle(title);
        initModality(javafx.stage.Modality.NONE);
        setResizable(true);

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        try {
            pane.getStylesheets().add(AppTheme.darkStylesheet());
        } catch (RuntimeException ignored) {
            // 样式加载失败不影响功能,继续使用默认样式
        }

        contentStack = new StackPane();
        contentStack.setStyle("-fx-background-color:#1e1e1e;");
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        viewToggle = new ToggleButton("DOT");
        viewToggle.getStyleClass().add("code-deck-toggle");
        viewToggle.setMinWidth(50);
        viewToggle.setMaxHeight(24);
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
            if (w != null) {
                DefaultWindowTheme.applyWindowDarkMode(w);
            }
            if (w instanceof javafx.stage.Stage s) {
                IconHelper.setStageIcon(s);
            }
        });
    }

    /**
     * 显示居中的加载指示器和提示文字(旋转进度圈 + 文本)
     *
     * @param msg 加载提示文字,如"正在生成图形..."
     */
    private void showLoading(String msg) {
        var indicator = new javafx.scene.control.ProgressIndicator(36);
        Label label = new Label(msg);
        label.setStyle("-fx-text-fill:#969696;-fx-font-size:13px;");
        VBox box = new VBox(12, indicator, label);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        box.setStyle("-fx-background-color:#1e1e1e;");
        contentStack.getChildren().setAll(box);
        viewToggle.setVisible(false);
        statusLabel.setText("");
    }

    /**
     * 解析 DOT 并渲染为 JavaFX 图形
     *
     * <p>先展示 DOT 源码(即时反馈),再同步解析 + 渲染图形(操作轻量,
     * 直接在 FX 线程执行不阻塞 UI)渲染失败时保留 DOT 源码视图</p>
     *
     * @param dot DOT 格式字符串
     */
    public void showDot(String dot) {
        runOnFx(() -> {
            String d = dot == null ? "" : dot;
            if (d.isBlank()) {
                showMsg(I18nUtil.getString("graph.noData"));
                return;
            }

            // 始终保留 DOT 源码视图作为后备
            showDotSource(d);
            try {
                currentGraphView = DotGraphRenderer.create(DotGraphParser.parse(d));
                contentStack.getChildren().setAll(currentGraphView);
                viewToggle.setVisible(true);
                viewToggle.setSelected(true);
                viewToggle.setText("DOT");
                statusLabel.setText("JavaFX 图形");
            } catch (RuntimeException e) {
                log.warn("GraphDialog: 原生图形渲染失败,保留 DOT 源码", e);
                currentGraphView = null;
                statusLabel.setText("DOT 源码(图形解析失败)");
            }
        });
    }

    /**
     * 在当前弹窗内显示纯文本消息
     *
     * @param msg 消息内容
     */
    public void showMessage(String msg) {
        runOnFx(() -> showMsg(msg));
    }

    // ======================== 静态工具方法 ========================

    /**
     * 切换到 DOT 源码视图(用于 Graph → DOT 切换或首次展示)
     * DOT 源码不自动换行以保持格式
     *
     * @param dot DOT 格式字符串
     */
    private void showDotSource(String dot) {
        switchToTextArea(dot, false);
    }

    /**
     * 显示纯文本消息(覆盖当前内容区域)
     * 消息文本自动换行以便阅读
     *
     * @param msg 消息内容,可为 null
     */
    private void showMsg(String msg) {
        switchToTextArea(msg == null ? "" : msg, true);
    }

    /**
     * 将内容区域切换为 TextArea
     *
     * @param text 显示的文本
     * @param wrap 是否启用自动换行(消息文本需换行,DOT 源码不需要)
     */
    private void switchToTextArea(String text, boolean wrap) {
        if (dotArea == null) {
            dotArea = new TextArea();
            dotArea.setEditable(false);
        }
        dotArea.setWrapText(wrap);
        dotArea.setStyle(DOT_STYLE);
        dotArea.setText(text);
        contentStack.getChildren().setAll(dotArea);
        viewToggle.setVisible(false);
        currentGraphView = null;
    }

    /**
     * 切换按钮回调：在图形视图和 DOT 源码视图之间切换
     *
     * <p>只有两个视图都存在时切换才有意义：当前显示图形 → 切到 DOT 源码 
     * 当前显示 DOT → 切回图形</p>
     */
    private void toggleView() {
        if (currentGraphView != null && contentStack.getChildren().contains(currentGraphView)) {
            // 图形 → DOT 源码
            contentStack.getChildren().setAll(dotArea);
            viewToggle.setText("Graph");
            statusLabel.setText("DOT 源码");
        } else if (dotArea != null && currentGraphView != null) {
            // DOT 源码 → 图形
            contentStack.getChildren().setAll(currentGraphView);
            viewToggle.setText("DOT");
            statusLabel.setText("JavaFX 图形");
        }
    }
}
