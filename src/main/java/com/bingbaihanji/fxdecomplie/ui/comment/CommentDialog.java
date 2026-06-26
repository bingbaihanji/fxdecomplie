package com.bingbaihanji.fxdecomplie.ui.comment;

import com.bingbaihanji.fxdecomplie.model.CommentData;
import com.bingbaihanji.util.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * 注释输入弹窗
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public final class CommentDialog {

    private static final Logger logger = LoggerFactory.getLogger(CommentDialog.class);

    private CommentDialog() {
        throw new AssertionError("utility class");
    }

    /**
     * 显示注释输入弹窗
     *
     * @param owner          父窗口
     * @param className      类全限定路径
     * @param memberSignature 成员签名
     * @param line            行号
     * @param sourceHash      源码 hash
     * @param optionsHash     选项 hash
     * @param existing        已有注释（更新模式），null 为新增模式
     * @param onSave          保存回调
     */
    public static void show(Window owner, String className, String memberSignature,
                            int line, String sourceHash, String optionsHash,
                            CommentData existing, Consumer<CommentData> onSave) {
        Dialog<CommentData> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(existing != null
                ? I18nUtil.getString("comment.updateTitle")
                : I18nUtil.getString("comment.title"));
        dialog.setResizable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));

        // 行号/成员标签
        Label infoLabel = new Label(I18nUtil.getString("comment.line") + ": " + line
                + (memberSignature != null && !memberSignature.isBlank()
                ? "  " + I18nUtil.getString("comment.member") + ": " + memberSignature : ""));
        grid.add(infoLabel, 0, 0, 2, 1);

        // 样式选择
        ComboBox<CommentData.CommentStyle> styleCombo = new ComboBox<>();
        styleCombo.getItems().addAll(CommentData.CommentStyle.values());
        styleCombo.setValue(existing != null ? existing.style() : CommentData.CommentStyle.LINE);
        grid.add(new Label(I18nUtil.getString("comment.style") + ":"), 0, 1);
        grid.add(styleCombo, 1, 1);

        // 文本输入
        TextArea textArea = new TextArea();
        textArea.setPromptText(I18nUtil.getString("comment.text"));
        textArea.setPrefRowCount(6);
        textArea.setPrefColumnCount(40);
        if (existing != null) {
            textArea.setText(existing.text());
        }
        GridPane.setHgrow(textArea, Priority.ALWAYS);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        grid.add(textArea, 0, 2, 2, 1);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setContent(grid);
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setOnShown(e -> {
            var win = dialogPane.getScene().getWindow();
            DefaultWindowTheme.applyWindowDarkMode(win);
            if (win instanceof javafx.stage.Stage s) {
                setDialogIcon(s);
            }
        });

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            String text = textArea.getText();
            if (text == null || text.isBlank()) {
                return existing != null ? new CommentData(className, memberSignature, line,
                        sourceHash, optionsHash, styleCombo.getValue(),
                        "", "bingbaihanji", Instant.now().toString()) : null;
            }
            return new CommentData(className, memberSignature, line,
                    sourceHash, optionsHash, styleCombo.getValue(),
                    text, "bingbaihanji", Instant.now().toString());
        });

        dialog.showAndWait().ifPresent(comment -> {
            if (comment.text().isEmpty() && existing == null) return; // 新增空注释不保存
            onSave.accept(comment);
        });
    }

    private static void setDialogIcon(javafx.stage.Stage stage) {
        try (var stream = CommentDialog.class.getResourceAsStream("/icon/logo.png")) {
            if (stream != null) {
                stage.getIcons().add(new javafx.scene.image.Image(stream));
            }
        } catch (Exception ignored) {
            logger.debug("设置注释对话框图标失败", ignored);
        }
    }
}
