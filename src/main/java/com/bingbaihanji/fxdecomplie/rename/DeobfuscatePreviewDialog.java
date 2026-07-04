package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.ui.IconHelper;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        dialog.setTitle("Deobfuscate — " + suggestions.size() + " renames");
        dialog.setResizable(true);

        ObservableList<RenameEntry> items = FXCollections.observableArrayList(suggestions);
        FilteredList<RenameEntry> filteredItems = new FilteredList<>(items, entry -> true);
        Map<RenameEntry, javafx.beans.property.BooleanProperty> selected = new LinkedHashMap<>();
        suggestions.forEach(entry -> selected.put(entry,
                new javafx.beans.property.SimpleBooleanProperty(true)));
        ListView<RenameEntry> listView = new ListView<>(filteredItems);
        listView.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();

            @Override
            protected void updateItem(RenameEntry item, boolean empty) {
                super.updateItem(item, empty);
                checkBox.selectedProperty().unbind();
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                checkBox.selectedProperty().bindBidirectional(selected.get(item));
                checkBox.setText("%s  %s.%s  ->  %s".formatted(
                        item.type(), shortClassName(item.className()), item.oldName(), item.newName()));
                setGraphic(checkBox);
                setText(null);
            }
        });
        listView.setPrefHeight(Math.min(suggestions.size() * 28 + 4, 400));
        listView.setPrefWidth(720);

        TextField filterField = new TextField();
        filterField.setPromptText("Filter");
        filterField.textProperty().addListener((obs, oldValue, newValue) ->
                filteredItems.setPredicate(entry -> matchesFilter(entry, newValue)));

        Button selectAll = new Button("All");
        selectAll.setOnAction(e -> items.forEach(item -> selected.get(item).set(true)));
        Button selectNone = new Button("None");
        selectNone.setOnAction(e -> items.forEach(item -> selected.get(item).set(false)));
        Button selectVisible = new Button("Select visible");
        selectVisible.setOnAction(e -> filteredItems.forEach(item -> selected.get(item).set(true)));
        Button clearVisible = new Button("Clear visible");
        clearVisible.setOnAction(e -> filteredItems.forEach(item -> selected.get(item).set(false)));
        Button clearCommon = new Button("Clear common");
        clearCommon.setOnAction(e -> items.stream()
                .filter(item -> AutoDeobfuscator.isCommonShortName(item.oldName()))
                .forEach(item -> selected.get(item).set(false)));
        HBox toolbar = new HBox(8, filterField, selectAll, selectNone,
                selectVisible, clearVisible, clearCommon);

        VBox content = new VBox(8, new Label("Select names to rename:"), toolbar, listView);
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
                    .filter(item -> selected.get(item).get())
                    .toList();
        });

        return dialog.showAndWait().orElse(List.of());
    }

    private static String shortClassName(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }
        int slash = className.lastIndexOf('/');
        return slash < 0 ? className : className.substring(slash + 1);
    }

    private static boolean matchesFilter(RenameEntry entry, String filter) {
        if (entry == null || filter == null || filter.isBlank()) {
            return true;
        }
        String needle = filter.toLowerCase(java.util.Locale.ROOT);
        return entry.type().toLowerCase(java.util.Locale.ROOT).contains(needle)
                || entry.className().toLowerCase(java.util.Locale.ROOT).contains(needle)
                || entry.oldName().toLowerCase(java.util.Locale.ROOT).contains(needle)
                || entry.newName().toLowerCase(java.util.Locale.ROOT).contains(needle);
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
