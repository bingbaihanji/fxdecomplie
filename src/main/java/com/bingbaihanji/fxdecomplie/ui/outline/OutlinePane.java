package com.bingbaihanji.fxdecomplie.ui.outline;

import com.bingbaihanji.fxdecomplie.model.MemberIndexEntry;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.WorkspaceIndexService;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 大纲面板,以树形结构显示当前类的字段、方法、内部类列表
 *
 * <p>当查看接口或抽象类时,方法节点会预置占位子节点以显示展开箭头,
 * 展开时自动异步查询并显示该方法的实现类/重写子类</p>
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class OutlinePane extends VBox {

    private static final Logger log = LoggerFactory.getLogger(OutlinePane.class);

    private final TextField filterField;
    private final TreeView<OutlineMember> treeView;
    private final TreeItem<OutlineMember> rootItem;
    private final Label statusLabel;
    private final AtomicLong updateGeneration = new AtomicLong();
    private final AtomicReference<Future<?>> currentTask = new AtomicReference<>();
    private final ObservableList<TreeItem<OutlineMember>> masterItems = FXCollections.observableArrayList();
    private JumpHandler jumpHandler;
    private NavigateHandler navigateHandler;
    private Workspace workspace;
    private WorkspaceIndex workspaceIndex;
    private String currentInternalName;
    private boolean currentIsInterface;
    private boolean currentIsAbstractClass;

    public OutlinePane() {
        setPadding(new Insets(4));
        setSpacing(4);
        setStyle("-fx-background-color: #252526;");

        Label title = new Label(I18nUtil.getString("outline.title"));
        title.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-padding: 2px 4px;");

        filterField = new TextField();
        filterField.setPromptText(I18nUtil.getString("outline.filter"));
        filterField.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc; -fx-font-size: 12px;");

        rootItem = new TreeItem<>(null);
        rootItem.setExpanded(true);

        treeView = new TreeView<>(rootItem);
        treeView.setShowRoot(false);
        treeView.setStyle("-fx-background-color: #252526;");
        VBox.setVgrow(treeView, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #808080; -fx-font-size: 11px; -fx-padding: 2px 4px;");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(OutlineMember item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    String icon = switch (item.type()) {
                        case FIELD -> "F ";
                        case METHOD -> "M ";
                        case INNER_CLASS -> "C ";
                        case IMPLEMENTATION -> "  ↓ ";
                    };
                    String displayText = item.type() == OutlineMember.MemberType.IMPLEMENTATION
                            ? icon + item.targetClassName() + "." + item.name()
                            + (item.methodDescriptor() != null ? item.methodDescriptor() : "")
                            : icon + item.name() + "  —  " + item.modifiers();
                    setText(displayText);
                    Color c = switch (item.type()) {
                        case METHOD -> Color.web("#dcdcaa");
                        case FIELD -> Color.web("#9cdcfe");
                        case INNER_CLASS -> Color.web("#4ec9b0");
                        case IMPLEMENTATION -> Color.web("#6a9955");
                    };
                    setTextFill(c);
                    setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas', monospace;");

                    if (item.type() == OutlineMember.MemberType.IMPLEMENTATION
                            && item.targetClassPath() != null) {
                        setTooltip(new javafx.scene.control.Tooltip(item.targetClassPath()));
                    }
                }
            }
        });

        // 点击处理
        treeView.setOnMouseClicked(e -> {
            TreeItem<OutlineMember> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected == null || selected.getValue() == null) {
                return;
            }
            OutlineMember member = selected.getValue();
            if (member.type() == OutlineMember.MemberType.IMPLEMENTATION) {
                if (navigateHandler != null && member.targetClassPath() != null) {
                    navigateHandler.navigate(member.targetClassPath(), member.name(),
                            member.targetClassName());
                }
            } else if (member.lineNumber() > 0 && jumpHandler != null) {
                jumpHandler.jump(member.lineNumber());
            }
        });

        filterField.textProperty().addListener((obs, old, text) -> {
            applyFilter(text == null ? "" : text);
        });

        getChildren().addAll(title, filterField, treeView, statusLabel);
    }

    // ── 公开 API ──

    private static boolean matchesFilter(OutlineMember member, String lowerFilter) {
        return member.name().toLowerCase().contains(lowerFilter)
                || member.modifiers().toLowerCase().contains(lowerFilter)
                || (member.targetClassName() != null
                && member.targetClassName().toLowerCase().contains(lowerFilter));
    }

    private static boolean hasMatchingChild(TreeItem<OutlineMember> item, String lowerFilter) {
        for (TreeItem<OutlineMember> child : item.getChildren()) {
            if (child.getValue() != null) {
                if (matchesFilter(child.getValue(), lowerFilter)) {
                    return true;
                }
                if (hasMatchingChild(child, lowerFilter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static TreeItem<OutlineMember> cloneForFilter(TreeItem<OutlineMember> source,
                                                          String lowerFilter, boolean selfMatch) {
        TreeItem<OutlineMember> clone = new TreeItem<>(source.getValue());
        if (selfMatch) {
            for (TreeItem<OutlineMember> child : source.getChildren()) {
                if (child.getValue() != null) {
                    clone.getChildren().add(new TreeItem<>(child.getValue()));
                }
            }
        } else {
            for (TreeItem<OutlineMember> child : source.getChildren()) {
                if (child.getValue() != null && matchesFilter(child.getValue(), lowerFilter)) {
                    clone.getChildren().add(new TreeItem<>(child.getValue()));
                }
            }
        }
        clone.setExpanded(true);
        return clone;
    }

    private static void expandAll(TreeItem<OutlineMember> parent) {
        if (!parent.isLeaf()) {
            parent.setExpanded(true);
            for (TreeItem<OutlineMember> child : parent.getChildren()) {
                expandAll(child);
            }
        }
    }

    public void setJumpHandler(JumpHandler handler) {
        this.jumpHandler = handler;
    }

    // ── 树构建 ──

    public void setNavigateHandler(NavigateHandler handler) {
        this.navigateHandler = handler;
    }

    // ── 懒加载实现类 ──

    /**
     * 设置工作区上下文
     * 必须在 {@link #update(String)} 之前调用,以确保树构建时能正确识别接口/抽象类方法
     *
     * @param workspace        当前工作区(用于延迟获取最新索引)
     * @param internalName     当前类的内部名称
     * @param isInterface      是否为接口
     * @param isAbstractClass  是否为抽象类
     */
    public void setWorkspaceContext(Workspace workspace, String internalName,
                                    boolean isInterface, boolean isAbstractClass) {
        this.workspace = workspace;
        this.workspaceIndex = workspace != null ? workspace.getIndex() : null;
        this.currentInternalName = internalName;
        this.currentIsInterface = isInterface;
        this.currentIsAbstractClass = isAbstractClass;
        updateIndexStatus();
    }

    /** 更新大纲内容(需先调用 setWorkspaceContext 设置上下文) */
    public void update(String sourceCode) {
        long generation = updateGeneration.incrementAndGet();
        BackgroundTasks.cancel(currentTask.getAndSet(null));
        String filterBefore = filterField.getText();
        Future<?> task = BackgroundTasks.run("OutlineParse", () -> {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            List<OutlineMember> members = OutlineParser.parse(sourceCode);
            List<OutlineMember> enriched = enrichMethodDescriptors(members);
            Platform.runLater(() -> {
                if (updateGeneration.get() != generation) {
                    return;
                }
                rebuildMaster(enriched);
                updateIndexStatus();
                if (filterBefore.equals(filterField.getText())) {
                    filterField.clear();
                }
            });
        });
        currentTask.set(task);
    }

    public void clear() {
        updateGeneration.incrementAndGet();
        BackgroundTasks.cancel(currentTask.getAndSet(null));
        masterItems.clear();
        rootItem.getChildren().clear();
        filterField.clear();
        statusLabel.setText("");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    // ── 过滤 ──

    private void rebuildMaster(List<OutlineMember> members) {
        masterItems.clear();
        boolean needsExpandArrow = currentIsInterface || currentIsAbstractClass;

        for (OutlineMember member : members) {
            TreeItem<OutlineMember> item = new TreeItem<>(member);

            if (needsExpandArrow && member.type() == OutlineMember.MemberType.METHOD) {
                // 预置占位子节点以显示展开箭头,访问时懒加载实现类
                TreeItem<OutlineMember> placeholder = new TreeItem<>(
                        new OutlineMember("⟳ " + I18nUtil.getString("outline.loading"),
                                OutlineMember.MemberType.IMPLEMENTATION, "", -1));
                item.getChildren().add(placeholder);

                // 监听展开事件,首次展开时触发懒加载
                item.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
                    if (isExpanded && !item.getChildren().isEmpty()
                            && item.getChildren().get(0).getValue() != null
                            && item.getChildren().get(0).getValue().name().startsWith("⟳")) {
                        loadImplementations(item);
                    }
                });
            }

            masterItems.add(item);
        }
        applyFilter(filterField.getText() == null ? "" : filterField.getText());
    }

    private void loadImplementations(TreeItem<OutlineMember> item) {
        OutlineMember member = item.getValue();
        if (member == null || currentInternalName == null) {
            return;
        }

        // 延迟获取最新的工作区索引(可能在第一次加载时还未构建完成)
        if (workspaceIndex == null || workspaceIndex == WorkspaceIndex.EMPTY) {
            if (workspace != null) {
                WorkspaceIndex latest = workspace.getIndex();
                if (latest != null && latest != WorkspaceIndex.EMPTY) {
                    this.workspaceIndex = latest;
                }
            }
        }

        if (workspaceIndex == null || workspaceIndex == WorkspaceIndex.EMPTY) {
            updateIndexStatus();
            item.getChildren().clear();
            item.getChildren().add(new TreeItem<>(
                    new OutlineMember(I18nUtil.getString("outline.indexing"),
                            OutlineMember.MemberType.IMPLEMENTATION, "", -1)));
            return;
        }

        String methodName = member.name();
        String descriptor = member.methodDescriptor();
        BackgroundTasks.run("OutlineImplLookup", () -> {
            List<OutlineService.ImplementationResult> results = OutlineService.findImplementations(
                    currentInternalName, methodName, descriptor, workspaceIndex);

            List<OutlineService.ImplementationResult> finalResults = results;
            Platform.runLater(() -> {
                item.getChildren().clear();
                if (finalResults.isEmpty()) {
                    item.getChildren().add(new TreeItem<>(
                            new OutlineMember(I18nUtil.getString("outline.noImplementations"),
                                    OutlineMember.MemberType.IMPLEMENTATION, "", -1)));
                } else {
                    for (OutlineService.ImplementationResult r : finalResults) {
                        OutlineMember implMember = OutlineMember.implementation(
                                r.methodName(), r.implementingClass(),
                                r.classPath(), r.descriptor());
                        item.getChildren().add(new TreeItem<>(implMember));
                    }
                }
                // 不做 setExpanded(true) 避免触发重复展开事件(用户已手动展开)
            });
        });
    }

    private List<OutlineMember> enrichMethodDescriptors(List<OutlineMember> members) {
        if (members.isEmpty() || workspaceIndex == null || workspaceIndex == WorkspaceIndex.EMPTY
                || currentInternalName == null || currentInternalName.isBlank()) {
            return members;
        }
        var classEntry = workspaceIndex.findClass(currentInternalName);
        if (classEntry == null || classEntry.methods().isEmpty()) {
            return members;
        }
        Map<String, List<MemberIndexEntry>> methodsByName = new HashMap<>();
        for (MemberIndexEntry method : classEntry.methods()) {
            if (method.name().startsWith("<")) {
                continue;
            }
            methodsByName.computeIfAbsent(method.name(), key -> new ArrayList<>()).add(method);
        }
        Map<String, Integer> usedByName = new HashMap<>();
        List<OutlineMember> result = new ArrayList<>(members.size());
        for (OutlineMember member : members) {
            if (member.type() != OutlineMember.MemberType.METHOD) {
                result.add(member);
                continue;
            }
            List<MemberIndexEntry> candidates = methodsByName.get(member.name());
            if (candidates == null || candidates.isEmpty()) {
                result.add(member);
                continue;
            }
            int offset = usedByName.merge(member.name(), 1, Integer::sum) - 1;
            MemberIndexEntry selected = candidates.get(Math.min(offset, candidates.size() - 1));
            result.add(member.withMethodDescriptor(selected.descriptor()));
        }
        return result;
    }

    private void updateIndexStatus() {
        boolean indexing = (currentIsInterface || currentIsAbstractClass)
                && workspace != null
                && !workspace.isIndexReady()
                && (workspace.isIndexBuildStarted() || WorkspaceIndexService.isIndexing(workspace));
        if (indexing) {
            statusLabel.setText(I18nUtil.getString("outline.indexing"));
            statusLabel.setVisible(true);
            statusLabel.setManaged(true);
        } else {
            statusLabel.setText("");
            statusLabel.setVisible(false);
            statusLabel.setManaged(false);
        }
    }

    private void applyFilter(String filter) {
        String lowerFilter = filter == null ? "" : filter.toLowerCase().strip();
        if (lowerFilter.isEmpty()) {
            rootItem.getChildren().setAll(masterItems);
        } else {
            List<TreeItem<OutlineMember>> filtered = new ArrayList<>();
            for (TreeItem<OutlineMember> item : masterItems) {
                if (item == null || item.getValue() == null) {
                    continue;
                }
                boolean selfMatch = matchesFilter(item.getValue(), lowerFilter);
                boolean childMatch = hasMatchingChild(item, lowerFilter);

                if (selfMatch || childMatch) {
                    TreeItem<OutlineMember> clone = cloneForFilter(item, lowerFilter, selfMatch);
                    filtered.add(clone);
                }
            }
            rootItem.getChildren().setAll(filtered);
            expandAll(rootItem);
        }
    }

    // ── 回调接口 ──

    @FunctionalInterface
    public interface JumpHandler {
        void jump(int lineNumber);
    }

    @FunctionalInterface
    public interface NavigateHandler {
        void navigate(String classPath, String methodName, String className);
    }
}
