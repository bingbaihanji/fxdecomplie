package com.bingbaihanji.fxdecomplie.ui;

import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * 通用对话框工具方法
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class DialogHelper {

    private DialogHelper() {
        throw new AssertionError("utility class");
    }

    public static void showInfo(Stage owner, String title, String message) {
        showAlert(owner, Alert.AlertType.INFORMATION, title, message);
    }

    public static void showWarning(Stage owner, String title, String message) {
        showAlert(owner, Alert.AlertType.WARNING, title, message);
    }

    public static void showError(Stage owner, String title, String message) {
        showAlert(owner, Alert.AlertType.ERROR, title, message);
    }

    public static void showAlert(Stage owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.initOwner(owner);
        alert.setOnShown(e -> {
            var window = alert.getDialogPane().getScene().getWindow();
            DefaultWindowTheme.applyWindowDarkMode(window);
            if (window instanceof Stage s) setDialogIcon(s);
        });
        alert.showAndWait();
    }

    private static void setDialogIcon(Stage stage) {
        try {
            var stream = DialogHelper.class.getResourceAsStream("/icon/logo.png");
            if (stream != null) {
                stage.getIcons().add(new Image(stream));
            }
        } catch (Exception ignored) {
        }
    }
}
