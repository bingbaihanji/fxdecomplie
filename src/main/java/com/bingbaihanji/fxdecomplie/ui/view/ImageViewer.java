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
 * IDEA 风格图片查看器：工具栏 + 滚轮缩放 + 旋转 + 拖拽平移。
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class ImageViewer extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(ImageViewer.class);
    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 10.0;
    private static final double ZOOM_STEP = 0.15;

    private final ImageView imageView = new ImageView();
    private final StackPane canvas = new StackPane(imageView);
    private final Label infoLabel = new Label("");

    private double zoom = 1.0;
    private double mouseDownX;
    private double mouseDownY;
    private double transOnDownX;
    private double transOnDownY;

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

    private static Button btn(String text, Runnable action) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: transparent; -fx-text-fill: #ccc; -fx-font-size: 14px; "
                + "-fx-padding: 2px 8px; -fx-cursor: hand;");
        b.setOnAction(e -> action.run());
        return b;
    }

    // ---- 操作 ----

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    public void loadImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        try {
            Image img = new Image(new ByteArrayInputStream(bytes));
            if (img.isError()) {
                logger.warn("Image error: {}", img.getException() != null
                        ? img.getException().getMessage() : "unknown");
                return;
            }
            imageView.setImage(img);
            if (!canvas.getChildren().contains(imageView)) {
                canvas.getChildren().setAll(imageView);
            }
            fitWindow();
        } catch (Exception ex) {
            logger.warn("Image load failed", ex);
        }
    }

    private void changeZoom(double delta) {
        zoom = clamp(zoom + delta, MIN_ZOOM, MAX_ZOOM);
        applyZoom();
        clamp();
        updateInfo();
    }

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

    private void rotate(int deg) {
        imageView.setRotate((imageView.getRotate() + deg) % 360);
        applyZoom();
        clamp();
        updateInfo();
    }

    private void applyZoom() {
        Image img = imageView.getImage();
        if (img != null) {
            imageView.setFitWidth(img.getWidth() * zoom);
            imageView.setFitHeight(0);
        }
    }

    // ---- 工具 ----

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

        // StackPane 居中，图片中心在视图中心。偏移范围 ÷ 2
        double minTx = (vw - iw) / 2.0;
        double maxTx = (iw - vw) / 2.0;
        tx = clamp(tx, Math.min(minTx, maxTx), Math.max(minTx, maxTx));

        double minTy = (vh - ih) / 2.0;
        double maxTy = (ih - vh) / 2.0;
        ty = clamp(ty, Math.min(minTy, maxTy), Math.max(minTy, maxTy));

        imageView.setTranslateX(tx);
        imageView.setTranslateY(ty);
    }

    private void updateInfo() {
        Image img = imageView.getImage();
        if (img == null) {
            infoLabel.setText("");
            return;
        }
        infoLabel.setText((int) img.getWidth() + "×" + (int) img.getHeight()
                + "  " + Math.round(zoom * 100) + "%  " + (int) imageView.getRotate() + "°");
    }

    @Override
    public void requestFocus() {
        super.requestFocus();
        setFocusTraversable(true);
    }
}
