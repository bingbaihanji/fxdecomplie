package com.bingbaihanji.fxdecomplie.ui;

import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用对话框工具方法
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class DialogHelper {

    private static final Logger logger = LoggerFactory.getLogger(DialogHelper.class);

    private DialogHelper() {
        throw new AssertionError("utility class");
    }

    public static void showInfo(Stage owner, String title, String message) {
        showAlert(owner, Alert.AlertType.INFORMATION, title, message);
    }

    public static void showWarning(Stage owner, String title, String message) {
        showAlert(owner, Alert.AlertType.WARNING, title, message);
    }

    /** 确认对话框（是/否），返回 true 表示用户点击"是" */
    public static boolean showConfirm(Stage owner, String title, String message) {
        logger.info("{} - {}", title, message);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) alert.initOwner(owner);
        try {
            alert.getDialogPane().getStylesheets().add(AppTheme.darkStylesheet());
        } catch (RuntimeException ignored) {
        }
        alert.setOnShown(e -> {
            var w = alert.getDialogPane().getScene().getWindow();
            DefaultWindowTheme.applyWindowDarkMode(w);
            if (w instanceof Stage s) IconHelper.setStageIcon(s);
        });
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    public static void showError(Stage owner, String title, String message) {
        showAlert(owner, Alert.AlertType.ERROR, title, message);
    }

    public static void showError(Window owner, String title, String message) {
        showAlert(owner, Alert.AlertType.ERROR, title, message);
    }

    public static void showAlert(Stage owner, Alert.AlertType type, String title, String message) {
        showAlert((Window) owner, type, title, message);
    }

    public static void showAlert(Window owner, Alert.AlertType type, String title, String message) {
        // 同步记录到控制台/日志文件，确保报错时有轨迹可查
        String logMsg = title + " - " + message;
        switch (type) {
            case ERROR -> logger.error(logMsg);
            case WARNING -> logger.warn(logMsg);
            case INFORMATION -> logger.info(logMsg);
            default -> logger.debug(logMsg);
        }
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) {
            alert.initOwner(owner);
        }
        try {
            alert.getDialogPane().getStylesheets().add(AppTheme.darkStylesheet());
        } catch (RuntimeException ignored) {
            logger.debug("应用暗色样式表失败", ignored);
        }
        alert.setOnShown(e -> {
            var window = alert.getDialogPane().getScene().getWindow();
            DefaultWindowTheme.applyWindowDarkMode(window);
            if (window instanceof Stage s) {
                IconHelper.setStageIcon(s);
            }
        });
        alert.showAndWait();
    }

}
