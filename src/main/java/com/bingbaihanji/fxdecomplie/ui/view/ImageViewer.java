package com.bingbaihanji.fxdecomplie.ui.view;

import javafx.geometry.Bounds;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;

/**
 * IDEA 风格图片查看器：工具栏 + 滚轮缩放 + 旋转 + 拖拽平移
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class ImageViewer extends BorderPane {

    private static final Logger log = LoggerFactory.getLogger(ImageViewer.class);
    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 10.0;
    private static final double ZOOM_STEP = 0.15;

    /** 图片显示控件 */
    private final ImageView imageView = new ImageView();
    /** 图片容器(StackPane 居中,支持拖拽平移) */
    private final StackPane canvas = new StackPane(imageView);
    /** 底部状态信息标签(尺寸、缩放、旋转角度) */
    private final Label infoLabel = new Label("");

    /** 当前缩放比例 */
    private double zoom = 1.0;
    /** 鼠标按下时的 X 坐标 */
    private double mouseDownX;
    /** 鼠标按下时的 Y 坐标 */
    private double mouseDownY;
    /** 鼠标按下时图片的 TranslateX */
    private double transOnDownX;
    /** 鼠标按下时图片的 TranslateY */
    private double transOnDownY;

    /** 创建图片查看器,包含工具栏(缩放/适配/旋转)和键盘快捷键 */
    public ImageViewer() {
        setStyle("-fx-background-color: #1e1e1e;");

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        canvas.setStyle("-fx-background-color: #1e1e1e;");
        canvas.setMinSize(100, 60);
        setCenter(canvas);

        // 工具栏
        ToolBar bar = new ToolBar(
                btn("−", () -> changeZoom(-ZOOM_STEP)),
                btn("+", () -> changeZoom(ZOOM_STEP)),
                btn("Fit", this::fitWindow),
                btn("1:1", this::actualSize),
                btn("↺", () -> rotate(-90)),
                btn("↻", () -> rotate(90)),
                new javafx.scene.control.Separator(),
                infoLabel);
        bar.setStyle("-fx-background-color: #2d2d2d; -fx-padding: 2px 6px;");
        infoLabel.setStyle("-fx-text-fill: #969696; -fx-font-size: 11px;");
        setTop(bar);

        // 事件
        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                mouseDownX = e.getX();
                mouseDownY = e.getY();
                transOnDownX = imageView.getTranslateX();
                transOnDownY = imageView.getTranslateY();
            }
        });
        canvas.setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            double dx = e.getX() - mouseDownX;
            double dy = e.getY() - mouseDownY;
            imageView.setTranslateX(transOnDownX + dx);
            imageView.setTranslateY(transOnDownY + dy);
            clamp();
        });
        canvas.setOnMouseReleased(e -> clamp());
        canvas.setOnScroll(e -> {
            changeZoom(e.getDeltaY() > 0 ? ZOOM_STEP : -ZOOM_STEP);
            e.consume();
        });
        canvas.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                fitWindow();
            }
        });
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.R) {
                rotate(e.isShiftDown() ? -90 : 90);
                e.consume();
            } else if (e.getCode() == KeyCode.DIGIT0) {
                fitWindow();
                e.consume();
            }
        });
        setFocusTraversable(true);
    }

    /** 创建透明背景风格的工具栏按钮 */
    private static Button btn(String text, Runnable action) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: transparent; -fx-text-fill: #ccc; -fx-font-size: 14px; "
                + "-fx-padding: 2px 8px; -fx-cursor: hand;");
        b.setOnAction(e -> action.run());
        return b;
    }

    /** 将值限制在 [min, max] 区间内 */
    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    /** 从字节数组加载并显示图片,加载成功后自动适配窗口 */
    public void loadImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        try {
            Image img = new Image(new ByteArrayInputStream(bytes));
            if (img.isError()) {
                log.warn("Image error: {}", img.getException() != null
                        ? img.getException().getMessage() : "unknown");
                return;
            }
            imageView.setImage(img);
            if (!canvas.getChildren().contains(imageView)) {
                canvas.getChildren().setAll(imageView);
            }
            fitWindow();
        } catch (Exception ex) {
            log.warn("Image load failed", ex);
        }
    }

    /** 按步长缩放图片(正值为放大,负值为缩小) */
    private void changeZoom(double delta) {
        zoom = clamp(zoom + delta, MIN_ZOOM, MAX_ZOOM);
        applyZoom();
        clamp();
        updateInfo();
    }

    /** 缩放图片使其适配当前视口大小(重置旋转和平移) */
    private void fitWindow() {
        Image img = imageView.getImage();
        Bounds vb = canvas.getLayoutBounds();
        if (img == null || vb == null || vb.getWidth() <= 0) {
            return;
        }
        zoom = Math.min(vb.getWidth() / img.getWidth(), vb.getHeight() / img.getHeight()) * 0.9;
        zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        imageView.setRotate(0);
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);
        applyZoom();
        clamp();
        updateInfo();
    }

    /** 恢复 1:1 原始尺寸(重置旋转和平移) */
    private void actualSize() {
        zoom = 1.0;
        imageView.setRotate(0);
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);
        applyZoom();
        clamp();
        updateInfo();
    }

    // ---- 边界约束 ----

    /** 旋转图片指定角度(累积,取模 360) */
    private void rotate(int deg) {
        imageView.setRotate((imageView.getRotate() + deg) % 360);
        applyZoom();
        clamp();
        updateInfo();
    }

    /** 根据当前缩放比例更新 ImageView 的 fitWidth */
    private void applyZoom() {
        Image img = imageView.getImage();
        if (img != null) {
            imageView.setFitWidth(img.getWidth() * zoom);
            imageView.setFitHeight(0);
        }
    }

    // ---- 工具 ----

    /** 限制图片平移范围,防止拖出视口边界 */
    private void clamp() {
        Image img = imageView.getImage();
        Bounds vb = canvas.getLayoutBounds();
        if (img == null || vb == null || vb.getWidth() <= 0) {
            return;
        }
        double vw = vb.getWidth();
        double vh = vb.getHeight();
        double iw = img.getWidth() * zoom;
        double ih = img.getHeight() * zoom;
        double tx = imageView.getTranslateX();
        double ty = imageView.getTranslateY();

        // StackPane 居中,图片中心在视图中心偏移范围 ÷ 2
        double minTx = (vw - iw) / 2.0;
        double maxTx = (iw - vw) / 2.0;
        tx = clamp(tx, Math.min(minTx, maxTx), Math.max(minTx, maxTx));

        double minTy = (vh - ih) / 2.0;
        double maxTy = (ih - vh) / 2.0;
        ty = clamp(ty, Math.min(minTy, maxTy), Math.max(minTy, maxTy));

        imageView.setTranslateX(tx);
        imageView.setTranslateY(ty);
    }

    /** 更新状态栏显示的图片尺寸、缩放、旋转信息 */
    private void updateInfo() {
        Image img = imageView.getImage();
        if (img == null) {
            infoLabel.setText("");
            return;
        }
        infoLabel.setText((int) img.getWidth() + "×" + (int) img.getHeight()
                + "  " + Math.round(zoom * 100) + "%  " + (int) imageView.getRotate() + "°");
    }

    /** 重写 requestFocus 以确保键盘事件能被 ImageViewer 捕获 */
    @Override
    public void requestFocus() {
        super.requestFocus();
        setFocusTraversable(true);
    }
}
