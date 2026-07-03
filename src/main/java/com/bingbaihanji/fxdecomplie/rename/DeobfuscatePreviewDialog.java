package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.ui.IconHelper;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;

import java.util.List;

/**
 * 反混淆预览对话框，列表展示原始名 → 建议新名，可勾选确认。
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class DeobfuscatePreviewDialog {

    private DeobfuscatePreviewDialog() {
        throw new AssertionError("utility class");
    }

    /**
     * 显示预览对话框。
     *
     * @param owner       父窗口
     * @param suggestions 建议的重命名列表
     * @return 用户确认的重命名列表（取消返回空列表）
     */
    public static List<RenameEntry> show(Stage owner, List<RenameEntry> suggestions) {
        if (suggestions.isEmpty()) {
            Dialog<String> d = new Dialog<>();
            d.initOwner(owner);
            d.setTitle("Deobfuscate");
            d.getDialogPane().setContent(new Label("No obfuscated names found."));
            d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            themeDialog(d);
            d.showAndWait();
            return List.of();
        }

        Dialog<List<RenameEntry>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Deobfuscate — " + suggestions.size() + " classes");
        dialog.setResizable(true);

        // 使用 CheckBoxListCell 展示
        ObservableList<RenameEntry> items = FXCollections.observableArrayList(suggestions);
        ListView<RenameEntry> listView = new ListView<>(items);
        listView.setCellFactory(CheckBoxListCell.forListView(item -> {
            javafx.beans.property.BooleanProperty prop =
                    new javafx.beans.property.SimpleBooleanProperty(true);
            return prop;
        }));
        listView.setPrefHeight(Math.min(suggestions.size() * 28 + 4, 400));
        listView.setPrefWidth(450);

        VBox content = new VBox(8, new Label("Select classes to rename:"), listView);
        content.setPadding(new Insets(12));

        DialogPane pane = dialog.getDialogPane();
        pane.setContent(content);
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        themeDialog(dialog);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) {
                return null;
            }
            return items.stream()
                    .map(item -> {
                        var cell = (CheckBoxListCell<RenameEntry>) listView.lookup(
                                ".list-cell[item=\"" + item.oldName() + "\"]");
                        return item;
                    })
                    .toList();
        });

        return dialog.showAndWait().orElse(List.of());
    }

    private static void themeDialog(Dialog<?> dialog) {
        dialog.setOnShown(e -> {
            DefaultWindowTheme.applyWindowDarkMode(
                    dialog.getDialogPane().getScene().getWindow());
            if (dialog.getDialogPane().getScene().getWindow() instanceof Stage s) {
                IconHelper.setStageIcon(s);
            }
        });
    }
}
