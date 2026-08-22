package com.bingbaihanji.fxdecomplie.ui.uml;

import com.bingbaihanji.fxdecomplie.ui.IconHelper;
import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * UML 类图弹窗:加载态 → 渲染画布,底部工具栏提供亮/暗主题切换,PNG 导出,PlantUML 导出.
 */
public class UmlClassDiagramDialog extends Dialog<Void> {

    private static final Logger log = LoggerFactory.getLogger(UmlClassDiagramDialog.class);

    private static final int DEFAULT_WIDTH = 880;

    private static final int DEFAULT_HEIGHT = 660;

    private final StackPane contentStack = new StackPane();

    private final Label statusLabel = new Label();

    private final ToggleGroup themeGroup = new ToggleGroup();

    private final javafx.stage.Window ownerWindow;

    private final ComboBox<Double> scaleCombo = new ComboBox<>();

    private UmlDiagram currentDiagram;

    private UmlTheme currentTheme = UmlTheme.DARK;

    private Pane diagramCanvas;

    private UmlDiagramRenderer.UmlView currentView;

    public UmlClassDiagramDialog(javafx.stage.Window owner, String title) {
        this.ownerWindow = owner;
        if (owner != null) {
            initOwner(owner);
        }
        setTitle(title);
        initModality(Modality.NONE);
        setResizable(true);

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        try {
            pane.getStylesheets().add(AppTheme.darkStylesheet());
        } catch (RuntimeException ignored) {
            // 样式加载失败不影响功能
        }

        contentStack.setStyle("-fx-background-color:#1e1e1e;");
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        RadioButton light = new RadioButton(I18nUtil.getString("uml.theme.light"));
        light.setToggleGroup(themeGroup);
        light.setUserData(UmlTheme.LIGHT);
        RadioButton dark = new RadioButton(I18nUtil.getString("uml.theme.dark"));
        dark.setToggleGroup(themeGroup);
        dark.setUserData(UmlTheme.DARK);
        dark.setSelected(true);
        themeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.getUserData() instanceof UmlTheme theme) {
                currentTheme = theme;
                render();
            }
        });

        Button fitButton = new Button(I18nUtil.getString("uml.export.fit"));
        fitButton.setOnAction(e -> {
            if (currentView != null) {
                currentView.fitToViewport().run();
            }
        });
        Button exportPng = new Button(I18nUtil.getString("uml.exportPng"));
        exportPng.setOnAction(e -> exportPng());
        Button exportPuml = new Button(I18nUtil.getString("uml.exportPuml"));
        exportPuml.setOnAction(e -> exportPuml());

        Label scaleLabel = new Label(I18nUtil.getString("uml.export.scale"));
        scaleLabel.setStyle("-fx-text-fill:#808080;-fx-font-size:11px;");
        scaleCombo.getItems().addAll(1.0, 2.0, 3.0);
        scaleCombo.setValue(2.0);
        scaleCombo.setPrefWidth(64);
        scaleCombo.setStyle("-fx-font-size:11px;");

        statusLabel.setStyle("-fx-text-fill:#808080;-fx-font-size:11px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(6, light, dark, new Separator(), fitButton,
                exportPng, scaleLabel, scaleCombo, exportPuml, spacer, statusLabel);
        bar.setStyle("-fx-padding:4px 8px;-fx-background-color:#252526;");
        bar.setMinHeight(30);
        bar.setAlignment(Pos.CENTER_LEFT);

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

        showLoading(I18nUtil.getString("uml.loading"));
    }

    private static void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /** 从 canvas 递归查找第一个 Group(包含类图内容的容器) */
    private static javafx.scene.Group findGroup(javafx.scene.Parent canvas) {
        for (javafx.scene.Node child : canvas.getChildrenUnmodifiable()) {
            if (child instanceof javafx.scene.Group g) {
                return g;
            }
            if (child instanceof javafx.scene.Parent p) {
                javafx.scene.Group found = findGroup(p);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** 显示已构建的类图(FX 线程) */
    public void showDiagram(UmlDiagram diagram) {
        runOnFx(() -> {
            currentDiagram = diagram;
            render();
        });
    }

    /** 显示纯文本消息(FX 线程) */
    public void showMessage(String msg) {
        runOnFx(() -> showMsg(msg));
    }

    private void render() {
        if (currentDiagram == null) {
            showMsg(I18nUtil.getString("uml.noData"));
            return;
        }
        try {
            UmlDiagramRenderer.UmlView view = UmlDiagramRenderer.create(currentDiagram, currentTheme);
            currentView = view;
            diagramCanvas = view.canvas();
            contentStack.getChildren().setAll(view.scrollPane());
            statusLabel.setText("");
        } catch (RuntimeException e) {
            log.warn("UML 类图渲染失败", e);
            showMsg(I18nUtil.getString("uml.renderFailed"));
        }
    }

    private void showLoading(String msg) {
        ProgressIndicator indicator = new ProgressIndicator(36);
        Label label = new Label(msg);
        label.setStyle("-fx-text-fill:#969696;-fx-font-size:13px;");
        VBox box = new VBox(12, indicator, label);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color:#1e1e1e;");
        contentStack.getChildren().setAll(box);
        statusLabel.setText("");
    }

    private void showMsg(String msg) {
        TextArea area = new TextArea(msg == null ? "" : msg);
        area.setEditable(false);
        area.setWrapText(true);
        contentStack.getChildren().setAll(area);
        statusLabel.setText("");
    }

    private void exportPng() {
        if (diagramCanvas == null || currentView == null) {
            return;
        }
        double exportScale = scaleCombo.getValue();
        if (exportScale <= 0) {
            exportScale = 2.0;
        }

        // 以原始内容尺寸计算导出分辨率(不受当前缩放影响)
        double contentW = currentView.contentWidth();
        double contentH = currentView.contentHeight();
        if (contentW <= 0 || contentH <= 0) {
            return;
        }
        long outW = Math.round(contentW * exportScale);
        long outH = Math.round(contentH * exportScale);
        // 整数溢出保护:WritableImage 内部用 int 安全检查
        if (outW > 16384 || outH > 16384 || outW * outH > 100_000_000) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.CONFIRMATION);
            alert.setTitle(I18nUtil.getString("uml.exportPng"));
            alert.setHeaderText(I18nUtil.getString("uml.export.tooLarge.confirm",
                    String.valueOf(outW), String.valueOf(outH)));
            alert.initOwner(ownerWindow);
            if (alert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
                    != javafx.scene.control.ButtonType.OK) {
                return;
            }
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("uml.exportPng"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG 图片", "*.png"));
        File file = chooser.showSaveDialog(ownerWindow);
        if (file == null) {
            return;
        }

        // 临时放大 canvas 和 Group 以导出分辨率渲染,snapshot 完毕后恢复
        double origZoom = currentView.currentZoom();
        javafx.scene.control.ScrollPane scroll = currentView.scrollPane();
        double origH = scroll.getHvalue();
        double origV = scroll.getVvalue();
        double vpW = scroll.getViewportBounds().getWidth();
        double vpH = scroll.getViewportBounds().getHeight();
        javafx.scene.Group group = findGroup(diagramCanvas);

        try {
            // 1. 将 Group 缩放设为导出倍率(内容填满导出 canvas)
            double scaledW = contentW * exportScale;
            double scaledH = contentH * exportScale;
            if (group != null) {
                group.getTransforms().setAll(new Scale(exportScale, exportScale, 0, 0));
                group.setLayoutX(0);
                group.setLayoutY(0);
            }

            // 2. 将 canvas 设为导出尺寸
            diagramCanvas.setMinSize(scaledW, scaledH);
            diagramCanvas.setPrefSize(scaledW, scaledH);
            diagramCanvas.setMaxSize(scaledW, scaledH);

            // 3. 强制布局更新
            diagramCanvas.applyCss();
            diagramCanvas.layout();

            // 4. 快照(canvas 以导出分辨率渲染,内容填满整张图)
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.web(currentTheme.background()));
            WritableImage image = diagramCanvas.snapshot(params, null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            statusLabel.setText(I18nUtil.getString("uml.export.done", file.getName()));
        } catch (Exception e) {
            log.error("导出 PNG 失败", e);
            statusLabel.setText(I18nUtil.getString("uml.export.failed",
                    e.getMessage() == null ? "" : e.getMessage()));
        } finally {
            // 恢复 Group 缩放和居中
            if (group != null) {
                group.getTransforms().setAll(new Scale(origZoom, origZoom, 0, 0));
            }

            // 恢复 canvas 尺寸(与 ZoomController.applyZoom 逻辑一致)
            double restoreW = Math.max(contentW * origZoom, vpW > 0 ? vpW : contentW * origZoom);
            double restoreH = Math.max(contentH * origZoom, vpH > 0 ? vpH : contentH * origZoom);
            diagramCanvas.setMinSize(restoreW, restoreH);
            diagramCanvas.setPrefSize(restoreW, restoreH);
            diagramCanvas.setMaxSize(restoreW, restoreH);

            // Group 居中
            if (group != null) {
                group.setLayoutX((restoreW - contentW * origZoom) / 2.0);
                group.setLayoutY((restoreH - contentH * origZoom) / 2.0);
            }

            // 恢复滚动位置
            scroll.setHvalue(origH);
            scroll.setVvalue(origV);
        }
    }

    private void exportPuml() {
        if (currentDiagram == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("uml.exportPuml"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PlantUML 源码", "*.puml"));
        File file = chooser.showSaveDialog(ownerWindow);
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(),
                    PumlExporter.buildSource(currentDiagram, currentTheme), StandardCharsets.UTF_8);
            statusLabel.setText(I18nUtil.getString("uml.export.done", file.getName()));
        } catch (Exception e) {
            log.error("导出 .puml 失败", e);
            statusLabel.setText(I18nUtil.getString("uml.export.failed",
                    e.getMessage() == null ? "" : e.getMessage()));
        }
    }
}
