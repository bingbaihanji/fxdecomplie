package com.bingbaihanji.fxdecomplie;


import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.*;
import com.bingbaihanji.fxdecomplie.service.*;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceTabManager;
import com.bingbaihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbaihanji.fxdecomplie.ui.code.CodeOnlyWindow;
import com.bingbaihanji.fxdecomplie.ui.code.StatusBar;
import com.bingbaihanji.fxdecomplie.ui.toolbar.MainToolBar;
import com.bingbaihanji.fxdecomplie.ui.export.ExportDialog;
import com.bingbaihanji.fxdecomplie.ui.menu.MainMenuBar;
import com.bingbaihanji.fxdecomplie.ui.quickopen.QuickOpenDialog;
import com.bingbaihanji.fxdecomplie.ui.search.*;
import com.bingbaihanji.fxdecomplie.ui.settings.SettingsDialog;
import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.fxdecomplie.ui.usage.FindUsageDialog;
import com.bingbaihanji.fxdecomplie.ui.window.AppHeaderBar;
import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * FxDecompiler 应用窗口的中央控制器
 * 管理外层工作区 TabPane、内部代码标签页、菜单栏操作、
 * 文件加载、搜索、导出及所有用户交互
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class MainWindow implements MainMenuBar.Actions {

    /** 应用配置 */
    private final AppConfig config;
    /** 编辑器主题数据 */
    private final VsCodeThemeLoader.ThemeData editorTheme;
    /** 是否使用自定义标题栏 */
    private final boolean useHeaderBar;
    /** HostServices 用于打开外部链接 */
    private final HostServices hostServices;

    /** 主窗口 Stage */
    private Stage stage;
    /** 状态栏 */
    private StatusBar statusBar;
    /** 外层标签页(JAR/ZIP/目录级别) */
    private TabPane outerTabPane;
    /** 工作区标签页管理器 */
    private WorkspaceTabManager tabManager;
    /** 类文件反编译/标签页打开器 */
    private ClassTabOpener classTabOpener;
    /** 当前选择的反编译引擎 */
    private DecompilerTypeEnum currentEngine = DecompilerTypeEnum.VINEFLOWER;
    /** 是否显示行号 */
    private boolean lineNumbersEnabled;
    /** 工具栏 */
    private MainToolBar toolBar;

    public MainWindow(AppConfig config) {
        this(config, false, null);
    }

    public MainWindow(AppConfig config, boolean useHeaderBar) {
        this(config, useHeaderBar, null);
    }

    public MainWindow(AppConfig config, boolean useHeaderBar, HostServices hostServices) {
        this.config = config;
        this.useHeaderBar = useHeaderBar;
        this.hostServices = hostServices;
        this.editorTheme = AppTheme.loadEditorTheme(config);
        this.lineNumbersEnabled = config.decompiler().lineNumbersEnabled();
    }

    private static void copyToClipboard(String value) {
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(value == null ? "" : value);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }

    /** 显示主窗口 */
    public void show(Stage stage) {
        this.stage = stage;
        this.currentEngine = config.decompiler().defaultEngine();

        outerTabPane = new TabPane();
        outerTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        statusBar = new StatusBar();

        tabManager = new WorkspaceTabManager(outerTabPane, statusBar);
        tabManager.setCurrentEngineName(currentEngine.name());
        tabManager.setDragDropConfig(config, editorTheme);
        tabManager.setWelcomeActions(new WorkspaceTabManager.WelcomeActions(
                this::openFile,
                this::openDirectory,
                this::openProject,
                config.recentFiles(),
                this::openRecentFile));
        classTabOpener = new ClassTabOpener(config, editorTheme, statusBar);
        // L2 缓存跨工作区共享,不清空以提升重复打开性能
        tabManager.showWelcomeTabIfEmpty();

        outerTabPane.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTab, newTab) -> {
                    tabManager.updateStatusForWorkspace(newTab);
                    refreshToolbarState();
                }
        );

        MainMenuBar menuBar = new MainMenuBar(this, currentEngine);
        toolBar = new MainToolBar(this, this);
        VBox topBars = new VBox();
        topBars.setOpacity(0.5);
        if (useHeaderBar) {
            topBars.getChildren().add(AppHeaderBar.create(stage, "FxDecompiler", menuBar));
        } else {
            topBars.getChildren().add(menuBar);
        }
        topBars.getChildren().add(toolBar);
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        // 拖放文件支持
        root.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });
        root.setOnDragDropped(event -> {
            java.util.List<java.io.File> files = event.getDragboard().getFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    loadFile(f);
                }
            }
            event.setDropCompleted(true);
            event.consume();
        });

        root.setTop(topBars);
        root.setCenter(outerTabPane);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, config.window().width(), config.window().height());
        scene.setFill(Color.web("#1e1e1e"));
        scene.getStylesheets().add(AppTheme.darkStylesheet());

        stage.setTitle("FxDecompiler");
        stage.setScene(scene);
        stage.show();
    }

    /** 刷新工具栏按钮状态 */
    private void refreshToolbarState() {
        if (toolBar != null) {
            boolean hasWorkspace = tabManager != null && tabManager.currentWorkspaceView() != null;
            boolean hasCodeTab = tabManager != null && tabManager.currentCodeTab() != null;
            toolBar.refreshState(hasWorkspace, hasCodeTab);
        }
    }

    /** 打开 JAR/ZIP/Class 文件 */
    @Override
    public void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("dialog.openFile.title"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Java Archives & Class Files", "*.jar", "*.zip", "*.class"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            loadFile(file);
        }
    }

    /** 打开目录 */
    @Override
    public void openDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18nUtil.getString("dialog.openDir.title"));
        File dir = chooser.showDialog(stage);
        if (dir != null) {
            loadFile(dir);
        }
    }

    /** 打开项目文件 */
    @Override
    public void openProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("project.open"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("FxDecompiler Project", "*.fxdproj"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            DecompilerProject project = ProjectFileManager.load(file.toPath());
            currentEngine = parseEngine(project.engine(), currentEngine);
            config.decompiler().defaultEngine(currentEngine);
            if (!project.exportPath().isBlank()) {
                config.export().lastPath(project.exportPath());
            }
            for (String inputPath : project.inputPaths()) {
                File input = new File(inputPath);
                if (input.exists()) {
                    loadFile(input);
                }
            }
            statusBar.setFilePath(I18nUtil.getString("project.opened", file.getAbsolutePath()));
        } catch (IOException ex) {
            showError(I18nUtil.getString("dialog.error.title"),
                    I18nUtil.getString("project.open.failed", ex.getMessage()));
        }
    }

    /** 保存当前项目文件 */
    @Override
    public void saveProject() {
        List<String> inputPaths = new ArrayList<>();
        String selectedPath = "";
        Tab selected = outerTabPane.getSelectionModel().getSelectedItem();
        for (Tab tab : outerTabPane.getTabs()) {
            WorkspaceView view = tabManager.getWorkspaceViews().get(tab);
            if (view == null) {
                continue;
            }
            String path = view.workspace().getSourceFile().getAbsolutePath();
            inputPaths.add(path);
            if (tab == selected) {
                selectedPath = path;
            }
        }
        if (inputPaths.isEmpty()) {
            showWarning(I18nUtil.getString("project.save"), I18nUtil.getString("dialog.export.noworkspace"));
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("project.save"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("FxDecompiler Project", "*.fxdproj"));
        chooser.setInitialFileName("workspace.fxdproj");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            ProjectFileManager.save(file.toPath(), new DecompilerProject(
                    1, currentEngine.name(), inputPaths, selectedPath, config.export().lastPath()));
            statusBar.setFilePath(I18nUtil.getString("project.saved", file.getAbsolutePath()));
        } catch (IOException ex) {
            showError(I18nUtil.getString("dialog.error.title"),
                    I18nUtil.getString("project.save.failed", ex.getMessage()));
        }
    }

    /** 关闭当前选中的工作区标签页(含解编译结果和文件树) */
    @Override
    public void closeCurrentWorkspace() {
        Tab selected = outerTabPane.getSelectionModel().getSelectedItem();
        if (selected != null && tabManager.getWorkspaceViews().containsKey(selected)) {
            tabManager.closeWorkspaceTab(selected);
        }
    }

    /** 关闭除当前选中之外的所有工作区标签页 */
    @Override
    public void closeOtherWorkspaces() {
        Tab selected = outerTabPane.getSelectionModel().getSelectedItem();
        if (selected == null || !tabManager.getWorkspaceViews().containsKey(selected)) {
            return;
        }
        tabManager.closeOtherWorkspaces(selected);
    }

    /** 保存当前代码标签页为 .java 文件 */
    @Override
    public void saveCurrentFile() {
        CodeEditorTab codeTab = tabManager.currentCodeTab();
        if (codeTab == null) {
            showWarning(I18nUtil.getString("dialog.save.title"), I18nUtil.getString("dialog.save.nofile"));
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("dialog.saveFile.title"));
        chooser.setInitialFileName(codeTab.getOpenFile().className() + ".java");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java Source", "*.java"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        try {
            ExportService.exportCurrentCode(codeTab.getOpenFile().sourceCode(), file.toPath());
            statusBar.setFilePath(I18nUtil.getString("status.saved", file.getAbsolutePath()));
        } catch (IOException ex) {
            showError(I18nUtil.getString("dialog.error.title"),
                    I18nUtil.getString("dialog.save.failed", ex.getMessage()));
        }
    }

    /** 批量导出所有类为 .java 文件 */
    @Override
    public void exportAllFiles() {
        WorkspaceView view = tabManager.currentWorkspaceView();
        if (view == null) {
            showWarning(I18nUtil.getString("dialog.export.title"), I18nUtil.getString("dialog.export.noworkspace"));
            return;
        }
        withWorkspaceIndex(view.workspace(),
                index -> doExport(view.workspace().getTreeRoot(), index));
    }

    /** 为单个树节点构建临时索引并弹出导出对话框(右键菜单入口) */
    private void exportTreeItem(TreeItem<FileTreeNode> rootItem) {
        if (rootItem == null || rootItem.getValue() == null) {
            return;
        }
        statusBar.setTask(I18nUtil.getString("task.indexing"));
        statusBar.setFilePath(I18nUtil.getString("status.indexing", rootItem.getValue().getName()));
        BackgroundTasks.run("Index-ExportNode", () -> {
            try {
                WorkspaceIndex index = WorkspaceIndex.build(rootItem);
                Platform.runLater(() -> {
                    statusBar.clearTask();
                    doExport(rootItem, index);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusBar.clearTask();
                    showError(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.index.failed", ex.getMessage()));
                });
            }
        });
    }

    /** 弹出导出配置对话框,提交后台导出任务并显示进度 */
    private void doExport(javafx.scene.control.TreeItem<FileTreeNode> rootItem,
                          WorkspaceIndex index) {
        var configOpt = ExportDialog.show(stage, config, currentEngine);
        if (configOpt.isEmpty()) {
            return;
        }
        ExportConfig exportConfig = configOpt.get();
        persistExportConfig(exportConfig);

        ExportDialog.ProgressHandle progressHandle = ExportDialog.showProgress(stage);
        final Future<?>[] exportTask = new Future<?>[1];
        java.util.concurrent.atomic.AtomicBoolean exportCanceled = new java.util.concurrent.atomic.AtomicBoolean(false);
        statusBar.setTask(I18nUtil.getString("task.exporting"));
        progressHandle.setOnCancel(() -> {
            exportCanceled.set(true);
            BackgroundTasks.cancel(exportTask[0]);
        });
        exportTask[0] = BackgroundTasks.run("Export", () -> {
            try {
                ExportResult result = ExportService.exportAll(
                        rootItem, exportConfig, index,
                        (path, pct) -> Platform.runLater(() -> {
                            statusBar.setFilePath(I18nUtil.getString(
                                    "status.exporting.detail", pct, path));
                            progressHandle.update(path, pct);
                        }));
                Platform.runLater(() -> {
                    progressHandle.close();
                    statusBar.clearTask();
                    if (exportCanceled.get()) {
                        statusBar.setFilePath(I18nUtil.getString("dialog.export.canceled"));
                        showWarning(I18nUtil.getString("dialog.export.title"),
                                I18nUtil.getString("dialog.export.canceled"));
                        return;
                    }
                    statusBar.setFilePath(I18nUtil.getString(
                            "status.exportDone", exportConfig.outputPath()));
                    showExportResult(exportConfig, result);
                });
            } catch (java.io.IOException ex) {
                Platform.runLater(() -> {
                    progressHandle.close();
                    statusBar.clearTask();
                    showError(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.export.failed", ex.getMessage()));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progressHandle.close();
                    statusBar.clearTask();
                    if (exportCanceled.get()) {
                        showWarning(I18nUtil.getString("dialog.export.title"),
                                I18nUtil.getString("dialog.export.canceled"));
                    } else {
                        showError(I18nUtil.getString("dialog.error.title"),
                                I18nUtil.getString("dialog.export.failed", ex.getMessage()));
                    }
                });
            }
        });
    }

    /** 退出应用 */
    @Override
    public void exit() {
        BackgroundTasks.shutdown();
        ClassTabOpener.shutdown();
        shutdownResources();
        DecompilerFactory.cleanup();
        javafx.stage.Window.getWindows().stream()
                .filter(w -> w instanceof javafx.stage.Stage && w != stage)
                .forEach(w -> ((javafx.stage.Stage) w).close());
        stage.close();
        javafx.application.Platform.exit();
    }

    public void shutdownResources() {
        if (tabManager == null) {
            return;
        }
        tabManager.getWorkspaceViews().values().forEach(view -> {
            if (view != null && view.workspace() != null) {
                view.workspace().close();
            }
        });
        tabManager.getWorkspaceViews().clear();
    }

    /** 复制选中文本 */
    @Override
    public void copySelection() {
        CodeEditorTab codeTab = tabManager.currentCodeTab();
        if (codeTab != null) {
            codeTab.getCodeArea().copy();
        }
    }

    /** 全选 */
    @Override
    public void selectAll() {
        CodeEditorTab codeTab = tabManager.currentCodeTab();
        if (codeTab != null) {
            codeTab.getCodeArea().selectAll();
        }
    }

    /** 放大字号 */
    @Override
    public void zoomIn() {
        CodeEditorTab codeTab = tabManager.currentCodeTab();
        if (codeTab != null) {
            codeTab.zoomIn();
        }
    }

    /** 缩小字号 */
    @Override
    public void zoomOut() {
        CodeEditorTab codeTab = tabManager.currentCodeTab();
        if (codeTab != null) {
            codeTab.zoomOut();
        }
    }

    /** 重置字号 */
    @Override
    public void resetZoom() {
        CodeEditorTab codeTab = tabManager.currentCodeTab();
        if (codeTab != null) {
            codeTab.resetZoom();
        }
    }

    /** 切换行号显示 */
    @Override
    public void toggleLineNumbers() {
        lineNumbersEnabled = !lineNumbersEnabled;
        config.decompiler().lineNumbersEnabled(lineNumbersEnabled);
        tabManager.getWorkspaceViews().values().forEach(view ->
                view.codeTabPane().getTabs().stream()
                        .filter(CodeEditorTab.class::isInstance)
                        .map(CodeEditorTab.class::cast)
                        .forEach(tab -> tab.setLineNumbersEnabled(lineNumbersEnabled))
        );
    }

    /** 折叠当前工作区文件树 */
    @Override
    public void collapseTree() {
        tabManager.collapseTreeInCurrentWorkspace();
    }

    /** 显示大纲工具窗口 */
    @Override
    public void showOutline() {
        tabManager.showOutlineToolWindow();
    }

    /** 显示继承工具窗口 */
    @Override
    public void showInheritance() {
        tabManager.showInheritanceToolWindow();
    }

    /** 隐藏底部工具窗口 */
    @Override
    public void hideBottomTools() {
        tabManager.hideBottomToolWindow();
    }

    /** 切换反编译引擎并重新反编译当前文件 */
    @Override
    public void selectEngine(DecompilerTypeEnum engine) {
        if (currentEngine == engine) {
            return;
        }
        currentEngine = engine;
        config.decompiler().defaultEngine(engine);
        tabManager.setCurrentEngineName(engine.name());
        statusBar.setEngine(engine.name());
        statusBar.setFilePath(I18nUtil.getString("status.currentEngine", engine.name()));

        WorkspaceView view = tabManager.currentWorkspaceView();
        CodeEditorTab currentTab = tabManager.currentCodeTab();
        if (view != null && currentTab != null) {
            classTabOpener.cancelCurrentTask();
            classTabOpener.refreshCurrentClassTab(
                    view.workspace(), view.codeTabPane(), currentTab, engine, lineNumbersEnabled);
        }
    }

    /** 用当前引擎重新反编译当前类 */
    @Override
    public void refreshCurrentTab() {
        WorkspaceView view = tabManager.currentWorkspaceView();
        CodeEditorTab currentTab = tabManager.currentCodeTab();
        if (view == null || currentTab == null) {
            statusBar.setFilePath(I18nUtil.getString("toolbar.reload.disabled"));
            return;
        }
        classTabOpener.cancelCurrentTask();
        classTabOpener.refreshCurrentClassTab(
                view.workspace(), view.codeTabPane(), currentTab, currentEngine, lineNumbersEnabled);
        statusBar.setFilePath(I18nUtil.getString("status.reloading", currentTab.getOpenFile().fullPath()));
    }

    /** 在文件树中定位当前打开的类文件 */
    @Override
    public void locateCurrentFileInTree() {
        CodeEditorTab currentTab = tabManager.currentCodeTab();
        if (currentTab == null) {
            statusBar.setFilePath(I18nUtil.getString("toolbar.reload.disabled"));
            return;
        }
        String fullPath = currentTab.getOpenFile().fullPath();
        boolean found = tabManager.selectTreeNodeByPath(fullPath);
        if (!found) {
            statusBar.setFilePath(I18nUtil.getString("toolbar.localizer.failed"));
        }
    }

    /** 用全部引擎反编译当前类并排打开标签页,方便对比输出 */
    @Override
    public void compareEngines() {
        WorkspaceView view = tabManager.currentWorkspaceView();
        CodeEditorTab currentTab = tabManager.currentCodeTab();
        if (view == null || currentTab == null) {
            showWarning(I18nUtil.getString("menu.engine.compareAll"),
                    I18nUtil.getString("dialog.needOpenFile"));
            return;
        }

        String fullPath = currentTab.getOpenFile().fullPath();
        FileTreeNode node = view.workspace().findNodeByPath(fullPath);
        if (node == null) {
            statusBar.setFilePath(I18nUtil.getString("status.locateFailed", fullPath));
            return;
        }

        statusBar.setFilePath(I18nUtil.getString("status.compareAllEngines", node.getFullPath()));
        for (DecompilerTypeEnum engine : DecompilerTypeEnum.values()) {
            classTabOpener.openClassTab(node, view.workspace(), view.codeTabPane(),
                    engine, lineNumbersEnabled, engine == DecompilerTypeEnum.values()[0] ? true : false, true);
        }
    }

    /** 打开搜索对话框 */
    @Override
    public void openSearch() {
        openSearch("");
    }

    /** 打开搜索对话框,可预填初始查询关键词 */
    private void openSearch(String initialQuery) {
        var view = tabManager.currentWorkspaceView();
        if (view == null) {
            showWarning(I18nUtil.getString("search.title"),
                    I18nUtil.getString("dialog.needOpenFile"));
            return;
        }
        withWorkspaceIndex(view.workspace(), index -> openSearchWithIndex(view, index, initialQuery));
    }

    /** 装配所有搜索 Provider、构建源码缓存并弹出搜索对话框 */
    private void openSearchWithIndex(WorkspaceView view, WorkspaceIndex index, String initialQuery) {
        // 从已打开标签页构建源码缓存
        java.util.Map<String, String> sourceCache = new java.util.HashMap<>();
        for (javafx.scene.control.Tab tab : view.codeTabPane().getTabs()) {
            if (tab instanceof CodeEditorTab codeTab
                    && codeTab.getOpenFile().engine() == currentEngine) {
                sourceCache.put(codeTab.getOpenFile().fullPath(),
                        codeTab.getOpenFile().sourceCode());
            }
        }

        // 创建包含所有 Provider 的 SearchService
        SearchService searchService = new SearchService();
        searchService.setExcludePatterns(config.search().excludePatterns());
        searchService.addProvider(new ClassSearchProvider(index.classPaths()));
        searchService.addProvider(new IndexedMemberSearchProvider(index));
        searchService.addProvider(new MethodSearchProvider());
        searchService.addProvider(new CodeSearchProvider());
        searchService.addProvider(new CommentSearchProvider());
        searchService.addProvider(new ResourceSearchProvider(index.resourceBytesByPath()));
        searchService.addProvider(new BytecodeSearchProvider(index));

        SearchDialog.show(stage, searchService, sourceCache,
                () -> buildFullSourceCache(view, sourceCache),
                config.search().fullSourceSearch(), config.search().resultLimit(),
                initialQuery,
                (fullPath, lineNumber) -> openClassByPath(view, fullPath, lineNumber));
    }

    /** 查找当前工作区内的类/方法/字段使用 */
    @Override
    public void openFindUsages() {
        var view = tabManager.currentWorkspaceView();
        if (view == null) {
            showWarning(I18nUtil.getString("usage.title"), I18nUtil.getString("dialog.export.noworkspace"));
            return;
        }
        withWorkspaceIndex(view.workspace(), index ->
                FindUsageDialog.show(stage, index,
                        (fullPath, lineNumber) -> openClassByPath(view, fullPath, lineNumber)));
    }

    /** 快速打开类 */
    @Override
    public void quickOpenClass() {
        var view = tabManager.currentWorkspaceView();
        if (view == null) {
            showWarning(I18nUtil.getString("menu.edit.quickOpen"),
                    I18nUtil.getString("dialog.needOpenFile"));
            return;
        }
        BackgroundTasks.run("QuickOpen-" + view.workspace().getName(), () -> {
            java.util.List<String> classNames = new java.util.ArrayList<>();
            collectClassNames(view.workspace().getTreeRoot(), classNames);
            Platform.runLater(() -> QuickOpenDialog.show(stage, classNames, fullPath -> {
                FileTreeNode node = view.workspace().findNodeByPath(fullPath);
                if (node != null) {
                    classTabOpener.openClassTab(node, view.workspace(), view.codeTabPane(),
                            currentEngine, lineNumbersEnabled);
                } else {
                    statusBar.setFilePath(I18nUtil.getString("status.locateFailed", fullPath));
                }
            }));
        });
    }

    /** 打开最近文件 */
    @Override
    public void openRecentFile(String path) {
        loadFile(new java.io.File(path));
    }

    /** 启动参数或外部入口打开文件/目录 */
    public void openInitialFile(File file) {
        if (file != null && file.exists()) {
            loadFile(file);
        }
    }

    /** 获取最近文件列表 */
    @Override
    public java.util.List<String> getRecentFiles() {
        return config.recentFiles();
    }

    @Override
    public void clearRecentFiles() {
        config.clearRecentFiles();
    }

    /** 打开新窗口 */
    @Override
    public void openNewWindow() {
        CodeEditorTab currentTab = tabManager.currentCodeTab();
        if (currentTab == null) {
            showWarning(I18nUtil.getString("dialog.warning.title"),
                    I18nUtil.getString("dialog.needOpenFile"));
            return;
        }
        CodeOnlyWindow.openFrom(currentTab, config, stage);
    }

    /** 显示关于对话框 */
    @Override
    public void about() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(I18nUtil.getString("about.title"));
        alert.setHeaderText("FxDecompiler");
        alert.setOnShown(e -> {
            var win = alert.getDialogPane().getScene().getWindow();
            DefaultWindowTheme.applyWindowDarkMode(win);
            if (win instanceof javafx.stage.Stage s) setAlertIcon(s);
        });

        javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink("www.bingbaihanji.com");
        link.setOnAction(e -> {
            if (hostServices != null) {
                hostServices.showDocument("https://www.bingbaihanji.com");
            }
        });

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8,
                new javafx.scene.control.Label(I18nUtil.getString("about.description")),
                new javafx.scene.control.Label(I18nUtil.getString("about.engines")),
                new javafx.scene.control.Label(""),
                new javafx.scene.control.Label(I18nUtil.getString("about.developer")),
                new javafx.scene.control.Label(I18nUtil.getString("about.date")),
                new javafx.scene.control.Label(I18nUtil.getString("about.website")),
                link);
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    /** 打开设置对话框 */
    @Override
    public void openSettings() {
        SettingsDialog.show(stage, config, this::applySettings);
    }

    /** 应用设置对话框确认后的配置变更：切换引擎、更新行号 */
    private void applySettings(AppConfig updated) {
        DecompilerTypeEnum configuredEngine = updated.decompiler().defaultEngine();
        if (configuredEngine != currentEngine) {
            selectEngine(configuredEngine);
        }
        lineNumbersEnabled = updated.decompiler().lineNumbersEnabled();
        tabManager.getWorkspaceViews().values().forEach(view ->
                view.codeTabPane().getTabs().stream()
                        .filter(CodeEditorTab.class::isInstance)
                        .map(CodeEditorTab.class::cast)
                        .forEach(tab -> tab.setLineNumbersEnabled(lineNumbersEnabled))
        );
    }

    /** 将导出对话框中选择的选项写回全局配置,下次打开时记住 */
    private void persistExportConfig(ExportConfig exportConfig) {
        config.export().defaultEngine(exportConfig.engine().name());
        config.export().defaultFormat(exportConfig.format().name());
        config.export().conflictPolicy(exportConfig.conflictPolicy().name());
        config.export().exportResources(exportConfig.exportResources());
        config.export().lastPath(exportConfig.outputPath().toString());
        config.save();
    }

    /** 根据导出结果弹出成功/部分成功对话框,支持打开输出目录和复制错误详情 */
    private void showExportResult(ExportConfig exportConfig, ExportResult result) {
        if (!result.hasErrors()) {
            showExportDoneDialog(I18nUtil.getString("dialog.export.success.title"),
                    I18nUtil.getString("dialog.export.success.message",
                            result.successCount(), exportConfig.outputPath()),
                    exportConfig.outputPath());
            return;
        }

        int limit = Math.min(8, result.errors().size());
        StringBuilder message = new StringBuilder(I18nUtil.getString(
                "dialog.export.partial.message",
                result.successCount(), result.totalFiles(), result.failedCount()));
        message.append(System.lineSeparator()).append(System.lineSeparator());
        for (int i = 0; i < limit; i++) {
            message.append("- ").append(result.errors().get(i)).append(System.lineSeparator());
        }
        if (result.errors().size() > limit) {
            message.append(I18nUtil.getString(
                    "dialog.export.moreErrors", result.errors().size() - limit));
        }
        showExportDoneDialog(I18nUtil.getString("dialog.export.partial.title"),
                message.toString(), exportConfig.outputPath(), result.errors());
    }

    // ── 内部辅助方法 ──

    /** 在工作区中按完整路径打开类并延迟跳转到指定行(搜索/FindUsages 双击回调) */
    private void openClassByPath(WorkspaceView view, String fullPath, int lineNumber) {
        FileTreeNode node = view.workspace().findNodeByPath(fullPath);
        if (node != null) {
            classTabOpener.openClassTab(node, view.workspace(), view.codeTabPane(),
                    currentEngine, lineNumbersEnabled);
            // 反编译完成后导航到目标行
            navigateToLine(view, fullPath, lineNumber, 0);
        }
    }

    /** 延迟轮询工作区标签页,等待解编译完成并将 CodeArea 滚动到目标行(最多 2 秒) */
    private void navigateToLine(WorkspaceView view, String fullPath, int lineNumber, int retries) {
        // 最多约 2 秒
        if (retries > 20) {
            return;
        }
        if (!tabManager.isWorkspaceActive(view)) return; // 工作区已关闭
        javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(100));
        delay.setOnFinished(e -> {
            if (!tabManager.isWorkspaceActive(view)) return;
            for (javafx.scene.control.Tab tab : view.codeTabPane().getTabs()) {
                if (tab instanceof CodeEditorTab codeTab
                        && codeTab.getOpenFile().fullPath().equals(fullPath)) {
                    var area = codeTab.getCodeArea();
                    if (area.getText() != null && !area.getText().isEmpty()) {
                        try {
                            codeTab.revealLine(lineNumber);
                            statusBar.setFilePath(I18nUtil.getString(
                                    "status.navigatedTo", fullPath, lineNumber));
                        } catch (Exception ignored) {
                        }
                        return;
                    }
                    navigateToLine(view, fullPath, lineNumber, retries + 1);
                    return;
                }
            }
        });
        delay.play();
    }

    /** 递归遍历文件树,收集所有 .class 节点的完整路径(用于快速打开对话框) */
    private void collectClassNames(TreeItem<FileTreeNode> item, java.util.List<String> result) {
        FileTreeNode data = item.getValue();
        if (data != null && data.isClassFile()) {
            result.add(data.getFullPath());
        }
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            collectClassNames(child, result);
        }
    }

    /** 计算与 ClassTabOpener 一致的 L2 缓存工作区键,确保跨组件缓存复用 */
    private static String workspaceKey(Workspace workspace) {
        File source = workspace.getSourceFile();
        long mtime = source.lastModified();
        long size = source.isFile() ? source.length() : 0L;
        return (source.getAbsolutePath() + "_" + mtime + "_" + size)
                .replace(':', '_').replace('\\', '_').replace('/', '_');
    }

    /**
     * 构建完整源码缓存供全文搜索使用,优先复用工作区级缓存和 L2 标签页缓存
     * 对未缓存的类逐类解编译(含 30 秒超时和 JD-Core 回退),完成后存入工作区缓存
     * 单次调用最多运行 120 秒,中间可被中断取消
     */
    private Map<String, String> buildFullSourceCache(WorkspaceView view,
                                                     Map<String, String> openTabSourceCache) {
        Map<String, String> safeOpenTabs = openTabSourceCache == null ? Map.of() : openTabSourceCache;
        Map<String, String> engineOptions = DecompilerOptions.forEngine(config, currentEngine);
        String cacheKey = currentEngine.name() + "|" + DecompilerOptions.hash(engineOptions);
        Map<String, String> cached = view.workspace().getSourceSearchCache(cacheKey);
        if (cached != null) {
            Map<String, String> merged = new LinkedHashMap<>(cached);
            merged.putAll(safeOpenTabs);
            return merged;
        }

        Map<String, String> fullSourceCache = new LinkedHashMap<>(safeOpenTabs);
        var index = awaitWorkspaceIndex(view.workspace());
        boolean indexReady = index != WorkspaceIndex.EMPTY;
        var context = DecompilerContext.fromWorkspaceIndex(index, engineOptions);
        var classes = index.classes();
        int total = classes.size();
        int processed = 0;
        long startTime = System.currentTimeMillis();
        boolean completed = indexReady;
        for (var cls : classes) {
            if (Thread.currentThread().isInterrupted()) {
                completed = false;
                break;
            }
            if (System.currentTimeMillis() - startTime > 120_000) { // 总计 2 分钟超时
                completed = false;
                break;
            }
            processed++;
            if (total > 100 && processed % 100 == 0) {
                int pct = processed * 100 / total;
                Platform.runLater(() -> statusBar.setTask(
                        I18nUtil.getString("task.indexing") + " (" + pct + "%)"));
            }
            if (!fullSourceCache.containsKey(cls.fullPath())) {
                if (Thread.currentThread().isInterrupted()) {
                    completed = false;
                    break;
                }
                // 优先查询 L2 内存缓存(复用已打开标签页的解编译结果,避免重复解编)
                String source = classTabOpener.getDecompileCache().get(
                        workspaceKey(view.workspace()), cls.internalName(),
                        currentEngine, DecompilerOptions.hash(engineOptions));
                if (source == null) {
                    // L2 miss: 带超时和 JD 回退的全量解编译
                    source = DecompilerRunner.decompileWithTimeout(
                            cls.fullPath(), cls.bytes(), currentEngine, context,
                            () -> !Thread.currentThread().isInterrupted());
                }
                if (!DecompilerRunner.isTransientFailureOutput(source)) {
                    fullSourceCache.put(cls.fullPath(), source);
                }
            }
        }
        fullSourceCache.values().removeIf(String::isEmpty);
        if (completed) {
            view.workspace().putSourceSearchCache(cacheKey, fullSourceCache);
        }
        return fullSourceCache;
    }

    /** 加载并打开文件 */
    private void loadFile(File file) {
        statusBar.setFilePath(I18nUtil.getString("status.loading", file.getAbsolutePath()));
        statusBar.setTask(I18nUtil.getString("task.loading"));

        WorkspaceLoader.loadAsync(file, config,
                workspace -> {
                    statusBar.clearTask();
                    tabManager.addWorkspaceTab(workspace,
                            (node, codeTabPane) -> classTabOpener.openClassTab(
                                    node, workspace, codeTabPane, currentEngine, lineNumbersEnabled),
                            (node, codeTabPane) -> classTabOpener.openTextFileTab(
                                    node, workspace, codeTabPane),
                            this::exportTreeItem,
                            node -> openFindUsagesForNode(workspace, node),
                            this::openSearchForPackage);
                    refreshToolbarState();
                },
                errorMsg -> {
                    statusBar.clearTask();
                    showError(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.load.error") + ": " + errorMsg);
                });
    }

    /** 显示导出成功对话框(无错误详情) */
    private void showExportDoneDialog(String title, String message, java.nio.file.Path outputPath) {
        showExportDoneDialog(title, message, outputPath, java.util.List.of());
    }

    /** 显示导出完成对话框(含错误详情列表和打开输出目录/复制详情按钮) */
    private void showExportDoneDialog(String title, String message, java.nio.file.Path outputPath,
                                      java.util.List<String> details) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.setOnShown(e -> {
            var win = alert.getDialogPane().getScene().getWindow();
            DefaultWindowTheme.applyWindowDarkMode(win);
            if (win instanceof javafx.stage.Stage s) setAlertIcon(s);
        });
        ButtonType openOutput = new ButtonType(I18nUtil.getString("dialog.export.openOutput"));
        alert.getButtonTypes().add(openOutput);
        if (details != null && !details.isEmpty()) {
            ButtonType copyDetails = new ButtonType(I18nUtil.getString("dialog.copyDetails"));
            alert.getButtonTypes().add(copyDetails);
            alert.showAndWait().ifPresent(button -> {
                if (button == openOutput) {
                    openOutputLocation(outputPath);
                } else if (button == copyDetails) {
                    copyToClipboard(String.join(System.lineSeparator(), details));
                }
            });
            return;
        }
        alert.showAndWait().ifPresent(button -> {
            if (button == openOutput) {
                openOutputLocation(outputPath);
            }
        });
    }

    /** 使用操作系统资源管理器打开导出目录(依赖 HostServices) */
    private void openOutputLocation(java.nio.file.Path outputPath) {
        if (outputPath == null) {
            return;
        }
        java.nio.file.Path target = java.nio.file.Files.isDirectory(outputPath)
                ? outputPath : outputPath.getParent();
        if (target != null && hostServices != null) {
            hostServices.showDocument(target.toUri().toString());
        }
    }

    /**
     * 确保工作区索引可用后执行回调若索引已就绪则同步回调；否则触发后台构建并异步等待
     * 工作区关闭后不再执行回调,避免操作已释放的 UI
     */
    private void withWorkspaceIndex(Workspace workspace, Consumer<WorkspaceIndex> onReady) {
        if (workspace == null || onReady == null) {
            return;
        }
        if (workspace.isIndexReady()) {
            onReady.accept(workspace.getIndex());
            return;
        }
        WorkspaceIndexService.ensureIndexingStarted(workspace);
        statusBar.setTask(I18nUtil.getString("task.indexing"));
        statusBar.setFilePath(I18nUtil.getString("status.indexing", workspace.getName()));
        workspace.getIndexFuture().whenComplete((index, error) -> Platform.runLater(() -> {
            statusBar.clearTask();
            if (error != null) {
                showError(I18nUtil.getString("dialog.error.title"),
                        I18nUtil.getString("dialog.index.failed", error.getMessage()));
                return;
            }
            if (tabManager.currentWorkspaceView() == null
                    || !workspace.equals(tabManager.currentWorkspaceView().workspace())) {
                statusBar.setFilePath(I18nUtil.getString("status.indexingComplete"));
                return;
            }
            onReady.accept(index);
        }));
    }

    /** 同步等待工作区索引构建完成(阻塞调用线程,仅后台线程使用) */
    private WorkspaceIndex awaitWorkspaceIndex(Workspace workspace) {
        if (workspace == null) {
            return WorkspaceIndex.EMPTY;
        }
        if (workspace.isIndexReady()) {
            return workspace.getIndex();
        }
        WorkspaceIndexService.ensureIndexingStarted(workspace);
        try {
            return workspace.getIndexFuture().get();
        } catch (Exception e) {
            return WorkspaceIndex.EMPTY;
        }
    }

    /** 将项目文件中保存的引擎名字符串还原为枚举值,非法值回退到默认引擎 */
    private static DecompilerTypeEnum parseEngine(String value, DecompilerTypeEnum fallback) {
        try {
            return DecompilerTypeEnum.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback == null ? DecompilerTypeEnum.VINEFLOWER : fallback;
        }
    }

    private void showInfo(String title, String message) {
        com.bingbaihanji.fxdecomplie.ui.DialogHelper.showInfo(stage, title, message);
    }

    /** 为文件树右键菜单触发的 Find Usages 打开对话框 */
    private void openFindUsagesForNode(Workspace workspace, FileTreeNode node) {
        if (workspace == null || node == null || !node.isClassFile()) {
            return;
        }
        withWorkspaceIndex(workspace, index ->
                FindUsageDialog.show(stage, index,
                        (fullPath, lineNumber) -> {
                            WorkspaceView view = tabManager.currentWorkspaceView();
                            if (view != null) {
                                openClassByPath(view, fullPath, lineNumber);
                            }
                        },
                        node.getFullPath().replace(".class", "")));
    }

    /** 提取节点所在的包路径作为搜索关键词并打开搜索对话框 */
    private void openSearchForPackage(FileTreeNode node) {
        String query = "";
        if (node != null) {
            String path = node.getFullPath();
            int slash = path.lastIndexOf('/');
            if (slash > 0) {
                query = path.substring(0, slash);
                statusBar.setFilePath(I18nUtil.getString(
                        "status.searchPackage", query));
            }
        }
        openSearch(query);
    }

    /** 弹出警告对话框 */
    private void showWarning(String title, String message) {
        com.bingbaihanji.fxdecomplie.ui.DialogHelper.showWarning(stage, title, message);
    }

    /** 弹出错误对话框 */
    private void showError(String title, String message) {
        com.bingbaihanji.fxdecomplie.ui.DialogHelper.showError(stage, title, message);
    }

    /** 为对话框/Stage 设置应用 Logo 图标 */
    private static void setAlertIcon(javafx.stage.Stage s) {
        try {
            var stream = MainWindow.class.getResourceAsStream("/icon/logo.png");
            if (stream != null) {
                s.getIcons().add(new javafx.scene.image.Image(stream));
            }
        } catch (Exception ignored) {
        }
    }

}
