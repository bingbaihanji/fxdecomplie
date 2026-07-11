package com.bingbaihanji.fxdecomplie.controller;


import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.*;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.CommentExportDecorator;
import com.bingbaihanji.fxdecomplie.service.CommentManager;
import com.bingbaihanji.fxdecomplie.service.DecompilerOptions;
import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceTabManager;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceView;
import com.bingbaihanji.fxdecomplie.ui.code.*;
import com.bingbaihanji.fxdecomplie.ui.comment.CommentDialog;
import com.bingbaihanji.fxdecomplie.ui.menu.MainMenuBar;
import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.fxdecomplie.ui.toolbar.MainToolBar;
import com.bingbaihanji.fxdecomplie.ui.window.AppHeaderBar;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

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
    /** 编辑器动作控制器 (剪贴板/缩放/行号/工具窗口) */
    private final EditorActionsController editorActions = new EditorActionsController(this);
    /** 代码导航控制器 (Ctrl+Click 跳转/引用解析/类节点查找) */
    private final NavigationController navigationController = new NavigationController(this);
    /** 重命名与反混淆控制器 (重命名/ProGuard 映射/反混淆预览/快照恢复) */
    private final RenameController renameController = new RenameController(this);
    /** 反混淆与 ProGuard 映射控制器 (自动反混淆/映射导入导出/快照恢复) */
    private final DeobfuscationController deobfuscationController = new DeobfuscationController(this);
    /** 搜索与用法查找控制器 (搜索对话框/Find Usages/包搜索/全文缓存/索引等待) */
    private final SearchController searchController = new SearchController(this);
    /** 引擎切换与图形展示控制器 (引擎切换/标签页刷新/继承图/CFG/方法图/引擎对比) */
    private final EngineController engineController = new EngineController(this);
    /** 设置控制器 (设置对话框/配置应用) */
    private final SettingsController settingsController = new SettingsController(this);
    /** 导出控制器 (批量导出/子树导出/导出配置持久化/导出结果对话框) */
    private final ExportController exportController = new ExportController(this);
    /** 工作区控制器 (打开文件/目录/项目、保存项目、关闭工作区、快速打开、最近文件、工作区加载) */
    private final WorkspaceController workspaceController = new WorkspaceController(this);
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
    private DecompilerTypeEnum currentEngine = DecompilerTypeEnum.JADX;
    /** 是否显示行号 */
    private boolean lineNumbersEnabled;
    /** 顶部工具栏 */
    private MainToolBar toolBar;
    /** 主菜单栏 */
    private MainMenuBar menuBar;

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

    /** 判断文件是否为可加载类型(JAR/ZIP/WAR/Class 及目录) */
    private static boolean isSupportedLoadFile(File file) {
        if (file.isDirectory()) {
            return true;
        }
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".zip")
                || name.endsWith(".war") || name.endsWith(".class");
    }

    // --- 供控制器访问共享状态的包级私有访问器 (Mediator 模式)---
    AppConfig config() {
        return config;
    }

    WorkspaceTabManager tabManager() {
        return tabManager;
    }

    MainToolBar toolBar() {
        return toolBar;
    }

    boolean lineNumbersEnabled() {
        return lineNumbersEnabled;
    }

    void setLineNumbersEnabled(boolean enabled) {
        this.lineNumbersEnabled = enabled;
    }

    ClassTabOpener classTabOpener() {
        return classTabOpener;
    }

    StatusBar statusBar() {
        return statusBar;
    }

    DecompilerTypeEnum currentEngine() {
        return currentEngine;
    }

    void setCurrentEngine(DecompilerTypeEnum engine) {
        this.currentEngine = engine;
    }

    MainMenuBar menuBar() {
        return menuBar;
    }

    Stage stage() {
        return stage;
    }

    HostServices hostServices() {
        return hostServices;
    }

    NavigationController navigationController() {
        return navigationController;
    }

    SearchController searchController() {
        return searchController;
    }

    EngineController engineController() {
        return engineController;
    }

    ExportController exportController() {
        return exportController;
    }

    TabPane outerTabPane() {
        return outerTabPane;
    }

    VsCodeThemeLoader.ThemeData editorTheme() {
        return editorTheme;
    }

    void setEditorTheme(VsCodeThemeLoader.ThemeData theme) {
        this.editorTheme = theme;
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
                        workspaceController.loadFile(f);
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
    void refreshToolbarState() {
        editorActions.refreshToolbarState();
    }

    /** 打开 JAR/ZIP/Class 文件 */
    @Override
    public void openFile() {
        workspaceController.openFile();
    }

    /** 打开目录 */
    @Override
    public void openDirectory() {
        workspaceController.openDirectory();
    }

    /** 打开项目文件 */
    @Override
    public void openProject() {
        workspaceController.openProject();
    }

    /** 保存当前项目文件 */
    @Override
    public void saveProject() {
        workspaceController.saveProject();
    }

    /** 关闭当前选中的工作区标签页(含解编译结果和文件树) */
    @Override
    public void closeCurrentWorkspace() {
        workspaceController.closeCurrentWorkspace();
    }

    /** 关闭除当前选中之外的所有工作区标签页 */
    @Override
    public void closeOtherWorkspaces() {
        workspaceController.closeOtherWorkspaces();
    }

    // ─── CodeActionHandler 实现 ─────────────────────────────────

    /** 保存当前代码标签页为 .java 文件 */
    @Override
    public void saveCurrentFile() {
        workspaceController.saveCurrentFile();
    }

    /** 批量导出所有类为 .java 文件 */
    @Override
    public void exportAllFiles() {
        exportController.exportAllFiles();
    }

    /** 退出应用 */
    @Override
    public void exit() {
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
        engineController.selectEngine(engine);
    }

    /** 用当前引擎重新反编译当前类 */
    @Override
    public void refreshCurrentTab() {
        engineController.refreshCurrentTab();
    }

    /** 在文件树中定位当前打开的类文件 */
    @Override
    public void locateCurrentFileInTree() {
        workspaceController.locateCurrentFileInTree();
    }

    @Override
    public void goToDeclaration(CodeMetadata.Reference reference) {
        navigationController.goToDeclaration(reference);
    }

    @Override
    public void goToDeclaration(CodeViewContext context, int lineNumber, String token) {
        navigationController.goToDeclaration(context, lineNumber, token);
    }

    @Override
    public void openClass(String fullPath, int line) {
        navigationController.openClass(fullPath, line);
    }

    @Override
    public void showInheritanceGraph(CodeViewContext context) {
        engineController.showInheritanceGraph(context);
    }

    @Override
    public void showControlFlowGraph(CodeViewContext context) {
        engineController.showControlFlowGraph(context);
    }

    @Override
    public void showMethodGraph(CodeViewContext context) {
        engineController.showMethodGraph(context);
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
        renameController.renameAtCaret(context, caret);
    }

    /** 根据 CodeViewContext 的路径和引擎查找对应的代码标签页(用于重命名操作定位) */
    CodeEditorTab codeTabForContext(WorkspaceView view, CodeViewContext context) {
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

    @Override
    public void searchInWorkspace(String selectedText) {
        searchController.searchInWorkspace(selectedText);
    }

    /** 复制引用到剪贴板后在状态栏显示提示 */
    @Override
    public void copyReference(String referenceText) {
        searchController.copyReference(referenceText);
    }

    /** 根据工作区对象查找其对应的 WorkspaceView */
    WorkspaceView workspaceViewFor(Workspace workspace) {
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
    byte[] classBytesForContext(CodeViewContext context) throws IOException {
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

    /** 创建当前引擎下工作区的注释作用域 */
    CommentScope commentScope(Workspace workspace, DecompilerTypeEnum engine) {
        return CommentScope.of(workspace,
                DecompilerOptions.hash(DecompilerOptions.forEngine(config, engine)));
    }

    /** 创建当前导出配置下工作区的注释作用域 */
    CommentScope commentScope(Workspace workspace, ExportConfig exportConfig) {
        return CommentScope.of(workspace, DecompilerOptions.hash(
                exportConfig == null ? Map.of() : exportConfig.engineOptions()));
    }

    /** 获取当前标签页源码并应用注释装饰(用于导出时附加用户注释) */
    String decoratedCurrentSource(CodeEditorTab codeTab) {
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
        engineController.compareEngines();
    }

    @Override
    public void deobfuscate() {
        deobfuscationController.deobfuscate();
    }

    @Override
    public void importProGuardMapping() {
        deobfuscationController.importProGuardMapping();
    }

    @Override
    public void exportProGuardMapping() {
        deobfuscationController.exportProGuardMapping();
    }

    @Override
    public void restoreLastRenameSnapshot() {
        deobfuscationController.restoreLastRenameSnapshot();
    }

    /** 打开搜索对话框 */
    @Override
    public void openSearch() {
        searchController.openSearch();
    }

    /** 查找当前工作区内的类/方法/字段使用 */
    @Override
    public void openFindUsages() {
        searchController.openFindUsages();
    }

    /** 快速打开类 */
    @Override
    public void quickOpenClass() {
        workspaceController.quickOpenClass();
    }

    /** 打开最近文件 */
    @Override
    public void openRecentFile(String path) {
        workspaceController.openRecentFile(path);
    }

    /** 启动参数或外部入口打开文件/目录 */
    public void openInitialFile(File file) {
        workspaceController.openInitialFile(file);
    }

    /** 获取最近文件列表 */
    @Override
    public java.util.List<String> getRecentFiles() {
        return workspaceController.getRecentFiles();
    }

    @Override
    public void clearRecentFiles() {
        workspaceController.clearRecentFiles();
    }

    /** 打开新窗口 */
    @Override
    public void openNewWindow() {
        workspaceController.openNewWindow();
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
        settingsController.openSettings();
    }

    /** 递归收集树节点数据(model 层 FileTreeModel 版本) */
    void collectTreeNodes(FileTreeModel item,
                          java.util.List<FileTreeNode> result) {
        FileTreeNode data = item.getValue();
        if (data != null) {
            result.add(data);
        }
        for (FileTreeModel child : item.getChildren()) {
            collectTreeNodes(child, result);
        }
    }

    /** 弹出错误对话框(标准错误标题) */
    void showError(String message) {
        showError(I18nUtil.getString("dialog.error.title"), message);
    }

    /** 弹出错误对话框 */
    void showError(String title, String message) {
        com.bingbaihanji.fxdecomplie.ui.DialogHelper.showError(stage, title, message);
    }

    /** 弹出警告对话框 */
    void showWarning(String title, String message) {
        com.bingbaihanji.fxdecomplie.ui.DialogHelper.showWarning(stage, title, message);
    }

    /**
     * 返回当前工作区视图 若无打开的工作区,弹出警告并返回 {@code null}
     * 用于收敛各控制器中重复的 "view == null → 警告 → return" 守卫
     *
     * @param warnTitle   警告标题(i18n 已解析)
     * @param warnMessage 警告内容(i18n 已解析)
     * @return 当前工作区视图,或 {@code null}
     */
    WorkspaceView requireWorkspaceOrWarn(String warnTitle, String warnMessage) {
        WorkspaceView view = tabManager.currentWorkspaceView();
        if (view == null) {
            showWarning(warnTitle, warnMessage);
        }
        return view;
    }

    /** 刷新指定工作区的文件树可见单元格(重命名/反混淆后,由 Rename/Deobfuscation 控制器共用) */
    void refreshWorkspaceTree(Workspace workspace) {
        WorkspaceView view = workspaceViewFor(workspace);
        if (view != null && view.treeView() != null) {
            view.treeView().refreshVisibleCells();
        }
    }

}
