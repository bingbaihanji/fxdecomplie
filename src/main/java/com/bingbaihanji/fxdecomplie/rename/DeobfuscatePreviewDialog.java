package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.util.I18nUtil;
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
 * 反混淆预览对话框,列表展示原始名 → 建议新名,可勾选确认
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class DeobfuscatePreviewDialog {

    private DeobfuscatePreviewDialog() {
        throw new AssertionError("utility class");
    }

    /**
     * 显示预览对话框
     *
     * @param owner       父窗口
     * @param suggestions 建议的重命名列表
     * @return 用户确认的重命名列表（取消返回空列表）
     */
    public static List<RenameEntry> show(Stage owner, List<RenameEntry> suggestions) {
        if (suggestions == null) {
            suggestions = List.of();
        }
        if (suggestions.isEmpty()) {
            Dialog<String> d = new Dialog<>();
            d.initOwner(owner);
            d.setTitle(I18nUtil.getString("deobfuscate.title"));
            d.getDialogPane().setContent(new Label(I18nUtil.getString("deobfuscate.no.obfuscated")));
            d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            themeDialog(d);
            d.showAndWait();
            return List.of();
        }

        Dialog<List<RenameEntry>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(I18nUtil.getString("deobfuscate.title") + " — " + suggestions.size() + " renames");
        dialog.setResizable(true);

        // 构建可观察列表和可过滤列表,支持用户通过关键词筛选
        ObservableList<RenameEntry> items = FXCollections.observableArrayList(suggestions);
        FilteredList<RenameEntry> filteredItems = new FilteredList<>(items, entry -> true);
        // 默认所有条目选中
        Map<RenameEntry, javafx.beans.property.BooleanProperty> selected = new LinkedHashMap<>();
        suggestions.forEach(entry -> selected.put(entry,
                new javafx.beans.property.SimpleBooleanProperty(true)));

        // 自定义单元格：每行一个 CheckBox,绑定选中状态
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

        // 搜索过滤输入框,实时过滤列表中的条目
        TextField filterField = new TextField();
        filterField.setPromptText(I18nUtil.getString("deobfuscate.filter"));
        filterField.textProperty().addListener((obs, oldValue, newValue) ->
                filteredItems.setPredicate(entry -> matchesFilter(entry, newValue)));

        // 工具栏按钮：全选、全不选、选择/清除可见、清除常见短名
        Button selectAll = new Button(I18nUtil.getString("deobfuscate.all"));
        selectAll.setOnAction(e -> items.forEach(item -> selected.get(item).set(true)));
        Button selectNone = new Button(I18nUtil.getString("deobfuscate.none"));
        selectNone.setOnAction(e -> items.forEach(item -> selected.get(item).set(false)));
        Button selectVisible = new Button(I18nUtil.getString("deobfuscate.select.visible"));
        selectVisible.setOnAction(e -> filteredItems.forEach(item -> selected.get(item).set(true)));
        Button clearVisible = new Button(I18nUtil.getString("deobfuscate.clear.visible"));
        clearVisible.setOnAction(e -> filteredItems.forEach(item -> selected.get(item).set(false)));
        Button clearCommon = new Button(I18nUtil.getString("deobfuscate.clear.common"));
        clearCommon.setOnAction(e -> items.stream()
                .filter(item -> AutoDeobfuscator.isCommonShortName(item.oldName()))
                .forEach(item -> selected.get(item).set(false)));
        HBox toolbar = new HBox(8, filterField, selectAll, selectNone,
                selectVisible, clearVisible, clearCommon);

        VBox content = new VBox(8, new Label(I18nUtil.getString("deobfuscate.select.names")), toolbar, listView);
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

    /** 从内部类名中提取短类名（去除包路径前缀） */
    private static String shortClassName(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }
        int slash = className.lastIndexOf('/');
        return slash < 0 ? className : className.substring(slash + 1);
    }

    /** 过滤匹配：在类型、类名、旧名、新名中搜索关键字（忽略大小写） */
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

    /** 为对话框应用原生风格主题 */
    private static void themeDialog(Dialog<?> dialog) {
        DialogHelper.applyNativeStyle(dialog);
    }
}
