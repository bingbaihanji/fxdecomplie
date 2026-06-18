package com.bingbihanji.fxdecomplie;

import com.bingbihanji.fxdecomplie.config.AppConfig;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbihanji.fxdecomplie.model.*;
import com.bingbihanji.fxdecomplie.service.*;
import com.bingbihanji.fxdecomplie.ui.WorkspaceTabManager;
import com.bingbihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbihanji.fxdecomplie.ui.code.StatusBar;
import com.bingbihanji.fxdecomplie.ui.export.ExportDialog;
import com.bingbihanji.fxdecomplie.ui.menu.MainMenuBar;
import com.bingbihanji.fxdecomplie.ui.quickopen.QuickOpenDialog;
import com.bingbihanji.fxdecomplie.ui.search.*;
import com.bingbihanji.fxdecomplie.ui.settings.SettingsDialog;
import com.bingbihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbihanji.fxdecomplie.ui.tree.FileTreeView;
import com.bingbihanji.fxdecomplie.ui.usage.FindUsageDialog;
import com.bingbihanji.fxdecomplie.ui.window.AppHeaderBar;
import com.bingbihanji.fxdecomplie.utils.I18nUtil;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central controller of the FxDecompiler application window.
 * Manages the outer workspace TabPane, inner code tabs, menu bar actions,
 * file loading, search, export, and all user interactions.
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
        this(config, false);
    }

    public MainWindow(AppConfig config, boolean useHeaderBar) {
        this.config = config;
        this.useHeaderBar = useHeaderBar;
        this.editorTheme = AppTheme.loadEditorTheme(config);
        this.lineNumbersEnabled = config.decompiler.lineNumbersEnabled;
    }

    /** 显示主窗口 */
    public void show(Stage stage) {
        this.stage = stage;
        this.currentEngine = config.decompiler.defaultEngine;

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
            currentEngine = DecompilerTypeEnum.valueOf(project.engine());
            config.decompiler.defaultEngine = currentEngine;
            if (!project.exportPath().isBlank()) {
                config.export.lastPath = project.exportPath();
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
                    1, currentEngine.name(), inputPaths, selectedPath, config.export.lastPath));
            statusBar.setFilePath(I18nUtil.getString("project.saved", file.getAbsolutePath()));
        } catch (IOException ex) {
            showError(I18nUtil.getString("dialog.error.title"),
                    I18nUtil.getString("project.save.failed", ex.getMessage()));
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
        chooser.setTitle(I18nUtil.getString("dialog.saveFile.title"));
        chooser.setInitialFileName(codeTab.getOpenFile().getClassName() + ".java");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java Source", "*.java"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        try {
            ExportService.exportCurrentCode(codeTab.getOpenFile().getSourceCode(), file.toPath());
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
        doExport(view.workspace().getTreeRoot(), view.workspace().getIndex());
    }

    private void exportTreeItem(TreeItem<FileTreeNode> rootItem) {
        if (rootItem == null || rootItem.getValue() == null) {
            return;
        }
        doExport(rootItem, WorkspaceIndex.build(rootItem));
    }

    private void doExport(javafx.scene.control.TreeItem<FileTreeNode> rootItem,
                          com.bingbihanji.fxdecomplie.model.WorkspaceIndex index) {
        var configOpt = ExportDialog.show(stage, config, currentEngine);
        if (configOpt.isEmpty()) {
            return;
        }
        ExportConfig exportConfig = configOpt.get();
        persistExportConfig(exportConfig);

        ExportDialog.ProgressHandle progressHandle = ExportDialog.showProgress(stage);
        final Future<?>[] exportTask = new Future<?>[1];
        java.util.concurrent.atomic.AtomicBoolean exportCanceled = new java.util.concurrent.atomic.AtomicBoolean(false);
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
                    showError(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.export.failed", ex.getMessage()));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progressHandle.close();
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
        config.decompiler.defaultEngine = engine;
        statusBar.setFilePath(I18nUtil.getString("status.currentEngine", engine.name()));

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
        if (view == null) {
            showWarning(I18nUtil.getString("search.title"),
                    I18nUtil.getString("dialog.needOpenFile"));
            return;
        }
        var index = view.workspace().getIndex();

        // Build source cache from open tabs
        java.util.Map<String, String> sourceCache = new java.util.HashMap<>();
        for (javafx.scene.control.Tab tab : view.codeTabPane().getTabs()) {
            if (tab instanceof CodeEditorTab codeTab) {
                sourceCache.put(codeTab.getOpenFile().getFullPath(),
                        codeTab.getOpenFile().getSourceCode());
            }
        }

        // Create SearchService with all providers
        SearchService searchService = new SearchService();
        searchService.addProvider(new ClassSearchProvider(index.classPaths()));
        searchService.addProvider(new IndexedMemberSearchProvider(index));
        searchService.addProvider(new MethodSearchProvider());
        searchService.addProvider(new CodeSearchProvider());
        searchService.addProvider(new CommentSearchProvider());
        searchService.addProvider(new ResourceSearchProvider(index.resourceBytesByPath()));
        searchService.addProvider(new BytecodeSearchProvider(index.bytecodeTextByPath()));

        SearchDialog.show(stage, searchService, sourceCache,
                () -> buildFullSourceCache(view, sourceCache),
                config.search.fullSourceSearch, config.search.resultLimit,
                (fullPath, lineNumber) -> openClassByPath(view, fullPath, lineNumber));
    }

    /** 查找当前工作区内的类/方法/字段使用。 */
    @Override
    public void openFindUsages() {
        var view = tabManager.currentWorkspaceView();
        if (view == null) {
            showWarning(I18nUtil.getString("usage.title"), I18nUtil.getString("dialog.export.noworkspace"));
            return;
        }
        FindUsageDialog.show(stage, view.workspace().getIndex(),
                (fullPath, lineNumber) -> openClassByPath(view, fullPath, lineNumber));
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

    /** 启动参数或外部入口打开文件/目录。 */
    public void openInitialFile(File file) {
        if (file != null && file.exists()) {
            loadFile(file);
        }
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

    /** 显示关于对话框 */
    @Override
    public void about() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("关于 FxDecompiler");
        alert.setHeaderText("FxDecompiler");

        javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink("www.bingbaihanji.com");
        link.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://www.bingbaihanji.com"));
            } catch (java.io.IOException ignored) {
            }
        });

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8,
                new javafx.scene.control.Label("基于 JavaFX 的 Java 反编译工具。"),
                new javafx.scene.control.Label("支持 Procyon / CFR / Vineflower / JD-Core 四种引擎。"),
                new javafx.scene.control.Label(""),
                new javafx.scene.control.Label("开发者: 冰白寒祭"),
                new javafx.scene.control.Label("开发日期: 2026-06-18"),
                new javafx.scene.control.Label("网站: "),
                link);
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    /** 打开设置对话框 */
    @Override
    public void openSettings() {
        SettingsDialog.show(stage, config, this::applySettings);
    }

    private void applySettings(AppConfig updated) {
        DecompilerTypeEnum configuredEngine = updated.decompiler.defaultEngine;
        if (configuredEngine != currentEngine) {
            selectEngine(configuredEngine);
        }
        lineNumbersEnabled = updated.decompiler.lineNumbersEnabled;
        tabManager.getWorkspaceViews().values().forEach(view ->
                view.codeTabPane().getTabs().stream()
                        .filter(CodeEditorTab.class::isInstance)
                        .map(CodeEditorTab.class::cast)
                        .forEach(tab -> tab.setLineNumbersEnabled(lineNumbersEnabled))
        );
    }

    private void persistExportConfig(ExportConfig exportConfig) {
        config.export.defaultEngine = exportConfig.engine().name();
        config.export.defaultFormat = exportConfig.format().name();
        config.export.conflictPolicy = exportConfig.conflictPolicy().name();
        config.export.exportResources = exportConfig.exportResources();
        config.export.lastPath = exportConfig.outputPath().toString();
        config.save();
    }

    private void showExportResult(ExportConfig exportConfig, ExportResult result) {
        if (!result.hasErrors()) {
            showInfo(I18nUtil.getString("dialog.export.success.title"),
                    I18nUtil.getString("dialog.export.success.message",
                            result.successCount(), exportConfig.outputPath()));
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
        showWarning(I18nUtil.getString("dialog.export.partial.title"), message.toString());
    }

    // helpers
    private void openClassByPath(WorkspaceView view, String fullPath, int lineNumber) {
        FileTreeNode node = classTabOpener.findNodeByPath(view.workspace().getTreeRoot(), fullPath);
        if (node != null) {
            classTabOpener.openClassTab(node, view.workspace(), view.codeTabPane(),
                    currentEngine, lineNumbersEnabled);
            // Navigate to the target line after decompilation completes
            navigateToLine(view, fullPath, lineNumber, 0);
        }
    }

    private void navigateToLine(WorkspaceView view, String fullPath, int lineNumber, int retries) {
        if (retries > 20) return; // max ~2 seconds
        javafx.application.Platform.runLater(() -> {
            for (javafx.scene.control.Tab tab : view.codeTabPane().getTabs()) {
                if (tab instanceof CodeEditorTab codeTab
                        && codeTab.getOpenFile().getFullPath().equals(fullPath)) {
                    var area = codeTab.getCodeArea();
                    if (area.getText() != null && !area.getText().isEmpty()) {
                        // Navigate to line
                        area.moveDocumentStart();
                        for (int i = 1; i < lineNumber; i++) area.moveParagraphDown();
                        area.requestFocus();
                        return;
                    }
                    // Tab exists but decompilation not done yet — retry
                    navigateToLine(view, fullPath, lineNumber, retries + 1);
                    return;
                }
            }
        });
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

    private Map<String, String> buildFullSourceCache(WorkspaceView view,
                                                     Map<String, String> openTabSourceCache) {
        Map<String, String> fullSourceCache = new LinkedHashMap<>(openTabSourceCache);
        var decompiler = DecompilerFactory.getDecompiler(currentEngine);
        for (var cls : view.workspace().getIndex().classes()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            fullSourceCache.computeIfAbsent(cls.fullPath(),
                    path -> {
                        if (Thread.currentThread().isInterrupted()) {
                            return "";
                        }
                        return decompiler.decompile(cls.fullPath(), cls.bytes(),
                                com.bingbihanji.fxdecomplie.decompiler.DecompilerContext
                                        .fromWorkspaceIndex(view.workspace().getIndex()));
                    });
        }
        fullSourceCache.values().removeIf(String::isEmpty);
        return fullSourceCache;
    }

    /** 加载并打开文件 */
    private void loadFile(File file) {
        String name = file.getName();
        boolean isArchive = isArchive(file);
        statusBar.setFilePath(I18nUtil.getString("status.loading", file.getAbsolutePath()));

        // ---- Pipeline: discover -> build tree -> create workspace -> add tab (all on background thread) ----
        BackgroundTasks.run("FileLoader-" + name, () -> {
            try {
                // ---- Step 1: scan JAR/ZIP/directory for all .class and resource entries ----
                var entries = ClassDiscoverer.discover(file);
                // ---- Step 2: build hierarchical file tree with bytecode caching ----
                TreeItem<FileTreeNode> treeRoot = FileTreeBuilder.build(name, entries);
                // ---- Step 3: create workspace model with index ----
                Workspace workspace = new Workspace(name, file, treeRoot, isArchive,
                        WorkspaceIndex.EMPTY);
                // ---- Step 4: create UI (file tree + code tabs) on JavaFX thread ----
                Platform.runLater(() -> tabManager.addWorkspaceTab(workspace,
                        (node, codeTabPane) -> classTabOpener.openClassTab(
                                node, workspace, codeTabPane, currentEngine, lineNumbersEnabled),
                        (node, codeTabPane) -> classTabOpener.openTextFileTab(
                                node, workspace, codeTabPane),
                        this::exportTreeItem));
                config.addRecentFile(file.getAbsolutePath());
                // Async: build full index after UI is shown
                BackgroundTasks.run("Index-" + name, () -> {
                    WorkspaceIndex fullIndex = WorkspaceIndex.build(treeRoot);
                    workspace.setIndex(fullIndex);
                });
            } catch (IOException e) {
                Platform.runLater(() -> showError(I18nUtil.getString("dialog.error.title"), I18nUtil.getString("dialog.load.error") + ": " + e.getMessage()));
            }
        });
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
