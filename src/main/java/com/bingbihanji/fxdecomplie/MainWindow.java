package com.bingbihanji.fxdecomplie;

import com.bingbihanji.fxdecomplie.app.BackgroundTasks;
import com.bingbihanji.fxdecomplie.app.ClassTabOpener;
import com.bingbihanji.fxdecomplie.app.ProcessService;
import com.bingbihanji.fxdecomplie.config.AppConfig;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbihanji.fxdecomplie.di.ServiceRegistry;
import com.bingbihanji.fxdecomplie.io.ClassDiscoverer;
import com.bingbihanji.fxdecomplie.io.ExportService;
import com.bingbihanji.fxdecomplie.io.FileTreeBuilder;
import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import com.bingbihanji.fxdecomplie.model.Workspace;
import com.bingbihanji.fxdecomplie.ui.WorkspaceTabManager;
import com.bingbihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbihanji.fxdecomplie.utils.I18nUtil;
import com.bingbihanji.fxdecomplie.ui.code.StatusBar;
import com.bingbihanji.fxdecomplie.ui.menu.MainMenuBar;
import com.bingbihanji.fxdecomplie.ui.quickopen.QuickOpenDialog;
import com.bingbihanji.fxdecomplie.ui.settings.SettingsDialog;
import com.bingbihanji.fxdecomplie.ui.search.BytecodeSearchProvider;
import com.bingbihanji.fxdecomplie.ui.search.ClassSearchProvider;
import com.bingbihanji.fxdecomplie.ui.search.CodeSearchProvider;
import com.bingbihanji.fxdecomplie.ui.search.CommentSearchProvider;
import com.bingbihanji.fxdecomplie.ui.search.MethodSearchProvider;
import com.bingbihanji.fxdecomplie.ui.search.ResourceSearchProvider;
import com.bingbihanji.fxdecomplie.ui.search.SearchDialog;
import com.bingbihanji.fxdecomplie.ui.search.SearchService;
import com.bingbihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbihanji.fxdecomplie.ui.tree.FileTreeView;
import com.bingbihanji.fxdecomplie.ui.window.AppHeaderBar;
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

public class MainWindow implements MainMenuBar.Actions {

    /** 应用配置 */
    private final AppConfig config;
    /** 服务注册表 */
    private final ServiceRegistry serviceRegistry;
    /** 编辑器主题数据 */
    private final VsCodeThemeLoader.ThemeData editorTheme;
    /** 是否使用自定义标题栏 */
    private final boolean useHeaderBar;

    /** 主窗口 Stage */
    private Stage stage;
    /** 状态栏 */
    private StatusBar statusBar;
    /** 外层标签页（JAR/ZIP/目录级别） */
    private TabPane outerTabPane;
    /** 工作区标签页管理器 */
    private WorkspaceTabManager tabManager;
    /** 类文件反编译/标签页打开器 */
    private ClassTabOpener classTabOpener;
    /** 当前选择的反编译引擎 */
    private DecompilerTypeEnum currentEngine = DecompilerTypeEnum.VINEFLOWER;
    /** 是否显示行号 */
    private boolean lineNumbersEnabled;

    public MainWindow(AppConfig config) {
        this(config, new ServiceRegistry(), false);
    }

    public MainWindow(AppConfig config, ServiceRegistry serviceRegistry, boolean useHeaderBar) {
        this.config = config;
        this.serviceRegistry = serviceRegistry;
        this.useHeaderBar = useHeaderBar;
        this.editorTheme = AppTheme.loadEditorTheme(config);
        this.lineNumbersEnabled = config.decompiler.lineNumbersEnabled;
    }

    /** 显示主窗口 */
    public void show(Stage stage) {
        this.stage = stage;
        this.currentEngine = parseDecompiler(config.decompiler.defaultEngine);

        outerTabPane = new TabPane();
        outerTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        statusBar = new StatusBar();

        tabManager = new WorkspaceTabManager(outerTabPane, statusBar);
        classTabOpener = new ClassTabOpener(config, editorTheme, statusBar);
        // L2 缓存跨工作区共享，不清空以提升重复打开性能
        tabManager.showWelcomeTabIfEmpty();

        outerTabPane.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTab, newTab) -> tabManager.updateStatusForWorkspace(newTab)
        );

        MainMenuBar menuBar = new MainMenuBar(this, currentEngine);
        VBox topBars = new VBox();
        if (useHeaderBar) {
            topBars.getChildren().add(AppHeaderBar.create(stage, "FxDecompiler", menuBar));
        } else {
            topBars.getChildren().add(menuBar);
        }

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
            for (java.io.File f : files) {
                loadFile(f);
            }
            event.setDropCompleted(true);
            event.consume();
        });

        root.setTop(topBars);
        root.setCenter(outerTabPane);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, config.window.width, config.window.height);
        scene.setFill(Color.web("#1e1e1e"));
        scene.getStylesheets().add(AppTheme.darkStylesheet());

        stage.setTitle("FxDecompiler");
        stage.setScene(scene);
        stage.show();
    }

    /** 打开 JAR/ZIP/Class 文件 */
    @Override
    public void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("打开文件");
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
        chooser.setTitle("打开目录");
        File dir = chooser.showDialog(stage);
        if (dir != null) {
            loadFile(dir);
        }
    }

    /** 关闭当前工作区标签页 */
    @Override
    public void closeCurrentWorkspace() {
        Tab selected = outerTabPane.getSelectionModel().getSelectedItem();
        if (selected != null && tabManager.getWorkspaceViews().containsKey(selected)) {
            tabManager.closeWorkspaceTab(selected);
        }
    }

    /** 关闭其他工作区标签页 */
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
        chooser.setTitle("保存文件");
        chooser.setInitialFileName(codeTab.getOpenFile().getClassName() + ".java");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java Source", "*.java"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        try {
            ExportService.exportCurrentCode(codeTab.getOpenFile().getSourceCode(), file.toPath());
            statusBar.setFilePath("已保存: " + file.getAbsolutePath());
        } catch (IOException ex) {
            showError(I18nUtil.getString("dialog.error.title"), "保存失败: " + ex.getMessage());
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

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择导出目录");
        File dir = chooser.showDialog(stage);
        if (dir == null) {
            return;
        }

        BackgroundTasks.run("ExportAll", () -> {
            try {
                ExportService.exportAllToDir(view.workspace().getTreeRoot(), currentEngine,
                        dir.toPath(), pct -> Platform.runLater(() ->
                                statusBar.setFilePath(I18nUtil.getString("status.exporting", pct))));
                Platform.runLater(() -> {
                    statusBar.setFilePath(I18nUtil.getString("status.exportDone", dir.getAbsolutePath()));
                    showInfo("导出成功", "导出完成到: " + dir.getAbsolutePath());
                });
            } catch (IOException ex) {
                Platform.runLater(() -> showError(I18nUtil.getString("dialog.error.title"), "导出失败: " + ex.getMessage()));
            }
        });
    }

    /** 退出应用 */
    @Override
    public void exit() {
        DecompilerFactory.cleanup();
        stage.close();
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
        config.decompiler.lineNumbersEnabled = lineNumbersEnabled;
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
        config.decompiler.defaultEngine = engine.name();
        statusBar.setFilePath("当前引擎: " + engine.name());

        WorkspaceView view = tabManager.currentWorkspaceView();
        CodeEditorTab currentTab = tabManager.currentCodeTab();
        if (view != null && currentTab != null) {
            classTabOpener.cancelCurrentTask();
            classTabOpener.refreshCurrentClassTab(
                    view.workspace(), view.codeTabPane(), currentTab, engine, lineNumbersEnabled);
        }
    }

    /** 打开搜索对话框 */
    @Override
    public void openSearch() {
        var view = tabManager.currentWorkspaceView();
        if (view == null) { showWarning("提示", "请先打开文件"); return; }

        // Build source cache from open tabs
        java.util.Map<String, String> sourceCache = new java.util.HashMap<>();
        for (javafx.scene.control.Tab tab : view.codeTabPane().getTabs()) {
            if (tab instanceof CodeEditorTab codeTab) {
                sourceCache.put(codeTab.getOpenFile().getFullPath(),
                        codeTab.getOpenFile().getSourceCode());
            }
        }

        // Build class name list for ClassSearchProvider
        java.util.List<String> classNames = new java.util.ArrayList<>();
        collectClassNames(view.workspace().getTreeRoot(), classNames);

        // Build resource cache for ResourceSearchProvider
        java.util.Map<String, byte[]> resourceCache = new java.util.HashMap<>();
        collectResourceBytes(view.workspace().getTreeRoot(), resourceCache);

        // Build bytecode cache for BytecodeSearchProvider (javap text)
        java.util.Map<String, String> bytecodeCache = new java.util.HashMap<>();
        // TODO: populate with javap-style output when bytecode view is available

        // Create SearchService with all providers
        SearchService searchService = new SearchService();
        searchService.addProvider(new ClassSearchProvider(classNames));
        searchService.addProvider(new MethodSearchProvider());
        searchService.addProvider(new CodeSearchProvider());
        searchService.addProvider(new CommentSearchProvider());
        searchService.addProvider(new ResourceSearchProvider(resourceCache));
        searchService.addProvider(new BytecodeSearchProvider(bytecodeCache));

        SearchDialog.show(stage, searchService, sourceCache, (fullPath, lineNumber) -> {
            openClassByPath(view, fullPath, lineNumber);
        });
    }

    /** 快速打开类 */
    @Override
    public void quickOpenClass() {
        var view = tabManager.currentWorkspaceView();
        if (view == null) { showWarning(I18nUtil.getString("dialog.export.title"), "请先打开文件"); return; }
        java.util.List<String> classNames = new java.util.ArrayList<>();
        collectClassNames(view.workspace().getTreeRoot(), classNames);
        QuickOpenDialog.show(stage, classNames, fullPath -> {
            FileTreeNode node = classTabOpener.findNodeByPath(view.workspace().getTreeRoot(), fullPath);
            if (node != null) {
                classTabOpener.openClassTab(node, view.workspace(), view.codeTabPane(),
                        currentEngine, lineNumbersEnabled);
            }
        });
    }

    /** 打开最近文件 */
    @Override
    public void openRecentFile(String path) {
        loadFile(new java.io.File(path));
    }

    /** 获取最近文件列表 */
    @Override
    public java.util.List<String> getRecentFiles() {
        return config.recentFiles;
    }

    /** 打开新窗口 */
    @Override
    public void openNewWindow() {
        var view = tabManager.currentWorkspaceView();
        if (view != null) {
            ProcessService.launchNewInstance(view.workspace().getSourceFile().getAbsolutePath());
        }
    }

    /** 打开设置对话框 */
    @Override
    public void openSettings() {
        SettingsDialog.show(stage, config, this::applySettings);
    }

    private void applySettings(AppConfig updated) {
        if (!updated.decompiler.defaultEngine.equals(config.decompiler.defaultEngine)) {
            selectEngine(parseDecompiler(updated.decompiler.defaultEngine));
        }
        lineNumbersEnabled = updated.decompiler.lineNumbersEnabled;
    }

    // helpers
    private void openClassByPath(WorkspaceView view, String fullPath, int lineNumber) {
        FileTreeNode node = classTabOpener.findNodeByPath(view.workspace().getTreeRoot(), fullPath);
        if (node != null) {
            classTabOpener.openClassTab(node, view.workspace(), view.codeTabPane(),
                    currentEngine, lineNumbersEnabled);
            // scroll to line after a short delay for decompilation
            javafx.application.Platform.runLater(() -> {
                for (javafx.scene.control.Tab tab : view.codeTabPane().getTabs()) {
                    if (tab instanceof CodeEditorTab codeTab
                            && codeTab.getOpenFile().getFullPath().equals(fullPath)) {
                        var area = codeTab.getCodeArea();
                        area.moveDocumentStart();
                        for (int i = 1; i < lineNumber; i++) area.moveParagraphDown();
                        area.requestFocus();
                    }
                }
            });
        }
    }

    private void collectClassNames(TreeItem<FileTreeNode> item, java.util.List<String> result) {
        FileTreeNode data = item.getValue();
        if (data != null && data.isClassFile()) {
            result.add(data.getFullPath());
        }
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            collectClassNames(child, result);
        }
    }

    private void collectResourceBytes(TreeItem<FileTreeNode> item,
                                       java.util.Map<String, byte[]> result) {
        FileTreeNode data = item.getValue();
        if (data != null) {
            byte[] bytes = data.getCachedBytes();
            if (bytes != null && bytes.length > 0) {
                result.putIfAbsent(data.getFullPath(), bytes);
            }
        }
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            collectResourceBytes(child, result);
        }
    }

    /** 加载并打开文件 */
    private void loadFile(File file) {
        String name = file.getName();
        boolean isArchive = isArchive(file);
        statusBar.setFilePath(I18nUtil.getString("status.loading", file.getAbsolutePath()));

        BackgroundTasks.run("FileLoader-" + name, () -> {
            try {
                var entries = ClassDiscoverer.discover(file);
                TreeItem<FileTreeNode> treeRoot = FileTreeBuilder.build(name, entries);
                Workspace workspace = new Workspace(name, file, treeRoot, isArchive);
                Platform.runLater(() -> tabManager.addWorkspaceTab(workspace,
                        (node, codeTabPane) -> classTabOpener.openClassTab(
                                node, workspace, codeTabPane, currentEngine, lineNumbersEnabled),
                        (node, codeTabPane) -> classTabOpener.openTextFileTab(
                                node, workspace, codeTabPane)));
                config.addRecentFile(file.getAbsolutePath());
            } catch (IOException e) {
                Platform.runLater(() -> showError(I18nUtil.getString("dialog.error.title"), I18nUtil.getString("dialog.load.error") + ": " + e.getMessage()));
            }
        });
    }

    /** 解析反编译器名称字符串为枚举 */
    private DecompilerTypeEnum parseDecompiler(String value) {
        try {
            return DecompilerTypeEnum.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return DecompilerTypeEnum.VINEFLOWER;
        }
    }

    /** 判断文件是否为归档文件（JAR/ZIP） */
    private boolean isArchive(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    /** 显示信息弹窗 */
    private void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    /** 显示警告弹窗 */
    private void showWarning(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message);
    }

    /** 显示错误弹窗 */
    private void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    /** 显示弹窗对话框 */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.initOwner(stage);
        alert.showAndWait();
    }

    /**
     * 工作区视图，关联 workspace、文件树、代码标签面板和工作区标签页。
     *
     * @param workspace     工作区
     * @param treeView      文件树视图
     * @param codeTabPane   代码标签页面板
     * @param workspaceTab  工作区标签页
     */
    public record WorkspaceView(
            Workspace workspace,
            FileTreeView treeView,
            TabPane codeTabPane,
            Tab workspaceTab
    ) {
    }
}
