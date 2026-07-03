package com.bingbaihanji.fxdecomplie.ui.comment;

import com.bingbaihanji.fxdecomplie.model.CommentData;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.CommentManager;
import com.bingbaihanji.util.I18nUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * 注释列表面板，以表格形式展示当前类的用户注释，支持过滤、双击跳转、删除。
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class CommentListPane extends VBox {

    private final TableView<CommentData> table = new TableView<>();
    private final ObservableList<CommentData> allRows = FXCollections.observableArrayList();
    private final TextField filterField = new TextField();
    private Consumer<Integer> onJumpToLine;
    private Runnable onDelete;
    private String workspaceHash;
    private String className;

    public CommentListPane() {
        setPadding(new Insets(4));
        filterField.setPromptText(I18nUtil.getString("comment.filterPrompt"));
        filterField.textProperty().addListener((obs, o, n) -> applyFilter());
        VBox.setMargin(filterField, new Insets(0, 0, 4, 0));

        table.setItems(allRows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(I18nUtil.getString("comment.empty")));

        TableColumn<CommentData, Integer> lineCol = new TableColumn<>(I18nUtil.getString("comment.column.line"));
        lineCol.setCellValueFactory(c -> javafx.beans.binding.Bindings.createObjectBinding(() -> c.getValue().line()));
        lineCol.setPrefWidth(45);
        lineCol.setMaxWidth(55);

        TableColumn<CommentData, String> memberCol = new TableColumn<>(I18nUtil.getString("comment.column.member"));
        memberCol.setCellValueFactory(c -> javafx.beans.binding.Bindings.createStringBinding(() ->
                c.getValue().memberSignature() != null && !c.getValue().memberSignature().isBlank()
                        ? c.getValue().memberSignature() : "—"));
        memberCol.setPrefWidth(100);

        TableColumn<CommentData, String> summaryCol = new TableColumn<>(I18nUtil.getString("comment.column.summary"));
        summaryCol.setCellValueFactory(c -> javafx.beans.binding.Bindings.createStringBinding(() -> {
            String t = c.getValue().text();
            return t != null && t.length() > 40 ? t.substring(0, 40) + "…" : t != null ? t : "";
        }));

        TableColumn<CommentData, String> timeCol = new TableColumn<>(I18nUtil.getString("comment.column.time"));
        timeCol.setCellValueFactory(c -> javafx.beans.binding.Bindings.createStringBinding(() -> {
            String t = c.getValue().time();
            return (t != null && t.length() >= 10 ? t.substring(0, 10) : "") + " " + c.getValue().author();
        }));
        timeCol.setPrefWidth(140);

        table.getColumns().addAll(lineCol, memberCol, summaryCol, timeCol);
        table.setRowFactory(tv -> {
            TableRow<CommentData> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && onJumpToLine != null) {
                    onJumpToLine.accept(row.getItem().line());
                }
            });
            return row;
        });

        MenuItem delItem = new MenuItem(I18nUtil.getString("comment.delete"));
        delItem.setOnAction(e -> {
            CommentData sel = table.getSelectionModel().getSelectedItem();
            if (sel != null && workspaceHash != null && className != null) {
                String ws = workspaceHash;
                String cn = className;
                CommentData toDelete = sel;
                BackgroundTasks.run("CommentDelete", () -> {
                    CommentManager.delete(ws, cn, toDelete.memberSignature(), toDelete.line(), toDelete.time());
                    Platform.runLater(() -> {
                        refresh();
                        if (onDelete != null) {
                            onDelete.run();
                        }
                    });
                });
            }
        });
        table.setContextMenu(new ContextMenu(delItem));
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(filterField, table);
    }

    public void load(String wsHash, String clsName) {
        this.workspaceHash = wsHash;
        this.className = clsName;
        refresh();
    }

    public void refresh() {
        if (workspaceHash == null || className == null) { allRows.clear(); return; }
        List<CommentData> list = CommentManager.load(workspaceHash, className);
        Platform.runLater(() -> { allRows.setAll(list); applyFilter(); });
    }

    public void clear() { workspaceHash = null; className = null; allRows.clear(); filterField.clear(); }
    public void setOnJumpToLine(Consumer<Integer> h) { this.onJumpToLine = h; }
    public void setOnDelete(Runnable r) { this.onDelete = r; }

    private void applyFilter() {
        String f = filterField.getText();
        if (f == null || f.isBlank()) { table.setItems(allRows); return; }
        String lo = f.toLowerCase();
        var filtered = allRows.filtered(c -> c.text().toLowerCase().contains(lo));
        table.setItems(filtered);
    }
}
