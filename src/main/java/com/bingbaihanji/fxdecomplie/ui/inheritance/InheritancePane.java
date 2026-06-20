package com.bingbaihanji.fxdecomplie.ui.inheritance;

import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * 类继承层次面板。显示当前类的父类链和已知子类。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class InheritancePane extends VBox {

    private final TreeView<InheritanceNode> treeView;
    private OpenHandler openHandler;

    public InheritancePane() {
        setPadding(new Insets(4));
        setSpacing(4);
        setStyle("-fx-background-color: #252526;");

        Label title = new Label(I18nUtil.getString("inheritance.title"));
        title.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-padding: 2px 4px;");

        treeView = new TreeView<>();
        treeView.setStyle("-fx-background-color: #252526;");
        VBox.setVgrow(treeView, Priority.ALWAYS);

        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(InheritanceNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String prefix = switch (item.relation()) {
                        case SELF -> "";
                        case SUPER_CLASS -> "↑ ";
                        case SUBCLASS -> "↓ ";
                        case INTERFACE -> "I ";
                    };
                    setText(prefix + item.displayName());
                    Color c = item.relation() == InheritanceNode.RelationType.SELF ? Color.web("#dcdcaa")
                            : item.relation() == InheritanceNode.RelationType.SUPER_CLASS ? Color.web("#c586c0")
                              : Color.web("#9cdcfe");
                    setTextFill(c);
                    setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas', monospace;");
                }
            }
        });

        treeView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && openHandler != null) {
                TreeItem<InheritanceNode> item = treeView.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue() != null
                        && item.getValue().relation() != InheritanceNode.RelationType.SELF) {
                    openHandler.open(item.getValue().className());
                }
            }
        });

        getChildren().addAll(title, treeView);
    }

    public void load(String fullPath, WorkspaceIndex index) {
        TreeItem<InheritanceNode> root = InheritanceService.buildTree(fullPath, index);
        if (root != null) {
            treeView.setRoot(root);
            root.setExpanded(true);
        } else {
            showUnavailable();
        }
    }

    public void showIndexing() {
        treeView.setRoot(new TreeItem<>(new InheritanceNode("",
                I18nUtil.getString("inheritance.indexing"),
                InheritanceNode.RelationType.SELF, 0)));
    }

    public void showUnavailable() {
        treeView.setRoot(new TreeItem<>(new InheritanceNode("",
                I18nUtil.getString("inheritance.unavailable"),
                InheritanceNode.RelationType.SELF, 0)));
    }

    public void showIndexPending() {
        treeView.setRoot(new TreeItem<>(new InheritanceNode("",
                I18nUtil.getString("inheritance.indexPending"),
                InheritanceNode.RelationType.SELF, 0)));
    }

    public void clear() {
        treeView.setRoot(null);
    }

    public void setOpenHandler(OpenHandler handler) {
        this.openHandler = handler;
    }

    @FunctionalInterface
    public interface OpenHandler {
        void open(String className);
    }
}
