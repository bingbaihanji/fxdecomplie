package com.bingbaihanji.fxdecomplie.ui.inheritance;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceGroup;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceNode;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceTree;
import com.bingbaihanji.fxdecomplie.model.reference.Kind;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.reference.InheritanceReferenceService;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 继承引用面板,用于展示当前类相关的父类、接口、注解、实现类、子类等可跳转引用
 *
 * @author bingbaihanji
 * @date 2026-07-20
 */
public final class InheritanceReferencePane extends VBox {

    private final TreeView<InheritanceReferenceNode> treeView;
    private final Label statusLabel;
    private final AtomicLong loadGeneration = new AtomicLong();
    private OpenHandler openHandler;
    private Runnable refreshAction;

    public InheritanceReferencePane() {
        setPadding(new Insets(4));
        setSpacing(4);
        setStyle("-fx-background-color: #252526;");

        HBox titleBar = new HBox(8);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(I18nUtil.getString("inheritance.title"));
        title.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-padding: 2px 4px;");

        Button refreshButton = new Button("↻");
        refreshButton.setTooltip(new Tooltip(
                I18nUtil.getString("inheritance.refresh.tooltip")));
        refreshButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc;");
        refreshButton.setOnAction(e -> {
            if (refreshAction != null) {
                refreshAction.run();
            }
        });

        HBox.setHgrow(title, Priority.ALWAYS);
        titleBar.getChildren().addAll(title, refreshButton);

        treeView = new TreeView<>();
        treeView.setStyle("-fx-background-color: #252526;");
        VBox.setVgrow(treeView, Priority.ALWAYS);

        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(InheritanceReferenceNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String prefix = switch (item.kind()) {
                    case SELF -> "";
                    case SUPER_CLASS -> "S ";
                    case INTERFACE -> "I ";
                    case ANNOTATION -> "T ";
                    case IMPLEMENTATION, SUBCLASS -> "↓ ";
                    case UNRESOLVED -> "";
                };
                setText(prefix + item.displayName());
                Color c = switch (item.kind()) {
                    case SELF -> Color.web("#dcdcaa");
                    case SUPER_CLASS -> Color.web("#c586c0");
                    case INTERFACE -> Color.web("#4ec9b0");
                    case ANNOTATION -> Color.web("#4fc1ff");
                    case IMPLEMENTATION, SUBCLASS -> Color.web("#9cdcfe");
                    case UNRESOLVED -> Color.web("#808080");
                };
                setTextFill(c);
                setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas', monospace;");
            }
        });

        treeView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && openHandler != null) {
                TreeItem<InheritanceReferenceNode> item = treeView.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue() != null
                        && item.getValue().kind() != Kind.SELF
                        && item.getValue().navigable()) {
                    openHandler.open(item.getValue());
                }
            }
        });

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #808080; -fx-font-size: 11px; -fx-padding: 2px 4px;");

        getChildren().addAll(titleBar, treeView, statusLabel);
    }

    /**
     * 加载指定类的继承引用树
     *
     * @param workspace  当前工作区
     * @param fullPath   目标类完整路径
     * @param classBytes 当前类字节码,可为空
     */
    public void load(Workspace workspace, String fullPath, byte[] classBytes) {
        showStatus(I18nUtil.getString("inheritance.status.building"));
        long gen = loadGeneration.incrementAndGet();
        BackgroundTasks.run("RefViewBuild", () -> {
            InheritanceReferenceTree tree = InheritanceReferenceService.buildTree(
                    workspace, fullPath, classBytes);
            Platform.runLater(() -> {
                if (loadGeneration.get() != gen) {
                    return;
                }
                render(tree);
            });
        });
    }

    private void render(InheritanceReferenceTree tree) {
        TreeItem<InheritanceReferenceNode> root = new TreeItem<>(tree.root());
        root.setExpanded(true);
        for (InheritanceReferenceGroup group : tree.groups()) {
            TreeItem<InheritanceReferenceNode> groupItem = new TreeItem<>(
                    new InheritanceReferenceNode("", group.title(), group.kind(), "",
                            tree.root().depth() + 1, false));
            for (InheritanceReferenceNode child : group.children()) {
                groupItem.getChildren().add(new TreeItem<>(child));
            }
            groupItem.setExpanded(!group.collapsible());
            root.getChildren().add(groupItem);
        }
        treeView.setRoot(root);
        showStatus(tree.statusMessage());
    }

    public void clear() {
        treeView.setRoot(null);
        statusLabel.setText("");
    }

    public void showStatus(String message) {
        statusLabel.setText(message == null ? "" : message);
    }

    public void setOpenHandler(OpenHandler handler) {
        this.openHandler = handler;
    }

    public void setRefreshAction(Runnable action) {
        this.refreshAction = action;
    }

    @FunctionalInterface
    public interface OpenHandler {
        void open(InheritanceReferenceNode node);
    }
}
