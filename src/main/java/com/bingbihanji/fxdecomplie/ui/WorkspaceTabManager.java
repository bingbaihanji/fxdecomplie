package com.bingbihanji.fxdecomplie.ui;

import com.bingbihanji.fxdecomplie.MainWindow;

import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import com.bingbihanji.fxdecomplie.utils.I18nUtil;
import com.bingbihanji.fxdecomplie.model.Workspace;
import com.bingbihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbihanji.fxdecomplie.ui.code.StatusBar;
import com.bingbihanji.fxdecomplie.ui.inheritance.InheritancePane;
import com.bingbihanji.fxdecomplie.ui.outline.OutlinePane;
import com.bingbihanji.fxdecomplie.ui.tree.FileTreeView;
import javafx.scene.Cursor;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.geometry.Orientation;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 管理外层 TabPane 中工作区标签页的创建和销毁。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class WorkspaceTabManager {

    /** 外层标签页 */
    private final TabPane outerTabPane;
    /** 状态栏 */
    private final StatusBar statusBar;
    /** 标签页 → 工作区视图映射 */
    private final Map<Tab, MainWindow.WorkspaceView> workspaceViews = new HashMap<>();
    /** 工作区标签页 → 底部工具窗口状态 */
    private final Map<Tab, WorkspaceTools> workspaceTools = new HashMap<>();
    /** 编辑区上下分割条的实际鼠标命中高度 */
    private static final double EDITOR_DIVIDER_HIT_HEIGHT = 12.0;
    /** 编辑区最小保留高度，避免底部工具窗口拖得过高 */
    private static final double MIN_EDITOR_HEIGHT = 160.0;
    /** 底部工具窗口最小保留高度 */
    private static final double MIN_TOOL_WINDOW_HEIGHT = 120.0;

    public WorkspaceTabManager(TabPane outerTabPane, StatusBar statusBar) {
        this.outerTabPane = outerTabPane;
        this.statusBar = statusBar;
    }

    /** 获取所有工作区视图 */
    public Map<Tab, MainWindow.WorkspaceView> getWorkspaceViews() {
        return workspaceViews;
    }

    /**
     * 添加工作区标签页。
     *
     * @param workspace       工作区
     * @param onClassClick    点击类节点时的回调
     * @param onTextFileClick 点击文本文件节点时的回调
     */
    public void addWorkspaceTab(Workspace workspace,
            BiConsumer<FileTreeNode, TabPane> onClassClick,
            BiConsumer<FileTreeNode, TabPane> onTextFileClick) {
        removeWelcomeTab();

        FileTreeView treeView = new FileTreeView(workspace.getTreeRoot());
        treeView.setPrefWidth(280);

        TabPane codeTabPane = new TabPane();
        codeTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        codeTabPane.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTab, newTab) -> updateStatusForCodeTab(newTab)
        );

        // 右键菜单：关闭其他 / 关闭全部
        codeTabPane.setOnContextMenuRequested(event -> {
            ContextMenu menu = new ContextMenu();
            MenuItem closeOthers = new MenuItem(I18nUtil.getString("context.closeOthers"));
            closeOthers.setOnAction(e -> {
                Tab current = codeTabPane.getSelectionModel().getSelectedItem();
                if (current != null) {
                    codeTabPane.getTabs().removeIf(t -> t != current);
                }
            });
            MenuItem closeAll = new MenuItem(I18nUtil.getString("context.closeAll"));
            closeAll.setOnAction(e -> codeTabPane.getTabs().clear());
            menu.getItems().addAll(closeOthers, closeAll);
            menu.show(codeTabPane, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        treeView.setOnMouseClicked(e -> {
            TreeItem<FileTreeNode> item = treeView.getSelectionModel().getSelectedItem();
            if (item == null) return;
            FileTreeNode node = item.getValue();
            if (node != null) {
                if (node.isClassFile()) {
                    onClassClick.accept(node, codeTabPane);
                } else if (node.isTextFile()) {
                    onTextFileClick.accept(node, codeTabPane);
                }
            }
        });

        OutlinePane outlinePane = new OutlinePane();
        InheritancePane inheritancePane = new InheritancePane();

        javafx.scene.control.Tab outlineTab = new javafx.scene.control.Tab(I18nUtil.getString("tab.outline"), outlinePane);
        outlineTab.setClosable(true);
        javafx.scene.control.Tab inheritTab = new javafx.scene.control.Tab(I18nUtil.getString("tab.inheritance"), inheritancePane);
        inheritTab.setClosable(true);

        TabPane sideTabPane = new TabPane();
        sideTabPane.getStyleClass().add("bottom-tool-window");
        sideTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        sideTabPane.getTabs().addAll(outlineTab, inheritTab);
        sideTabPane.setMinHeight(0);
        sideTabPane.setPrefHeight(228);
        sideTabPane.setMaxHeight(Double.MAX_VALUE);

        SplitPane editorSplitPane = new SplitPane();
        editorSplitPane.getStyleClass().add("editor-split-pane");
        editorSplitPane.setOrientation(Orientation.VERTICAL);
        Region resizeHandle = createBottomToolResizeHandle(editorSplitPane);
        VBox bottomToolContainer = new VBox(resizeHandle, sideTabPane);
        bottomToolContainer.getStyleClass().add("bottom-tool-container");
        bottomToolContainer.setMinHeight(MIN_TOOL_WINDOW_HEIGHT);
        bottomToolContainer.setPrefHeight(240);
        bottomToolContainer.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(sideTabPane, Priority.ALWAYS);
        editorSplitPane.getItems().addAll(codeTabPane, bottomToolContainer);
        editorSplitPane.setDividerPositions(0.72);

        SplitPane splitPane = new SplitPane(treeView, editorSplitPane);
        splitPane.setDividerPositions(0.25);

        // 代码标签页切换时更新大纲和继承面板
        codeTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab instanceof CodeEditorTab codeTab) {
                outlinePane.update(codeTab.getOpenFile().getSourceCode());
                inheritancePane.load(codeTab.getOpenFile().getFullPath());
            } else {
                outlinePane.clear();
                inheritancePane.clear();
            }
        });

        // 绑定大纲点击跳转
        outlinePane.setJumpHandler(line -> {
            CodeEditorTab codeTab = getCurrentCodeTab(codeTabPane);
            if (codeTab != null) {
                var area = codeTab.getCodeArea();
                area.moveDocumentStart();
                for (int i = 1; i < line; i++) area.moveParagraphDown();
                area.requestFocus();
            }
        });

        // 绑定继承树双击打开
        inheritancePane.setOpenHandler(className -> {
            FileTreeNode node = findClassNode(workspace.getTreeRoot(), className);
            if (node != null) {
                onClassClick.accept(node, codeTabPane);
            }
        });

        Tab tab = new Tab(workspace.getName(), splitPane);
        WorkspaceTools tools = new WorkspaceTools(
                editorSplitPane, bottomToolContainer, sideTabPane, outlineTab, inheritTab);
        outlineTab.setOnClosed(e -> hideToolWindowIfEmpty(tab));
        inheritTab.setOnClosed(e -> hideToolWindowIfEmpty(tab));
        MainWindow.WorkspaceView view = new MainWindow.WorkspaceView(workspace, treeView, codeTabPane, tab);
        workspaceViews.put(tab, view);
        workspaceTools.put(tab, tools);
        tab.setOnClosed(event -> cleanupClosedWorkspace(tab));

        outerTabPane.getTabs().add(tab);
        outerTabPane.getSelectionModel().select(tab);
        statusBar.setFilePath(workspace.getSourceFile().getAbsolutePath());
    }

    /** 关闭指定标签页 */
    public void closeWorkspaceTab(Tab tab) {
        cleanupClosedWorkspace(tab);
        outerTabPane.getTabs().remove(tab);
    }

    /** 关闭除 keepTab 外的所有标签页 */
    public void closeOtherWorkspaces(Tab keepTab) {
        outerTabPane.getTabs().stream()
                .filter(tab -> tab != keepTab)
                .toList()
                .forEach(this::closeWorkspaceTab);
    }

    /** 获取当前选中的工作区视图 */
    public MainWindow.WorkspaceView currentWorkspaceView() {
        Tab selected = outerTabPane.getSelectionModel().getSelectedItem();
        return selected == null ? null : workspaceViews.get(selected);
    }

    /** 获取当前选中的代码标签页 */
    public CodeEditorTab currentCodeTab() {
        MainWindow.WorkspaceView view = currentWorkspaceView();
        if (view == null) return null;
        Tab selected = view.codeTabPane().getSelectionModel().getSelectedItem();
        return selected instanceof CodeEditorTab codeTab ? codeTab : null;
    }

    /** 更新状态栏以反映当前工作区 */
    public void updateStatusForWorkspace(Tab tab) {
        MainWindow.WorkspaceView view = tab == null ? null : workspaceViews.get(tab);
        if (view == null) {
            statusBar.clear();
            return;
        }
        var codeSelected = view.codeTabPane().getSelectionModel().getSelectedItem();
        if (codeSelected instanceof CodeEditorTab codeTab) {
            statusBar.setFilePath(formatClassPath(codeTab.getOpenFile().getFullPath()));
            statusBar.setEncoding("UTF-8");
            var caret = codeTab.getCodeArea().getCaretPosition();
            statusBar.setCursorPosition(caret.index() + 1, caret.charIndex() + 1);
        }
    }

    public void showWelcomeTabIfEmpty() {
        if (outerTabPane.getTabs().isEmpty()) {
            outerTabPane.getTabs().add(createWelcomeTab());
        }
    }

    /** 折叠当前工作区文件树 */
    public void collapseTreeInCurrentWorkspace() {
        MainWindow.WorkspaceView view = currentWorkspaceView();
        if (view != null) {
            collapse(view.workspace().getTreeRoot());
            view.workspace().getTreeRoot().setExpanded(true);
        }
    }

    /** 显示当前工作区的大纲工具窗口 */
    public void showOutlineToolWindow() {
        showToolWindow(ToolTab.OUTLINE);
    }

    /** 显示当前工作区的继承工具窗口 */
    public void showInheritanceToolWindow() {
        showToolWindow(ToolTab.INHERITANCE);
    }

    /** 隐藏当前工作区底部工具窗口，但保留其中的标签页状态 */
    public void hideBottomToolWindow() {
        Tab tab = outerTabPane.getSelectionModel().getSelectedItem();
        WorkspaceTools tools = tab == null ? null : workspaceTools.get(tab);
        if (tools != null) {
            hideToolWindow(tools);
        }
    }

    /** 清理已关闭的工作区 */
    private void cleanupClosedWorkspace(Tab tab) {
        workspaceViews.remove(tab);
        workspaceTools.remove(tab);

        if (workspaceViews.isEmpty()) {
            statusBar.clear();
            if (outerTabPane.getTabs().stream().noneMatch(existing -> I18nUtil.getString("tab.welcome").equals(existing.getText()))) {
                outerTabPane.getTabs().add(createWelcomeTab());
            }
        }

    }

    /** 显示并选中指定工具窗口标签页 */
    private void showToolWindow(ToolTab toolTab) {
        Tab workspaceTab = outerTabPane.getSelectionModel().getSelectedItem();
        WorkspaceTools tools = workspaceTab == null ? null : workspaceTools.get(workspaceTab);
        if (tools == null) {
            return;
        }

        Tab tab = toolTab == ToolTab.OUTLINE ? tools.outlineTab : tools.inheritanceTab;
        if (!tools.toolTabPane.getTabs().contains(tab)) {
            tools.toolTabPane.getTabs().add(tab);
        }
        showToolWindow(tools);
        tools.toolTabPane.getSelectionModel().select(tab);
    }

    /** 将底部工具窗口重新放回纵向 SplitPane */
    private void showToolWindow(WorkspaceTools tools) {
        if (!tools.editorSplitPane.getItems().contains(tools.toolContainer)) {
            tools.editorSplitPane.getItems().add(tools.toolContainer);
            tools.editorSplitPane.setDividerPositions(tools.dividerPosition);
        }
    }

    /** 如果所有工具标签页都关闭，则隐藏底部工具窗口 */
    private void hideToolWindowIfEmpty(Tab workspaceTab) {
        WorkspaceTools tools = workspaceTools.get(workspaceTab);
        if (tools != null && tools.toolTabPane.getTabs().isEmpty()) {
            hideToolWindow(tools);
        }
    }

    /** 从纵向 SplitPane 中移除底部工具窗口，并记住当前高度比例 */
    private void hideToolWindow(WorkspaceTools tools) {
        if (!tools.editorSplitPane.getItems().contains(tools.toolContainer)) {
            return;
        }
        double[] positions = tools.editorSplitPane.getDividerPositions();
        if (positions.length > 0) {
            tools.dividerPosition = positions[0];
        }
        tools.editorSplitPane.getItems().remove(tools.toolContainer);
    }

    private void updateStatusForCodeTab(Tab tab) {
        if (tab instanceof CodeEditorTab codeTab) {
            statusBar.setFilePath(formatClassPath(codeTab.getOpenFile().getFullPath()));
            statusBar.setEncoding("UTF-8");
            var caret = codeTab.getCodeArea().getCaretPosition();
            statusBar.setCursorPosition(caret.index() + 1, caret.charIndex() + 1);
        }
    }

    /** 创建底部工具窗口顶部的独立拖拽手柄。 */
    private static Region createBottomToolResizeHandle(SplitPane splitPane) {
        Region handle = new Region();
        handle.getStyleClass().add("bottom-tool-resize-handle");
        handle.setMinHeight(EDITOR_DIVIDER_HIT_HEIGHT);
        handle.setPrefHeight(EDITOR_DIVIDER_HIT_HEIGHT);
        handle.setMaxHeight(EDITOR_DIVIDER_HIT_HEIGHT);
        handle.setPickOnBounds(true);
        handle.setCursor(Cursor.V_RESIZE);
        installBottomToolResizeDrag(splitPane, handle);
        return handle;
    }

    /** 通过拖拽手柄主动调整纵向 SplitPane 的分割比例。 */
    private static void installBottomToolResizeDrag(SplitPane splitPane, Region handle) {
        double[] dragStartY = new double[1];
        double[] dragStartDivider = new double[] { 0.72 };

        handle.setOnMousePressed(event -> {
            double[] positions = splitPane.getDividerPositions();
            if (positions.length > 0) {
                dragStartDivider[0] = positions[0];
            }
            dragStartY[0] = event.getScreenY();
            event.consume();
        });

        handle.setOnMouseDragged(event -> {
            double height = splitPane.getHeight();
            if (height <= 0) {
                return;
            }

            double delta = (event.getScreenY() - dragStartY[0]) / height;
            double minPosition = Math.min(0.8, MIN_EDITOR_HEIGHT / height);
            double maxPosition = Math.max(minPosition, 1.0 - MIN_TOOL_WINDOW_HEIGHT / height);
            splitPane.setDividerPositions(clamp(dragStartDivider[0] + delta, minPosition, maxPosition));
            event.consume();
        });
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 获取当前代码标签页 */
    private CodeEditorTab getCurrentCodeTab(TabPane codeTabPane) {
        javafx.scene.control.Tab selected = codeTabPane.getSelectionModel().getSelectedItem();
        return selected instanceof CodeEditorTab codeTab ? codeTab : null;
    }

    /** 在文件树中查找指定类名的节点 */
    private FileTreeNode findClassNode(TreeItem<FileTreeNode> item, String className) {
        FileTreeNode data = item.getValue();
        String searchName = className.replace(".class", "").replace("\\", "/");
        if (data != null && data.isClassFile()
                && data.getFullPath().replace(".class", "").equals(searchName)) {
            return data;
        }
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            FileTreeNode found = findClassNode(child, className);
            if (found != null) return found;
        }
        return null;
    }

    /** 移除欢迎标签页 */
    private void removeWelcomeTab() {
        outerTabPane.getTabs().removeIf(tab -> !workspaceViews.containsKey(tab));
    }

    /** 创建欢迎标签页 */
    private Tab createWelcomeTab() {
        Label placeholder = new Label(I18nUtil.getString("welcome.text"));
        placeholder.getStyleClass().add("empty-state-label");
        StackPane content = new StackPane(placeholder);
        content.getStyleClass().add("empty-state");
        StackPane.setAlignment(placeholder, Pos.CENTER);
        Tab tab = new Tab(I18nUtil.getString("tab.welcome"), content);
        tab.setClosable(false);
        return tab;
    }

    /** 递归折叠树节点 */
    private void collapse(TreeItem<FileTreeNode> item) {
        item.setExpanded(false);
        item.getChildren().forEach(this::collapse);
    }

    /** 格式化类路径显示（/ → &gt;，去 .class 后缀） */
    public static String formatClassPath(String fullPath) {
        String display = fullPath.replace('/', '>').replace('\\', '>');
        return display.endsWith(".class")
                ? display.substring(0, display.length() - 6)
                : display;
    }

    private enum ToolTab {
        OUTLINE,
        INHERITANCE
    }

    private static final class WorkspaceTools {
        private final SplitPane editorSplitPane;
        private final Node toolContainer;
        private final TabPane toolTabPane;
        private final Tab outlineTab;
        private final Tab inheritanceTab;
        private double dividerPosition = 0.72;

        private WorkspaceTools(SplitPane editorSplitPane, Node toolContainer, TabPane toolTabPane,
                               Tab outlineTab, Tab inheritanceTab) {
            this.editorSplitPane = editorSplitPane;
            this.toolContainer = toolContainer;
            this.toolTabPane = toolTabPane;
            this.outlineTab = outlineTab;
            this.inheritanceTab = inheritanceTab;
        }
    }
}
