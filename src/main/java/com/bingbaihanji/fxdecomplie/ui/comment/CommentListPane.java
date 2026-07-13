package com.bingbaihanji.fxdecomplie.ui.comment;

import com.bingbaihanji.fxdecomplie.model.CommentData;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.CommentManager;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
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
 * 注释列表面板,以表格形式展示当前类的用户注释,支持过滤 双击跳转 删除
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

    /**
     * 构建注释列表面板,包含过滤器输入框和四列表格(行号 成员 摘要 时间)
     */
    public CommentListPane() {
        setPadding(new Insets(4));
        // 过滤器输入框：监听文本变化实时过滤
        filterField.setPromptText(I18nUtil.getString("comment.filterPrompt"));
        filterField.textProperty().addListener((obs, o, n) -> applyFilter());
        VBox.setMargin(filterField, new Insets(0, 0, 4, 0));

        // 表格基本配置
        table.setItems(allRows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(I18nUtil.getString("comment.empty")));

        // 行号列
        TableColumn<CommentData, Integer> lineCol = new TableColumn<>(I18nUtil.getString("comment.column.line"));
        lineCol.setCellValueFactory(c -> javafx.beans.binding.Bindings.createObjectBinding(() -> c.getValue().line()));
        lineCol.setPrefWidth(45);
        lineCol.setMaxWidth(55);

        // 成员签名列
        TableColumn<CommentData, String> memberCol = new TableColumn<>(I18nUtil.getString("comment.column.member"));
        memberCol.setCellValueFactory(c -> javafx.beans.binding.Bindings.createStringBinding(() ->
                c.getValue().memberSignature() != null && !c.getValue().memberSignature().isBlank()
                        ? c.getValue().memberSignature() : "—"));
        memberCol.setPrefWidth(100);

        // 注释摘要列(截取前 40 字符)
        TableColumn<CommentData, String> summaryCol = new TableColumn<>(I18nUtil.getString("comment.column.summary"));
        summaryCol.setCellValueFactory(c -> javafx.beans.binding.Bindings.createStringBinding(() -> {
            String t = c.getValue().text();
            return t != null && t.length() > 40 ? t.substring(0, 40) + "…" : t != null ? t : "";
        }));

        // 时间/作者列
        TableColumn<CommentData, String> timeCol = new TableColumn<>(I18nUtil.getString("comment.column.time"));
        timeCol.setCellValueFactory(c -> javafx.beans.binding.Bindings.createStringBinding(() -> {
            String t = c.getValue().time();
            return (t != null && t.length() >= 10 ? t.substring(0, 10) : "") + " " + c.getValue().author();
        }));
        timeCol.setPrefWidth(140);

        table.getColumns().addAll(lineCol, memberCol, summaryCol, timeCol);

        // 行工厂：双击行跳转到对应行号
        table.setRowFactory(tv -> {
            TableRow<CommentData> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && onJumpToLine != null) {
                    onJumpToLine.accept(row.getItem().line());
                }
            });
            return row;
        });

        // 右键菜单：删除选中注释,后台线程执行后刷新列表
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

    /**
     * 加载指定工作区和类的注释列表
     *
     * @param wsHash  工作区哈希标识
     * @param clsName 类全限定名
     */
    public void load(String wsHash, String clsName) {
        this.workspaceHash = wsHash;
        this.className = clsName;
        refresh();
    }

    /**
     * 刷新当前注释列表,从 CommentManager 后台线程重新加载数据
     */
    public void refresh() {
        if (workspaceHash == null || className == null) {
            allRows.clear();
            return;
        }
        String ws = workspaceHash;
        String cn = className;
        BackgroundTasks.run("CommentLoad", () -> {
            List<CommentData> list = CommentManager.load(ws, cn);
            Platform.runLater(() -> {
                allRows.setAll(list);
                applyFilter();
            });
        });
    }

    /** 清空面板状态和所有数据行 */
    public void clear() {
        workspaceHash = null;
        className = null;
        allRows.clear();
        filterField.clear();
    }

    /** 设置双击行时的跳转回调 */
    public void setOnJumpToLine(Consumer<Integer> h) {
        this.onJumpToLine = h;
    }

    /** 设置删除注释后的回调 */
    public void setOnDelete(Runnable r) {
        this.onDelete = r;
    }

    /** 根据过滤器文本对注释行进行大小写不敏感的匹配过滤 */
    private void applyFilter() {
        String f = filterField.getText();
        if (f == null || f.isBlank()) {
            table.setItems(allRows);
            return;
        }
        String lo = f.toLowerCase();
        var filtered = allRows.filtered(c -> c.text().toLowerCase().contains(lo));
        table.setItems(filtered);
    }
}
