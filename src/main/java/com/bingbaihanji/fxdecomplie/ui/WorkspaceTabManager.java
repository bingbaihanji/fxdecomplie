package com.bingbaihanji.fxdecomplie.ui;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.service.NavigationService;
import com.bingbaihanji.fxdecomplie.service.WorkspaceIndexService;
import com.bingbaihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbaihanji.fxdecomplie.ui.code.SplitEditorPane;
import com.bingbaihanji.fxdecomplie.ui.code.StatusBar;
import com.bingbaihanji.fxdecomplie.ui.comment.CommentListPane;
import com.bingbaihanji.fxdecomplie.ui.inheritance.InheritancePane;
import com.bingbaihanji.fxdecomplie.ui.outline.OutlinePane;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.fxdecomplie.ui.tree.FileTreeView;
import com.bingbaihanji.util.I18nUtil;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 管理外层 TabPane 中工作区标签页的创建和销毁
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class WorkspaceTabManager {

    /** 编辑区上下分割条的实际鼠标命中高度 */
    private static final double EDITOR_DIVIDER_HIT_HEIGHT = 12.0;
    /** 编辑区最小保留高度,避免底部工具窗口拖得过高 */
    private static final double MIN_EDITOR_HEIGHT = 160.0;
    /** 底部工具窗口最小保留高度 */
    private static final double MIN_TOOL_WINDOW_HEIGHT = 120.0;
    /** 外层标签页 */
    private final TabPane outerTabPane;
    /** 状态栏 */
    private final StatusBar statusBar;
    /** 标签页 → 工作区视图映射 */
    private final Map<Tab, WorkspaceView> workspaceViews = new HashMap<>();
    /** 工作区标签页 → 底部工具窗口状态 */
    private final Map<Tab, WorkspaceTools> workspaceTools = new HashMap<>();
    /** 欢迎页动作 */
    private WelcomeActions welcomeActions = WelcomeActions.empty();
    /** 当前引擎显示名 */
    private String currentEngineName = "";
    /** 拖放配置(用于主窗口安装代码标签拖拽处理器) */
    private AppConfig dragDropConfig;
    private VsCodeThemeLoader.ThemeData dragDropTheme;
    /** 内层代码标签切换回调（工具栏刷新等） */
    private Runnable onActiveTabChange;

    /** 构造工作区标签页管理器 */
    public WorkspaceTabManager(TabPane outerTabPane, StatusBar statusBar) {
        this.outerTabPane = outerTabPane;
        this.statusBar = statusBar;
    }

    /** 创建底部工具窗口顶部的独立拖拽手柄 */
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

    /** 通过拖拽手柄主动调整纵向 SplitPane 的分割比例 */
    private static void installBottomToolResizeDrag(SplitPane splitPane, Region handle) {
        double[] dragStartY = new double[1];
        double[] dragStartDivider = new double[]{0.72};

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

    /** 格式化类路径显示(/ → &gt;,去 .class 后缀) */
    public static String formatClassPath(String fullPath) {
        String display = fullPath.replace('/', '>').replace('\\', '>');
        return display.endsWith(".class")
                ? display.substring(0, display.length() - 6)
                : display;
    }

    /**
     * 为文件树视图安装右键上下文菜单
     *
     * @param treeView          目标文件树视图
     * @param workspace         当前工作区
     * @param codeTabPane       代码标签页面板
     * @param navigationService 导航服务（前进/后退支持）
     * @param onClassClick      点击类节点时的回调
     * @param onTextFileClick   点击文本文件节点时的回调
     * @param onExportNode      导出节点回调
     * @param onFindUsage       查找使用回调
     * @param onSearchPackage   搜索包回调
     * @param onHexClick        十六进制查看回调
     */
    private static void installTreeContextMenu(FileTreeView treeView, Workspace workspace,
                                               TabPane codeTabPane, NavigationService navigationService,
                                               BiConsumer<FileTreeNode, TabPane> onClassClick,
                                               BiConsumer<FileTreeNode, TabPane> onTextFileClick,
                                               TreeItemAction onExportNode,
                                               java.util.function.Consumer<FileTreeNode> onFindUsage,
                                               java.util.function.Consumer<FileTreeNode> onSearchPackage,
                                               BiConsumer<FileTreeNode, TabPane> onHexClick) {
        // 预计算 workspaceHash 避免每次 cell 更新时重复字符串拼接
        String workspaceHash = CommentScope.workspaceHash(workspace);
        treeView.setCellFactory(tv -> {
            com.bingbaihanji.fxdecomplie.ui.tree.FileTreeCell cell =
                    new com.bingbaihanji.fxdecomplie.ui.tree.FileTreeCell(
                            node -> displayTreeNodeName(workspaceHash, node));
            cell.setOnContextMenuRequested(event -> {
                TreeItem<FileTreeNode> item = cell.getTreeItem();
                if (cell.isEmpty() || item == null || item.getValue() == null) {
                    return;
                }
                treeView.getSelectionModel().select(item);
                ContextMenu menu = buildTreeContextMenu(item, workspace, codeTabPane,
                        navigationService, onClassClick, onTextFileClick, onExportNode,
                        onFindUsage, onSearchPackage, onHexClick);
                menu.show(cell, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            return cell;
        });
    }

    /** 获取树节点显示名称,优先使用反混淆别名,回退到原始名称 */
    private static String displayTreeNodeName(String workspaceHash, FileTreeNode node) {
        if (node == null) {
            return "";
        }
        if (!node.isClassFile() || workspaceHash.isBlank()) {
            return node.getName();
        }
        String displayName = RenameService.displayClassName(node.getFullPath(), workspaceHash);
        return displayName == null || displayName.isBlank() ? node.getName() : displayName + ".class";
    }

    /**
     * 构建文件树节点的右键上下文菜单
     *
     * @param item              当前右键点击的树节点
     * @param workspace         当前工作区
     * @param codeTabPane       代码标签页面板
     * @param navigationService 导航服务
     * @param onClassClick      类节点点击回调
     * @param onTextFileClick   文本文件节点点击回调
     * @param onExportNode      导出节点回调
     * @param onFindUsage       查找使用回调
     * @param onSearchPackage   搜索包回调
     * @param onHexClick        十六进制查看回调
     * @return 包含打开/导览/导出/复制/展开等菜单项的上下文菜单
     */
    private static ContextMenu buildTreeContextMenu(TreeItem<FileTreeNode> item, Workspace workspace,
                                                    TabPane codeTabPane,
                                                    NavigationService navigationService,
                                                    BiConsumer<FileTreeNode, TabPane> onClassClick,
                                                    BiConsumer<FileTreeNode, TabPane> onTextFileClick,
                                                    TreeItemAction onExportNode,
                                                    java.util.function.Consumer<FileTreeNode> onFindUsage,
                                                    java.util.function.Consumer<FileTreeNode> onSearchPackage,
                                                    BiConsumer<FileTreeNode, TabPane> onHexClick) {
        FileTreeNode node = item.getValue();
        ContextMenu menu = new ContextMenu();

        MenuItem open = new MenuItem(I18nUtil.getString("context.open"));
        open.setDisable(!node.isClassFile() && !node.isTextFile());
        open.setOnAction(e -> {
            if (node.isClassFile()) {
                navigationService.openPath(navigationService.classPath(workspace, node),
                        workspace, codeTabPane, onClassClick, onTextFileClick);
            } else if (node.isTextFile()) {
                navigationService.openPath(navigationService.resourcePath(workspace, node),
                        workspace, codeTabPane, onClassClick, onTextFileClick);
            }
        });

        MenuItem openHex = new MenuItem("Open in Hex");
        openHex.setDisable(node.isClassFile());
        openHex.setOnAction(e -> onHexClick.accept(node, codeTabPane));

        MenuItem export = new MenuItem(I18nUtil.getString("context.exportNode"));
        export.setOnAction(e -> onExportNode.accept(item));

        MenuItem findUsage = new MenuItem(I18nUtil.getString("context.findUsages"));
        findUsage.setDisable(!node.isClassFile());
        findUsage.setOnAction(e -> onFindUsage.accept(node));

        MenuItem searchPackage = new MenuItem(I18nUtil.getString("context.searchPackage"));
        searchPackage.setOnAction(e -> onSearchPackage.accept(node));

        MenuItem back = new MenuItem(I18nUtil.getString("context.back"));
        back.setDisable(!navigationService.canGoBack());
        back.setOnAction(e -> navigationService.goBack(workspace, codeTabPane,
                onClassClick, onTextFileClick));

        MenuItem forward = new MenuItem(I18nUtil.getString("context.forward"));
        forward.setDisable(!navigationService.canGoForward());
        forward.setOnAction(e -> navigationService.goForward(workspace, codeTabPane,
                onClassClick, onTextFileClick));

        MenuItem copyPath = new MenuItem(I18nUtil.getString("context.copyPath"));
        copyPath.setOnAction(e -> copyToClipboard(node.getFullPath()));

        MenuItem copyClassName = new MenuItem(I18nUtil.getString("context.copyClassName"));
        copyClassName.setDisable(!node.isClassFile());
        copyClassName.setOnAction(e -> {
            String fp = node.getFullPath();
            String name = fp.endsWith(".class") ? fp.substring(0, fp.length() - 6) : fp;
            copyToClipboard(name.replace('/', '.').replace('\\', '.'));
        });

        MenuItem expand = new MenuItem(I18nUtil.getString("context.expand"));
        expand.setOnAction(e -> expand(item));

        MenuItem collapse = new MenuItem(I18nUtil.getString("context.collapse"));
        collapse.setOnAction(e -> collapseStatic(item));

        menu.getItems().addAll(open, openHex, back, forward, export, findUsage, searchPackage, new SeparatorMenuItem(),
                copyPath, copyClassName, new SeparatorMenuItem(), expand, collapse);
        return menu;
    }

    /** 根据树节点类型（类文件/文本文件）打开对应的标签页 */
    private static void openTreeItem(TreeItem<FileTreeNode> item, Workspace workspace,
                                     TabPane codeTabPane, NavigationService navigationService,
                                     BiConsumer<FileTreeNode, TabPane> onClassClick,
                                     BiConsumer<FileTreeNode, TabPane> onTextFileClick,
                                     BiConsumer<FileTreeNode, TabPane> onHexClick) {
        if (item == null) {
            return;
        }
        FileTreeNode node = item.getValue();
        if (node == null) {
            return;
        }
        if (node.isClassFile()) {
            navigationService.openPath(navigationService.classPath(workspace, node),
                    workspace, codeTabPane, onClassClick, onTextFileClick);
        } else if (node.isTextFile()) {
            navigationService.openPath(navigationService.resourcePath(workspace, node),
                    workspace, codeTabPane, onClassClick, onTextFileClick);
        }
    }

    /** 复制字符串到系统剪贴板 */
    private static void copyToClipboard(String value) {
        ClipboardContent content = new ClipboardContent();
        content.putString(value == null ? "" : value);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** 递归展开树节点及其所有子节点 */
    private static void expand(TreeItem<FileTreeNode> item) {
        item.setExpanded(true);
        item.getChildren().forEach(WorkspaceTabManager::expand);
    }

    /** 递归折叠树节点及其所有子节点 */
    private static void collapseStatic(TreeItem<FileTreeNode> item) {
        item.setExpanded(false);
        item.getChildren().forEach(WorkspaceTabManager::collapseStatic);
    }

    /** 格式化最近文件路径显示（文件名 + 完整路径） */
    private static String recentDisplay(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String name = new File(path).getName();
        return name.isBlank() ? path : name + "    " + path;
    }

    /** 更新标签页的固定（pin）指示器文本,在已固定标签前加圆形标记 */
    private static void updatePinnedTabText(Tab tab) {
        if (tab == null) {
            return;
        }
        boolean pinned = Boolean.TRUE.equals(tab.getProperties().get("pinned"));
        if (tab instanceof CodeEditorTab codeTab) {
            codeTab.setPinnedIndicator(pinned);
            return;
        }
        String text = tab.getText();
        if (pinned && !text.startsWith("● ")) {
            tab.setText("● " + text);
        } else if (!pinned && text.startsWith("● ")) {
            tab.setText(text.substring(2));
        }
    }

    /** 根据代码标签页当前选中的类刷新继承面板 */
    private static void refreshInheritanceForCurrentSelection(Workspace workspace, TabPane codeTabPane,
                                                              InheritancePane inheritancePane,
                                                              boolean startIndexIfNeeded) {
        Tab selected = codeTabPane.getSelectionModel().getSelectedItem();
        if (selected instanceof CodeEditorTab codeTab) {
            refreshInheritancePane(workspace, codeTabPane, inheritancePane,
                    codeTab.getOpenFile().fullPath(), startIndexIfNeeded);
        } else {
            inheritancePane.clear();
        }
    }

    /** 加载指定类的继承信息到继承面板,索引未就绪时显示等待状态 */
    private static void refreshInheritancePane(Workspace workspace, TabPane codeTabPane,
                                               InheritancePane inheritancePane,
                                               String selectedPath,
                                               boolean startIndexIfNeeded) {
        if (workspace == null || inheritancePane == null || selectedPath == null || selectedPath.isBlank()) {
            return;
        }
        if (workspace.isIndexReady()) {
            inheritancePane.load(selectedPath, workspace.getIndex());
            return;
        }
        if (!startIndexIfNeeded) {
            inheritancePane.showIndexPending();
            return;
        }

        inheritancePane.showIndexing();
        if (!workspace.isIndexBuildStarted()) {
            WorkspaceIndexService.ensureIndexingStarted(workspace);
        }
        workspace.getIndexFuture().whenComplete((index, error) -> Platform.runLater(() -> {
            if (error != null) {
                inheritancePane.showUnavailable();
                return;
            }
            Tab selected = codeTabPane.getSelectionModel().getSelectedItem();
            if (selected instanceof CodeEditorTab selectedCodeTab
                    && selectedPath.equals(selectedCodeTab.getOpenFile().fullPath())) {
                inheritancePane.load(selectedPath, index);
            }
        }));
    }

    /** 深度优先递归查找具有指定完整路径的树节点 */
    private static TreeItem<FileTreeNode> findTreeItemByPath(
            TreeItem<FileTreeNode> parent, String fullPath) {
        if (parent == null || fullPath == null) {
            return null;
        }
        FileTreeNode node = parent.getValue();
        if (node != null && fullPath.equals(node.getFullPath())) {
            return parent;
        }
        for (TreeItem<FileTreeNode> child : parent.getChildren()) {
            TreeItem<FileTreeNode> found = findTreeItemByPath(child, fullPath);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 设置内层代码标签页切换回调（用于工具栏状态刷新等） */
    public void setOnActiveTabChange(Runnable callback) {
        this.onActiveTabChange = callback;
    }

    /** 设置拖放配置,用于在 workspace 代码标签页面板安装跨窗口拖拽支持 */
    public void setDragDropConfig(AppConfig config, VsCodeThemeLoader.ThemeData theme) {
        this.dragDropConfig = config;
        this.dragDropTheme = theme;
    }

    /** 获取所有工作区视图映射（标签页到 WorkspaceView） */
    public Map<Tab, WorkspaceView> getWorkspaceViews() {
        return workspaceViews;
    }

    /** 设置欢迎页动作回调并刷新欢迎页内容 */
    public void setWelcomeActions(WelcomeActions welcomeActions) {
        this.welcomeActions = welcomeActions == null ? WelcomeActions.empty() : welcomeActions;
        refreshWelcomeTab();
    }

    /** 更新当前反编译引擎名称并同步到状态栏 */
    public void setCurrentEngineName(String currentEngineName) {
        this.currentEngineName = currentEngineName == null ? "" : currentEngineName;
        statusBar.setEngine(this.currentEngineName);
    }

    /**
     * 添加工作区标签页（简化版,不含查找使用/搜索包/十六进制查看回调）
     *
     * @param workspace       工作区
     * @param onClassClick    点击类节点时的回调
     * @param onTextFileClick 点击文本文件节点时的回调
     */
    public void addWorkspaceTab(Workspace workspace,
                                BiConsumer<FileTreeNode, TabPane> onClassClick,
                                BiConsumer<FileTreeNode, TabPane> onTextFileClick) {
        addWorkspaceTab(workspace, onClassClick, onTextFileClick, item -> {
        });
    }

    /**
     * 添加工作区标签页（含导出回调,不含查找使用/搜索包/十六进制查看回调）
     *
     * @param workspace       工作区
     * @param onClassClick    点击类节点时的回调
     * @param onTextFileClick 点击文本文件节点时的回调
     * @param onExportNode    导出当前树节点的回调
     */
    public void addWorkspaceTab(Workspace workspace,
                                BiConsumer<FileTreeNode, TabPane> onClassClick,
                                BiConsumer<FileTreeNode, TabPane> onTextFileClick,
                                TreeItemAction onExportNode) {
        addWorkspaceTab(workspace, onClassClick, onTextFileClick, onExportNode,
                node -> {
                }, node -> {
                }, (node, pane) -> {
                });
    }

    /**
     * 添加工作区标签页（完整版,包含所有回调）
     *
     * @param workspace       工作区
     * @param onClassClick    点击类节点时的回调
     * @param onTextFileClick 点击文本文件节点时的回调
     * @param onExportNode    导出当前树节点的回调
     * @param onFindUsage     查找使用回调
     * @param onSearchPackage 搜索当前包回调
     * @param onHexClick      十六进制查看回调
     */
    public void addWorkspaceTab(Workspace workspace,
                                BiConsumer<FileTreeNode, TabPane> onClassClick,
                                BiConsumer<FileTreeNode, TabPane> onTextFileClick,
                                TreeItemAction onExportNode,
                                java.util.function.Consumer<FileTreeNode> onFindUsage,
                                java.util.function.Consumer<FileTreeNode> onSearchPackage,
                                BiConsumer<FileTreeNode, TabPane> onHexClick) {
        removeWelcomeTab();

        FileTreeView treeView = new FileTreeView(workspace.getTreeRoot());
        treeView.setPrefWidth(280);
        NavigationService navigationService = new NavigationService();

        // 分屏编辑器（1-3 个并排 TabPane）
        SplitEditorPane splitEditorPane = new SplitEditorPane(dragDropConfig, dragDropTheme);
        TabPane codeTabPane = splitEditorPane.primaryTabPane();
        codeTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        codeTabPane.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTab, newTab) -> {
                    updateStatusForCodeTab(newTab);
                    if (onActiveTabChange != null) {
                        onActiveTabChange.run();
                    }
                }
        );

        treeView.setOnMouseClicked(e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY || e.getClickCount() != 1) {
                return;
            }
            TreeItem<FileTreeNode> item = treeView.getSelectionModel().getSelectedItem();
            openTreeItem(item, workspace, codeTabPane, navigationService, onClassClick, onTextFileClick, onHexClick);
        });
        treeView.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                TreeItem<FileTreeNode> item = treeView.getSelectionModel().getSelectedItem();
                openTreeItem(item, workspace, codeTabPane, navigationService, onClassClick, onTextFileClick, onHexClick);
                e.consume();
            }
        });
        installTreeContextMenu(treeView, workspace, codeTabPane, navigationService,
                onClassClick, onTextFileClick, onExportNode,
                onFindUsage, onSearchPackage, onHexClick);

        OutlinePane outlinePane = new OutlinePane();
        InheritancePane inheritancePane = new InheritancePane();
        CommentListPane commentListPane = new CommentListPane();

        javafx.scene.control.Tab outlineTab = new javafx.scene.control.Tab(I18nUtil.getString("tab.outline"), outlinePane);
        outlineTab.setClosable(true);
        javafx.scene.control.Tab inheritTab = new javafx.scene.control.Tab(I18nUtil.getString("tab.inheritance"), inheritancePane);
        inheritTab.setClosable(true);
        javafx.scene.control.Tab commentsTab = new javafx.scene.control.Tab(I18nUtil.getString("tab.comments"), commentListPane);
        commentsTab.setClosable(true);

        TabPane sideTabPane = new TabPane();
        sideTabPane.getStyleClass().add("bottom-tool-window");
        sideTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        sideTabPane.getTabs().addAll(outlineTab, inheritTab, commentsTab);
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
        editorSplitPane.getItems().addAll(splitEditorPane, bottomToolContainer);
        editorSplitPane.setDividerPositions(0.72);

        SplitPane splitPane = new SplitPane(treeView, editorSplitPane);
        splitPane.setDividerPositions(0.25);

        // 代码标签页切换时更新大纲、继承和注释面板
        codeTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab instanceof CodeEditorTab codeTab) {
                outlinePane.update(codeTab.getOpenFile().sourceCode());
                String selectedPath = codeTab.getOpenFile().fullPath();
                boolean inheritanceSelected = sideTabPane.getSelectionModel().getSelectedItem() == inheritTab;
                refreshInheritancePane(workspace, codeTabPane, inheritancePane, selectedPath, inheritanceSelected);
                String wsHash = CommentScope.of(workspace, "").workspaceHash();
                commentListPane.load(wsHash, selectedPath);
            } else {
                outlinePane.clear();
                inheritancePane.clear();
                commentListPane.clear();
            }
        });

        sideTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == inheritTab) {
                refreshInheritanceForCurrentSelection(workspace, codeTabPane, inheritancePane, true);
            }
        });

        // 绑定大纲点击跳转
        outlinePane.setJumpHandler(line -> {
            CodeEditorTab codeTab = getCurrentCodeTab(codeTabPane);
            if (codeTab != null) {
                var area = codeTab.getCodeArea();
                area.select(jfx.incubator.scene.control.richtext.TextPos.ofLeading(line - 1, 0),
                        jfx.incubator.scene.control.richtext.TextPos.ofLeading(line - 1, 0));
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

        // 绑定注释面板回调
        commentListPane.setOnJumpToLine(line -> {
            CodeEditorTab codeTab = getCurrentCodeTab(codeTabPane);
            if (codeTab != null) {
                var area = codeTab.getCodeArea();
                area.select(jfx.incubator.scene.control.richtext.TextPos.ofLeading(line - 1, 0),
                        jfx.incubator.scene.control.richtext.TextPos.ofLeading(line - 1, 0));
                area.requestFocus();
            }
        });

        Tab tab = new Tab(workspace.getName(), splitPane);
        WorkspaceTools tools = new WorkspaceTools(
                editorSplitPane, bottomToolContainer, sideTabPane, outlineTab, inheritTab, commentsTab);
        outlineTab.setOnClosed(e -> hideToolWindowIfEmpty(tab));
        inheritTab.setOnClosed(e -> hideToolWindowIfEmpty(tab));
        commentsTab.setOnClosed(e -> hideToolWindowIfEmpty(tab));
        WorkspaceView view = new WorkspaceView(workspace, treeView, splitEditorPane, tab);
        workspaceViews.put(tab, view);
        workspaceTools.put(tab, tools);
        tab.setOnCloseRequest(event -> {
            if (!confirmWorkspaceClose()) {
                event.consume();
            }
        });
        tab.setOnClosed(event -> cleanupClosedWorkspace(tab));

        outerTabPane.getTabs().add(tab);
        outerTabPane.getSelectionModel().select(tab);
        statusBar.setFilePath(workspace.getSourceFile().getAbsolutePath());
        statusBar.setEngine(currentEngineName);
    }

    /** 关闭指定工作区标签页并释放相关资源 */
    public void closeWorkspaceTab(Tab tab) {
        cleanupClosedWorkspace(tab);
        outerTabPane.getTabs().remove(tab);
    }

    /** 关闭除 keepTab 外的所有工作区标签页 */
    public void closeOtherWorkspaces(Tab keepTab) {
        outerTabPane.getTabs().stream()
                .filter(tab -> tab != keepTab)
                .toList()
                .forEach(this::closeWorkspaceTab);
    }

    /** 获取当前选中标签页对应的工作区视图,无选中时返回 null */
    public WorkspaceView currentWorkspaceView() {
        Tab selected = outerTabPane.getSelectionModel().getSelectedItem();
        return selected == null ? null : workspaceViews.get(selected);
    }

    /** 检查工作区视图是否仍然活跃（未被用户关闭）,用于防止对已释放 UI 的操作 */
    public boolean isWorkspaceActive(WorkspaceView view) {
        return view != null && workspaceViews.containsKey(view.workspaceTab());
    }

    /** 获取当前工作区中选中的代码编辑标签页,无选中时返回 null */
    public CodeEditorTab currentCodeTab() {
        WorkspaceView view = currentWorkspaceView();
        if (view == null) {
            return null;
        }
        return view.splitEditorPane().currentCodeTab();
    }

    /** 根据指定标签页的工作区状态更新状态栏（文件路径、编码、引擎、光标位置） */
    public void updateStatusForWorkspace(Tab tab) {
        WorkspaceView view = tab == null ? null : workspaceViews.get(tab);
        if (view == null) {
            statusBar.clear();
            return;
        }
        var codeSelected = view.splitEditorPane().currentCodeTab();
        if (codeSelected != null) {
            CodeEditorTab codeTab = codeSelected;
            statusBar.setFilePath(formatClassPath(codeTab.getOpenFile().fullPath()));
            statusBar.setEncoding("UTF-8");
            statusBar.setEngine(codeTab.getOpenFile().engine().name());
            var codeArea = codeTab.getCodeArea();
            if (codeArea == null) {
                return;
            }
            var caret = codeArea.getCaretPosition();
            statusBar.setCursorPosition(caret.index() + 1, caret.offset() + 1);
        }
    }

    /** 当没有工作区标签页时显示欢迎页 */
    public void showWelcomeTabIfEmpty() {
        if (outerTabPane.getTabs().isEmpty()) {
            outerTabPane.getTabs().add(createWelcomeTab());
        }
    }

    /** 刷新欢迎页内容 */
    private void refreshWelcomeTab() {
        for (Tab tab : outerTabPane.getTabs()) {
            if (!workspaceViews.containsKey(tab)) {
                tab.setContent(createWelcomeContent());
                return;
            }
        }
    }

    /** 折叠当前工作区文件树 */
    public void collapseTreeInCurrentWorkspace() {
        WorkspaceView view = currentWorkspaceView();
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

    /** 隐藏当前工作区底部工具窗口,但保留其中的标签页状态不关闭 */
    public void hideBottomToolWindow() {
        Tab tab = outerTabPane.getSelectionModel().getSelectedItem();
        WorkspaceTools tools = tab == null ? null : workspaceTools.get(tab);
        if (tools != null) {
            hideToolWindow(tools);
        }
    }

    /** 关闭工作区前弹出确认对话框 */
    private boolean confirmWorkspaceClose() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18nUtil.getString("workspace.close.title"));
        alert.setHeaderText(null);
        alert.setContentText(I18nUtil.getString("workspace.close.message"));
        var scene = outerTabPane.getScene();
        if (scene != null && scene.getWindow() != null) {
            alert.initOwner(scene.getWindow());
        }
        DialogHelper.applyNativeStyle(alert);
        var result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /** 清理已关闭的工作区 */
    private void cleanupClosedWorkspace(Tab tab) {
        if (!workspaceViews.containsKey(tab)) {
            return;
        }
        WorkspaceView view = workspaceViews.remove(tab);
        WorkspaceTools tools = workspaceTools.remove(tab);

        // 清理内部代码标签页,释放内存,阻止已排队的 Platform.runLater 继续操作
        if (view != null) {
            for (TabPane pane : view.splitEditorPane().allTabPanes()) {
                pane.getTabs().clear();
                pane.getSelectionModel().clearSelection();
            }
            view.workspace().close();
        }

        if (workspaceViews.isEmpty()) {
            statusBar.clear();
            if (outerTabPane.getTabs().stream().noneMatch(this::isWelcomeTab)) {
                outerTabPane.getTabs().add(createWelcomeTab());
            }
        }

    }

    private boolean isWelcomeTab(Tab tab) {
        return Boolean.TRUE.equals(tab.getProperties().get("welcome"));
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

    /** 如果所有工具标签页都关闭,则隐藏底部工具窗口 */
    private void hideToolWindowIfEmpty(Tab workspaceTab) {
        WorkspaceTools tools = workspaceTools.get(workspaceTab);
        if (tools != null && tools.toolTabPane.getTabs().isEmpty()) {
            hideToolWindow(tools);
        }
    }

    /** 从纵向 SplitPane 中移除底部工具窗口,并记住当前高度比例 */
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
            statusBar.setFilePath(formatClassPath(codeTab.getOpenFile().fullPath()));
            statusBar.setEncoding("UTF-8");
            statusBar.setEngine(codeTab.getOpenFile().engine().name());
            var codeArea = codeTab.getCodeArea();
            if (codeArea == null) {
                return;
            }
            var caret = codeArea.getCaretPosition();
            // caret 可能在 CodeArea 初始布局完成前为 null
            if (caret == null) {
                return;
            }
            statusBar.setCursorPosition(caret.index() + 1, caret.offset() + 1);
        }
    }

    /** 获取当前代码标签页 */
    private CodeEditorTab getCurrentCodeTab(TabPane codeTabPane) {
        javafx.scene.control.Tab selected = codeTabPane.getSelectionModel().getSelectedItem();
        return selected instanceof CodeEditorTab codeTab ? codeTab : null;
    }

    /** 在文件树中查找指定类名的节点 */
    private FileTreeNode findClassNode(TreeItem<FileTreeNode> item, String className) {
        FileTreeNode data = item.getValue();
        String searchName = className.endsWith(".class") ? className.substring(0, className.length() - 6) : className;
        searchName = searchName.replace("\\", "/");
        if (data != null && data.isClassFile()) {
            String fp = data.getFullPath();
            String name = fp.endsWith(".class") ? fp.substring(0, fp.length() - 6) : fp;
            if (name.equals(searchName)) {
                return data;
            }
        }
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            FileTreeNode found = findClassNode(child, className);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 移除欢迎标签页 */
    private void removeWelcomeTab() {
        outerTabPane.getTabs().removeIf(tab -> !workspaceViews.containsKey(tab));
    }

    /** 创建欢迎标签页 */
    private Tab createWelcomeTab() {
        Tab tab = new Tab(I18nUtil.getString("tab.welcome"), createWelcomeContent());
        tab.getProperties().put("welcome", true);
        tab.setClosable(false);
        return tab;
    }

    private Node createWelcomeContent() {
        Label title = new Label(I18nUtil.getString("welcome.title"));
        title.getStyleClass().add("welcome-title");

        Label subtitle = new Label(I18nUtil.getString("welcome.subtitle"));
        subtitle.getStyleClass().add("welcome-subtitle");

        Button openFile = new Button(I18nUtil.getString("welcome.openFile"));
        openFile.setOnAction(e -> welcomeActions.openFile().run());
        Button openDir = new Button(I18nUtil.getString("welcome.openDir"));
        openDir.setOnAction(e -> welcomeActions.openDirectory().run());
        Button openProject = new Button(I18nUtil.getString("welcome.openProject"));
        openProject.setOnAction(e -> welcomeActions.openProject().run());
        FlowPane actions = new FlowPane(8, 8, openFile, openDir, openProject);
        actions.getStyleClass().add("welcome-actions");

        // 最近打开
        VBox recentBox = new VBox(4);
        recentBox.getStyleClass().add("welcome-recent");
        Label recentTitle = new Label(I18nUtil.getString("welcome.recent"));
        recentTitle.getStyleClass().add("welcome-section-title");
        recentBox.getChildren().add(recentTitle);
        List<String> recentFiles = welcomeActions.recentFiles();
        if (recentFiles == null || recentFiles.isEmpty()) {
            Label empty = new Label(I18nUtil.getString("welcome.recent.empty"));
            empty.getStyleClass().add("welcome-muted");
            recentBox.getChildren().add(empty);
        } else {
            recentFiles.stream().limit(8).forEach(path -> {
                Button recent = new Button(recentDisplay(path));
                recent.getStyleClass().add("welcome-recent-button");
                recent.setMaxWidth(Double.MAX_VALUE);
                recent.setOnAction(e -> welcomeActions.openRecent().accept(path));
                recentBox.getChildren().add(recent);
            });
        }

        Label drop = new Label(I18nUtil.getString("welcome.dropHint"));
        drop.getStyleClass().add("welcome-muted");

        VBox panel = new VBox(14, title, subtitle, actions, recentBox, drop);
        panel.getStyleClass().add("welcome-panel");
        panel.setMaxWidth(720);

        StackPane content = new StackPane(panel);
        content.getStyleClass().add("empty-state");
        StackPane.setAlignment(panel, Pos.CENTER);
        return content;
    }

    /** 递归折叠树节点 */
    private void collapse(TreeItem<FileTreeNode> item) {
        item.setExpanded(false);
        item.getChildren().forEach(this::collapse);
    }

    /**
     * 在文件树中递归查找并选中有指定完整路径的节点
     *
     * @param fullPath 目标文件完整内部路径
     * @return 找到并选中返回 true,否则 false
     */
    public boolean selectTreeNodeByPath(String fullPath) {
        if (fullPath == null || fullPath.isBlank()) {
            return false;
        }
        String normalized = fullPath.replace('\\', '/');
        Tab selected = outerTabPane.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return false;
        }
        WorkspaceView view = workspaceViews.get(selected);
        if (view == null || view.treeView() == null) {
            return false;
        }
        FileTreeView treeView = view.treeView();
        TreeItem<FileTreeNode> root = treeView.getRoot();
        if (root == null) {
            return false;
        }
        TreeItem<FileTreeNode> found = findTreeItemByPath(root, normalized);
        if (found != null) {
            // 展开所有父节点
            TreeItem<FileTreeNode> parent = found.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }
            treeView.getSelectionModel().select(found);
            int row = treeView.getRow(found);
            if (row >= 0) {
                treeView.scrollTo(row);
            }
            return true;
        }
        return false;
    }

    private enum ToolTab {
        OUTLINE,
        INHERITANCE
    }

    @FunctionalInterface
    public interface TreeItemAction {
        void accept(TreeItem<FileTreeNode> item);
    }

    /** 欢迎页动作回调集合,封装打开文件/目录/项目及最近文件列表的操作 */
    public record WelcomeActions(
            Runnable openFile,
            Runnable openDirectory,
            Runnable openProject,
            List<String> recentFiles,
            Consumer<String> openRecent
    ) {
        public WelcomeActions {
            openFile = openFile == null ? () -> {
            } : openFile;
            openDirectory = openDirectory == null ? () -> {
            } : openDirectory;
            openProject = openProject == null ? () -> {
            } : openProject;
            recentFiles = recentFiles == null ? List.of() : List.copyOf(recentFiles);
            openRecent = openRecent == null ? path -> {
            } : openRecent;
        }

        /** 返回一个所有回调均为空操作的默认实例 */
        public static WelcomeActions empty() {
            return new WelcomeActions(null, null, null, List.of(), null);
        }
    }

    private static final class WorkspaceTools {
        private final SplitPane editorSplitPane;
        private final Node toolContainer;
        private final TabPane toolTabPane;
        private final Tab outlineTab;
        private final Tab inheritanceTab;
        private final Tab commentsTab;
        private double dividerPosition = 0.72;

        private WorkspaceTools(SplitPane editorSplitPane, Node toolContainer, TabPane toolTabPane,
                               Tab outlineTab, Tab inheritanceTab, Tab commentsTab) {
            this.editorSplitPane = editorSplitPane;
            this.toolContainer = toolContainer;
            this.toolTabPane = toolTabPane;
            this.outlineTab = outlineTab;
            this.inheritanceTab = inheritanceTab;
            this.commentsTab = commentsTab;
        }
    }
}
