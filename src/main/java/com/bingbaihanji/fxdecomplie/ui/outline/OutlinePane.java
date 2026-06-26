package com.bingbaihanji.fxdecomplie.ui.outline;

import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.util.I18nUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 大纲面板显示当前类的字段、方法、内部类列表,支持实时过滤和点击跳转
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class OutlinePane extends VBox {

    private final TextField filterField;
    private final ListView<OutlineMember> listView;
    private final ObservableList<OutlineMember> sourceList;
    private final FilteredList<OutlineMember> filteredList;
    private final AtomicLong updateGeneration = new AtomicLong();
    private JumpHandler jumpHandler;

    public OutlinePane() {
        setPadding(new Insets(4));
        setSpacing(4);
        setStyle("-fx-background-color: #252526;");

        Label title = new Label(I18nUtil.getString("outline.title"));
        title.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-padding: 2px 4px;");

        filterField = new TextField();
        filterField.setPromptText(I18nUtil.getString("outline.filter"));
        filterField.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc; -fx-font-size: 12px;");

        listView = new ListView<>();
        sourceList = FXCollections.observableArrayList();
        filteredList = new FilteredList<>(sourceList, p -> true);
        listView.setItems(filteredList);
        listView.setStyle("-fx-background-color: #252526;");
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(OutlineMember item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String icon = switch (item.type()) {
                        case FIELD -> "F ";
                        case METHOD -> "M ";
                        case INNER_CLASS -> "C ";
                    };
                    setText(icon + item.name() + "  —  " + item.modifiers());
                    Color c = item.type() == OutlineMember.MemberType.METHOD ? Color.web("#dcdcaa")
                            : item.type() == OutlineMember.MemberType.FIELD ? Color.web("#9cdcfe")
                              : Color.web("#4ec9b0");
                    setTextFill(c);
                    setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas', monospace;");
                }
            }
        });

        listView.setOnMouseClicked(e -> {
            OutlineMember selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null && jumpHandler != null) {
                jumpHandler.jump(selected.lineNumber());
            }
        });

        filterField.textProperty().addListener((obs, old, text) -> {
            filteredList.setPredicate(member -> {
                if (text == null || text.isEmpty()) return true;
                return member.name().toLowerCase().contains(text.toLowerCase());
            });
        });

        getChildren().addAll(title, filterField, listView);
    }

    /** 更新大纲内容 */
    public void update(String sourceCode) {
        long generation = updateGeneration.incrementAndGet();
        // 快照当前过滤字段文本，避免覆盖用户在解析期间手动输入的内容
        String filterBefore = filterField.getText();
        BackgroundTasks.run("OutlineParse", () -> {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            List<OutlineMember> members = OutlineParser.parse(sourceCode);
            Platform.runLater(() -> {
                if (updateGeneration.get() == generation) {
                    sourceList.setAll(members);
                    // 仅当用户在解析期间未修改过滤条件时才清除
                    if (filterBefore.equals(filterField.getText())) {
                        filterField.clear();
                    }
                }
            });
        });
    }

    /** 清空 */
    public void clear() {
        updateGeneration.incrementAndGet();
        sourceList.clear();
        filterField.clear();
    }

    public void setJumpHandler(JumpHandler handler) {
        this.jumpHandler = handler;
    }

    @FunctionalInterface
    public interface JumpHandler {
        void jump(int lineNumber);
    }
}
