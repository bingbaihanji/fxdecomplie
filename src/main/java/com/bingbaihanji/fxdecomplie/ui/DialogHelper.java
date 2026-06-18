package com.bingbaihanji.fxdecomplie.ui;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * 通用对话框工具方法。
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
        alert.showAndWait();
    }
}
