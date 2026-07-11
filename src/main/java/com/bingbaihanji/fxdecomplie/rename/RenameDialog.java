package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.util.I18nUtil;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * 重命名对话框
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class RenameDialog {

    private RenameDialog() {
        throw new AssertionError("utility class");
    }

    /**
     * 显示重命名对话框
     *
     * @param owner   父窗口
     * @param oldName 当前名称
     * @param kind    类型("class" / "method" / "field")
     * @return 新名称,取消返回 null
     */
    public static String show(Stage owner, String oldName, String kind) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(I18nUtil.getString("rename.title") + " " + kind);
        dialog.setResizable(false);

        VBox content = new VBox(8);
        content.setPadding(new Insets(16));
        content.getChildren().add(new Label(I18nUtil.getString("rename.title") + " " + kind + ": " + oldName));

        TextField field = new TextField(oldName);
        field.setPrefWidth(300);
        field.selectAll();
        content.getChildren().add(field);
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #f48771;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        content.getChildren().add(errorLabel);

        DialogPane pane = dialog.getDialogPane();
        pane.setContent(content);
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // 拦截 OK 按钮点击,在前端做输入校验,避免无效值传递到后端
        javafx.scene.Node okButton = pane.lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String name = field.getText() == null ? "" : field.getText().trim();
            // 校验1：名称不能为空
            if (name.isEmpty()) {
                errorLabel.setText(I18nUtil.getString("rename.name.required"));
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                field.requestFocus();
                event.consume();
                return;
            }
            // 校验2：新名称不能与旧名称相同
            if (name.equals(oldName)) {
                errorLabel.setText(I18nUtil.getString("rename.same.name"));
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                field.requestFocus();
                event.consume();
                return;
            }
            // 校验3：必须是合法的 Java 标识符
            if (!RenameService.isValidName(name)) {
                errorLabel.setText(I18nUtil.getString("rename.invalid.identifier"));
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                field.requestFocus();
                event.consume();
                return;
            }
        });

        // 对话框显示后自动聚焦输入框
        dialog.setOnShown(e -> {
            field.requestFocus();
        });
        DialogHelper.applyNativeStyle(dialog);

        // 结果转换器：再次校验确保 ButtonType.OK 返回有效名称
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) {
                return null;
            }
            String name = field.getText().trim();
            return RenameService.isValidName(name) && !name.equals(oldName) ? name : null;
        });

        return dialog.showAndWait().orElse(null);
    }
}
