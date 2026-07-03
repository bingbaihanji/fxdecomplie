package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.ui.IconHelper;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * 重命名对话框。
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class RenameDialog {

    private RenameDialog() {
        throw new AssertionError("utility class");
    }

    /**
     * 显示重命名对话框。
     *
     * @param owner   父窗口
     * @param oldName 当前名称
     * @param kind    类型（"class" / "method" / "field"）
     * @return 新名称，取消返回 null
     */
    public static String show(Stage owner, String oldName, String kind) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Rename " + kind);
        dialog.setResizable(false);

        VBox content = new VBox(8);
        content.setPadding(new Insets(16));
        content.getChildren().add(new Label("Rename " + kind + ": " + oldName));

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
        javafx.scene.Node okButton = pane.lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String name = field.getText() == null ? "" : field.getText().trim();
            if (name.isEmpty()) {
                errorLabel.setText("Name is required.");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                field.requestFocus();
                event.consume();
                return;
            }
            if (name.equals(oldName)) {
                errorLabel.setText("New name is the same as the old name.");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                field.requestFocus();
                event.consume();
                return;
            }
            if (!RenameService.isValidName(name)) {
                errorLabel.setText("Invalid Java identifier.");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                field.requestFocus();
                event.consume();
            }
        });

        dialog.setOnShown(e -> {
            DefaultWindowTheme.applyWindowDarkMode(pane.getScene().getWindow());
            if (pane.getScene().getWindow() instanceof Stage s) {
                IconHelper.setStageIcon(s);
            }
            field.requestFocus();
        });

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
