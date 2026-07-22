package com.bingbaihanji.fxdecomplie.ui;

import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.event.EventHandler;
import javafx.scene.control.*;
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

    private static final Logger log = LoggerFactory.getLogger(DialogHelper.class);

    private DialogHelper() {
        throw new AssertionError("utility class");
    }

    /** 显示信息提示对话框 */
    public static void showInfo(Stage owner, String title, String message) {
        showAlert(owner, Alert.AlertType.INFORMATION, title, message);
    }

    /** 显示警告提示对话框 */
    public static void showWarning(Stage owner, String title, String message) {
        showAlert(owner, Alert.AlertType.WARNING, title, message);
    }

    /** 确认对话框(是/否),返回 true 表示用户点击"是" */
    public static boolean showConfirm(Stage owner, String title, String message) {
        log.info("{} - {}", title, message);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) {
            alert.initOwner(owner);
        }
        applyNativeStyle(alert);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    /** 显示错误对话框(Stage 重载) */
    public static void showError(Stage owner, String title, String message) {
        showAlert(owner, Alert.AlertType.ERROR, title, message);
    }

    /** 显示错误对话框(Window 重载,适用于非 Stage 窗口场景) */
    public static void showError(Window owner, String title, String message) {
        showAlert(owner, Alert.AlertType.ERROR, title, message);
    }

    /**
     * 显示带操作按钮的错误对话框：重试 / 取消 / 查看日志
     *
     * @param owner     父窗口
     * @param title     对话框标题
     * @param message   错误信息
     * @param onRetry   点击"重试"时的回调(null 安全)
     * @param onViewLog 点击"查看日志"时的回调(null 安全)
     */
    public static void showErrorWithActions(Stage owner, String title, String message,
                                            Runnable onRetry, Runnable onViewLog) {
        log.error("{} - {}", title, message);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        ButtonType retryBtn = new ButtonType(
                I18nUtil.getString("dialog.retry"),
                ButtonBar.ButtonData.YES);
        ButtonType cancelBtn = new ButtonType(
                I18nUtil.getString("dialog.cancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType logBtn = new ButtonType(
                I18nUtil.getString("dialog.viewLog"),
                ButtonBar.ButtonData.HELP);

        alert.getButtonTypes().setAll(retryBtn, cancelBtn, logBtn);

        if (owner != null) {
            alert.initOwner(owner);
        }
        applyNativeStyle(alert);

        alert.showAndWait().ifPresent(bt -> {
            if (bt == retryBtn && onRetry != null) {
                onRetry.run();
            } else if (bt == logBtn && onViewLog != null) {
                onViewLog.run();
            }
        });
    }

    /** 显示通用 Alert 对话框(Stage 重载) */
    public static void showAlert(Stage owner, Alert.AlertType type, String title, String message) {
        showAlert((Window) owner, type, title, message);
    }

    public static void showAlert(Window owner, Alert.AlertType type, String title, String message) {
        // 同步记录到控制台/日志文件,确保报错时有轨迹可查
        String logMsg = title + " - " + message;
        switch (type) {
            case ERROR -> log.error(logMsg);
            case WARNING -> log.warn(logMsg);
            case INFORMATION -> log.info(logMsg);
            default -> log.debug(logMsg);
        }
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) {
            alert.initOwner(owner);
        }
        applyNativeStyle(alert);
        alert.showAndWait();
    }

    /** 为 JavaFX Dialog/Alert 应用应用暗色 CSS native 窗口暗色边框和窗口图标 */
    public static void applyNativeStyle(Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }
        try {
            var stylesheets = dialog.getDialogPane().getStylesheets();
            String darkStylesheet = AppTheme.darkStylesheet();
            if (!stylesheets.contains(darkStylesheet)) {
                stylesheets.add(darkStylesheet);
            }
        } catch (RuntimeException ignored) {
            log.debug("应用暗色样式表失败", ignored);
        }
        EventHandler<DialogEvent> previousOnShown = dialog.getOnShown();
        dialog.setOnShown(e -> {
            if (previousOnShown != null) {
                previousOnShown.handle(e);
            }
            var scene = dialog.getDialogPane().getScene();
            var window = scene == null ? null : scene.getWindow();
            if (window == null) {
                return;
            }
            DefaultWindowTheme.applyWindowDarkMode(window);
            if (window instanceof Stage s) {
                IconHelper.setStageIcon(s);
            }
        });
    }
}
