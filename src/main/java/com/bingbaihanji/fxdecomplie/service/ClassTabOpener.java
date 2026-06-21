package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.OpenFile;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceTabManager;
import com.bingbaihanji.fxdecomplie.ui.code.CodeOnlyWindow;
import com.bingbaihanji.fxdecomplie.ui.code.CodeActionHandler;
import com.bingbaihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbaihanji.fxdecomplie.ui.code.CodeViewContext;
import com.bingbaihanji.fxdecomplie.ui.code.LineNumberGutter;
import com.bingbaihanji.fxdecomplie.ui.code.StatusBar;
import com.bingbaihanji.fxdecomplie.ui.outline.OutlineParser;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 负责 class 文件的反编译和代码标签页的创建/刷新
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class ClassTabOpener {

    private static final Logger logger = LoggerFactory.getLogger(ClassTabOpener.class);
    /** class 打开属于交互任务,使用专用虚拟线程,避免被索引/搜索任务队列拖慢 */
    private static final ExecutorService OPEN_EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("class-open-", 0).factory());
    /** 超过该大小时跳过源码 metadata 提取，避免打开大类时延迟首屏显示 */
    private static final int METADATA_SOURCE_THRESHOLD = 500_000;
    /** 应用配置 */
    private final AppConfig config;
    /** 编辑器主题数据 */
    private final VsCodeThemeLoader.ThemeData editorTheme;
    /** 状态栏引用 */
    private final StatusBar statusBar;
    /** L2 反编译源码缓存,避免重复反编译已打开的类 */
    private final DecompileCache decompileCache = new DecompileCache();

    /** 暴露 L2 缓存给全文搜索等批量解编译场景复用已打开的标签页结果 */
    public DecompileCache getDecompileCache() {
        return decompileCache;
    }

    /** 设置代码操作回调，用于自动安装右键菜单 */
    public void setCodeActionHandler(CodeActionHandler handler) {
        this.codeActionHandler = handler;
    }
    /** 当前运行的反编译任务,用于在切换时取消 */
    private volatile Future<?> currentDecompileTask;
    /** 当前由主导航创建的占位标签,用于任务取消时清理 */
    private volatile Tab currentLoadingTab;
    /** 代码操作回调，用于安装右键菜单 */
    private volatile CodeActionHandler codeActionHandler;
    /** 主导航反编译请求序号,用于丢弃快速切换产生的过期结果 */
    private final AtomicLong decompileGeneration = new AtomicLong();

    public ClassTabOpener(AppConfig config, VsCodeThemeLoader.ThemeData editorTheme, StatusBar statusBar) {
        this.config = config;
        this.editorTheme = editorTheme;
        this.statusBar = statusBar;
    }

    /** 提取简短类名(去 .class 后缀) */
    private static String className(FileTreeNode node) {
        String name = node.getName();
        return name.endsWith(".class") ? name.substring(0, name.length() - 6) : name;
    }

    /** 计算工作区缓存键：使用完整路径 + mtime + size,消除路径碰撞和同路径替换误命中 */
    private static String computeWorkspaceKey(Workspace workspace) {
        File source = workspace.getSourceFile();
        long mtime = source.lastModified();
        long size = source.isFile() ? source.length() : 0L;
        return (source.getAbsolutePath() + "_" + mtime + "_" + size)
                .replace(':', '_').replace('\\', '_').replace('/', '_');
    }

    /**
     * 判断异常是否由线程中断引起(用户导航取消任务,不应弹窗)
     * 递归检查异常链中每一层,最终兜底检查当前线程中断标志
     */
    private static boolean isInterruptRelated(Exception ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof InterruptedException) return true;
            if (cause instanceof CancellationException) return true;
            cause = cause.getCause();
        }
        return Thread.currentThread().isInterrupted();
    }

    /** 检测字节数组的文本编码并解码为字符串(支持 BOM、UTF-8、UTF-16、ISO-8859-1 回退) */
    private static String decodeText(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2) {
            if ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                return new String(bytes, 2, bytes.length - 2, java.nio.charset.StandardCharsets.UTF_16);
            }
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                return new String(bytes, 2, bytes.length - 2, java.nio.charset.StandardCharsets.UTF_16LE);
            }
        }
        try {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * 打开 class 文件的代码标签页如果已打开则切换到已有标签页
     *
     * @param node               文件树节点
     * @param workspace          工作区
     * @param codeTabPane        代码标签页面板
     * @param engine             反编译引擎
     * @param lineNumbersEnabled 是否显示行号
     */
    public void openClassTab(FileTreeNode node, Workspace workspace, TabPane codeTabPane,
                             DecompilerTypeEnum engine, boolean lineNumbersEnabled) {
        openClassTab(node, workspace, codeTabPane, engine, lineNumbersEnabled, true, true);
    }

    public void openClassTab(FileTreeNode node, Workspace workspace, TabPane codeTabPane,
                             DecompilerTypeEnum engine, boolean lineNumbersEnabled,
                             boolean cancelPrevious, boolean replaceDifferentEngine) {
        Tab existingTab = findOrRemoveOpenClassTab(codeTabPane, node, engine, replaceDifferentEngine);
        if (existingTab != null) {
            codeTabPane.getSelectionModel().select(existingTab);
            return;
        }

        statusBar.setFilePath(I18nUtil.getString("status.decompiling", node.getFullPath()));
        statusBar.setTask(I18nUtil.getString("task.decompiling"));

        if (cancelPrevious) {
            BackgroundTasks.cancel(currentDecompileTask);
            removeLoadingTab(codeTabPane, currentLoadingTab);
        }
        long requestId = cancelPrevious ? decompileGeneration.incrementAndGet() : -1L;

        Tab loadingTab = createLoadingTab(node, engine);
        AtomicReference<Future<?>> taskRef = new AtomicReference<>();
        AtomicReference<Tab> activeLoadingRef = new AtomicReference<>(loadingTab);
        loadingTab.setOnClosed(e -> BackgroundTasks.cancel(taskRef.get()));
        codeTabPane.getTabs().add(loadingTab);
        codeTabPane.getSelectionModel().select(loadingTab);
        if (cancelPrevious) {
            currentLoadingTab = loadingTab;
        }

        Future<?> task = runOpenTask("Decompile-" + node.getName(), () -> {
            try {
                if (!isRequestCurrent(requestId, cancelPrevious)) {
                    return;
                }
                byte[] bytes = readClassBytes(node, workspace);
                if (!isRequestCurrent(requestId, cancelPrevious)) {
                    return;
                }
                if (bytes == null) {
                    Platform.runLater(() -> {
                        if (!isRequestCurrent(requestId, cancelPrevious)) {
                            removeLoadingTab(codeTabPane, loadingTab);
                            return;
                        }
                        if (!isTabOpen(codeTabPane, loadingTab)) {
                            clearTaskIfCurrent(loadingTab);
                            return;
                        }
                        boolean clearTask = shouldClearTaskFor(loadingTab);
                        removeLoadingTab(codeTabPane, loadingTab);
                        showAlert(I18nUtil.getString("dialog.error.title"),
                                I18nUtil.getString("dialog.read.unable", node.getFullPath()));
                        if (clearTask) {
                            statusBar.clearTask();
                        }
                    });
                    return;
                }

                String internalName = node.getFullPath().replace(".class", "");
                DecompileResult result = decompileWithCache(internalName, engine, bytes, node, workspace,
                        () -> isRequestCurrent(requestId, cancelPrevious));
                if (!isRequestCurrent(requestId, cancelPrevious)) {
                    return;
                }
                String sourceCode = result.sourceCode();
                CodeMetadata metadata = result.metadata();
                DecompilerTypeEnum usedEngine = result.engine();
                Consumer<CodeMetadata.Reference> completedNavigate = createNavigationHandler(
                        workspace, codeTabPane, usedEngine, lineNumbersEnabled);
                OpenFile openFile = new OpenFile(className(node), node.getFullPath(), sourceCode, usedEngine);

                Platform.runLater(() -> {
                    if (!isRequestCurrent(requestId, cancelPrevious)) {
                        removeLoadingTab(codeTabPane, activeLoadingRef.get());
                        return;
                    }
                    CodeEditorTab codeTab = createCodeEditorTab(openFile, lineNumbersEnabled, bytes,
                            metadata, completedNavigate);
                    if (!replaceLoadingTab(codeTabPane, activeLoadingRef.get(), codeTab)) {
                        return;
                    }
                    codeTabPane.getSelectionModel().select(codeTab);
                    statusBar.setFilePath(
                            WorkspaceTabManager.formatClassPath(node.getFullPath()));
                    statusBar.setEncoding("UTF-8");
                    statusBar.setEngine(usedEngine.name());
                    statusBar.clearTask();
                    bindCaretPosition(codeTab);
                    installContextMenu(codeTab, node, workspace, openFile, bytes, metadata);
                });
            } catch (Exception ex) {
                // 用户导航到其他类时任务被取消,中断异常是预期行为,不弹窗
                if (isInterruptRelated(ex)) {
                    Thread.interrupted();
                    Platform.runLater(() -> {
                        Tab currentLoading = activeLoadingRef.get();
                        boolean clearTask = shouldClearTaskFor(currentLoading);
                        removeLoadingTab(codeTabPane, currentLoading);
                        if (clearTask) {
                            statusBar.clearTask();
                        }
                    });
                    return;
                }
                Platform.runLater(() -> {
                    if (!isRequestCurrent(requestId, cancelPrevious)) {
                        removeLoadingTab(codeTabPane, activeLoadingRef.get());
                        return;
                    }
                    Tab currentLoading = activeLoadingRef.get();
                    if (!isTabOpen(codeTabPane, currentLoading)) {
                        clearTaskIfCurrent(currentLoading);
                        return;
                    }
                    boolean clearTask = shouldClearTaskFor(currentLoading);
                    removeLoadingTab(codeTabPane, currentLoading);
                    showAlert(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.decompile.failed"));
                    if (clearTask) {
                        statusBar.clearTask();
                    }
                });
            }
        });
        taskRef.set(task);
        if (cancelPrevious) {
            currentDecompileTask = task;
        }
    }

    /**
     * 用新引擎重新反编译当前标签页(同位置替换)
     *
     * @param workspace          工作区
     * @param codeTabPane        代码标签页面板
     * @param currentTab         当前选中的标签页
     * @param engine             反编译引擎
     * @param lineNumbersEnabled 是否显示行号
     */
    public void refreshCurrentClassTab(Workspace workspace, TabPane codeTabPane, CodeEditorTab currentTab,
                                       DecompilerTypeEnum engine, boolean lineNumbersEnabled) {
        String fullPath = currentTab.getOpenFile().fullPath();
        FileTreeNode node = workspace.findNodeByPath(fullPath);
        if (node == null) {
            statusBar.setFilePath(I18nUtil.getString("status.locateFailed", fullPath));
            return;
        }

        int tabIndex = codeTabPane.getTabs().indexOf(currentTab);
        statusBar.setFilePath(I18nUtil.getString(
                "status.redecompiling", engine.name(), fullPath));
        statusBar.setTask(I18nUtil.getString("task.decompiling"));

        BackgroundTasks.cancel(currentDecompileTask);
        long requestId = decompileGeneration.incrementAndGet();
        currentDecompileTask = runOpenTask("Redecompile-" + node.getName(), () -> {
            try {
                if (!isRequestCurrent(requestId, true)) {
                    return;
                }
                byte[] bytes = readClassBytes(node, workspace);
                if (!isRequestCurrent(requestId, true)) {
                    return;
                }
                if (bytes == null) {
                    Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.read.unable", fullPath)));
                    Platform.runLater(statusBar::clearTask);
                    return;
                }

                String internalName = fullPath.replace(".class", "");
                DecompileResult result = decompileWithCache(internalName, engine, bytes, node, workspace,
                        () -> isRequestCurrent(requestId, true));
                if (!isRequestCurrent(requestId, true)) {
                    return;
                }
                String sourceCode = result.sourceCode();
                CodeMetadata metadata = result.metadata();
                DecompilerTypeEnum usedEngine = result.engine();
                OpenFile openFile = new OpenFile(className(node), fullPath, sourceCode, usedEngine);

                Platform.runLater(() -> {
                    if (!isRequestCurrent(requestId, true)) {
                        return;
                    }
                    Consumer<CodeMetadata.Reference> onNavigate = ref -> {
                        if (ref.type() == CodeMetadata.RefType.CLASS_REF && ref.targetClass() != null) {
                            String targetPath = ref.targetClass().replace('.', '/') + ".class";
                            FileTreeNode targetNode = workspace.findNodeByPath(targetPath);
                            if (targetNode != null) {
                                openClassTab(targetNode, workspace, codeTabPane, usedEngine, lineNumbersEnabled);
                            }
                        }
                    };
                    CodeEditorTab replacement = createCodeEditorTab(openFile, lineNumbersEnabled, bytes,
                            metadata, onNavigate);
                    if (tabIndex < 0 || tabIndex >= codeTabPane.getTabs().size()
                            || codeTabPane.getTabs().get(tabIndex) != currentTab) {
                        statusBar.clearTask();
                        return;
                    }
                    codeTabPane.getTabs().set(tabIndex, replacement);
                    codeTabPane.getSelectionModel().select(replacement);
                    bindCaretPosition(replacement);
                    installContextMenu(replacement, node, workspace, openFile, bytes, metadata);
                    statusBar.setFilePath(
                            WorkspaceTabManager.formatClassPath(fullPath));
                    statusBar.setEncoding("UTF-8");
                    statusBar.setEngine(usedEngine.name());
                    statusBar.clearTask();
                });
            } catch (Exception ex) {
                // 用户导航到其他类时任务被取消,中断异常是预期行为,不弹窗
                if (isInterruptRelated(ex)) {
                    Thread.interrupted();
                    return;
                }
                Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                        I18nUtil.getString("dialog.decompile.failed")));
                Platform.runLater(statusBar::clearTask);
            }
        });
    }

    /** 取消当前运行的反编译任务 */
    public void cancelCurrentTask() {
        decompileGeneration.incrementAndGet();
        BackgroundTasks.cancel(currentDecompileTask);
    }

    /**
     * 打开文本文件标签页(XML/JSON/YML/properties/.java 等)
     * 读取字节码转为 UTF-8 文本,在只读 CodeArea 中显示
     */
    public void openTextFileTab(FileTreeNode node, Workspace workspace, TabPane codeTabPane) {
        // 去重检查：使用完整路径避免同名不同路径文件冲突
        for (Tab tab : codeTabPane.getTabs()) {
            if (node.getFullPath().equals(tab.getUserData())) {
                codeTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        statusBar.setFilePath(I18nUtil.getString("status.reading", node.getFullPath()));
        statusBar.setTask(I18nUtil.getString("task.reading"));

        BackgroundTasks.run("TextFile-" + node.getName(), () -> {
            try {
                byte[] bytes = readFileBytes(node, workspace);
                if (bytes == null) {
                    Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.read.unable", node.getFullPath())));
                    Platform.runLater(statusBar::clearTask);
                    return;
                }

                String text = decodeText(bytes);

                Platform.runLater(() -> {
                    jfx.incubator.scene.control.richtext.CodeArea codeArea =
                            new jfx.incubator.scene.control.richtext.CodeArea();
                    codeArea.getStyleClass().add("code-editor");
                    codeArea.setSyntaxDecorator(
                            com.bingbaihanji.fxdecomplie.ui.code.TextFileDecorator.instance());
                    codeArea.setEditable(false);
                    codeArea.setWrapText(config.decompiler().wrapText());
                    LineNumberGutter.setEnabled(codeArea, true);
                    codeArea.setText(text);
                    codeArea.setFont(javafx.scene.text.Font.font(config.theme().fontFamily(), config.theme().fontSize()));

                    Tab tab = new Tab(node.getName(), codeArea);
                    tab.setUserData(node.getFullPath());
                    tab.setOnClosed(e -> {
                    }); // no cleanup needed
                    codeTabPane.getTabs().add(tab);
                    codeTabPane.getSelectionModel().select(tab);

                    statusBar.setFilePath(node.getFullPath());
                    statusBar.setEncoding("UTF-8");
                    statusBar.clearTask();
                });
            } catch (Exception ex) {
                if (ex instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    Thread.interrupted();
                    return;
                }
                Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                        I18nUtil.getString("dialog.read.failed")));
                Platform.runLater(statusBar::clearTask);
            }
        });
    }

    /** 读取文件字节码(文本文件版本,和 readClassBytes 逻辑相同) */
    private byte[] readFileBytes(FileTreeNode node, Workspace workspace) throws IOException {
        byte[] bytes = node.resolveBytes();
        if (bytes != null) return bytes;
        if (!workspace.isArchive()) {
            File source = workspace.getSourceFile();
            return Files.readAllBytes(new File(source, node.getFullPath()).toPath());
        }
        // 对于归档文件,字节码应已在 ClassDiscoverer 中缓存
        return null;
    }

    /** 创建代码编辑器标签页 */
    /** 在 CodeEditorTab 上安装右键上下文菜单 */
    private void installContextMenu(CodeEditorTab codeTab, FileTreeNode node, Workspace workspace,
                                     OpenFile openFile, byte[] classBytes, CodeMetadata metadata) {
        CodeActionHandler handler = codeActionHandler;
        if (handler == null) return;
        WorkspaceIndex index = workspace.getIndex();
        String workspaceHash = workspaceHash(workspace);
        String sourceHash = sourceHash(openFile.sourceCode());
        String optionsHash = DecompilerOptions.hash(DecompilerOptions.forEngine(config, openFile.engine()));
        CodeViewContext ctx = new CodeViewContext(workspace, node, openFile,
                classBytes, metadata, index, workspaceHash, sourceHash, optionsHash);
        var panel = codeTab.getCodeViewPanel();
        if (panel != null) {
            panel.installContextMenu(ctx, handler);
        }
    }

    private static String workspaceHash(Workspace ws) {
        java.io.File f = ws.getSourceFile();
        return f.getAbsolutePath().replace('\\', '/') + "@"
                + f.lastModified() + "@" + f.length();
    }

    private static String sourceHash(String src) {
        if (src == null) return "";
        // 简单 hash：取前200字符 + 总长度
        return "len=" + src.length() + ":"
                + Integer.toHexString(src.hashCode());
    }

    private CodeEditorTab createCodeEditorTab(OpenFile openFile, boolean lineNumbersEnabled,
                                              byte[] classBytes, CodeMetadata metadata,
                                              Consumer<CodeMetadata.Reference> onNavigate) {
        CodeEditorTab tab = new CodeEditorTab(
                openFile, editorTheme,
                config.theme().fontFamily(), config.theme().fontSize(),
                config.decompiler().wrapText(), lineNumbersEnabled,
                classBytes, metadata, onNavigate
        );
        CodeOnlyWindow.enableTabDrag(tab);
        return tab;
    }

    private Consumer<CodeMetadata.Reference> createNavigationHandler(Workspace workspace, TabPane codeTabPane,
                                                                     DecompilerTypeEnum engine,
                                                                     boolean lineNumbersEnabled) {
        return ref -> {
            if (ref.type() == CodeMetadata.RefType.CLASS_REF && ref.targetClass() != null) {
                String targetPath = ref.targetClass().replace('.', '/') + ".class";
                FileTreeNode targetNode = workspace.findNodeByPath(targetPath);
                if (targetNode != null) {
                    openClassTab(targetNode, workspace, codeTabPane, engine, lineNumbersEnabled);
                }
            }
        };
    }

    private Tab createLoadingTab(FileTreeNode node, DecompilerTypeEnum engine) {
        javafx.scene.control.ProgressIndicator indicator = new javafx.scene.control.ProgressIndicator();
        indicator.setMaxSize(28, 28);
        javafx.scene.control.Label title = new javafx.scene.control.Label(
                I18nUtil.getString("task.decompiling"));
        title.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 14px; -fx-font-weight: bold;");
        javafx.scene.control.Label detail = new javafx.scene.control.Label(
                I18nUtil.getString("tab.decompiling.message", node.getFullPath()));
        detail.setWrapText(true);
        detail.setStyle("-fx-text-fill: #9aa7b0; -fx-font-size: 12px;");
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10, indicator, title, detail);
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setPadding(new javafx.geometry.Insets(24));
        content.setStyle("-fx-background-color: #1e1e1e;");
        Tab tab = new Tab(className(node) + " [" + engine.name() + "]");
        tab.setContent(content);
        return tab;
    }

    private boolean replaceLoadingTab(TabPane codeTabPane, Tab loadingTab, CodeEditorTab codeTab) {
        if (codeTabPane == null || loadingTab == null || codeTab == null) {
            return false;
        }
        int index = codeTabPane.getTabs().indexOf(loadingTab);
        if (index < 0) {
            return false;
        }
        loadingTab.setOnClosed(null);
        codeTabPane.getTabs().set(index, codeTab);
        clearCurrentLoadingTab(loadingTab);
        return true;
    }

    private void removeLoadingTab(TabPane codeTabPane, Tab loadingTab) {
        if (codeTabPane == null || loadingTab == null) {
            return;
        }
        loadingTab.setOnClosed(null);
        codeTabPane.getTabs().remove(loadingTab);
        clearCurrentLoadingTab(loadingTab);
    }

    private void clearCurrentLoadingTab(Tab loadingTab) {
        if (currentLoadingTab == loadingTab) {
            currentLoadingTab = null;
        }
    }

    private static boolean isTabOpen(TabPane codeTabPane, Tab tab) {
        return codeTabPane != null && tab != null && codeTabPane.getTabs().contains(tab);
    }

    private boolean shouldClearTaskFor(Tab loadingTab) {
        return currentLoadingTab == null || currentLoadingTab == loadingTab;
    }

    private void clearTaskIfCurrent(Tab loadingTab) {
        if (shouldClearTaskFor(loadingTab)) {
            statusBar.clearTask();
        }
    }

    private boolean isRequestCurrent(long requestId, boolean guarded) {
        return !guarded || decompileGeneration.get() == requestId;
    }

    private static Future<?> runOpenTask(String name, Runnable task) {
        return OPEN_EXECUTOR.submit(() -> {
            Thread.currentThread().setName(name);
            Thread.interrupted();
            task.run();
        });
    }

    /** 查找已打开的同名 class 标签页,移除不同引擎的重复标签页 */
    private Tab findOrRemoveOpenClassTab(TabPane codeTabPane, FileTreeNode node,
                                         DecompilerTypeEnum engine,
                                         boolean removeDifferentEngine) {
        java.util.List<Tab> toRemove = new java.util.ArrayList<>();
        for (Tab tab : codeTabPane.getTabs()) {
            if (tab instanceof CodeEditorTab codeTab
                    && codeTab.getOpenFile().fullPath().equals(node.getFullPath())) {
                if (codeTab.getOpenFile().engine() == engine) {
                    return tab;
                }
                if (removeDifferentEngine) {
                    toRemove.add(tab);
                }
            }
        }
        codeTabPane.getTabs().removeAll(toRemove);
        return null;
    }

    /** 读取类字节码(依次尝试节点缓存、工作区索引、全局缓存、磁盘读取) */
    private byte[] readClassBytes(FileTreeNode node, Workspace workspace) throws IOException {
        byte[] bytes = WorkspaceByteReader.readNodeBytes(workspace, node, true);
        if (bytes != null) return bytes;
        String internalName = node.getFullPath().replace(".class", "");
        bytes = workspaceIndexForBackground(workspace).getClassBytes(internalName);
        if (bytes != null) return bytes;
        if (!workspace.isArchive()) {
            File source = workspace.getSourceFile();
            return Files.readAllBytes(new File(source, node.getFullPath()).toPath());
        }
        return null;
    }

    /** 绑定光标位置到状态栏 */
    private void bindCaretPosition(CodeEditorTab codeTab) {
        codeTab.getCodeArea().caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (newPos != null) {
                statusBar.setCursorPosition(newPos.index() + 1, newPos.charIndex() + 1);
            }
        });
    }

    /**
     * 共享的反编译+缓存逻辑：依次查询 L2 内存缓存、L3 磁盘缓存,最后执行反编译
     * 命中 L3 时回填 L2,反编译结果同时写入 L2 和 L3
     * workspaceKey 使用路径 + mtime + size,避免同路径文件替换后命中旧缓存
     */
    private DecompileResult decompileWithCache(String internalName, DecompilerTypeEnum engine,
                                               byte[] bytes, FileTreeNode node, Workspace workspace,
                                               BooleanSupplier active) {
        DecompilerTypeEnum effectiveEngine = effectiveEngineFor(bytes, engine);
        var engineOptions = DecompilerOptions.forEngine(config, effectiveEngine);
        String optionsHash = DecompilerOptions.hash(engineOptions);
        String wsKey = computeWorkspaceKey(workspace);

        // ---- L2: 内存反编译缓存(最快路径) ----
        String sourceCode = decompileCache.get(wsKey, internalName, effectiveEngine, optionsHash);

        // ---- L2 未命中:尝试 L3 磁盘持久化缓存 ----
        if (sourceCode == null) {
            sourceCode = DiskCodeCache.load(wsKey, internalName, effectiveEngine, optionsHash);
            if (sourceCode != null) {
                // ---- L3 命中:回填 L2,使下次查询即时完成 ----
                decompileCache.put(wsKey, internalName, effectiveEngine, optionsHash, sourceCode);
            }
        }

        // ---- L2+L3 均未命中:执行实际反编译 ----
        if (sourceCode == null) {
            if (!active.getAsBoolean()) {
                throw new CancellationException("反编译请求已被替换");
            }
            Thread.interrupted();
            String finalPath = node.getFullPath();
            DecompilerContext context = DecompilerRunner.contextForWorkspace(
                    workspace, engineOptions);
            sourceCode = DecompilerRunner.decompileWithTimeout(finalPath, bytes,
                    effectiveEngine, context, active);
            if (!active.getAsBoolean()) {
                throw new CancellationException("反编译请求已被替换");
            }
            if (!DecompilerRunner.isTransientFailureOutput(sourceCode)) {
                // ---- 保存反编译结果到 L2(即时) ----
                decompileCache.put(wsKey, internalName, effectiveEngine, optionsHash, sourceCode);

                // ---- 异步保存到 L3 磁盘缓存(非阻塞) ----
                final String finalSource = sourceCode;
                BackgroundTasks.run("DiskCache-" + internalName, () -> {
                    DiskCodeCache.save(wsKey, internalName, effectiveEngine, optionsHash, finalSource);
                });
            }
        }

        // ---- 提取元数据用于 Ctrl+Click 导航；大源码的链接扫描会被禁用，这里也跳过避免拖慢首屏 ----
        CodeMetadata metadata = sourceCode != null && sourceCode.length() <= METADATA_SOURCE_THRESHOLD
                ? OutlineParser.extractMetadata(sourceCode)
                : new CodeMetadata(java.util.Map.of());
        return new DecompileResult(sourceCode, metadata, effectiveEngine);
    }

    private DecompilerTypeEnum effectiveEngineFor(byte[] bytes, DecompilerTypeEnum requestedEngine) {
        if (requestedEngine == null) {
            return DecompilerTypeEnum.VINEFLOWER;
        }
        if (requestedEngine != DecompilerTypeEnum.JD) {
            return requestedEngine;
        }
        int major = classMajorVersion(bytes);
        // JD-Core 对 Java 21+/class major 65+ 的支持不稳定，JavaFX 25 项目应优先走 Vineflower。
        return major >= 65 ? DecompilerTypeEnum.VINEFLOWER : requestedEngine;
    }

    private static int classMajorVersion(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return 0;
        }
        return ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
    }

    /** 显示错误弹窗 */
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR, message);
            alert.setTitle(title);
            alert.setHeaderText(null);
            javafx.stage.Window owner = javafx.stage.Window.getWindows().stream()
                    .filter(javafx.stage.Window::isShowing)
                    .findFirst().orElse(null);
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.showAndWait();
        });
    }

    private WorkspaceIndex workspaceIndexForBackground(Workspace workspace) {
        if (workspace == null) {
            return WorkspaceIndex.EMPTY;
        }
        if (workspace.isIndexReady()) {
            return workspace.getIndex();
        }
        return WorkspaceIndex.EMPTY;
    }

    /** 优雅关闭反编译器执行器,最多等待 2 秒 */
    public static void shutdown() {
        OPEN_EXECUTOR.shutdownNow();
        DecompilerRunner.shutdown();
    }

    /** 反编译结果,包含源码和元数据 */
    private record DecompileResult(String sourceCode, CodeMetadata metadata, DecompilerTypeEnum engine) {
    }

}
