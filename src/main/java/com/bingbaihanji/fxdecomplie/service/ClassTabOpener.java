package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.*;
import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceTabManager;
import com.bingbaihanji.fxdecomplie.ui.code.*;
import com.bingbaihanji.fxdecomplie.ui.outline.OutlineParser;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.util.I18nUtil;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    /** 交互打开 class 的反编译超时，避免坏类/混淆类长时间占住首屏 */
    private static final int INTERACTIVE_TIMEOUT_SECONDS = 15;
    /** 应用配置 */
    private final AppConfig config;
    /** 编辑器主题数据 */
    private volatile VsCodeThemeLoader.ThemeData editorTheme;
    /** 状态栏引用 */
    private final StatusBar statusBar;
    /** L2 反编译源码缓存,避免重复反编译已打开的类 */
    private final DecompileCache decompileCache = new DecompileCache();
    /** 主导航反编译请求序号,用于丢弃快速切换产生的过期结果 */
    private final AtomicLong decompileGeneration = new AtomicLong();
    /** 当前运行的反编译任务,用于在切换时取消 */
    private volatile Future<?> currentDecompileTask;
    /** 当前由主导航创建的占位标签,用于任务取消时清理 */
    private volatile Tab currentLoadingTab;
    /** 当前已显示但源码仍在反编译中的代码标签,用于快速切换时清理 */
    private volatile CodeEditorTab currentPendingTab;
    /** 代码操作回调，用于安装右键菜单 */
    private volatile CodeActionHandler codeActionHandler;
    /** 标签页就绪回调（工具栏刷新等） */
    private volatile Runnable onTabReady;

    public ClassTabOpener(AppConfig config, VsCodeThemeLoader.ThemeData editorTheme, StatusBar statusBar) {
        this.config = config;
        this.editorTheme = editorTheme;
        this.statusBar = statusBar;
    }

    /** 更新编辑器主题，后续新打开的标签页将使用新主题 */
    public void setEditorTheme(VsCodeThemeLoader.ThemeData newTheme) {
        this.editorTheme = newTheme;
    }

    /** 提取简短类名(去 .class 后缀) */
    private static String className(FileTreeNode node) {
        String name = node.getName();
        return name.endsWith(".class") ? name.substring(0, name.length() - 6) : name;
    }

    /** 计算工作区缓存键：使用完整路径 + mtime + size,消除路径碰撞和同路径替换误命中 */
    public static String computeWorkspaceKey(Workspace workspace) {
        File source = workspace.getSourceFile();
        long mtime = source.lastModified();
        long size = source.isFile() ? source.length() : 0L;
        return (source.getAbsolutePath() + "_" + mtime + "_" + size)
                .replace(':', '_').replace('\\', '_').replace('/', '_');
    }

    /** 计算 class 字节指纹(CRC32)，用于缓存键防内容变更误命中 */
    public static String computeClassFingerprint(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "0";
        }
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(bytes);
        return Long.toHexString(crc.getValue());
    }

    /**
     * 判断异常是否由线程中断引起(用户导航取消任务,不应弹窗)
     * 递归检查异常链中每一层,最终兜底检查当前线程中断标志
     */
    private static boolean isInterruptRelated(Exception ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof InterruptedException) {
                return true;
            }
            if (cause instanceof CancellationException) {
                return true;
            }
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

    private static boolean isTabOpen(TabPane codeTabPane, Tab tab) {
        return codeTabPane != null && tab != null && codeTabPane.getTabs().contains(tab);
    }

    private static Future<?> runOpenTask(String name, Runnable task) {
        return OPEN_EXECUTOR.submit(() -> {
            Thread.currentThread().setName(name);
            Thread.interrupted();
            task.run();
        });
    }

    private static int classMajorVersion(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return 0;
        }
        return ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
    }

    private static CodeMetadata emptyMetadata() {
        return new CodeMetadata(java.util.Map.of());
    }

    private static String pendingSource(FileTreeNode node, DecompilerTypeEnum engine) {
        return I18nUtil.getString("tab.decompiling.source",
                node.getFullPath(), engine == null ? "" : engine.name());
    }

    private static String failedSource(FileTreeNode node, Exception ex) {
        return I18nUtil.getString("tab.decompile.failed.source", node.getFullPath(), errorReason(ex));
    }

    private static String errorReason(Throwable ex) {
        if (ex == null) {
            return "未知错误";
        }
        String message = ex.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return ex.getClass().getSimpleName();
    }

    /** 优雅关闭反编译器执行器,最多等待 2 秒 */
    public static void shutdown() {
        OPEN_EXECUTOR.shutdownNow();
        DecompilerRunner.shutdown();
    }

    /** 解析错误弹窗的父窗口：优先焦点窗口，其次第一个可见窗口 */
    private static javafx.stage.Window resolveOwnerWindow() {
        // 尝试从焦点节点反查所属窗口
        javafx.scene.Scene scene = javafx.stage.Window.getWindows().stream()
                .filter(w -> w instanceof javafx.stage.Stage s && s.isShowing())
                .findFirst()
                .map(w -> ((javafx.stage.Stage) w).getScene())
                .orElse(null);
        if (scene != null) {
            javafx.scene.Node focused = scene.getFocusOwner();
            if (focused != null) {
                javafx.stage.Window owner = focused.getScene().getWindow();
                if (owner != null && owner.isShowing()) {
                    return owner;
                }
            }
            return scene.getWindow();
        }
        // 最终回退：取第一个可见窗口
        return javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isShowing)
                .findFirst().orElse(null);
    }

    /** 创建代码编辑器标签页 */

    /** 暴露 L2 缓存给全文搜索等批量解编译场景复用已打开的标签页结果 */
    public DecompileCache getDecompileCache() {
        return decompileCache;
    }

    /** 设置代码操作回调，用于自动安装右键菜单 */
    public void setCodeActionHandler(CodeActionHandler handler) {
        this.codeActionHandler = handler;
    }

    /** 设置标签页就绪回调（工具栏刷新等） */
    public void setOnTabReady(Runnable callback) {
        this.onTabReady = callback;
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

    /**
     * 在指定 TabPane 中打开类标签，不替换不同引擎的现有标签（用于分屏场景）。
     *
     * @param node              类文件节点
     * @param workspace         工作区
     * @param targetPane        目标 TabPane（分屏中的某个 cell）
     * @param engine            反编译引擎
     * @param lineNumbersEnabled 是否显示行号
     */
    public void openClassTabIn(TabPane targetPane, FileTreeNode node, Workspace workspace,
                               DecompilerTypeEnum engine, boolean lineNumbersEnabled) {
        openClassTab(node, workspace, targetPane, engine, lineNumbersEnabled, true, false);
    }

    public void openClassTab(FileTreeNode node, Workspace workspace, TabPane codeTabPane,
                             DecompilerTypeEnum engine, boolean lineNumbersEnabled,
                             boolean cancelPrevious, boolean replaceDifferentEngine) {
        Tab existingTab = findOrRemoveOpenClassTab(codeTabPane, node, engine, replaceDifferentEngine);
        if (existingTab != null) {
            codeTabPane.getSelectionModel().select(existingTab);
            if (onTabReady != null) {
                onTabReady.run();
            }
            return;
        }

        statusBar.setFilePath(I18nUtil.getString("status.decompiling", node.getFullPath()));
        statusBar.setTask(I18nUtil.getString("task.decompiling"));

        long requestId = cancelPrevious ? decompileGeneration.incrementAndGet() : -1L;
        if (cancelPrevious) {
            BackgroundTasks.cancel(currentDecompileTask);
            removeLoadingTab(codeTabPane, currentLoadingTab);
            removePendingTab(codeTabPane, currentPendingTab);
        }

        Tab loadingTab = createLoadingTab(node, engine);
        AtomicReference<Future<?>> taskRef = new AtomicReference<>();
        codeTabPane.getTabs().add(loadingTab);
        codeTabPane.getSelectionModel().select(loadingTab);
        if (cancelPrevious) {
            currentLoadingTab = loadingTab;
        }

        Future<?> task = runOpenTask("Decompile-" + node.getName(), () -> {
            AtomicReference<CodeEditorTab> pendingTabRef = new AtomicReference<>();
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
                DecompilerTypeEnum effectiveEngine = effectiveEngineFor(bytes, engine);
                CodeMetadata emptyMetadata = emptyMetadata();
                Consumer<CodeMetadata.Reference> pendingNavigate = createNavigationHandler(
                        workspace, codeTabPane, effectiveEngine, lineNumbersEnabled);
                OpenFile pendingOpenFile = new OpenFile(className(node), node.getFullPath(),
                        pendingSource(node, effectiveEngine), effectiveEngine);

                CodeEditorTab pendingTab = createPendingCodeTab(codeTabPane, loadingTab,
                        node, workspace, pendingOpenFile, lineNumbersEnabled,
                        bytes, emptyMetadata, pendingNavigate, taskRef,
                        requestId, cancelPrevious);
                pendingTabRef.set(pendingTab);
                if (pendingTab == null || !isRequestCurrent(requestId, cancelPrevious)) {
                    return;
                }
                if (cancelPrevious) {
                    currentPendingTab = pendingTab;
                }

                DecompileResult result = decompileWithCache(internalName, effectiveEngine, bytes, node, workspace,
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
                        removePendingTab(codeTabPane, pendingTab);
                        return;
                    }
                    if (!isTabOpen(codeTabPane, pendingTab)) {
                        clearTaskIfCurrent(pendingTab);
                        return;
                    }
                    pendingTab.setOnClosed(null);
                    pendingTab.updateDecompiledContent(openFile, metadata, completedNavigate);
                    pendingTab.setSourceReady(true);
                    codeTabPane.getSelectionModel().select(pendingTab);
                    if (onTabReady != null) {
                        onTabReady.run();
                    }
                    statusBar.setFilePath(
                            WorkspaceTabManager.formatClassPath(node.getFullPath()));
                    statusBar.setEncoding("UTF-8");
                    statusBar.setEngine(usedEngine.name());
                    statusBar.clearTask();
                    bindCaretPosition(pendingTab);
                    installContextMenu(pendingTab, node, workspace, openFile, bytes, metadata);
                    clearCurrentPendingTab(pendingTab);
                });
            } catch (Exception ex) {
                // 用户导航到其他类时任务被取消,中断异常是预期行为,不弹窗
                if (isInterruptRelated(ex)) {
                    Thread.interrupted();
                    Platform.runLater(() -> {
                        removePendingTab(codeTabPane, pendingTabRef.get());
                        removeLoadingTab(codeTabPane, loadingTab);
                        if (shouldClearTaskFor(loadingTab)) {
                            statusBar.clearTask();
                        }
                    });
                    return;
                }
                logger.error("打开 class 失败: {}", node.getFullPath(), ex);
                Platform.runLater(() -> {
                    if (!isRequestCurrent(requestId, cancelPrevious)) {
                        removePendingTab(codeTabPane, pendingTabRef.get());
                        removeLoadingTab(codeTabPane, loadingTab);
                        return;
                    }
                    CodeEditorTab pendingTab = pendingTabRef.get();
                    if (pendingTab != null && isTabOpen(codeTabPane, pendingTab)) {
                        OpenFile failedFile = new OpenFile(className(node), node.getFullPath(),
                                failedSource(node, ex), engine == null ? DecompilerTypeEnum.VINEFLOWER : engine);
                        pendingTab.setOnClosed(null);
                        pendingTab.updateDecompiledContent(failedFile, emptyMetadata(), null);
                        pendingTab.setSourceReady(true);
                        bindCaretPosition(pendingTab);
                        installContextMenu(pendingTab, node, workspace, failedFile,
                                pendingTab.getClassBytes(), emptyMetadata());
                        clearCurrentPendingTab(pendingTab);
                        statusBar.clearTask();
                        return;
                    }
                    if (!isTabOpen(codeTabPane, loadingTab)) {
                        clearTaskIfCurrent(loadingTab);
                        return;
                    }
                    boolean clearTask = shouldClearTaskFor(loadingTab);
                    removeLoadingTab(codeTabPane, loadingTab);
                    showAlert(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.decompile.failed", errorReason(ex)));
                    if (clearTask) {
                        statusBar.clearTask();
                    }
                });
            }
        });
        taskRef.set(task);
        loadingTab.setOnClosed(e -> {
            Future<?> f = taskRef.get();
            if (f != null) {
                BackgroundTasks.cancel(f);
            }
            clearCurrentLoadingTab(loadingTab);
        });
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
        refreshCurrentClassTab(workspace, codeTabPane, currentTab, engine, lineNumbersEnabled, true);
    }

    public void refreshCurrentClassTab(Workspace workspace, TabPane codeTabPane, CodeEditorTab currentTab,
                                       DecompilerTypeEnum engine, boolean lineNumbersEnabled,
                                       boolean bypassCache) {
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

        long requestId = decompileGeneration.incrementAndGet();
        BackgroundTasks.cancel(currentDecompileTask);
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
                if (bypassCache) {
                    String wsKey = computeWorkspaceKey(workspace) + "_" + computeClassFingerprint(bytes);
                    decompileCache.invalidate(wsKey, internalName);
                }
                DecompileResult result = bypassCache
                        ? decompileFresh(internalName, engine, bytes, node, workspace,
                        () -> isRequestCurrent(requestId, true))
                        : decompileWithCache(internalName, engine, bytes, node, workspace,
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
                    replacement.setWorkspaceContext(workspace, node);
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
                logger.error("重新反编译 class 失败: {}", fullPath, ex);
                Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                        I18nUtil.getString("dialog.decompile.failed", errorReason(ex))));
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
                logger.error("读取文本资源失败: {}", node.getFullPath(), ex);
                Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                        I18nUtil.getString("dialog.read.failed", errorReason(ex))));
                Platform.runLater(statusBar::clearTask);
            }
        });
    }

    /** 读取文件字节码(文本文件版本,和 readClassBytes 逻辑相同) */
    private byte[] readFileBytes(FileTreeNode node, Workspace workspace) throws IOException {
        byte[] bytes = node.resolveBytes();
        if (bytes != null) {
            return bytes;
        }
        if (!workspace.isArchive()) {
            File source = workspace.getSourceFile();
            return Files.readAllBytes(new File(source, node.getFullPath()).toPath());
        }
        // 对于归档文件,字节码应已在 ClassDiscoverer 中缓存
        return null;
    }

    /** 在 CodeEditorTab 上安装右键上下文菜单 */
    private void installContextMenu(CodeEditorTab codeTab, FileTreeNode node, Workspace workspace,
                                    OpenFile openFile, byte[] classBytes, CodeMetadata metadata) {
        CodeActionHandler handler = codeActionHandler;
        if (handler == null) {
            return;
        }
        WorkspaceIndex index = workspace.getIndex();
        String workspaceHash = com.bingbaihanji.fxdecomplie.model.CommentScope.workspaceHash(workspace);
        String sourceHash = CommentExportDecorator.sourceHash(openFile.sourceCode());
        String optionsHash = DecompilerOptions.hash(DecompilerOptions.forEngine(config, openFile.engine()));
        CodeViewContext ctx = new CodeViewContext(workspace, node, openFile,
                classBytes, metadata, index, workspaceHash, sourceHash, optionsHash);
        var panel = codeTab.getCodeViewPanel();
        if (panel != null) {
            panel.installContextMenu(ctx, handler);
        }
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
        // 设置分屏请求回调：在右侧新 cell 中打开同 class 的不同引擎
        tab.setOnSplitRequested(sourceTab -> {
            SplitEditorPane sep = sourceTab.getSplitEditorPane();
            if (sep == null || sep.activeCellCount() >= 3) return;
            Workspace ws = (Workspace) sourceTab.getProperties().get("workspace");
            FileTreeNode node = (FileTreeNode) sourceTab.getProperties().get("fileTreeNode");
            if (ws == null || node == null) {
                return;
            }
            // 使用与源 tab 相同的引擎
            DecompilerTypeEnum sameEngine = sourceTab.getOpenFile().engine();
            // 创建分屏 cell
            sep.splitRight(sourceTab);
            // 找到新创建的 cell（非主 cell 且非源 cell）
            TabPane targetPane = null;
            for (TabPane pane : sep.allTabPanes()) {
                if (pane != sep.primaryTabPane() && pane != sep.tabPaneFor(sourceTab)) {
                    targetPane = pane;
                    break;
                }
            }
            if (targetPane == null) {
                targetPane = sep.allTabPanes().get(sep.activeCellCount() - 1);
            }
            openClassTab(node, ws, targetPane, sameEngine, lineNumbersEnabled, true, false);
        });
        // 设置切换引擎回调：原地用新引擎重反编译
        tab.setOnSwitchEngine(newEngine -> {
            Workspace ws = (Workspace) tab.getProperties().get("workspace");
            FileTreeNode node = (FileTreeNode) tab.getProperties().get("fileTreeNode");
            if (ws == null || node == null) {
                return;
            }
            TabPane pane = tab.getSplitEditorPane() != null
                    ? tab.getSplitEditorPane().tabPaneFor(tab) : null;
            if (pane == null) {
                return;
            }
            refreshCurrentClassTab(ws, pane, tab, newEngine, lineNumbersEnabled);
        });
        return tab;
    }

    private CodeEditorTab createPendingCodeTab(TabPane codeTabPane, Tab loadingTab,
                                               FileTreeNode node, Workspace workspace,
                                               OpenFile pendingOpenFile, boolean lineNumbersEnabled,
                                               byte[] bytes, CodeMetadata metadata,
                                               Consumer<CodeMetadata.Reference> onNavigate,
                                               AtomicReference<Future<?>> taskRef,
                                               long requestId, boolean guarded) throws Exception {
        CompletableFuture<CodeEditorTab> created = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                if (!isRequestCurrent(requestId, guarded)) {
                    removeLoadingTab(codeTabPane, loadingTab);
                    created.complete(null);
                    return;
                }
                if (!isTabOpen(codeTabPane, loadingTab)) {
                    clearTaskIfCurrent(loadingTab);
                    created.complete(null);
                    return;
                }
                CodeEditorTab pendingTab = createCodeEditorTab(pendingOpenFile, lineNumbersEnabled,
                        bytes, metadata, onNavigate);
                pendingTab.setWorkspaceContext(workspace, node);
                pendingTab.setSourceReady(false);
                pendingTab.setOnClosed(e -> {
                    BackgroundTasks.cancel(taskRef.get());
                    clearCurrentPendingTab(pendingTab);
                });
                if (!replaceLoadingTab(codeTabPane, loadingTab, pendingTab)) {
                    created.complete(null);
                    return;
                }
                codeTabPane.getSelectionModel().select(pendingTab);
                if (onTabReady != null) {
                    onTabReady.run();
                }
                statusBar.setFilePath(WorkspaceTabManager.formatClassPath(node.getFullPath()));
                statusBar.setEncoding("UTF-8");
                statusBar.setEngine(pendingOpenFile.engine().name());
                bindCaretPosition(pendingTab);
                installContextMenu(pendingTab, node, workspace, pendingOpenFile, bytes, metadata);
                created.complete(pendingTab);
            } catch (Exception e) {
                created.completeExceptionally(e);
            } catch (Error e) {
                created.completeExceptionally(e);
                throw e;
            }
        });
        try {
            return created.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(cause);
        } catch (TimeoutException e) {
            throw new RuntimeException("创建代码标签页超时", e);
        }
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

    private void removePendingTab(TabPane codeTabPane, CodeEditorTab pendingTab) {
        if (codeTabPane == null || pendingTab == null) {
            return;
        }
        pendingTab.setOnClosed(null);
        if (!pendingTab.isSourceReady()) {
            codeTabPane.getTabs().remove(pendingTab);
        }
        clearCurrentPendingTab(pendingTab);
    }

    private void clearCurrentLoadingTab(Tab loadingTab) {
        if (currentLoadingTab == loadingTab) {
            currentLoadingTab = null;
        }
    }

    private void clearCurrentPendingTab(CodeEditorTab pendingTab) {
        if (currentPendingTab == pendingTab) {
            currentPendingTab = null;
        }
    }

    private boolean shouldClearTaskFor(Tab loadingTab) {
        boolean loadingCurrent = currentLoadingTab == null || currentLoadingTab == loadingTab;
        boolean pendingCurrent = currentPendingTab == null || currentPendingTab == loadingTab;
        return loadingCurrent && pendingCurrent;
    }

    private void clearTaskIfCurrent(Tab loadingTab) {
        if (shouldClearTaskFor(loadingTab)) {
            statusBar.clearTask();
        }
    }

    private boolean isRequestCurrent(long requestId, boolean guarded) {
        return !guarded || decompileGeneration.get() == requestId;
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
        if (bytes != null) {
            return bytes;
        }
        String internalName = node.getFullPath().replace(".class", "");
        bytes = workspaceIndexForBackground(workspace).getClassBytes(internalName);
        if (bytes != null) {
            return bytes;
        }
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
                statusBar.setCursorPosition(newPos.index() + 1, newPos.offset() + 1);
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
        String wsKey = computeWorkspaceKey(workspace) + "_" + computeClassFingerprint(bytes);

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
                    effectiveEngine, context, active, INTERACTIVE_TIMEOUT_SECONDS);
            if (!active.getAsBoolean()) {
                throw new CancellationException("反编译请求已被替换");
            }
            if (!DecompilerRunner.isFailureOutput(sourceCode)) {
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

    /** 强制重新反编译当前类，跳过 L2/L3 读取；成功后用新结果回填缓存。 */
    private DecompileResult decompileFresh(String internalName, DecompilerTypeEnum engine,
                                           byte[] bytes, FileTreeNode node, Workspace workspace,
                                           BooleanSupplier active) {
        DecompilerTypeEnum effectiveEngine = effectiveEngineFor(bytes, engine);
        var engineOptions = DecompilerOptions.forEngine(config, effectiveEngine);
        String optionsHash = DecompilerOptions.hash(engineOptions);
        String wsKey = computeWorkspaceKey(workspace) + "_" + computeClassFingerprint(bytes);
        decompileCache.invalidate(wsKey, internalName);
        DiskCodeCache.invalidate(wsKey, internalName);

        if (!active.getAsBoolean()) {
            throw new CancellationException("反编译请求已被替换");
        }
        Thread.interrupted();
        DecompilerContext context = DecompilerRunner.contextForWorkspace(workspace, engineOptions);
        String sourceCode = DecompilerRunner.decompileWithTimeout(node.getFullPath(), bytes,
                effectiveEngine, context, active, INTERACTIVE_TIMEOUT_SECONDS);
        if (!active.getAsBoolean()) {
            throw new CancellationException("反编译请求已被替换");
        }
        if (!DecompilerRunner.isFailureOutput(sourceCode)) {
            decompileCache.put(wsKey, internalName, effectiveEngine, optionsHash, sourceCode);
            final String finalSource = sourceCode;
            BackgroundTasks.run("DiskCache-" + internalName, () ->
                    DiskCodeCache.save(wsKey, internalName, effectiveEngine, optionsHash, finalSource));
        }

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

    /** 显示错误弹窗，优先以焦点窗口为 owner */
    private void showAlert(String title, String message) {
        Runnable action = () -> {
            javafx.stage.Window owner = resolveOwnerWindow();
            DialogHelper.showError(owner, title, message);
        };
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
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

    /** 反编译结果,包含源码和元数据 */
    private record DecompileResult(String sourceCode, CodeMetadata metadata, DecompilerTypeEnum engine) {
    }

}
