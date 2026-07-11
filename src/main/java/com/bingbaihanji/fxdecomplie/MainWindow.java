package com.bingbaihanji.fxdecomplie;


import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.*;
import com.bingbaihanji.fxdecomplie.service.*;
import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceTabManager;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceView;
import com.bingbaihanji.fxdecomplie.ui.code.*;
import com.bingbaihanji.fxdecomplie.ui.comment.CommentDialog;
import com.bingbaihanji.fxdecomplie.ui.export.ExportDialog;
import com.bingbaihanji.fxdecomplie.ui.graph.GraphDialog;
import com.bingbaihanji.fxdecomplie.ui.graph.GraphService;
import com.bingbaihanji.fxdecomplie.ui.inheritance.InheritanceService;
import com.bingbaihanji.fxdecomplie.ui.menu.MainMenuBar;
import com.bingbaihanji.fxdecomplie.ui.quickopen.QuickOpenDialog;
import com.bingbaihanji.fxdecomplie.ui.search.*;
import com.bingbaihanji.fxdecomplie.ui.settings.SettingsDialog;
import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.fxdecomplie.ui.toolbar.MainToolBar;
import com.bingbaihanji.fxdecomplie.ui.usage.FindUsageDialog;
import com.bingbaihanji.fxdecomplie.ui.window.AppHeaderBar;
import com.bingbaihanji.fxdecomplie.util.ClassNameUtil;
import com.bingbaihanji.fxdecomplie.util.text.JavaSourceAnalyzer;
import com.bingbaihanji.util.I18nUtil;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FxDecompiler 应用窗口的中央控制器
 * 管理外层工作区 TabPane、内部代码标签页、菜单栏操作、
 * 文件加载、搜索、导出及所有用户交互
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class MainWindow implements MainMenuBar.Actions, CodeActionHandler {

    private static final Logger log = LoggerFactory.getLogger(MainWindow.class);

    /** 应用全局配置(窗口、主题、引擎偏好等) */
    private final AppConfig config;
    /** 是否使用自定义标题栏(Windows 原生窗口控制) */
    private final boolean useHeaderBar;
    /** HostServices,用于打开外部浏览器链接 */
    private final HostServices hostServices;
    /** 编辑器当前主题数据(语法高亮颜色等) */
    private VsCodeThemeLoader.ThemeData editorTheme;
    /** 主窗口 Stage 引用 */
    private Stage stage;
    /** 底部状态栏 */
    private StatusBar statusBar;
    /** 外层 TabPane(JAR/ZIP/目录级别标签页) */
    private TabPane outerTabPane;
    /** 工作区标签页管理器 */
    private WorkspaceTabManager tabManager;
    /** 类文件反编译及标签页打开器(含 L2 缓存) */
    private ClassTabOpener classTabOpener;
    /** 当前选择的反编译引擎 */
    private DecompilerTypeEnum currentEngine = DecompilerTypeEnum.VINEFLOWER;
    /** 是否显示行号 */
    private boolean lineNumbersEnabled;
    /** 顶部工具栏 */
    private MainToolBar toolBar;
    /** 主菜单栏 */
    private MainMenuBar menuBar;
    /** 编辑器动作控制器（剪贴板/缩放/行号/工具窗口） */
    private final EditorActionsController editorActions = new EditorActionsController(this);

    // --- 供控制器访问共享状态的包级私有访问器（Mediator 模式）---
    AppConfig config() { return config; }

    WorkspaceTabManager tabManager() { return tabManager; }

    MainToolBar toolBar() { return toolBar; }

    boolean lineNumbersEnabled() { return lineNumbersEnabled; }

    void setLineNumbersEnabled(boolean enabled) { this.lineNumbersEnabled = enabled; }

    /** 使用全局配置构造主窗口 */
    public MainWindow(AppConfig config) {
        this(config, false, null);
    }

    /** 使用配置和标题栏选项构造主窗口(不含 HostServices) */
    public MainWindow(AppConfig config, boolean useHeaderBar) {
        this(config, useHeaderBar, null);
    }

    /** 完整构造主窗口,初始化配置、主题、引擎和行号等设置 */
    public MainWindow(AppConfig config, boolean useHeaderBar, HostServices hostServices) {
        this.config = config;
        this.useHeaderBar = useHeaderBar;
        this.hostServices = hostServices;
        this.editorTheme = AppTheme.loadEditorTheme(config);
        this.lineNumbersEnabled = config.decompiler().lineNumbersEnabled();
    }

    /** 复制字符串到系统剪贴板 */
    private static void copyToClipboard(String value) {
        EditorActionsController.copyToClipboard(value);
    }

    /** 将点分隔的类名转换为内部类路径(首段大写后用 $ 替代 .) */
    private static String toInnerClassPath(String className) {
        return JavaSourceAnalyzer.toInnerClassPath(className);
    }

    /** 判断树节点名是否与简单类令牌匹配(含内部类 $ 后缀匹配) */
    private static boolean matchesSimpleClassName(FileTreeNode node, String simpleToken,
                                                  String expectedClassFile) {
        return JavaSourceAnalyzer.matchesSimpleClassName(node, simpleToken, expectedClassFile);
    }

    /** 在源码中查找匹配 token 的声明行,优先返回离点击行最近的匹配 */
    private static int findDeclarationLine(String sourceCode, String token, int clickedLine) {
        return JavaSourceAnalyzer.findDeclarationLine(sourceCode, token, clickedLine);
    }

    /** 判断源码行是否看起来是声明行(类/接口/枚举/方法/字段声明) */
    private static boolean looksLikeDeclarationLine(String line, String simpleToken) {
        return JavaSourceAnalyzer.looksLikeDeclarationLine(line, simpleToken);
    }

    /** 去除行尾的 // 注释部分,返回纯代码(正确处理字符串字面量内的 // ) */
    private static String stripLineComment(String line) {
        return JavaSourceAnalyzer.stripLineComment(line);
    }

    /**
     * 使用 indexOf 快速检查行中是否包含指定关键字后跟 token (替代正则编译)
     *
     * @param line     被检查的行
     * @param token    要匹配的标识符
     * @param keywords 关键字数组(含尾部空格,如 "class ")
     * @return true 若行中某关键字后(经空白分隔)紧跟 token 且两侧满足词边界
     */
    private static boolean containsKeywordBeforeToken(String line, String token, String... keywords) {
        return JavaSourceAnalyzer.containsKeywordBeforeToken(line, token, keywords);
    }

    /** 清理声明 token,去除首尾非 Java 名称/路径字符 */
    private static String sanitizeDeclarationToken(String token) {
        return JavaSourceAnalyzer.sanitizeDeclarationToken(token);
    }

    /** @return true 若字符是声明 token 的有效字符(Java 标识符、点号、美元符、斜杠) */
    private static boolean isDeclarationTokenChar(char ch) {
        return JavaSourceAnalyzer.isDeclarationTokenChar(ch);
    }

    /** 从限定名中提取简单名(最后一段) */
    private static String tokenSimpleName(String token) {
        return JavaSourceAnalyzer.tokenSimpleName(token);
    }

    /** @return true 若 token 首字母大写、下划线或美元符号开头(相对类引用) */
    private static boolean isRelativeClassToken(String token) {
        return JavaSourceAnalyzer.isRelativeClassToken(token);
    }

    /** @return true 若 token 应优先作为类引用导航(含点号或首字母大写) */
    private static boolean shouldPreferClassNavigation(String token) {
        return JavaSourceAnalyzer.shouldPreferClassNavigation(token);
    }

    /** @return true 若 token 应在工作区中进行类查找(限定名、相对类引用或混淆短名) */
    private static boolean shouldSearchWorkspaceForClassToken(String token) {
        return JavaSourceAnalyzer.shouldSearchWorkspaceForClassToken(token);
    }

    /** @return true 若 token 是可能的混淆类名(短名、非常见词汇、仅含合法字符) */
    private static boolean isShortObfuscatedClassToken(String token) {
        return JavaSourceAnalyzer.isShortObfuscatedClassToken(token);
    }

    /** 判断指定行是否看起来使用了 token 指代的类(new/extends/import/类型声明等语境) */
    private static boolean looksLikeClassUsageAtLine(String sourceCode, int lineNumber, String token) {
        return JavaSourceAnalyzer.looksLikeClassUsageAtLine(sourceCode, lineNumber, token);
    }

    /** @return true 若重命名条目允许回退到可见标识符替换(类名或通用标识符类型) */
    private static boolean allowVisibleIdentifierFallback(
            com.bingbaihanji.fxdecomplie.rename.RenameEntry entry) {
        if (entry == null || entry.type() == null) {
            return false;
        }
        return com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS.equals(entry.type())
                || com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_IDENTIFIER.equals(entry.type());
    }

    /** @return true 若重命名条目是类级别的重命名 */
    private static boolean isClassRenameEntry(
            com.bingbaihanji.fxdecomplie.rename.RenameEntry entry) {
        return entry != null
                && com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS.equals(entry.type());
    }

    /** @return true 若两个内部类名处于同一包下 */
    private static boolean samePackage(String leftInternalName, String rightInternalName) {
        return JavaSourceAnalyzer.samePackage(leftInternalName, rightInternalName);
    }

    /** 标准化内部类名(委托 ClassNameUtil) */
    private static String normalizeInternalClassName(String className) {
        return JavaSourceAnalyzer.normalizeInternalClassName(className);
    }

    /** 获取内部类名的包名部分(委托 ClassNameUtil) */
    private static String packageName(String internalName) {
        return JavaSourceAnalyzer.packageName(internalName);
    }

    /** 委托 ClassTabOpener 计算 L2 缓存工作区键,确保跨组件缓存复用 */
    private static String workspaceKey(Workspace workspace) {
        return com.bingbaihanji.fxdecomplie.ui.code.ClassTabOpener.computeWorkspaceKey(workspace);
    }

    /** 将项目文件中保存的引擎名字符串还原为枚举值,非法值回退到默认引擎 */
    private static DecompilerTypeEnum parseEngine(String value, DecompilerTypeEnum fallback) {
        try {
            return DecompilerTypeEnum.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback == null ? DecompilerTypeEnum.VINEFLOWER : fallback;
        }
    }

    /** 判断文件是否为可加载类型(JAR/ZIP/WAR/Class 及目录) */
    private static boolean isSupportedLoadFile(File file) {
        if (file.isDirectory()) {
            return true;
        }
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".zip")
                || name.endsWith(".war") || name.endsWith(".class");
    }

    /** 将 TextPos(行号+列偏移)转换为文本在原始字符串中的平坦偏移量 */
    private static int flatOffset(String text,
                                  jfx.incubator.scene.control.richtext.TextPos pos) {
        return JavaSourceAnalyzer.flatOffset(text, pos);
    }

    /** 判断 offset 位置附近的标识符是否是类/接口/枚举声明的类型名 */
    private static boolean isDeclaredTypeNameAt(String sourceCode, int offset, String caretName) {
        return JavaSourceAnalyzer.isDeclaredTypeNameAt(sourceCode, offset, caretName);
    }

    /** 获取类的叶子名(去除包名后的最后一段,跳过内部类 $ 前缀) */
    private static String classLeafName(String className) {
        return JavaSourceAnalyzer.classLeafName(className);
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
        tabManager.setOnActiveTabChange(this::refreshToolbarState);
        tabManager.setDragDropConfig(config, editorTheme);
        tabManager.setWelcomeActions(new WorkspaceTabManager.WelcomeActions(
                this::openFile,
                this::openDirectory,
                this::openProject,
                config.recentFiles(),
                this::openRecentFile));
        classTabOpener = new ClassTabOpener(config, editorTheme, statusBar);
        classTabOpener.setCodeActionHandler(this);
        classTabOpener.setOnTabReady(this::refreshToolbarState);
        // L2 缓存跨工作区共享,不清空以提升重复打开性能
        tabManager.showWelcomeTabIfEmpty();

        outerTabPane.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTab, newTab) -> {
                    tabManager.updateStatusForWorkspace(newTab);
                    refreshToolbarState();
                }
        );

        menuBar = new MainMenuBar(this, currentEngine);
        toolBar = new MainToolBar(this, this);
        VBox topBars = new VBox();
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
            if (files != null && !files.isEmpty()) {
                boolean allSupported = true;
                for (java.io.File f : files) {
                    if (!isSupportedLoadFile(f)) {
                        showError(I18nUtil.getString("dialog.error.title"),
                                I18nUtil.getString("dialog.load.unsupported", f.getName()));
                        allSupported = false;
                        break;
                    }
                }
                if (allSupported) {
                    for (java.io.File f : files) {
                        loadFile(f);
                    }
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

    /** 根据当前工作区和代码标签页状态刷新工具栏按钮的启用/禁用状态 */
    private void refreshToolbarState() {
        editorActions.refreshToolbarState();
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

    // ─── CodeActionHandler 实现 ─────────────────────────────────

    /** 保存当前代码标签页为 .java 文件 */
    @Override
    public void saveCurrentFile() {
        CodeEditorTab codeTab = tabManager.currentCodeTab();
        if (codeTab == null) {
            showWarning(I18nUtil.getString("dialog.save.title"), I18nUtil.getString("dialog.save.nofile"));
            return;
        }
        if (!codeTab.isSourceReady()) {
            showWarning(I18nUtil.getString("dialog.save.title"), I18nUtil.getString("dialog.save.pending"));
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
            ExportService.exportCurrentCode(decoratedCurrentSource(codeTab), file.toPath());
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
        withWorkspaceIndex(view.workspace(), index -> {
            // 将树遍历移至后台线程,避免阻塞 FX 线程
            BackgroundTasks.run("Export-CollectNodes", () -> {
                java.util.List<FileTreeNode> nodes = new java.util.ArrayList<>();
                collectTreeNodes(view.workspace().getTreeRoot(), nodes);
                Platform.runLater(() -> doExport(nodes, index, view.workspace()));
            });
        });
    }

    /** 为单个树节点构建临时索引并弹出导出对话框(右键菜单入口,在后台线程索引构建) */
    private void exportTreeItem(TreeItem<FileTreeNode> rootItem) {
        if (rootItem == null || rootItem.getValue() == null) {
            return;
        }
        statusBar.setTask(I18nUtil.getString("task.indexing"));
        statusBar.setFilePath(I18nUtil.getString("status.indexing", rootItem.getValue().getName()));
        WorkspaceView exportView = tabManager.currentWorkspaceView();
        Workspace exportWorkspace = exportView == null ? null : exportView.workspace();
        // 将树遍历移至后台线程,避免阻塞 FX 线程
        BackgroundTasks.run("Index-ExportNode", () -> {
            try {
                java.util.List<FileTreeNode> nodesSnapshot = new java.util.ArrayList<>();
                collectTreeNodes(rootItem, nodesSnapshot);
                WorkspaceIndex index = exportWorkspace == null
                        ? WorkspaceIndex.build(nodesSnapshot)
                        : exportWorkspace.getOrBuildIndex();
                Platform.runLater(() -> {
                    statusBar.clearTask();
                    doExport(nodesSnapshot, index, exportWorkspace);
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

    /** 弹出导出配置对话框,提交后台导出任务并显示进度条,支持取消操作 */
    private void doExport(java.util.List<FileTreeNode> nodes,
                          WorkspaceIndex index, Workspace workspace) {
        var configOpt = ExportDialog.show(stage, config, currentEngine);
        if (configOpt.isEmpty()) {
            return;
        }
        ExportConfig exportConfig = configOpt.get();
        persistExportConfig(exportConfig);

        ExportDialog.ProgressHandle progressHandle = ExportDialog.showProgress(stage);
        java.util.concurrent.atomic.AtomicReference<Future<?>> exportTaskRef = new java.util.concurrent.atomic.AtomicReference<>();
        AtomicBoolean exportCanceled = new AtomicBoolean(false);
        // 必须在提交任务前注册取消回调,避免用户点击取消时 exportTaskRef 尚未设置
        progressHandle.setOnCancel(() -> {
            exportCanceled.set(true);
            Future<?> f = exportTaskRef.get();
            if (f != null) {
                BackgroundTasks.cancel(f);
            }
        });
        statusBar.setTask(I18nUtil.getString("task.exporting"));
        exportTaskRef.set(BackgroundTasks.run("Export", () -> {
            try {
                ExportResult result = ExportService.exportAll(
                        nodes, exportConfig, index, commentScope(workspace, exportConfig),
                        (path, pct) -> Platform.runLater(() -> {
                            statusBar.setFilePath(I18nUtil.getString(
                                    "status.exporting.detail", pct, path));
                            progressHandle.update(path, pct);
                        }),
                        (java.util.function.BooleanSupplier) () -> exportCanceled.get());
                // 立即快照,避免 Platform.runLater 延迟执行期间被用户点取消覆盖
                final boolean cancelled = exportCanceled.get();
                Platform.runLater(() -> {
                    progressHandle.close();
                    statusBar.clearTask();
                    if (cancelled) {
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
                log.error("导出失败", ex);
                final boolean cancelled = exportCanceled.get();
                Platform.runLater(() -> {
                    progressHandle.close();
                    statusBar.clearTask();
                    if (cancelled) {
                        statusBar.setFilePath(I18nUtil.getString("dialog.export.canceled"));
                        showWarning(I18nUtil.getString("dialog.export.title"),
                                I18nUtil.getString("dialog.export.canceled"));
                        return;
                    }
                    showError(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.export.failed", ex.getMessage()));
                });
            } catch (Exception ex) {
                log.error("导出失败", ex);
                final boolean cancelled = exportCanceled.get();
                Platform.runLater(() -> {
                    progressHandle.close();
                    statusBar.clearTask();
                    if (cancelled) {
                        showWarning(I18nUtil.getString("dialog.export.title"),
                                I18nUtil.getString("dialog.export.canceled"));
                    } else {
                        showError(I18nUtil.getString("dialog.error.title"),
                                I18nUtil.getString("dialog.export.failed", ex.getMessage()));
                    }
                });
            }
        }, rejected -> Platform.runLater(() -> {
            exportCanceled.set(true);
            progressHandle.close();
            statusBar.clearTask();
            showError(I18nUtil.getString("dialog.error.title"),
                    "Export task rejected: background queue is full");
        })));
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
        editorActions.copySelection();
    }

    /** 全选 */
    @Override
    public void selectAll() {
        editorActions.selectAll();
    }

    /** 放大字号 */
    @Override
    public void zoomIn() {
        editorActions.zoomIn();
    }

    /** 缩小字号 */
    @Override
    public void zoomOut() {
        editorActions.zoomOut();
    }

    /** 重置字号 */
    @Override
    public void resetZoom() {
        editorActions.resetZoom();
    }

    /** 切换行号显示 */
    @Override
    public void toggleLineNumbers() {
        editorActions.toggleLineNumbers();
    }

    /** 折叠当前工作区文件树 */
    @Override
    public void collapseTree() {
        editorActions.collapseTree();
    }

    /** 显示大纲工具窗口 */
    @Override
    public void showOutline() {
        editorActions.showOutline();
    }

    /** 显示继承工具窗口 */
    @Override
    public void showInheritance() {
        editorActions.showInheritance();
    }

    /** 隐藏底部工具窗口 */
    @Override
    public void hideBottomTools() {
        editorActions.hideBottomTools();
    }

    /** 切换反编译引擎并重新反编译当前文件 */
    @Override
    public void selectEngine(DecompilerTypeEnum engine) {
        if (currentEngine == engine) {
            return;
        }
        log.info("切换反编译引擎: {} -> {}", currentEngine, engine);
        currentEngine = engine;
        config.decompiler().defaultEngine(engine);
        if (menuBar != null) {
            menuBar.setSelectedEngine(engine);
        }
        tabManager.setCurrentEngineName(engine.name());
        statusBar.setEngine(engine.name());
        statusBar.setFilePath(I18nUtil.getString("status.currentEngine", engine.name()));

        WorkspaceView view = tabManager.currentWorkspaceView();
        CodeEditorTab currentTab = tabManager.currentCodeTab();
        if (view != null && currentTab != null) {
            TabPane targetPane = view.splitEditorPane().tabPaneFor(currentTab);
            if (targetPane == null) {
                targetPane = view.splitEditorPane().primaryTabPane();
            }
            classTabOpener.cancelCurrentTask();
            classTabOpener.refreshCurrentClassTab(
                    view.workspace(), targetPane, currentTab, engine, lineNumbersEnabled);
        }
    }

    /** 用当前引擎重新反编译当前类 */
    @Override
    public void refreshCurrentTab() {
        refreshCurrentTab(currentEngine);
    }

    /** 用指定引擎重新反编译当前类 */
    private void refreshCurrentTab(DecompilerTypeEnum engine) {
        WorkspaceView view = tabManager.currentWorkspaceView();
        CodeEditorTab currentTab = tabManager.currentCodeTab();
        if (view == null || currentTab == null) {
            statusBar.setFilePath(I18nUtil.getString("toolbar.reload.disabled"));
            return;
        }
        TabPane targetPane = view.splitEditorPane().tabPaneFor(currentTab);
        if (targetPane == null) {
            targetPane = view.splitEditorPane().primaryTabPane();
        }
        classTabOpener.cancelCurrentTask();
        classTabOpener.refreshCurrentClassTab(
                view.workspace(), targetPane, currentTab, engine, lineNumbersEnabled);
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

    @Override
    public void goToDeclaration(CodeMetadata.Reference reference) {
        if (reference == null || reference.targetClass() == null) {
            return;
        }
        WorkspaceView view = tabManager.currentWorkspaceView();
        if (view == null) {
            return;
        }
        FileTreeNode node = findNodeForReference(view.workspace(), reference);
        if (node != null) {
            classTabOpener.openClassTab(node, view.workspace(), view.codeTabPane(),
                    currentEngine, lineNumbersEnabled);
        } else {
            statusBar.setFilePath(I18nUtil.getString("status.locateFailed", reference.targetClass()));
        }
    }

    @Override
    public void goToDeclaration(CodeViewContext context, int lineNumber, String token) {
        if (context == null || context.workspace() == null) {
            return;
        }
        String targetToken = sanitizeDeclarationToken(token);

        // 当点击的是当前类声明名时,不跳转(避免同名类跨包误导航)
        if (isCurrentClassDeclaration(context, lineNumber, targetToken)) {
            return;
        }
        if (context.metadata() != null) {
            var refs = context.metadata().getRefsAtLine(lineNumber);
            if (!refs.isEmpty()) {
                // 计算点击处的列号,用于同名类精确匹配
                int column = computeClickColumn(context, lineNumber, token);
                CodeMetadata.Reference selected = selectReference(refs, token, column);
                if (openReferenceInWorkspace(context.workspace(), selected)) {
                    return;
                }
            }
        }

        if (targetToken.isBlank()) {
            statusBar.setFilePath(I18nUtil.getString("status.locateFailed", ""));
            return;
        }

        Workspace workspace = context.workspace();
        WorkspaceIndex index = workspace.isIndexReady() ? workspace.getIndex() : context.workspaceIndex();
        String sourceCode = context.openFile() == null ? "" : context.openFile().sourceCode();
        statusBar.setFilePath(I18nUtil.getString("status.locating", targetToken));

        boolean preferClassNavigation = shouldPreferClassNavigation(targetToken)
                || looksLikeClassUsageAtLine(sourceCode, lineNumber, targetToken);
        FileTreeNode node = null;
        if (preferClassNavigation) {
            node = findNodeForToken(workspace, index, targetToken,
                    context.classInternalName(), sourceCode, true);
            if (node != null) {
                openNodeInWorkspace(workspace, node);
                return;
            }
        }

        if (revealDeclarationInCurrentTab(context, lineNumber, targetToken)) {
            return;
        }

        if (!preferClassNavigation) {
            if (shouldSearchWorkspaceForClassToken(targetToken)) {
                node = findNodeForToken(workspace, index, targetToken,
                        context.classInternalName(), sourceCode, true);
            }
        }
        if (node != null) {
            openNodeInWorkspace(workspace, node);
            return;
        }

        statusBar.setFilePath(I18nUtil.getString("status.locateFailed", targetToken));
    }

    /**
     * 判断点击处是否为当前类的声明名
     * 防止同名类跨包误导航(如 com.pig4cloud.service.a 和 com.pig4cloud.domain.a)
     */
    private boolean isCurrentClassDeclaration(CodeViewContext context, int lineNumber, String token) {
        if (token == null || token.isBlank() || context.classInternalName() == null) {
            return false;
        }
        String currentSimpleName = classLeafName(context.classInternalName());
        if (!token.equals(currentSimpleName)) {
            return false;
        }
        String sourceCode = context.openFile() == null ? "" : context.openFile().sourceCode();
        if (sourceCode.isBlank()) {
            return false;
        }
        String[] lines = sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (lineNumber < 1 || lineNumber > lines.length) {
            return false;
        }
        String line = stripLineComment(lines[lineNumber - 1]).strip();
        return line.contains("class " + token) || line.contains("interface " + token)
                || line.contains("enum " + token) || line.contains("record " + token)
                || line.contains("@interface " + token);
    }

    @Override
    public void openClass(String fullPath, int line) {
        WorkspaceView view = tabManager.currentWorkspaceView();
        if (view == null || fullPath == null) {
            return;
        }
        FileTreeNode node = view.workspace().findNodeByPath(fullPath);
        if (node != null) {
            classTabOpener.openClassTab(node, view.workspace(), view.codeTabPane(),
                    currentEngine, lineNumbersEnabled);
        }
    }

    @Override
    public void showInheritanceGraph(CodeViewContext context) {
        if (context == null) {
            return;
        }
        String fullPath = context.classInternalName();
        if (fullPath == null || fullPath.isBlank()) {
            showGraphFailed(fullPath, null);
            return;
        }
        Workspace workspace = context.workspace();
        WorkspaceIndex index = workspace != null && workspace.isIndexReady()
                ? workspace.getIndex()
                : context.workspaceIndex();
        statusBar.setTask(I18nUtil.getString("task.loading"));
        statusBar.setFilePath(I18nUtil.getString("graph.building", fullPath));
        log.info("请求查看继承图: {}", fullPath);
        GraphDialog dialog = new GraphDialog(stage,
                I18nUtil.getString("context.inheritanceGraph") + " - " + fullPath);
        dialog.show();
        BackgroundTasks.run("InheritanceGraph-" + fullPath, () -> {
            try {
                byte[] classBytes = classBytesForContext(context);
                if (classBytes == null || classBytes.length == 0) {
                    Platform.runLater(() -> showGraphFailed(dialog, fullPath, null));
                    return;
                }
                WorkspaceIndex graphIndex = workspace != null
                        ? awaitWorkspaceIndex(workspace)
                        : index;
                if (graphIndex == WorkspaceIndex.EMPTY && index != null) {
                    graphIndex = index;
                }
                var tree = InheritanceService.buildTree(fullPath, graphIndex, classBytes);
                if (tree == null) {
                    Platform.runLater(() -> showGraphFailed(dialog, fullPath, null));
                    return;
                }
                String dot = GraphService.toInheritanceDOT(tree);
                Platform.runLater(() -> {
                    statusBar.clearTask();
                    dialog.showDot(dot);
                });
            } catch (Exception e) {
                log.error("查看继承图失败: {}", fullPath, e);
                Platform.runLater(() -> showGraphFailed(dialog, fullPath, e));
            }
        });
    }

    @Override
    public void showControlFlowGraph(CodeViewContext context) {
        if (context == null || context.openFile() == null) {
            return;
        }
        String source = context.openFile().sourceCode();
        // 获取当前光标行,确定所在方法
        WorkspaceView view = workspaceViewFor(context.workspace());
        if (view == null) {
            return;
        }
        int line = 1;
        CodeEditorTab codeTab = view.splitEditorPane().currentCodeTab();
        if (codeTab != null && codeTab.getCodeArea() != null) {
            var caret = codeTab.getCodeArea().getCaretPosition();
            if (caret != null) {
                line = caret.index() + 1;
            }
        }
        String methodName = com.bingbaihanji.fxdecomplie.ui.code.CodeSyncHelper
                .findMethodAtLine(source, line);
        if (methodName == null || methodName.isBlank()) {
            return;
        }
        statusBar.setTask(I18nUtil.getString("task.loading"));
        statusBar.setFilePath("CFG - " + methodName);
        GraphDialog dialog = new GraphDialog(stage, "CFG - " + methodName);
        dialog.show();
        BackgroundTasks.run("CFG-" + methodName, () -> {
            try {
                byte[] classBytes = classBytesForContext(context);
                if (classBytes == null) {
                    Platform.runLater(() -> showGraphFailed(dialog, null, null));
                    return;
                }
                String dot = com.bingbaihanji.fxdecomplie.ui.graph.CfgAnalyzer
                        .buildCfgDot(classBytes, methodName, null);
                Platform.runLater(() -> dialog.showDot(dot));
                Platform.runLater(statusBar::clearTask);
            } catch (Exception e) {
                log.error("CFG生成失败", e);
                Platform.runLater(() -> {
                    showGraphFailed(dialog, null, e);
                    statusBar.clearTask();
                });
            }
        });
    }

    @Override
    public void showMethodGraph(CodeViewContext context) {
        if (context == null) {
            return;
        }
        String fullPath = context.classInternalName();
        if (fullPath == null || fullPath.isBlank()) {
            showGraphFailed(fullPath, null);
            return;
        }
        statusBar.setTask(I18nUtil.getString("task.loading"));
        statusBar.setFilePath(I18nUtil.getString("graph.building", fullPath));
        log.info("请求查看方法图: {}", fullPath);
        GraphDialog dialog = new GraphDialog(stage,
                I18nUtil.getString("context.methodGraph") + " - " + fullPath);
        dialog.show();
        BackgroundTasks.run("MethodGraph-" + fullPath, () -> {
            try {
                byte[] classBytes = classBytesForContext(context);
                if (classBytes == null || classBytes.length == 0) {
                    Platform.runLater(() -> showGraphFailed(dialog, fullPath, null));
                    return;
                }
                var graph = GraphService.parseMethodCalls(classBytes);
                if (graph.methods().isEmpty()) {
                    Platform.runLater(() -> showGraphFailed(dialog, fullPath, null));
                    return;
                }
                String dot = GraphService.toMethodDOT(graph);
                Platform.runLater(() -> {
                    statusBar.clearTask();
                    dialog.showDot(dot);
                });
            } catch (Exception e) {
                log.error("查看方法图失败: {}", fullPath, e);
                Platform.runLater(() -> showGraphFailed(dialog, fullPath, e));
            }
        });
    }

    private void showGraphFailed(String fullPath, Throwable error) {
        showGraphFailed(null, fullPath, error);
    }

    private void showGraphFailed(GraphDialog dialog, String fullPath, Throwable error) {
        statusBar.clearTask();
        statusBar.setFilePath(I18nUtil.getString("graph.renderFailed"));
        String message = I18nUtil.getString("graph.renderFailed")
                + (fullPath == null || fullPath.isBlank() ? "" : ": " + fullPath);
        if (error != null) {
            message += System.lineSeparator() + error.getMessage();
        }
        if (dialog != null) {
            dialog.showMessage(message);
            return;
        }
        if (error == null) {
            showWarning(I18nUtil.getString("dialog.warning.title"), message);
        } else {
            showError(I18nUtil.getString("dialog.error.title"),
                    message);
        }
    }

    @Override
    public void addOrUpdateComment(CodeViewContext context, jfx.incubator.scene.control.richtext.TextPos caretPosition) {
        if (context == null) {
            return;
        }
        String text = context.openFile() != null ? context.openFile().sourceCode() : "";
        int line = caretPosition == null ? 1 : Math.max(1, caretPosition.index() + 1);
        String memberSig = "";
        // 从源码中提取当前方法签名
        String methodName = com.bingbaihanji.fxdecomplie.ui.code.CodeSyncHelper.findMethodAtLine(text, line);
        if (methodName != null) {
            memberSig = methodName;
        }

        // 查找已有注释
        java.util.List<CommentData> existing = com.bingbaihanji.fxdecomplie.service.CommentManager
                .load(context.workspaceHash(), context.classInternalName());
        CommentData existingComment = null;
        for (CommentData c : existing) {
            if (c.line() == line && c.memberSignature().equals(memberSig)) {
                existingComment = c;
                break;
            }
        }

        CommentDialog.show(stage, context.classInternalName(), memberSig, line,
                context.sourceHash(), context.optionsHash(), existingComment,
                comment -> {
                    com.bingbaihanji.fxdecomplie.service.CommentManager
                            .save(context.workspaceHash(), comment);
                    refreshVisibleComments(context);
                    statusBar.setFilePath(I18nUtil.getString("comment.saved") + " L" + comment.line());
                });
    }

    @Override
    public void deleteComment(CodeViewContext ctx, int line) {
        if (ctx == null) {
            return;
        }
        final CodeViewContext context = ctx;
        String text = context.openFile() != null ? context.openFile().sourceCode() : "";
        String memberSig = com.bingbaihanji.fxdecomplie.ui.code.CodeSyncHelper.findMethodAtLine(text, line);
        final String effectiveSig = (memberSig != null) ? memberSig : "";
        java.util.List<CommentData> existing = CommentManager.load(
                context.workspaceHash(), context.classInternalName());
        CommentData toDelete = null;
        for (CommentData c : existing) {
            if (c.line() == line && c.memberSignature().equals(effectiveSig)) {
                toDelete = c;
                break;
            }
        }
        if (toDelete == null) {
            return;
        }
        final CommentData finalToDelete = toDelete;
        final String wsHash = context.workspaceHash();
        final String clsName = context.classInternalName();
        final int targetLine = line;
        if (com.bingbaihanji.fxdecomplie.ui.DialogHelper.showConfirm(stage,
                I18nUtil.getString("comment.delete"),
                I18nUtil.getString("comment.confirmDelete"))) {
            BackgroundTasks.run("CommentDelete", () -> {
                CommentManager.delete(wsHash, clsName,
                        finalToDelete.memberSignature(), finalToDelete.line(), finalToDelete.time());
                Platform.runLater(() -> {
                    refreshVisibleComments(context);
                    statusBar.setFilePath(I18nUtil.getString("comment.delete") + " L" + targetLine);
                });
            });
        }
    }

    @Override
    public void renameAtCaret(CodeViewContext context,
                              jfx.incubator.scene.control.richtext.TextPos caret) {
        if (context == null || caret == null) {
            statusBar.setFilePath("Rename unavailable: no code context");
            return;
        }
        WorkspaceView view = workspaceViewFor(context.workspace());
        if (view == null) {
            statusBar.setFilePath("Rename unavailable: workspace not found");
            return;
        }
        CodeEditorTab codeTab = codeTabForContext(view, context);
        if (codeTab == null || codeTab.getCodeArea() == null) {
            statusBar.setFilePath("Rename unavailable: code tab not found");
            return;
        }
        String text = codeTab.getCodeArea().getText();
        if (text == null || text.isEmpty()) {
            statusBar.setFilePath("Rename unavailable: source is empty");
            return;
        }
        int offset = flatOffset(text, caret);
        String caretName = com.bingbaihanji.fxdecomplie.rename.RenameService
                .identifierAt(text, offset);
        if (caretName.isBlank()) {
            statusBar.setFilePath("Rename unavailable: place cursor on a Java name");
            return;
        }
        WorkspaceIndex index = context.workspace() != null && context.workspace().isIndexReady()
                ? context.workspace().getIndex() : context.workspaceIndex();
        String wsHash = CommentScope.workspaceHash(context.workspace());
        var targetOpt = com.bingbaihanji.fxdecomplie.rename.RenameService.resolveTarget(
                text, offset, context.classInternalName(), context.classBytes(), index, wsHash);
        var target = targetOpt.orElseGet(() -> new com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget(
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_IDENTIFIER,
                        context.classInternalName(), caretName, caretName, ""),
                "identifier", caretName));
        target = refineClassRenameTarget(context.workspace(), context.classInternalName(),
                caretName, wsHash, target);
        target = forceCurrentClassRenameTarget(context, text, offset, caretName, wsHash, target);
        var baseEntry = target.entry();
        String oldName = target.currentName();
        repairCurrentClassDisplayMappingIfNeeded(context, codeTab, wsHash, baseEntry, oldName, text);

        // 显示对话框
        String newName = com.bingbaihanji.fxdecomplie.rename.RenameDialog
                .show(stage, oldName, target.kind());
        if (newName == null || newName.equals(oldName)) {
            statusBar.setFilePath("Rename canceled: " + oldName);
            return;
        }

        com.bingbaihanji.fxdecomplie.rename.RenameEntry visibleEntry =
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        baseEntry.type(), baseEntry.className(), oldName, newName,
                        baseEntry.desc());
        String currentRenamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                .applySingleRename(text, visibleEntry, codeTab.getOpenFile().fullPath(), wsHash);
        if (java.util.Objects.equals(currentRenamedSource, text)
                && allowVisibleIdentifierFallback(visibleEntry)) {
            currentRenamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .replaceVisibleIdentifier(text, oldName, newName);
        }
        if (isClassRenameEntry(visibleEntry)) {
            currentRenamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .replaceVisibleClassDeclaration(currentRenamedSource, oldName, newName);
        }
        boolean currentTabChanged = false;
        if (!java.util.Objects.equals(currentRenamedSource, text)) {
            String displayClass = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .displayClassName(codeTab.getOpenFile().fullPath(), wsHash);
            codeTab.updateSourceCode(displayClass, currentRenamedSource);
            reinstallCodeContext(codeTab, currentRenamedSource);
            currentTabChanged = true;
        }

        // 保存
        com.bingbaihanji.fxdecomplie.rename.RenameEntry entry =
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        baseEntry.type(), baseEntry.className(), baseEntry.oldName(),
                        newName, baseEntry.desc());
        boolean saved = com.bingbaihanji.fxdecomplie.rename.RenameService.save(wsHash,
                entry);
        String postSaveSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                .applySingleRename(text, visibleEntry, codeTab.getOpenFile().fullPath(), wsHash);
        if (java.util.Objects.equals(postSaveSource, text)
                && allowVisibleIdentifierFallback(visibleEntry)) {
            postSaveSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .replaceVisibleIdentifier(text, oldName, newName);
        }
        if (isClassRenameEntry(visibleEntry)) {
            postSaveSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .replaceVisibleClassDeclaration(postSaveSource, oldName, newName);
        }
        String savedDisplayClass = com.bingbaihanji.fxdecomplie.rename.RenameService
                .displayClassName(codeTab.getOpenFile().fullPath(), wsHash);
        String currentVisibleSource = codeTab.getCodeArea() == null
                ? codeTab.getOpenFile().sourceCode() : codeTab.getCodeArea().getText();
        if (!java.util.Objects.equals(postSaveSource, currentVisibleSource)
                || !java.util.Objects.equals(savedDisplayClass, codeTab.getOpenFile().className())) {
            codeTab.updateSourceCode(savedDisplayClass, postSaveSource);
            reinstallCodeContext(codeTab, postSaveSource);
            currentTabChanged = true;
        }

        if (context.workspace() != null) {
            context.workspace().clearSourceSearchCaches();
        }
        int changedTabs = refreshOpenTabsAfterRename(context.workspace(), wsHash, visibleEntry, codeTab);
        refreshWorkspaceTree(context.workspace());
        int totalChangedTabs = changedTabs + (currentTabChanged ? 1 : 0);
        log.info("重命名完成: type={}, class={}, old={}, new={}, changedTabs={}",
                baseEntry.type(), baseEntry.className(), oldName, newName, totalChangedTabs);
        if (totalChangedTabs == 0) {
            statusBar.setFilePath((saved ? "Rename saved" : "Rename memory-only")
                    + " but no visible text changed: " + oldName + " -> " + newName);
        } else {
            statusBar.setFilePath((saved ? "Renamed " : "Renamed memory-only ")
                    + target.kind() + " [" + baseEntry.type()
                    + "]: " + oldName + " -> " + newName);
        }
    }

    /** 修复当前类的重命名显示映射(当显示名与重命名映射不一致时自动修正) */
    private void repairCurrentClassDisplayMappingIfNeeded(
            CodeViewContext context, CodeEditorTab codeTab, String workspaceHash,
            com.bingbaihanji.fxdecomplie.rename.RenameEntry baseEntry,
            String currentName, String sourceCode) {
        if (context == null || codeTab == null || baseEntry == null
                || currentName == null || currentName.isBlank()
                || !com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS
                .equals(baseEntry.type())) {
            return;
        }
        String currentClass = normalizeInternalClassName(context.classInternalName());
        String entryClass = com.bingbaihanji.fxdecomplie.rename.RenameService
                .originalInternalName(baseEntry.className(), workspaceHash);
        if (!ClassNameUtil.sameInternalName(entryClass, currentClass)) {
            return;
        }
        String displayClass = com.bingbaihanji.fxdecomplie.rename.RenameService
                .displayClassName(codeTab.getOpenFile().fullPath(), workspaceHash);
        if (currentName.equals(displayClass)) {
            return;
        }
        com.bingbaihanji.fxdecomplie.rename.RenameEntry repairEntry =
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS,
                        baseEntry.className(), baseEntry.oldName(), currentName, baseEntry.desc());
        boolean saved = com.bingbaihanji.fxdecomplie.rename.RenameService
                .save(workspaceHash, repairEntry);
        if (!saved) {
            return;
        }
        String repairedDisplay = com.bingbaihanji.fxdecomplie.rename.RenameService
                .displayClassName(codeTab.getOpenFile().fullPath(), workspaceHash);
        codeTab.updateSourceCode(repairedDisplay, sourceCode);
        reinstallCodeContext(codeTab, sourceCode);
        if (context.workspace() != null) {
            context.workspace().clearSourceSearchCaches();
            refreshWorkspaceTree(context.workspace());
        }
        log.info("修复类显示映射: class={}, visible={}, display={}",
                baseEntry.className(), currentName, repairedDisplay);
    }

    /** 当光标位于类声明处时强制将重命名目标设为本类 */
    private com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget forceCurrentClassRenameTarget(
            CodeViewContext context, String sourceCode, int offset, String caretName,
            String workspaceHash,
            com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget target) {
        if (context == null || caretName == null || caretName.isBlank()
                || context.classInternalName() == null || context.classInternalName().isBlank()) {
            return target;
        }
        if (!isCurrentClassRenamePosition(context, sourceCode, offset, caretName, workspaceHash)) {
            return target;
        }
        String owner = normalizeInternalClassName(context.classInternalName());
        String originalOwner = com.bingbaihanji.fxdecomplie.rename.RenameService
                .originalInternalName(owner, workspaceHash);
        String originalLeaf = classLeafName(originalOwner);
        if (originalLeaf.isBlank()) {
            originalLeaf = classLeafName(owner);
        }
        return new com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget(
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS,
                        originalOwner, originalLeaf, caretName, ""),
                "class", caretName);
    }

    private boolean isCurrentClassRenamePosition(CodeViewContext context, String sourceCode,
                                                 int offset, String caretName, String workspaceHash) {
        String currentDisplay = com.bingbaihanji.fxdecomplie.rename.RenameService
                .displayClassName(context.classInternalName(), workspaceHash);
        String currentOriginal = classLeafName(context.classInternalName());
        if (caretName.equals(currentDisplay) || caretName.equals(currentOriginal)) {
            return true;
        }
        return isDeclaredTypeNameAt(sourceCode, offset, caretName);
    }

    private com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget refineClassRenameTarget(
            Workspace workspace, String currentClassName, String caretName,
            String workspaceHash,
            com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget target) {
        if (target == null || caretName == null || caretName.isBlank()) {
            return target;
        }
        var entry = target.entry();
        if (entry != null && com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS
                .equals(entry.type())) {
            return target;
        }
        String currentInternal = normalizeInternalClassName(currentClassName);
        String currentSimple = tokenSimpleName(currentInternal);
        FileTreeNode node = null;
        String currentDisplayName = com.bingbaihanji.fxdecomplie.rename.RenameService
                .displayClassName(currentClassName, workspaceHash);
        if (caretName.equals(currentSimple) || caretName.equals(currentDisplayName)) {
            node = workspace == null ? null : workspace.findNodeByPath(currentInternal + ".class");
        }
        if (node == null) {
            node = findNodeBySimpleNameInTree(workspace, caretName, currentClassName);
        }
        if (node == null || !node.isClassFile()) {
            return target;
        }
        String owner = node.getFullPath();
        if (owner.endsWith(".class")) {
            owner = owner.substring(0, owner.length() - ".class".length());
        }
        return new com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget(
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS,
                        owner, caretName, caretName, ""),
                "class", caretName);
    }

    /** 根据 CodeViewContext 的路径和引擎查找对应的代码标签页(用于重命名操作定位) */
    private CodeEditorTab codeTabForContext(WorkspaceView view, CodeViewContext context) {
        if (view == null || context == null || context.openFile() == null) {
            return view == null ? null : view.splitEditorPane().currentCodeTab();
        }
        String fullPath = context.openFile().fullPath();
        DecompilerTypeEnum engine = context.openFile().engine();
        for (TabPane pane : view.splitEditorPane().allTabPanes()) {
            for (Tab tab : pane.getTabs()) {
                if (tab instanceof CodeEditorTab codeTab
                        && codeTab.getOpenFile() != null
                        && fullPath.equals(codeTab.getOpenFile().fullPath())
                        && engine == codeTab.getOpenFile().engine()) {
                    return codeTab;
                }
            }
        }
        return view.splitEditorPane().currentCodeTab();
    }

    /** 重命名后刷新所有已打开的代码标签页 */
    private int refreshOpenTabsAfterRename(Workspace workspace, String workspaceHash,
                                           com.bingbaihanji.fxdecomplie.rename.RenameEntry visibleEntry) {
        return refreshOpenTabsAfterRename(workspace, workspaceHash, visibleEntry, null);
    }

    /** 重命名后刷新所有已打开的代码标签页,可指定跳过某个标签页(触发源标签页已自行更新) */
    private int refreshOpenTabsAfterRename(Workspace workspace, String workspaceHash,
                                           com.bingbaihanji.fxdecomplie.rename.RenameEntry visibleEntry,
                                           CodeEditorTab skipTab) {
        WorkspaceView view = workspaceViewFor(workspace);
        if (view == null || workspaceHash == null || workspaceHash.isBlank()) {
            return 0;
        }
        int changedTabs = 0;
        for (TabPane pane : view.splitEditorPane().allTabPanes()) {
            for (Tab tab : pane.getTabs()) {
                if (!(tab instanceof CodeEditorTab codeTab) || codeTab.getOpenFile() == null) {
                    continue;
                }
                if (skipTab != null && codeTab == skipTab) {
                    continue;
                }
                OpenFile openFile = codeTab.getOpenFile();
                String currentVisibleSource = codeTab.getCodeArea() == null
                        ? openFile.sourceCode() : codeTab.getCodeArea().getText();
                String renamedSource;
                if (visibleEntry == null) {
                    renamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                            .applyRenames(currentVisibleSource, workspaceHash, openFile.fullPath());
                } else if (com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS
                        .equals(visibleEntry.type())) {
                    // 类重命名：仅通过 applySingleRename 应用(有包上下文检查)
                    // 不使用 replaceVisibleIdentifier(会按简单名全局替换导致跨包误匹配)
                    renamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                            .applySingleRename(currentVisibleSource, visibleEntry,
                                    openFile.fullPath(), workspaceHash);
                } else {
                    renamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                            .applySingleRename(currentVisibleSource, visibleEntry,
                                    openFile.fullPath(), workspaceHash);
                    if (java.util.Objects.equals(renamedSource, currentVisibleSource)
                            && allowVisibleIdentifierFallback(visibleEntry)) {
                        renamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                                .replaceVisibleIdentifier(currentVisibleSource,
                                        visibleEntry.oldName(), visibleEntry.newName());
                    }
                }
                if (java.util.Objects.equals(renamedSource, currentVisibleSource)) {
                    continue;
                }
                String displayClass = com.bingbaihanji.fxdecomplie.rename.RenameService
                        .displayClassName(openFile.fullPath(), workspaceHash);
                codeTab.updateSourceCode(displayClass, renamedSource);
                changedTabs++;
                reinstallCodeContext(codeTab, renamedSource);
            }
        }
        return changedTabs;
    }

    /** 在源码更新后重新安装代码标签页的上下文菜单(Ctrl+Click 导航和右键菜单) */
    private void reinstallCodeContext(CodeEditorTab codeTab, String sourceCode) {
        if (codeTab == null) {
            return;
        }
        var panel = codeTab.getCodeViewPanel();
        CodeViewContext oldCtx = panel == null ? null : panel.getContextMenuContext();
        CodeActionHandler handler = panel == null ? null : panel.getContextMenuHandler();
        if (oldCtx != null && handler != null) {
            String sourceHash = CommentExportDecorator.sourceHash(sourceCode);
            CodeViewContext newCtx = new CodeViewContext(oldCtx.workspace(), oldCtx.node(),
                    codeTab.getOpenFile(), oldCtx.classBytes(), codeTab.getMetadata(),
                    oldCtx.workspaceIndex(), oldCtx.workspaceHash(), sourceHash,
                    oldCtx.optionsHash());
            panel.installContextMenu(newCtx, handler);
        }
    }

    /**
     * 刷新工作区文件树,确保所有可见 cell 的显示名与当前重命名状态一致
     *
     * <p>JavaFX {@code Node.refresh()} 仅重新应用 CSS,不会触发 TreeCell 的
     * {@code updateItem}FileTreeView.refreshVisibleCells() 通过重建 cell factory
     * 强制 VirtualFlow 重建所有可见 cell,从而触发 updateItem 获取最新显示名</p>
     */
    private void refreshWorkspaceTree(Workspace workspace) {
        WorkspaceView view = workspaceViewFor(workspace);
        if (view != null && view.treeView() != null) {
            view.treeView().refreshVisibleCells();
        }
    }

    @Override
    public void searchInWorkspace(String selectedText) {
        if (selectedText == null || selectedText.isBlank()) {
            return;
        }
        openSearch(selectedText);
    }

    /** 复制引用到剪贴板后在状态栏显示提示 */
    @Override
    public void copyReference(String referenceText) {
        if (referenceText != null && !referenceText.isEmpty()) {
            statusBar.setFilePath(I18nUtil.getString("status.copied") + ": " + referenceText);
        }
    }

    /** 根据引用信息在工作区中定位并打开目标类 */
    private boolean openReferenceInWorkspace(Workspace workspace, CodeMetadata.Reference reference) {
        FileTreeNode node = findNodeForReference(workspace, reference);
        if (node != null) {
            openNodeInWorkspace(workspace, node);
            return true;
        }
        return false;
    }

    /** 根据引用对象在工作区文件树中查找目标节点(含重命名回查) */
    private FileTreeNode findNodeForReference(Workspace workspace, CodeMetadata.Reference reference) {
        if (workspace == null || reference == null || reference.targetClass() == null) {
            return null;
        }
        FileTreeNode direct = findClassPath(workspace, reference.targetClass());
        if (direct != null) {
            return direct;
        }
        String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                .of(workspace, "").workspaceHash();
        List<String> originals = com.bingbaihanji.fxdecomplie.rename.RenameService
                .originalInternalNameCandidates(reference.targetClass(), wsHash);
        for (String original : originals) {
            FileTreeNode node = findClassPathRaw(workspace, original);
            if (node != null) {
                return node;
            }
        }
        String original = com.bingbaihanji.fxdecomplie.rename.RenameService
                .originalInternalName(reference.targetClass(), wsHash);
        return findClassPathRaw(workspace, original);
    }

    /** 从引用列表中选择与 token 最匹配的引用(按简单名/限定名匹配) */
    private CodeMetadata.Reference selectReference(List<CodeMetadata.Reference> refs, String token) {
        return selectReference(refs, token, -1);
    }

    /**
     * 计算用户点击处 token 在源码行中的起始列号
     *
     * <p>从反编译源码中定位指定行,找到 token 在该行中首次出现的位置 
     * 用于在多个同名引用中精确匹配用户点击的那一个 </p>
     *
     * @param context    代码视图上下文
     * @param lineNumber 行号(从 1 开始)
     * @param token      点击处的标识符
     * @return token 在行中的起始列号(从 0 开始),-1 表示未找到
     */
    private int computeClickColumn(CodeViewContext context, int lineNumber, String token) {
        if (context == null || context.openFile() == null || token == null || token.isBlank()) {
            return -1;
        }
        String sourceCode = context.openFile().sourceCode();
        if (sourceCode == null || sourceCode.isBlank()) {
            return -1;
        }
        String[] lines = sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (lineNumber < 1 || lineNumber > lines.length) {
            return -1;
        }
        String line = lines[lineNumber - 1];
        String cleanToken = sanitizeDeclarationToken(token);
        if (cleanToken.isBlank()) {
            return -1;
        }
        // 找到 token 在行中的位置(作为独立标识符,不是更长标识符的一部分)
        int idx = line.indexOf(cleanToken);
        if (idx < 0) {
            return -1;
        }
        // 验证不是更长标识符的一部分
        if (idx > 0 && Character.isJavaIdentifierPart(line.charAt(idx - 1))) {
            return -1;
        }
        int end = idx + cleanToken.length();
        if (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
            return -1;
        }
        return idx;
    }

    /**
     * 从引用列表中选择与 token 匹配的引用,支持按列位置精确匹配
     *
     * <p>当同一行有多个同简单名的引用(如混淆后的多个 {@code a} 类)时,
     * 列位置匹配可以精确选择用户点击的那个引用,避免误导航 </p>
     *
     * @param refs   行上的所有引用
     * @param token  点击处的标识符
     * @param column 点击处的列号(从 0 开始,-1 表示未知)
     * @return 匹配的引用
     */
    private CodeMetadata.Reference selectReference(List<CodeMetadata.Reference> refs,
                                                   String token, int column) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        String targetToken = sanitizeDeclarationToken(token);
        if (!targetToken.isBlank()) {
            String simpleToken = tokenSimpleName(targetToken);
            // 分两轮匹配：第一轮找有精确列位置的引用,第二轮找无列位置的引用
            // 这样字节码增强的引用(有 columnStart)优先于正则提取的引用(columnStart=-1)
            CodeMetadata.Reference bestWithColumn = null;
            int closestDistance = Integer.MAX_VALUE;
            CodeMetadata.Reference fallbackNoColumn = null;

            for (CodeMetadata.Reference ref : refs) {
                if (ref.targetClass() == null) {
                    continue;
                }
                String normalized = ref.targetClass().replace('.', '/');
                // 完全限定名精确匹配(最高优先级)
                if (ref.targetClass().equals(targetToken)
                        || normalized.equals(targetToken.replace('.', '/'))) {
                    return ref;
                }
                // 简单名匹配
                if (tokenSimpleName(normalized).equals(simpleToken)) {
                    if (ref.columnStart() >= 0) {
                        // 有列位置的引用(来自字节码增强)：按距离选最近的
                        int distance = column >= 0
                                ? Math.abs(ref.columnStart() - column)
                                : 0; // 无点击列信息时,取第一个有列位置的
                        if (bestWithColumn == null || distance < closestDistance) {
                            closestDistance = distance;
                            bestWithColumn = ref;
                        }
                    } else if (fallbackNoColumn == null) {
                        // 无列位置的引用(来自正则提取)：仅作为兜底
                        fallbackNoColumn = ref;
                    }
                }
            }
            // 优先返回有列位置的匹配(字节码增强的精确引用)
            if (bestWithColumn != null) {
                return bestWithColumn;
            }
            if (fallbackNoColumn != null) {
                return fallbackNoColumn;
            }
            return null;
        }
        return refs.getFirst();
    }

    /** 在当前工作区中打开指定树节点对应的类标签页 */
    private void openNodeInWorkspace(Workspace workspace, FileTreeNode node) {
        WorkspaceView view = workspaceViewFor(workspace);
        if (view == null || node == null) {
            return;
        }
        classTabOpener.openClassTab(node, workspace, view.codeTabPane(),
                currentEngine, lineNumbersEnabled);
    }

    /** 根据工作区对象查找其对应的 WorkspaceView */
    private WorkspaceView workspaceViewFor(Workspace workspace) {
        if (workspace == null || tabManager == null) {
            return null;
        }
        return tabManager.getWorkspaceViews().values().stream()
                .filter(view -> view != null && workspace.equals(view.workspace()))
                .findFirst()
                .orElse(null);
    }

    /** 查找包含指定代码标签页的 WorkspaceView */
    private WorkspaceView workspaceViewForCodeTab(CodeEditorTab codeTab) {
        if (codeTab == null || tabManager == null) {
            return null;
        }
        return tabManager.getWorkspaceViews().values().stream()
                .filter(view -> view != null && view.splitEditorPane().allTabPanes().stream()
                        .anyMatch(pane -> pane.getTabs().contains(codeTab)))
                .findFirst()
                .orElse(null);
    }

    /** 获取上下文中类的字节码(优先从上下文、节点缓存、工作区中获取) */
    private byte[] classBytesForContext(CodeViewContext context) throws IOException {
        if (context == null) {
            return null;
        }
        byte[] classBytes = context.classBytes();
        if (classBytes != null && classBytes.length > 0) {
            return classBytes;
        }
        FileTreeNode node = context.node();
        if (node != null) {
            byte[] cached = node.getCachedBytes();
            if (cached != null && cached.length > 0) {
                return cached.clone();
            }
            return node.resolveBytes();
        }
        Workspace workspace = context.workspace();
        String fullPath = context.classInternalName();
        if (workspace != null && fullPath != null) {
            FileTreeNode target = workspace.findNodeByPath(fullPath);
            return target == null ? null : target.resolveBytes();
        }
        return null;
    }

    /** 尝试在当前代码标签页中定位并滚动到 token 的声明行 */
    private boolean revealDeclarationInCurrentTab(CodeViewContext context, int clickedLine,
                                                  String token) {
        if (context == null || token == null || token.isBlank()
                || context.openFile() == null) {
            return false;
        }
        String source = context.openFile().sourceCode();
        int declarationLine = findDeclarationLine(source, token, clickedLine);
        if (declarationLine <= 0) {
            return false;
        }
        WorkspaceView view = workspaceViewFor(context.workspace());
        if (view == null) {
            return false;
        }
        String fullPath = context.openFile().fullPath();
        for (TabPane pane : view.splitEditorPane().allTabPanes()) {
            for (Tab tab : pane.getTabs()) {
                if (tab instanceof CodeEditorTab codeTab
                        && fullPath.equals(codeTab.getOpenFile().fullPath())
                        && context.openFile().engine() == codeTab.getOpenFile().engine()) {
                    pane.getSelectionModel().select(codeTab);
                    codeTab.revealLine(declarationLine);
                    statusBar.setFilePath(I18nUtil.getString(
                            "status.navigatedTo", fullPath, declarationLine));
                    return true;
                }
            }
        }
        return false;
    }

    /** 在工作区中按 token 查找文件树节点(多策略回退：直接查找→同包→import→重命名→索引→遍历) */
    private FileTreeNode findNodeForToken(Workspace workspace, WorkspaceIndex index,
                                          String token, String currentClassName,
                                          String sourceCode, boolean allowClassLookup) {
        if (workspace == null || token == null || token.isBlank()) {
            return null;
        }
        if (!allowClassLookup && !shouldSearchWorkspaceForClassToken(token)) {
            return null;
        }

        String normalized = token.replace('.', '/');
        String currentInternal = normalizeInternalClassName(currentClassName);
        boolean qualifiedToken = normalized.contains("/");
        FileTreeNode direct = qualifiedToken
                ? findClassPath(workspace, normalized)
                : findClassPathRaw(workspace, normalized);
        if (direct != null && !ClassNameUtil.sameInternalName(direct.getFullPath(), currentClassName)) {
            return direct;
        }

        String currentPackage = packageName(currentInternal);
        if (isRelativeClassToken(token) && !currentPackage.isBlank()) {
            FileTreeNode samePackageRelative = findClassPath(workspace,
                    currentPackage + "/" + normalized);
            if (samePackageRelative != null
                    && !ClassNameUtil.sameInternalName(samePackageRelative.getFullPath(), currentClassName)) {
                return samePackageRelative;
            }
        }

        FileTreeNode sourceResolved = findNodeFromSourceImports(workspace, token,
                currentClassName, sourceCode);
        if (sourceResolved != null) {
            return sourceResolved;
        }

        FileTreeNode renamedResolved = findNodeFromRenameDisplayIndex(
                workspace, normalized, currentInternal);
        if (renamedResolved != null
                && !ClassNameUtil.sameInternalName(renamedResolved.getFullPath(), currentClassName)) {
            return renamedResolved;
        }

        FileTreeNode indexResolved = findNodeBySimpleNameInIndex(workspace, index, token, currentInternal);
        if (indexResolved != null) {
            return indexResolved;
        }
        if (index != null && index != WorkspaceIndex.EMPTY) {
            return null;
        }
        return findNodeBySimpleNameInTree(workspace, token, currentClassName);
    }

    /** 在工作区索引中按简单类名查找节点(优先同包匹配,回退到首个匹配) */
    private FileTreeNode findNodeBySimpleNameInIndex(Workspace workspace, WorkspaceIndex index,
                                                     String token, String currentInternal) {
        if (workspace == null || index == null || index == WorkspaceIndex.EMPTY
                || token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.replace('.', '/');
        String simpleToken = tokenSimpleName(token);
        simpleToken = simpleToken.endsWith(".class") ? simpleToken.substring(0, simpleToken.length() - 6) : simpleToken;
        FileTreeNode firstMatch = null;
        for (var cls : index.classes()) {
            if (ClassNameUtil.sameInternalName(cls.internalName(), currentInternal)) {
                continue;
            }
            String indexedSimple = cls.simpleName();
            indexedSimple = indexedSimple.endsWith(".class") ? indexedSimple.substring(0, indexedSimple.length() - 6) : indexedSimple;
            if (ClassNameUtil.sameInternalName(cls.internalName(), normalized)
                    || ClassNameUtil.sameInternalName(cls.fullPath(), normalized)
                    || indexedSimple.equals(simpleToken)
                    || indexedSimple.endsWith("$" + simpleToken)) {
                FileTreeNode node = workspace.findNodeByPath(cls.fullPath());
                if (node == null) {
                    continue;
                }
                if (firstMatch == null) {
                    firstMatch = node;
                }
                if (samePackage(currentInternal, cls.internalName())) {
                    return node;
                }
            }
        }
        return firstMatch;
    }

    /** 通过重命名/反混淆映射反向查找原类名对应的树节点 */
    private FileTreeNode findNodeFromRenameDisplayIndex(Workspace workspace, String token,
                                                        String currentInternalName) {
        if (workspace == null || token == null || token.isBlank()) {
            return null;
        }
        String workspaceHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                .workspaceHash(workspace);
        String normalized = normalizeInternalClassName(token);
        String simple = tokenSimpleName(normalized);
        String currentPackage = packageName(currentInternalName);
        List<String> candidates = new ArrayList<>();
        candidates.add(normalized);
        if (!currentPackage.isBlank() && !simple.isBlank()) {
            candidates.add(currentPackage + "/" + simple);
        }
        candidates.add(simple);
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            List<String> originals = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .originalInternalNameCandidates(candidate, workspaceHash);
            FileTreeNode node = bestNodeForOriginalCandidates(workspace, originals, currentInternalName);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /** 从原始类名候选列表中选择最佳匹配的树节点(优先同包) */
    private FileTreeNode bestNodeForOriginalCandidates(Workspace workspace, List<String> originals,
                                                       String currentInternalName) {
        if (workspace == null || originals == null || originals.isEmpty()) {
            return null;
        }
        String current = normalizeInternalClassName(currentInternalName);
        FileTreeNode best = null;
        int bestScore = Integer.MAX_VALUE;
        for (String original : originals) {
            FileTreeNode node = findClassPathRaw(workspace, original);
            if (node == null || ClassNameUtil.sameInternalName(node.getFullPath(), current)) {
                continue;
            }
            String normalizedOriginal = normalizeInternalClassName(original);
            int score = samePackage(current, normalizedOriginal) ? 0 : 1;
            if (score < bestScore) {
                bestScore = score;
                best = node;
            }
        }
        return best;
    }

    /** 通过解析源码中的 import 语句解析 token 对应的类节点 */
    private FileTreeNode findNodeFromSourceImports(Workspace workspace, String token,
                                                   String currentClassName, String sourceCode) {
        if (workspace == null || token == null || token.isBlank()) {
            return null;
        }
        String simpleToken = tokenSimpleName(token);
        simpleToken = simpleToken.endsWith(".class") ? simpleToken.substring(0, simpleToken.length() - 6) : simpleToken;
        if (simpleToken.isBlank()) {
            return null;
        }
        String currentInternal = normalizeInternalClassName(currentClassName);
        String currentPackage = packageName(currentInternal);
        FileTreeNode samePackage = findClassPath(workspace,
                currentPackage.isBlank() ? simpleToken : currentPackage + "/" + simpleToken);
        if (samePackage != null && !ClassNameUtil.sameInternalName(samePackage.getFullPath(), currentClassName)) {
            return samePackage;
        }

        if (sourceCode == null || sourceCode.isBlank()) {
            return null;
        }
        for (String line : sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            Matcher matcher = JavaSourceAnalyzer.IMPORT_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String imported = matcher.group(2);
            if (imported == null || imported.isBlank()) {
                continue;
            }
            if (imported.endsWith(".*")) {
                String packagePath = imported.substring(0, imported.length() - 2).replace('.', '/');
                FileTreeNode wildcard = findClassPath(workspace, packagePath + "/" + simpleToken);
                if (wildcard != null) {
                    return wildcard;
                }
                continue;
            }

            String importedSimple = tokenSimpleName(imported);
            importedSimple = importedSimple.endsWith(".class") ? importedSimple.substring(0, importedSimple.length() - 6) : importedSimple;
            if (importedSimple.equals(simpleToken) || imported.endsWith("." + token)) {
                FileTreeNode importedNode = findClassPath(workspace, imported.replace('.', '/'));
                if (importedNode != null) {
                    return importedNode;
                }
                FileTreeNode innerNode = findClassPath(workspace, toInnerClassPath(imported));
                if (innerNode != null) {
                    return innerNode;
                }
            }
        }
        return null;
    }

    /** BFS 遍历整个文件树按简单类名查找节点(最慢的回退策略) */
    private FileTreeNode findNodeBySimpleNameInTree(Workspace workspace, String token,
                                                    String currentClassName) {
        if (workspace == null || token == null || token.isBlank()) {
            return null;
        }
        String simpleToken = tokenSimpleName(token);
        simpleToken = simpleToken.endsWith(".class") ? simpleToken.substring(0, simpleToken.length() - 6) : simpleToken;
        if (simpleToken.isBlank()) {
            return null;
        }
        String expectedClassFile = simpleToken + ".class";
        String currentInternal = normalizeInternalClassName(currentClassName);
        String currentPackage = packageName(currentInternal);
        List<FileTreeNode> matches = new ArrayList<>();
        FileTreeModel root = workspace.getTreeRoot();
        if (root == null) {
            return null;
        }
        ArrayDeque<FileTreeModel> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            FileTreeModel item = queue.removeFirst();
            FileTreeNode node = item.getValue();
            if (node != null && node.isClassFile()
                    && !ClassNameUtil.sameInternalName(node.getFullPath(), currentClassName)
                    && matchesSimpleClassName(node, simpleToken, expectedClassFile)) {
                matches.add(node);
            }
            queue.addAll(item.getChildren());
        }
        return matches.stream()
                .min(Comparator.comparingInt(node ->
                        samePackage(currentInternal, normalizeInternalClassName(node.getFullPath())) ? 0
                                : packageName(normalizeInternalClassName(node.getFullPath()))
                                .equals(currentPackage) ? 1 : 2))
                .orElse(null);
    }

    /** 在工作区文件树中按内部名查找类节点(含重命名回退查找) */
    private FileTreeNode findClassPath(Workspace workspace, String internalName) {
        if (workspace == null || internalName == null || internalName.isBlank()) {
            return null;
        }
        FileTreeNode raw = findClassPathRaw(workspace, internalName);
        if (raw != null) {
            return raw;
        }
        String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                .of(workspace, "").workspaceHash();
        String original = com.bingbaihanji.fxdecomplie.rename.RenameService
                .originalInternalName(internalName, wsHash);
        if (!normalizeInternalClassName(original).equals(normalizeInternalClassName(internalName))) {
            return findClassPathRaw(workspace, original);
        }
        return null;
    }

    /** 直接在工作区文件树中按内部名查找类节点(不含重命名回退) */
    private FileTreeNode findClassPathRaw(Workspace workspace, String internalName) {
        if (workspace == null || internalName == null || internalName.isBlank()) {
            return null;
        }
        for (String candidate : ClassNameUtil.classFilePathCandidates(internalName)) {
            FileTreeNode node = workspace.findNodeByPath(candidate);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /** 创建当前引擎下工作区的注释作用域 */
    private CommentScope commentScope(Workspace workspace, DecompilerTypeEnum engine) {
        return CommentScope.of(workspace,
                DecompilerOptions.hash(DecompilerOptions.forEngine(config, engine)));
    }

    /** 创建当前导出配置下工作区的注释作用域 */
    private CommentScope commentScope(Workspace workspace, ExportConfig exportConfig) {
        return CommentScope.of(workspace, DecompilerOptions.hash(
                exportConfig == null ? Map.of() : exportConfig.engineOptions()));
    }

    /** 获取当前标签页源码并应用注释装饰(用于导出时附加用户注释) */
    private String decoratedCurrentSource(CodeEditorTab codeTab) {
        if (codeTab == null || codeTab.getOpenFile() == null) {
            return "";
        }
        OpenFile openFile = codeTab.getOpenFile();
        WorkspaceView view = workspaceViewForCodeTab(codeTab);
        return CommentExportDecorator.applyForClass(openFile.sourceCode(), openFile.fullPath(),
                commentScope(view == null ? null : view.workspace(), openFile.engine()));
    }

    /** 刷新当前类的所有已打开标签页中的注释装饰显示 */
    private void refreshVisibleComments(CodeViewContext context) {
        WorkspaceView view = workspaceViewFor(context.workspace());
        if (view == null || context.openFile() == null) {
            return;
        }
        for (TabPane pane : view.splitEditorPane().allTabPanes()) {
            for (Tab tab : pane.getTabs()) {
                if (tab instanceof CodeEditorTab codeTab
                        && codeTab.getOpenFile() != null
                        && context.openFile().fullPath().equals(codeTab.getOpenFile().fullPath())
                        && context.openFile().engine() == codeTab.getOpenFile().engine()) {
                    String decorated = CommentExportDecorator.applyForClass(
                            codeTab.getOpenFile().sourceCode(),
                            codeTab.getOpenFile().fullPath(),
                            new CommentScope(context.workspaceHash(), context.optionsHash()));
                    codeTab.updateVisibleSource(decorated);
                    return;
                }
            }
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
        DecompilerTypeEnum[] engines = DecompilerTypeEnum.values();
        // 第一个引擎在主 panel 打开；其余引擎通过 splitRight 分布到不同分屏
        for (int i = 0; i < engines.length; i++) {
            final TabPane targetPane;
            if (i == 0) {
                targetPane = view.splitEditorPane().primaryTabPane();
            } else {
                TabPane newCell = view.splitEditorPane().splitRight(null);
                if (newCell != null) {
                    targetPane = newCell;
                } else {
                    // 已达最大分屏数,剩余引擎放入最右侧 cell
                    var panes = view.splitEditorPane().allTabPanes();
                    targetPane = panes.get(panes.size() - 1);
                }
            }
            classTabOpener.openClassTab(node, view.workspace(), targetPane,
                    engines[i], lineNumbersEnabled, i == 0, i == 0);
        }
    }

    @Override
    public void deobfuscate() {
        WorkspaceView view = tabManager.currentWorkspaceView();
        if (view == null) {
            return;
        }
        Workspace workspace = view.workspace();
        log.info("deobfuscate: starting, workspace={}", workspace.getName());
        statusBar.setTask("Deobfuscating");
        statusBar.setFilePath("Scanning obfuscated names...");
        // 将树遍历和扫描移至后台线程,避免阻塞 FX 线程
        BackgroundTasks.run("Deobfuscate-Scan", () -> {
            try {
                java.util.List<FileTreeNode> nodesSnapshot = new java.util.ArrayList<>();
                collectTreeNodes(workspace.getTreeRoot(), nodesSnapshot);
                long classNodeCount = nodesSnapshot.stream().filter(FileTreeNode::isClassFile).count();
                WorkspaceIndex index = workspace.isIndexReady()
                        ? workspace.getIndex()
                        : WorkspaceIndex.EMPTY;
                java.util.List<com.bingbaihanji.fxdecomplie.rename.RenameEntry> suggestions =
                        com.bingbaihanji.fxdecomplie.rename.AutoDeobfuscator.scan(nodesSnapshot, index);
                boolean memberScanComplete = index != WorkspaceIndex.EMPTY;
                log.info("deobfuscate: scan returned {} suggestions (nodes={}, memberScanComplete={})",
                        suggestions.size(), nodesSnapshot.size(), memberScanComplete);
                if (!memberScanComplete) {
                    WorkspaceIndexService.ensureIndexingStarted(workspace);
                }
                Platform.runLater(() ->
                        showDeobfuscatePreview(workspace, suggestions, memberScanComplete, classNodeCount));
            } catch (Exception ex) {
                log.error("反混淆扫描失败", ex);
                Platform.runLater(() -> {
                    statusBar.clearTask();
                    showError(I18nUtil.getString("dialog.error.title"),
                            "Deobfuscate failed: " + ex.getMessage());
                });
            }
        });
    }

    /** 显示反混淆预览对话框,提交用户选择的重命名条目并刷新所有相关标签页 */
    private void showDeobfuscatePreview(Workspace workspace,
                                        java.util.List<com.bingbaihanji.fxdecomplie.rename.RenameEntry> suggestions,
                                        boolean memberScanComplete,
                                        long classNodeCount) {
        statusBar.clearTask();
        if (suggestions == null || suggestions.isEmpty()) {
            com.bingbaihanji.fxdecomplie.rename.DeobfuscatePreviewDialog.show(stage, List.of());
            if (!memberScanComplete) {
                statusBar.setFilePath("No obfuscated names found in " + classNodeCount
                        + " class nodes. Indexing continues for fields and methods.");
            }
            return;
        }
        log.info("showDeobfuscatePreview: {} suggestions to show", suggestions.size());
        String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                .of(workspace, "").workspaceHash();
        java.util.List<com.bingbaihanji.fxdecomplie.rename.RenameEntry> selected =
                com.bingbaihanji.fxdecomplie.rename.DeobfuscatePreviewDialog.show(stage, suggestions);
        log.info("showDeobfuscatePreview: dialog returned {} selected entries", selected.size());
        if (selected.isEmpty()) {
            statusBar.setFilePath("Deobfuscate cancelled or no entries selected");
            return;
        }
        int saved = com.bingbaihanji.fxdecomplie.rename.RenameService.saveAll(wsHash, selected, true);
        if (saved == 0) {
            statusBar.setFilePath("Deobfuscate failed: rename mapping was not saved");
            showError(I18nUtil.getString("dialog.error.title"),
                    "Deobfuscate failed: rename mapping was not saved.");
            return;
        }
        if (saved != selected.size()) {
            log.warn("反混淆批量保存未完全成功: selected={}, saved={}", selected.size(), saved);
        }
        if (workspace != null) {
            workspace.clearSourceSearchCaches();
        }
        int reloadTabs = reloadOpenTabsAfterDeobfuscate(workspace);
        refreshWorkspaceTree(workspace);
        statusBar.setFilePath("Deobfuscated: " + selected.size()
                + " selected, " + saved + " saved, " + reloadTabs + " tabs reload scheduled"
                + (memberScanComplete ? "" : "; index still building for member names"));
    }

    @Override
    public void importProGuardMapping() {
        WorkspaceView view = tabManager.currentWorkspaceView();
        if (view == null) {
            showWarning("Import ProGuard Mapping", I18nUtil.getString("dialog.export.noworkspace"));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import ProGuard Mapping");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Mapping Files", "*.txt", "*.map"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            String text = java.nio.file.Files.readString(file.toPath());
            java.util.List<com.bingbaihanji.fxdecomplie.rename.RenameEntry> entries =
                    com.bingbaihanji.fxdecomplie.rename.RenameService.parseProGuardMapping(text);
            if (entries.isEmpty()) {
                showWarning("Import ProGuard Mapping", "No mapping entries found.");
                return;
            }
            Workspace workspace = view.workspace();
            String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                    .of(workspace, "").workspaceHash();
            int saved = com.bingbaihanji.fxdecomplie.rename.RenameService.saveAll(wsHash, entries, true);
            if (saved == 0) {
                showError("Import ProGuard Mapping", "No mapping entries could be saved.");
                return;
            }
            workspace.clearSourceSearchCaches();
            int reloadTabs = reloadOpenTabsAfterDeobfuscate(workspace);
            refreshWorkspaceTree(workspace);
            statusBar.setFilePath("Imported mapping: " + saved + " saved, "
                    + reloadTabs + " tabs reload scheduled");
        } catch (java.io.IOException e) {
            log.error("导入 ProGuard mapping 失败", e);
            showError("Import ProGuard Mapping", e.getMessage());
        }
    }

    @Override
    public void exportProGuardMapping() {
        WorkspaceView view = tabManager.currentWorkspaceView();
        if (view == null) {
            showWarning("Export ProGuard Mapping", I18nUtil.getString("dialog.export.noworkspace"));
            return;
        }
        String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                .of(view.workspace(), "").workspaceHash();
        String mapping = com.bingbaihanji.fxdecomplie.rename.RenameService.exportProGuard(wsHash);
        if (mapping.isBlank()) {
            showWarning("Export ProGuard Mapping", "No rename mappings found.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export ProGuard Mapping");
        chooser.setInitialFileName("mapping.txt");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Mapping Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), mapping);
            statusBar.setFilePath("Exported mapping: " + file.getAbsolutePath());
        } catch (java.io.IOException e) {
            log.error("导出 ProGuard mapping 失败", e);
            showError("Export ProGuard Mapping", e.getMessage());
        }
    }

    @Override
    public void restoreLastRenameSnapshot() {
        WorkspaceView view = tabManager.currentWorkspaceView();
        if (view == null) {
            showWarning("Restore Rename Snapshot", I18nUtil.getString("dialog.export.noworkspace"));
            return;
        }
        Workspace workspace = view.workspace();
        String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                .of(workspace, "").workspaceHash();
        boolean restored = com.bingbaihanji.fxdecomplie.rename.RenameService
                .restoreLatestBackup(wsHash);
        if (!restored) {
            statusBar.setFilePath("No rename snapshot found");
            showWarning("Restore Rename Snapshot", "No rename snapshot found for current workspace.");
            return;
        }
        workspace.clearSourceSearchCaches();
        int reloadTabs = reloadOpenTabsAfterDeobfuscate(workspace);
        refreshWorkspaceTree(workspace);
        statusBar.setFilePath("Rename snapshot restored, " + reloadTabs + " tabs reloaded");
    }

    /** 反混淆后重新反编译所有已打开的代码标签页以应用新的重命名映射 */
    private int reloadOpenTabsAfterDeobfuscate(Workspace workspace) {
        WorkspaceView view = workspaceViewFor(workspace);
        if (view == null) {
            return 0;
        }
        classTabOpener.cancelCurrentTask();
        java.util.List<java.util.Map.Entry<TabPane, CodeEditorTab>> tabs = new java.util.ArrayList<>();
        for (TabPane pane : view.splitEditorPane().allTabPanes()) {
            for (Tab tab : pane.getTabs()) {
                if (tab instanceof CodeEditorTab codeTab && codeTab.getOpenFile() != null) {
                    tabs.add(java.util.Map.entry(pane, codeTab));
                }
            }
        }
        for (var item : tabs) {
            classTabOpener.refreshCurrentClassTab(workspace, item.getKey(), item.getValue(),
                    item.getValue().getOpenFile().engine(), lineNumbersEnabled, false, false);
        }
        return tabs.size();
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
        java.util.Map<String, String> sourceCache = new HashMap<>();
        for (TabPane pane : view.splitEditorPane().allTabPanes()) {
            for (javafx.scene.control.Tab tab : pane.getTabs()) {
                if (tab instanceof CodeEditorTab codeTab
                        && codeTab.getOpenFile().engine() == currentEngine
                        && codeTab.isSourceReady()) {
                    sourceCache.put(codeTab.getOpenFile().fullPath(),
                            codeTab.getOpenFile().sourceCode());
                }
            }
        }

        // 构建 classPath → displayName 映射(支持搜索反混淆/重命名后的名称)
        String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                .of(view.workspace(), "").workspaceHash();
        java.util.Map<String, String> displayNamesByPath = new HashMap<>();
        for (String classPath : index.classPaths()) {
            String display = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .displayClassName(classPath, wsHash);
            if (display != null && !display.isBlank()) {
                String pathWithoutExt = classPath.endsWith(".class")
                        ? classPath.substring(0, classPath.length() - 6) : classPath;
                String simpleOriginal = pathWithoutExt.substring(
                        pathWithoutExt.lastIndexOf('/') + 1);
                // 仅当 display 与原始简单名不同时才加入映射(有重命名/反混淆)
                if (!display.equals(simpleOriginal)) {
                    displayNamesByPath.put(classPath, display + ".class");
                }
            }
        }

        // 创建包含所有 Provider 的 SearchService
        SearchService searchService = new SearchService();
        searchService.setExcludePatterns(config.search().excludePatterns());
        searchService.addProvider(new ClassSearchProvider(index.classPaths(), displayNamesByPath));
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
        java.util.List<String> classNames = new java.util.ArrayList<>();
        collectClassNames(view.workspace().getTreeRoot(), classNames);
        QuickOpenDialog.show(stage, classNames, fullPath -> {
            FileTreeNode node = view.workspace().findNodeByPath(fullPath);
            if (node != null) {
                classTabOpener.openClassTab(node, view.workspace(), view.codeTabPane(),
                        currentEngine, lineNumbersEnabled);
            } else {
                statusBar.setFilePath(I18nUtil.getString("status.locateFailed", fullPath));
            }
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
        if (!currentTab.isSourceReady()) {
            showWarning(I18nUtil.getString("dialog.warning.title"),
                    I18nUtil.getString("dialog.save.pending"));
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
        DialogHelper.applyNativeStyle(alert);

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
        log.info("显示关于对话框");
        alert.showAndWait();
    }

    // ── 内部辅助方法 ──

    /** 打开设置对话框 */
    @Override
    public void openSettings() {
        String oldEditorTheme = config.theme().editorTheme();
        SettingsDialog.show(stage, config, updated -> {
            boolean engineSwitched = applySettings(updated);

            // 检测编辑器主题变更 — 必须在 refreshCurrentTab 之前更新 ClassTabOpener,
            // 否则异步反编译任务会用旧主题创建新标签页覆盖掉 reapplyTheme 的结果
            String newEditorTheme = config.theme().editorTheme();
            if (!newEditorTheme.equals(oldEditorTheme)) {
                editorTheme = AppTheme.loadEditorTheme(config);
                classTabOpener.setEditorTheme(editorTheme);
                tabManager.getWorkspaceViews().values().forEach(view ->
                        view.splitEditorPane().forEachTab(tab ->
                                tab.reapplyTheme(editorTheme))
                );
            }

            DecompilerTypeEnum activeTabEngine = activeCodeTabEngine();
            if (!engineSwitched && tabManager != null && tabManager.currentCodeTab() != null) {
                refreshCurrentTab(activeTabEngine);
            }
        });
    }

    /** 应用设置对话框确认后的配置变更：切换引擎、更新行号显示和字体设置,返回是否切换了引擎 */
    private boolean applySettings(AppConfig updated) {
        DecompilerTypeEnum configuredEngine = updated.decompiler().defaultEngine();
        boolean engineSwitched = false;
        if (configuredEngine != currentEngine) {
            selectEngine(configuredEngine);
            engineSwitched = true;
        }
        lineNumbersEnabled = updated.decompiler().lineNumbersEnabled();
        int newFontSize = updated.theme().fontSize();
        String newFontFamily = updated.theme().fontFamily();
        tabManager.getWorkspaceViews().values().forEach(view ->
                view.splitEditorPane().forEachTab(tab -> {
                    tab.setLineNumbersEnabled(lineNumbersEnabled);
                    tab.applyFontSettings(newFontSize, newFontFamily);
                })
        );

        return engineSwitched;
    }

    /** 获取当前活动代码标签页实际使用的反编译引擎 */
    private DecompilerTypeEnum activeCodeTabEngine() {
        CodeEditorTab currentTab = tabManager == null ? null : tabManager.currentCodeTab();
        if (currentTab == null || currentTab.getOpenFile() == null) {
            return currentEngine;
        }
        return currentTab.getOpenFile().engine();
    }

    /** 将导出对话框中选择的选项写回全局配置,下次打开时记住 */
    private void persistExportConfig(ExportConfig exportConfig) {
        boolean followCurrent = config.export().defaultEngine().isBlank();
        if (!followCurrent || exportConfig.engine() != currentEngine) {
            config.export().defaultEngine(exportConfig.engine().name());
        }
        config.export().defaultFormat(exportConfig.format().name());
        config.export().conflictPolicy(exportConfig.conflictPolicy().name());
        config.export().exportResources(exportConfig.exportResources());
        config.export().lastPath(exportConfig.outputPath().toString());
        config.save();
    }

    /** 根据导出结果弹出成功/部分成功对话框,支持打开输出目录和复制错误详情列表 */
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

    /** 延迟轮询工作区标签页,等待反编译完成并将 CodeArea 滚动到目标行(最多 2 秒) */
    private void navigateToLine(WorkspaceView view, String fullPath, int lineNumber, int retries) {
        // 最多约 2 秒
        if (retries > 20) {
            statusBar.setFilePath(I18nUtil.getString("status.navigateTimeout", fullPath));
            return;
        }
        if (!tabManager.isWorkspaceActive(view)) {
            return; // 工作区已关闭
        }
        javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(100));
        delay.setOnFinished(e -> {
            if (!tabManager.isWorkspaceActive(view)) {
                return;
            }
            for (TabPane pane : view.splitEditorPane().allTabPanes()) {
                for (javafx.scene.control.Tab tab : pane.getTabs()) {
                    if (tab instanceof CodeEditorTab codeTab
                            && codeTab.getOpenFile() != null
                            && codeTab.getOpenFile().fullPath().equals(fullPath)) {
                        var area = codeTab.getCodeArea();
                        if (area.getText() != null && !area.getText().isEmpty()) {
                            try {
                                codeTab.revealLine(lineNumber);
                                statusBar.setFilePath(I18nUtil.getString(
                                        "status.navigatedTo", fullPath, lineNumber));
                            } catch (Exception ignored) {
                                log.debug("导航跳转行失败", ignored);
                            }
                            return;
                        }
                        navigateToLine(view, fullPath, lineNumber, retries + 1);
                        return;
                    }
                }
            }
        });
        delay.play();
    }

    /** 递归收集树节点数据(在 FX 线程调用,避免后台线程访问 TreeItem) */
    private void collectTreeNodes(TreeItem<FileTreeNode> item,
                                  java.util.List<FileTreeNode> result) {
        FileTreeNode data = item.getValue();
        if (data != null) {
            result.add(data);
        }
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            collectTreeNodes(child, result);
        }
    }

    /** 递归收集树节点数据(model 层 FileTreeModel 版本) */
    private void collectTreeNodes(FileTreeModel item,
                                  java.util.List<FileTreeNode> result) {
        FileTreeNode data = item.getValue();
        if (data != null) {
            result.add(data);
        }
        for (FileTreeModel child : item.getChildren()) {
            collectTreeNodes(child, result);
        }
    }

    /** 递归收集文件树中所有 .class 节点的完整路径(用于快速打开对话框) */
    private void collectClassNames(FileTreeModel item, java.util.List<String> result) {
        FileTreeNode data = item.getValue();
        if (data != null && data.isClassFile()) {
            result.add(data.getFullPath());
        }
        for (FileTreeModel child : item.getChildren()) {
            collectClassNames(child, result);
        }
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
                byte[] classBytes = cls.bytes();
                if (classBytes == null || classBytes.length == 0) {
                    log.debug("全文搜索跳过不可读类: {}", cls.fullPath());
                    continue;
                }
                String fp = ClassTabOpener.computeClassFingerprint(classBytes);
                String source = classTabOpener.getDecompileCache().get(
                        workspaceKey(view.workspace()) + "_" + fp, cls.internalName(),
                        currentEngine, DecompilerOptions.hash(engineOptions));
                if (source == null) {
                    // L2 miss: 带超时和 JD 回退的全量解编译
                    source = DecompilerRunner.decompileWithTimeout(
                            cls.fullPath(), classBytes, currentEngine, context,
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

    /** 异步加载并打开文件(JAR/ZIP/Class/目录),在工作区标签页中展示文件树和反编译内容 */
    private void loadFile(File file) {
        log.info("loadFile: {} (size={}, isDir={})", file.getAbsolutePath(),
                file.length(), file.isDirectory());
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
                            this::openSearchForPackage,
                            (node, codeTabPane) -> classTabOpener.openFileInHexView(
                                    node, workspace, codeTabPane));
                    refreshToolbarState();
                },
                errorMsg -> {
                    statusBar.clearTask();
                    showError(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.load.error") + ": " + errorMsg);
                });
    }

    /** 显示导出成功对话框(无错误详情列表) */
    private void showExportDoneDialog(String title, String message, java.nio.file.Path outputPath) {
        showExportDoneDialog(title, message, outputPath, java.util.List.of());
    }

    /** 显示导出完成对话框(含错误详情列表及打开输出目录/复制详情按钮) */
    private void showExportDoneDialog(String title, String message, java.nio.file.Path outputPath,
                                      java.util.List<String> details) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        DialogHelper.applyNativeStyle(alert);
        ButtonType openOutput = new ButtonType(I18nUtil.getString("dialog.export.openOutput"));
        alert.getButtonTypes().add(openOutput);
        log.info("显示导出完成对话框: {}", message);
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
            return workspace.getIndexFuture().get(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("等待工作区索引超时或失败", e);
            return WorkspaceIndex.EMPTY;
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
                        (node.getFullPath().endsWith(".class") ? node.getFullPath().substring(0, node.getFullPath().length() - 6) : node.getFullPath())));
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

}
