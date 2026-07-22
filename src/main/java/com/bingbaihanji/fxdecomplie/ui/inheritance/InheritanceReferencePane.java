package com.bingbaihanji.fxdecomplie.ui.inheritance;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceGroup;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceNode;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceTree;
import com.bingbaihanji.fxdecomplie.model.reference.Kind;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.WorkspaceIndexService;
import com.bingbaihanji.fxdecomplie.service.reference.InheritanceReferenceService;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 继承引用面板,用于展示当前类相关的父类、接口、注解、实现类、子类
 * 以及方法级别的重写/被重写/继承关系
 *
 * <p>支持搜索过滤和方法签名 tooltip</p>
 *
 * @author bingbaihanji
 * @date 2026-07-20
 */
public final class InheritanceReferencePane extends VBox {

    private final TreeView<InheritanceReferenceNode> treeView;
    private final TextField filterField;
    private final Label statusLabel;
    private final AtomicLong loadGeneration = new AtomicLong();
    private final AtomicReference<Future<?>> currentTask = new AtomicReference<>();
    private OpenHandler openHandler;
    private Runnable refreshAction;
    /** 缓存的当前树(未过滤),用于搜索重建 */
    private TreeItem<InheritanceReferenceNode> cachedRoot;

    public InheritanceReferencePane() {
        setPadding(new Insets(4));
        setSpacing(4);
        setStyle("-fx-background-color: #252526;");

        // 标题栏
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

        // 搜索过滤框
        filterField = new TextField();
        filterField.setPromptText(I18nUtil.getString("outline.filter"));
        filterField.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc; -fx-font-size: 12px;");
        filterField.textProperty().addListener((obs, old, text) -> {
            applyFilter(text == null ? "" : text);
        });

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
                    setTooltip(null);
                    return;
                }
                String prefix = switch (item.kind()) {
                    case SELF -> "";
                    case SUPER_CLASS -> "S ";
                    case INTERFACE -> "I ";
                    case ANNOTATION -> "T ";
                    case IMPLEMENTATION, SUBCLASS -> "↓ ";
                    case OVERRIDES -> "↻ ";
                    case OVERRIDDEN_BY -> "↺ ";
                    case INHERITED -> "· ";
                    case UNRESOLVED -> "";
                };
                setText(prefix + item.displayName());
                Color c = switch (item.kind()) {
                    case SELF -> Color.web("#dcdcaa");
                    case SUPER_CLASS -> Color.web("#c586c0");
                    case INTERFACE -> Color.web("#4ec9b0");
                    case ANNOTATION -> Color.web("#4fc1ff");
                    case IMPLEMENTATION, SUBCLASS -> Color.web("#9cdcfe");
                    case OVERRIDES -> Color.web("#dcdcaa");
                    case OVERRIDDEN_BY -> Color.web("#ce9178");
                    case INHERITED -> Color.web("#6a9955");
                    case UNRESOLVED -> Color.web("#808080");
                };
                setTextFill(c);
                setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas', monospace;");

                // Tooltip: 完整路径或方法签名
                if (item.descriptor() != null && !item.descriptor().isBlank()) {
                    String tip = item.displayName();
                    if (item.ownerClassName() != null) {
                        tip = item.ownerClassName() + "." + item.displayName();
                    }
                    setTooltip(new Tooltip(tip));
                } else if (item.navigable() && item.fullPath() != null
                        && !item.fullPath().isBlank()) {
                    setTooltip(new Tooltip(item.fullPath()));
                }
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

        getChildren().addAll(titleBar, filterField, treeView, statusLabel);
    }

    /**
     * 加载指定类的继承引用树
     *
     * @param workspace  当前工作区
     * @param fullPath   目标类完整路径
     * @param classBytes 当前类字节码,可为空
     */
    public void load(Workspace workspace, String fullPath, byte[] classBytes) {
        if (workspace != null && !workspace.isIndexReady()
                && (workspace.isIndexBuildStarted() || WorkspaceIndexService.isIndexing(workspace))) {
            showStatus(I18nUtil.getString("inheritance.indexing"));
        } else {
            showStatus(I18nUtil.getString("inheritance.status.building"));
        }
        long gen = loadGeneration.incrementAndGet();
        BackgroundTasks.cancel(currentTask.getAndSet(null));
        Future<?> task = BackgroundTasks.run("RefViewBuild", () -> {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            InheritanceReferenceTree tree = InheritanceReferenceService.buildTree(
                    workspace, fullPath, classBytes);
            Platform.runLater(() -> {
                if (loadGeneration.get() != gen) {
                    return;
                }
                render(tree);
            });
        });
        currentTask.set(task);
    }

    private void render(InheritanceReferenceTree tree) {
        TreeItem<InheritanceReferenceNode> root = new TreeItem<>(tree.root());
        root.setExpanded(true);
        cachedRoot = root;

        for (InheritanceReferenceGroup group : tree.groups()) {
            TreeItem<InheritanceReferenceNode> groupItem = new TreeItem<>(
                    new InheritanceReferenceNode("", group.title(), group.kind(), "",
                            tree.root().depth() + 1, false));
            for (InheritanceReferenceNode child : group.children()) {
                groupItem.getChildren().add(new TreeItem<>(child));
            }
            // 默认展开策略: SELF/SUPER_CLASS/INTERFACE/ANNOTATION → 展开; 其他 → 折叠
            boolean expanded = switch (group.kind()) {
                case SUPER_CLASS, INTERFACE, ANNOTATION -> true;
                case OVERRIDES -> group.children().size() <= 10;
                default -> false;
            };
            groupItem.setExpanded(expanded);
            root.getChildren().add(groupItem);
        }
        treeView.setRoot(root);
        showStatus(tree.statusMessage());
        filterField.clear();
    }

    public void clear() {
        loadGeneration.incrementAndGet();
        BackgroundTasks.cancel(currentTask.getAndSet(null));
        treeView.setRoot(null);
        cachedRoot = null;
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

    // ── 过滤逻辑 ──

    /**
     * 应用搜索过滤:从缓存的完整树中筛选匹配项并重建显示
     */
    private void applyFilter(String filter) {
        if (cachedRoot == null) {
            return;
        }
        String lowerFilter = filter == null ? "" : filter.toLowerCase().strip();
        if (lowerFilter.isEmpty()) {
            treeView.setRoot(cachedRoot);
            return;
        }

        // 克隆过滤:仅保留匹配的节点及其祖先
        TreeItem<InheritanceReferenceNode> filteredRoot = new TreeItem<>(cachedRoot.getValue());
        filteredRoot.setExpanded(true);

        for (TreeItem<InheritanceReferenceNode> groupItem : cachedRoot.getChildren()) {
            TreeItem<InheritanceReferenceNode> filteredGroup = filterGroup(groupItem, lowerFilter);
            if (filteredGroup != null) {
                filteredGroup.setExpanded(true);
                filteredRoot.getChildren().add(filteredGroup);
            }
        }

        treeView.setRoot(filteredRoot);
    }

    /** 过滤分组节点:检查分组标题和子节点 */
    private TreeItem<InheritanceReferenceNode> filterGroup(
            TreeItem<InheritanceReferenceNode> groupItem, String lowerFilter) {
        InheritanceReferenceNode groupValue = groupItem.getValue();
        boolean groupMatches = groupValue != null
                && groupValue.displayName().toLowerCase().contains(lowerFilter);

        List<TreeItem<InheritanceReferenceNode>> matchingChildren = new ArrayList<>();
        for (TreeItem<InheritanceReferenceNode> child : groupItem.getChildren()) {
            if (child.getValue() != null
                    && child.getValue().displayName().toLowerCase().contains(lowerFilter)) {
                matchingChildren.add(new TreeItem<>(child.getValue()));
            }
        }

        if (groupMatches || !matchingChildren.isEmpty()) {
            TreeItem<InheritanceReferenceNode> result = new TreeItem<>(groupValue);
            if (groupMatches) {
                // 分组标题匹配 → 包含所有子节点
                for (TreeItem<InheritanceReferenceNode> child : groupItem.getChildren()) {
                    if (child.getValue() != null) {
                        result.getChildren().add(new TreeItem<>(child.getValue()));
                    }
                }
            } else {
                // 仅子节点匹配 → 只显示匹配的子节点
                result.getChildren().setAll(matchingChildren);
            }
            return result;
        }

        return null;
    }

    @FunctionalInterface
    public interface OpenHandler {
        void open(InheritanceReferenceNode node);
    }
}
