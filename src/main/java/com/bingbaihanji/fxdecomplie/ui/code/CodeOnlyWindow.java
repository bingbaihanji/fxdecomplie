package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.OpenFile;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.CommentExportDecorator;
import com.bingbaihanji.fxdecomplie.service.DecompilerOptions;
import com.bingbaihanji.fxdecomplie.service.DecompilerRunner;
import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.fxdecomplie.ui.outline.OutlineParser;
import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量级纯代码窗口，用于分离的编辑器标签页
 * 支持与主窗口之间的跨窗口标签拖放
 */
public final class CodeOnlyWindow {

    public static final DataFormat CODE_TAB_FORMAT =
            new DataFormat("application/x-fxdecomplie-code-tab");
    private static final String DRAG_SOURCE_INSTALLED =
            "fxdecomplie.codeTabDragSourceInstalled";
    private static final String ENGINE_SWITCH_TASK =
            "fxdecomplie.codeOnly.engineSwitchTask";
    private static final String ENGINE_SWITCH_REQUEST =
            "fxdecomplie.codeOnly.engineSwitchRequest";
    private static final String CODE_ONLY_TARGET =
            "fxdecomplie.codeOnly.target";
    private static final String CODE_ONLY_SPLIT_EDITOR =
            "fxdecomplie.codeOnly.splitEditor";
    private static final String CODE_ONLY_CLOSE_CLEANUP =
            "fxdecomplie.codeOnly.closeCleanup";
    private static final String ORIGINAL_HANDLERS_SAVED =
            "fxdecomplie.codeOnly.originalHandlersSaved";
    private static final String ORIGINAL_SPLIT_HANDLER =
            "fxdecomplie.codeOnly.originalSplitHandler";
    private static final String ORIGINAL_ENGINE_HANDLER =
            "fxdecomplie.codeOnly.originalEngineHandler";
    private static final String TOKEN_PREFIX = "fxdecomplie-code-tab:";
    private static final int MAX_DRAG_PAYLOADS = 128;
    private static final int METADATA_SOURCE_THRESHOLD = 500_000;
    private static final Map<String, CodeTabPayload> DRAG_PAYLOADS =
            new ConcurrentHashMap<>();
    private static final Map<String, TabPane> DRAG_SOURCES =
            new ConcurrentHashMap<>();
    private static final Map<String, CodeEditorTab> DRAG_SOURCE_TABS =
            new ConcurrentHashMap<>();
    private static final AtomicLong ENGINE_SWITCH_IDS = new AtomicLong();
    private static final Logger logger = LoggerFactory.getLogger(CodeOnlyWindow.class);

    private final AppConfig config;
    private final VsCodeThemeLoader.ThemeData editorTheme;
    private final SplitEditorPane splitEditorPane;
    private final TabPane tabPane;
    private final Stage stage;

    private CodeOnlyWindow(AppConfig config, Stage stage) {
        this.config = config;
        this.editorTheme = AppTheme.loadEditorTheme(config);
        this.splitEditorPane = new SplitEditorPane(config, editorTheme);
        this.tabPane = splitEditorPane.primaryTabPane();
        markCodeOnlyPane(tabPane, splitEditorPane);
        this.stage = stage;
    }

    public static CodeOnlyWindow openFrom(CodeEditorTab sourceTab, AppConfig config,
                                          Stage owner) {
        Stage stage = new Stage();
        setAppIcon(stage);
        if (owner != null) {
            stage.setX(owner.getX() + 56);
            stage.setY(owner.getY() + 56);
            stage.setWidth(Math.max(760, owner.getWidth() * 0.72));
            stage.setHeight(Math.max(520, owner.getHeight() * 0.72));
        } else {
            stage.setWidth(960);
            stage.setHeight(640);
        }

        CodeOnlyWindow window = new CodeOnlyWindow(config, stage);
        window.addTab(copyTab(sourceTab, config, window.editorTheme));
        window.show();
        return window;
    }

    /** 为单个 CodeEditorTab 标题启用拖拽 */
    public static void enableTabDrag(CodeEditorTab tab) {
        Node title = tab.getGraphic();
        if (title == null || Boolean.TRUE.equals(title.getProperties().get(DRAG_SOURCE_INSTALLED))) {
            return;
        }
        title.getProperties().put(DRAG_SOURCE_INSTALLED, true);
        title.addEventFilter(MouseEvent.DRAG_DETECTED, event -> {
            TabPane pane = tab.getTabPane();
            if (pane != null) {
                pane.getSelectionModel().select(tab);
            }
            startCodeTabDrag(title, tab);
            event.consume();
        });
    }

    private static void startCodeTabDrag(Node source, CodeEditorTab tab) {
        if (!tab.isSourceReady()) {
            return;
        }
        var dragboard = source.startDragAndDrop(TransferMode.MOVE);
        String token = UUID.randomUUID().toString();
        DRAG_PAYLOADS.put(token, toPayload(tab));
        DRAG_SOURCE_TABS.put(token, tab);
        TabPane sourcePane = tab.getTabPane();
        if (sourcePane != null) {
            DRAG_SOURCES.put(token, sourcePane);
        }
        trimDragPayloads();
        ClipboardContent content = new ClipboardContent();
        content.put(CODE_TAB_FORMAT, token);
        content.putString(TOKEN_PREFIX + token);
        dragboard.setContent(content);
        DragCleanupHandler cleanup = new DragCleanupHandler(source, token);
        source.addEventHandler(DragEvent.DRAG_DONE, cleanup);
    }

    /**
     * 在任意 TabPane 上安装跨窗口标签拖放处理器
     * 由主窗口和辅助窗口调用
     */
    public static void installDragDropHandlers(TabPane tabPane, AppConfig config,
                                               VsCodeThemeLoader.ThemeData editorTheme) {
        tabPane.addEventFilter(DragEvent.DRAG_OVER, event -> {
            TabPane sourcePane = resolveSourcePane(event.getDragboard(), false);
            if (sourcePane != tabPane && hasPayload(event.getDragboard())) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        tabPane.addEventFilter(DragEvent.DRAG_DROPPED, event -> {
            String token = getDragToken(event.getDragboard());
            if (dropTabInto(tabPane, token, config, editorTheme, false)) {
                event.setDropCompleted(true);
                event.consume();
                return;
            }
            event.setDropCompleted(false);
        });
        enableTabDragListener(tabPane);
    }

    // ==================== 拖拽源 ====================

    /**
     * 添加一个监听器，每当 CodeEditorTab 添加到 TabPane 时调用 enableTabDrag
     */
    public static void enableTabDragListener(TabPane tabPane) {
        tabPane.getTabs().addListener(
                (javafx.collections.ListChangeListener<Tab>) change -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            for (Tab tab : change.getAddedSubList()) {
                                if (tab instanceof CodeEditorTab codeTab) {
                                    enableTabDrag(codeTab);
                                }
                            }
                        }
                    }
                });
    }

    private static boolean hasPayload(Dragboard dragboard) {
        return resolvePayload(dragboard, false) != null;
    }

    // ==================== 拖拽目标(共享，任意 TabPane 均可使用) ====================

    private static CodeTabPayload resolvePayload(Dragboard dragboard, boolean remove) {
        CodeTabPayload payload = resolvePayload(
                dragboard.getContent(CODE_TAB_FORMAT), remove);
        if (payload != null) {
            return payload;
        }
        String text = dragboard.getString();
        if (text != null && text.startsWith(TOKEN_PREFIX)) {
            return resolvePayload(text.substring(TOKEN_PREFIX.length()), remove);
        }
        return null;
    }

    private static CodeTabPayload resolvePayload(String token, boolean remove) {
        if (token == null) {
            return null;
        }
        return remove ? DRAG_PAYLOADS.remove(token) : DRAG_PAYLOADS.get(token);
    }

    // ==================== 负载辅助方法 ====================

    private static CodeTabPayload resolvePayload(Object content, boolean remove) {
        if (content instanceof CodeTabPayload payload) {
            return payload;
        }
        if (content instanceof String token) {
            return remove ? DRAG_PAYLOADS.remove(token) : DRAG_PAYLOADS.get(token);
        }
        return null;
    }

    private static String getDragToken(Dragboard dragboard) {
        Object content = dragboard.getContent(CODE_TAB_FORMAT);
        if (content instanceof String token) {
            return token;
        }
        String text = dragboard.getString();
        if (text != null && text.startsWith(TOKEN_PREFIX)) {
            return text.substring(TOKEN_PREFIX.length());
        }
        return null;
    }

    private static TabPane resolveSourcePane(Dragboard dragboard, boolean remove) {
        String token = getDragToken(dragboard);
        return resolveSourcePane(token, remove);
    }

    private static TabPane resolveSourcePane(String token, boolean remove) {
        if (token == null) {
            return null;
        }
        return remove ? DRAG_SOURCES.remove(token) : DRAG_SOURCES.get(token);
    }

    private static CodeEditorTab resolveSourceTab(String token, boolean remove) {
        if (token == null) {
            return null;
        }
        return remove ? DRAG_SOURCE_TABS.remove(token) : DRAG_SOURCE_TABS.get(token);
    }

    private static void removeMatchingTabNow(TabPane pane, CodeTabPayload payload) {
        if (pane == null || payload == null) {
            return;
        }
        List<Tab> toRemove = new ArrayList<>();
        for (Tab tab : pane.getTabs()) {
            if (tab instanceof CodeEditorTab codeTab
                    && Objects.equals(codeTab.getOpenFile().fullPath(), payload.fullPath())
                    && codeTab.getOpenFile().engine() == payload.engine()) {
                toRemove.add(tab);
            }
        }
        pane.getTabs().removeAll(toRemove);
    }

    private static void trimDragPayloads() {
        while (DRAG_PAYLOADS.size() > MAX_DRAG_PAYLOADS) {
            var iterator = DRAG_PAYLOADS.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            String key = iterator.next();
            cleanupDragToken(key);
        }
    }

    private static void cleanupDragToken(String token) {
        if (token == null) {
            return;
        }
        DRAG_PAYLOADS.remove(token);
        DRAG_SOURCES.remove(token);
        DRAG_SOURCE_TABS.remove(token);
    }

    private static boolean dropTabInto(TabPane targetPane, String token, AppConfig config,
                                       VsCodeThemeLoader.ThemeData editorTheme,
                                       boolean clearSplitContext) {
        if (targetPane == null || token == null) {
            return false;
        }
        TabPane sourcePane = resolveSourcePane(token, true);
        CodeEditorTab sourceTab = resolveSourceTab(token, true);
        CodeTabPayload payload = resolvePayload(token, true);
        if (sourcePane == targetPane || (sourceTab != null && sourceTab.getTabPane() == targetPane)) {
            return false;
        }

        CodeEditorTab droppedTab = sourceTab != null
                ? sourceTab
                : payload == null ? null : createTab(payload, config, editorTheme);
        if (droppedTab == null) {
            return false;
        }

        if (sourceTab != null) {
            if (!detachTab(sourceTab, sourcePane, targetPane)) {
                return false;
            }
        } else if (sourcePane != null) {
            removeMatchingTabNow(sourcePane, payload);
        }

        if (clearSplitContext) {
            droppedTab.clearSplitEditorPane();
        }
        targetPane.getTabs().add(droppedTab);
        targetPane.getSelectionModel().select(droppedTab);
        if (isCodeOnlyTarget(targetPane)) {
            configureForCodeOnlyTarget(droppedTab, targetPane, config, editorTheme);
        } else {
            restoreOriginalHandlers(droppedTab);
        }
        enableTabDrag(droppedTab);
        return true;
    }

    private static void markCodeOnlyPane(TabPane pane, SplitEditorPane splitEditorPane) {
        if (pane == null) {
            return;
        }
        pane.getProperties().put(CODE_ONLY_TARGET, true);
        if (splitEditorPane != null) {
            pane.getProperties().put(CODE_ONLY_SPLIT_EDITOR, splitEditorPane);
        }
    }

    private static void configureForCodeOnlyTarget(CodeEditorTab tab, TabPane targetPane,
                                                   AppConfig config,
                                                   VsCodeThemeLoader.ThemeData editorTheme) {
        if (tab == null || targetPane == null || !isCodeOnlyTarget(targetPane)) {
            return;
        }
        saveOriginalHandlers(tab);
        Object sep = targetPane.getProperties().get(CODE_ONLY_SPLIT_EDITOR);
        if (sep instanceof SplitEditorPane splitEditor) {
            tab.setSplitEditorPane(splitEditor);
        }
        installFallbackHandlers(tab, config, editorTheme);
        installCloseCleanup(tab);
    }

    private static boolean isCodeOnlyTarget(TabPane pane) {
        return pane != null && Boolean.TRUE.equals(pane.getProperties().get(CODE_ONLY_TARGET));
    }

    private static void saveOriginalHandlers(CodeEditorTab tab) {
        if (tab == null || Boolean.TRUE.equals(tab.getProperties().get(ORIGINAL_HANDLERS_SAVED))) {
            return;
        }
        tab.getProperties().put(ORIGINAL_HANDLERS_SAVED, true);
        var splitHandler = tab.getOnSplitRequested();
        if (splitHandler != null) {
            tab.getProperties().put(ORIGINAL_SPLIT_HANDLER, splitHandler);
        }
        var engineHandler = tab.getOnSwitchEngine();
        if (engineHandler != null) {
            tab.getProperties().put(ORIGINAL_ENGINE_HANDLER, engineHandler);
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreOriginalHandlers(CodeEditorTab tab) {
        if (tab == null || !Boolean.TRUE.equals(tab.getProperties().remove(ORIGINAL_HANDLERS_SAVED))) {
            return;
        }
        Object splitHandler = tab.getProperties().remove(ORIGINAL_SPLIT_HANDLER);
        Object engineHandler = tab.getProperties().remove(ORIGINAL_ENGINE_HANDLER);
        tab.setOnSplitRequested(splitHandler instanceof java.util.function.Consumer<?>
                ? (java.util.function.Consumer<CodeEditorTab>) splitHandler : null);
        tab.setOnSwitchEngine(engineHandler instanceof java.util.function.Consumer<?>
                ? (java.util.function.Consumer<DecompilerTypeEnum>) engineHandler : null);
    }

    private static void installCloseCleanup(CodeEditorTab tab) {
        if (tab == null || Boolean.TRUE.equals(tab.getProperties().get(CODE_ONLY_CLOSE_CLEANUP))) {
            return;
        }
        tab.getProperties().put(CODE_ONLY_CLOSE_CLEANUP, true);
        tab.addEventHandler(Tab.CLOSED_EVENT, event -> cancelEngineSwitch(tab));
    }

    private static void cancelEngineSwitch(CodeEditorTab tab) {
        if (tab == null) {
            return;
        }
        Object task = tab.getProperties().remove(ENGINE_SWITCH_TASK);
        if (task instanceof Future<?> future) {
            BackgroundTasks.cancel(future);
        }
        tab.getProperties().remove(ENGINE_SWITCH_REQUEST);
    }

    private static boolean detachTab(CodeEditorTab tab, TabPane sourcePane, TabPane targetPane) {
        TabPane owner = tab.getTabPane();
        if (owner == targetPane) {
            return false;
        }
        if (owner != null) {
            owner.getTabs().remove(tab);
        } else if (sourcePane != null) {
            sourcePane.getTabs().remove(tab);
        }
        return tab.getTabPane() == null;
    }

    public static CodeEditorTab copyTab(CodeEditorTab sourceTab, AppConfig config,
                                        VsCodeThemeLoader.ThemeData editorTheme) {
        CodeEditorTab copy = createTab(toPayload(sourceTab), config, editorTheme);
        copyRuntimeContext(sourceTab, copy);
        return copy;
    }

    private static void copyRuntimeContext(CodeEditorTab sourceTab, CodeEditorTab copy) {
        if (sourceTab == null || copy == null) {
            return;
        }
        Object ws = sourceTab.getProperties().get("workspace");
        Object node = sourceTab.getProperties().get("fileTreeNode");
        if (ws != null) {
            copy.getProperties().put("workspace", ws);
        }
        if (node != null) {
            copy.getProperties().put("fileTreeNode", node);
        }
        if (Boolean.TRUE.equals(sourceTab.getProperties().get("pinned"))) {
            copy.getProperties().put("pinned", true);
            copy.updatePinnedDisplay(true);
        }
        CodeViewPanel sourcePanel = sourceTab.getCodeViewPanel();
        CodeViewPanel copyPanel = copy.getCodeViewPanel();
        if (sourcePanel == null || copyPanel == null) {
            return;
        }
        CodeViewContext context = sourcePanel.getContextMenuContext();
        CodeActionHandler handler = sourcePanel.getContextMenuHandler();
        if (context != null && handler != null) {
            copyPanel.installContextMenu(context, handler);
            copy.updateVisibleSource(displaySourceFor(copy.getOpenFile(), context));
        }
    }

    private static CodeEditorTab createTab(CodeTabPayload payload, AppConfig config,
                                           VsCodeThemeLoader.ThemeData editorTheme) {
        OpenFile openFile = new OpenFile(payload.className(), payload.fullPath(),
                payload.sourceCode(), payload.engine());
        CodeEditorTab tab = new CodeEditorTab(openFile, editorTheme,
                config.theme().fontFamily(), config.theme().fontSize(),
                config.decompiler().wrapText(), config.decompiler().lineNumbersEnabled(),
                payload.classBytes(), OutlineParser.extractMetadata(payload.sourceCode()), null);
        installFallbackHandlers(tab, config, editorTheme);
        return tab;
    }

    private static void installFallbackHandlers(CodeEditorTab tab, AppConfig config,
                                                VsCodeThemeLoader.ThemeData editorTheme) {
        tab.setOnSwitchEngine(newEngine -> switchEngineInCodeOnlyWindow(
                tab, config, editorTheme, newEngine));
        tab.setOnSplitRequested(sourceTab -> {
            SplitEditorPane sep = sourceTab.getSplitEditorPane();
            if (sep == null || sep.activeCellCount() >= 3) {
                return;
            }
            TabPane sourcePane = sep.tabPaneFor(sourceTab);
            sep.splitRight(sourceTab);
            TabPane targetPane = null;
            for (TabPane pane : sep.allTabPanes()) {
                if (pane != sourcePane && pane.getTabs().isEmpty()) {
                    targetPane = pane;
                    break;
                }
            }
            if (targetPane == null) {
                var panes = sep.allTabPanes();
                targetPane = panes.get(panes.size() - 1);
            }
            markCodeOnlyPane(targetPane, sep);
            CodeEditorTab copy = copyTab(sourceTab, config, editorTheme);
            copy.setSplitEditorPane(sep);
            targetPane.getTabs().add(copy);
            targetPane.getSelectionModel().select(copy);
            enableTabDrag(copy);
        });
    }

    private static void switchEngineInCodeOnlyWindow(CodeEditorTab tab, AppConfig config,
                                                     VsCodeThemeLoader.ThemeData editorTheme,
                                                     DecompilerTypeEnum requestedEngine) {
        if (tab == null || requestedEngine == null || tab.getOpenFile() == null) {
            return;
        }
        Object oldTask = tab.getProperties().get(ENGINE_SWITCH_TASK);
        if (oldTask instanceof Future<?> future) {
            BackgroundTasks.cancel(future);
        }

        long requestId = ENGINE_SWITCH_IDS.incrementAndGet();
        tab.getProperties().put(ENGINE_SWITCH_REQUEST, requestId);
        OpenFile previous = tab.getOpenFile();
        tab.setSourceReady(false);
        tab.updateVisibleSource("// " + I18nUtil.getString("task.decompiling")
                + ": " + previous.fullPath() + " [" + requestedEngine.name() + "]");

        Future<?> task = BackgroundTasks.run("CodeOnlySwitchEngine-" + previous.fullPath(), () -> {
            try {
                byte[] bytes = classBytesForTab(tab);
                DecompilerTypeEnum engine = effectiveEngineFor(bytes, requestedEngine);
                Map<String, String> options = DecompilerOptions.forEngine(config, engine);
                Workspace workspace = workspaceForTab(tab);
                DecompilerContext context = DecompilerRunner.contextForWorkspace(workspace, options);
                String source = DecompilerRunner.decompileWithTimeout(
                        previous.fullPath(), bytes, engine, context,
                        () -> !Thread.currentThread().isInterrupted()
                              && isEngineSwitchCurrent(tab, requestId));
                if (!isEngineSwitchCurrent(tab, requestId)) {
                    return;
                }
                if (DecompilerRunner.isTransientFailureOutput(source)) {
                    logger.error("副窗口切换反编译引擎失败: {} -> {}\n{}",
                            previous.fullPath(), engine, source);
                    Platform.runLater(() -> {
                        if (isEngineSwitchCurrent(tab, requestId)) {
                            restorePreviousSource(tab, previous);
                            tab.setSourceReady(true);
                            showDecompileFailed(tab, source);
                        }
                    });
                    return;
                }

                CodeMetadata metadata = source != null && source.length() <= METADATA_SOURCE_THRESHOLD
                        ? OutlineParser.extractMetadata(source)
                        : new CodeMetadata(Map.of());
                OpenFile updated = new OpenFile(previous.className(), previous.fullPath(), source, engine);
                Platform.runLater(() -> {
                    if (!isEngineSwitchCurrent(tab, requestId)) {
                        return;
                    }
                    updateCodeOnlyTab(tab, updated, bytes, metadata, options);
                    installFallbackHandlers(tab, config, editorTheme);
                });
            } catch (Exception e) {
                logger.error("副窗口切换反编译引擎异常: {} -> {}",
                        previous.fullPath(), requestedEngine, e);
                Platform.runLater(() -> {
                    if (isEngineSwitchCurrent(tab, requestId)) {
                        restorePreviousSource(tab, previous);
                        tab.setSourceReady(true);
                        showDecompileFailed(tab, e.getMessage());
                    }
                });
            }
        });
        tab.getProperties().put(ENGINE_SWITCH_TASK, task);
    }

    private static boolean isEngineSwitchCurrent(CodeEditorTab tab, long requestId) {
        Object value = tab.getProperties().get(ENGINE_SWITCH_REQUEST);
        return value instanceof Long id && id == requestId;
    }

    private static byte[] classBytesForTab(CodeEditorTab tab) throws java.io.IOException {
        byte[] bytes = tab.getClassBytes();
        if (bytes != null && bytes.length > 0) {
            return bytes;
        }
        Object node = tab.getProperties().get("fileTreeNode");
        if (node instanceof FileTreeNode fileTreeNode) {
            return fileTreeNode.resolveBytes();
        }
        return null;
    }

    private static Workspace workspaceForTab(CodeEditorTab tab) {
        Object workspace = tab.getProperties().get("workspace");
        return workspace instanceof Workspace ws ? ws : null;
    }

    private static void updateCodeOnlyTab(CodeEditorTab tab, OpenFile openFile, byte[] bytes,
                                          CodeMetadata metadata, Map<String, String> options) {
        CodeViewPanel panel = tab.getCodeViewPanel();
        CodeViewContext previousContext = panel == null ? null : panel.getContextMenuContext();
        CodeActionHandler handler = panel == null ? null : panel.getContextMenuHandler();
        tab.updateDecompiledContent(openFile, metadata, null);

        CodeViewContext context = buildUpdatedContext(tab, previousContext,
                openFile, bytes, metadata, options);
        if (context != null && handler != null) {
            tab.getCodeViewPanel().installContextMenu(context, handler);
            tab.updateVisibleSource(displaySourceFor(openFile, context));
        }
        tab.setSourceReady(true);
        tab.getProperties().remove(ENGINE_SWITCH_TASK);
        TabPane pane = tab.getTabPane();
        if (pane != null) {
            pane.getSelectionModel().select(tab);
        }
    }

    private static CodeViewContext buildUpdatedContext(CodeEditorTab tab,
                                                       CodeViewContext previousContext,
                                                       OpenFile openFile, byte[] bytes,
                                                       CodeMetadata metadata,
                                                       Map<String, String> options) {
        Workspace workspace = previousContext != null
                ? previousContext.workspace() : workspaceForTab(tab);
        FileTreeNode node = previousContext != null ? previousContext.node() : null;
        if (node == null) {
            Object nodeProperty = tab.getProperties().get("fileTreeNode");
            if (nodeProperty instanceof FileTreeNode fileTreeNode) {
                node = fileTreeNode;
            }
        }
        WorkspaceIndex index = workspace != null && workspace.isIndexReady()
                ? workspace.getIndex()
                : previousContext != null ? previousContext.workspaceIndex() : WorkspaceIndex.EMPTY;
        String workspaceHash = previousContext != null
                ? previousContext.workspaceHash()
                : CommentScope.workspaceHash(workspace);
        String optionsHash = DecompilerOptions.hash(options);
        return new CodeViewContext(workspace, node, openFile, bytes, metadata, index,
                workspaceHash, CommentExportDecorator.sourceHash(openFile.sourceCode()), optionsHash);
    }

    private static void restorePreviousSource(CodeEditorTab tab, OpenFile previous) {
        CodeViewPanel panel = tab == null ? null : tab.getCodeViewPanel();
        CodeViewContext context = panel == null ? null : panel.getContextMenuContext();
        tab.updateVisibleSource(displaySourceFor(previous, context));
        tab.getProperties().remove(ENGINE_SWITCH_TASK);
    }

    private static String displaySourceFor(OpenFile openFile, CodeViewContext context) {
        if (openFile == null) {
            return "";
        }
        if (context == null) {
            return openFile.sourceCode();
        }
        return CommentExportDecorator.applyForClass(openFile.sourceCode(), openFile.fullPath(),
                new CommentScope(context.workspaceHash(), context.optionsHash()));
    }

    private static DecompilerTypeEnum effectiveEngineFor(byte[] bytes,
                                                         DecompilerTypeEnum requestedEngine) {
        if (requestedEngine == null) {
            return DecompilerTypeEnum.VINEFLOWER;
        }
        if (requestedEngine != DecompilerTypeEnum.JD) {
            return requestedEngine;
        }
        int major = classMajorVersion(bytes);
        return major >= 65 ? DecompilerTypeEnum.VINEFLOWER : requestedEngine;
    }

    private static int classMajorVersion(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return 0;
        }
        return ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
    }

    private static void showDecompileFailed(CodeEditorTab tab, String message) {
        javafx.stage.Window owner = tab.getTabPane() == null
                || tab.getTabPane().getScene() == null
                ? null : tab.getTabPane().getScene().getWindow();
        DialogHelper.showError(owner, I18nUtil.getString("dialog.error.title"),
                I18nUtil.getString("dialog.decompile.failed", firstLine(message)));
    }

    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        String normalized = message.replace("\r\n", "\n").replace('\r', '\n');
        int idx = normalized.indexOf('\n');
        return idx >= 0 ? normalized.substring(0, idx) : normalized;
    }

    private static CodeTabPayload toPayload(CodeEditorTab tab) {
        OpenFile openFile = tab.getOpenFile();
        return new CodeTabPayload(openFile.className(), openFile.fullPath(),
                openFile.sourceCode(), openFile.engine(), tab.getClassBytes());
    }

    // ==================== 标签页创建 ====================

    private static void setAppIcon(Stage stage) {
        try {
            var stream = CodeOnlyWindow.class.getResourceAsStream("/icon/logo.png");
            if (stream != null) {
                stage.getIcons().add(new Image(stream));
            }
        } catch (Exception ignored) {
            logger.debug("设置独立窗口图标失败", ignored);
        }
    }

    public void addTab(CodeEditorTab tab) {
        tab.setSplitEditorPane(splitEditorPane);
        tabPane.getTabs().add(tab);
        configureForCodeOnlyTarget(tab, tabPane, config, editorTheme);
        enableTabDrag(tab);
        tabPane.getSelectionModel().select(tab);
        updateTitle();
    }

    private void show() {
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        tabPane.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> updateTitle());

        BorderPane root = new BorderPane(splitEditorPane);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, stage.getWidth(), stage.getHeight());
        scene.setFill(Color.web("#1e1e1e"));
        scene.getStylesheets().add(AppTheme.darkStylesheet());

        updateTitle();
        stage.setScene(scene);
        stage.setOnHidden(event -> cancelAllEngineSwitchTasks());
        stage.show();
        DefaultWindowTheme.applyWindowDarkMode(stage);
    }

    private void cancelAllEngineSwitchTasks() {
        splitEditorPane.forEachTab(CodeOnlyWindow::cancelEngineSwitch);
    }

    // ==================== 应用图标 ====================

    private void updateTitle() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected instanceof CodeEditorTab codeTab) {
            stage.setTitle("FxDecompiler - " + codeTab.getDisplayTitle());
        } else {
            stage.setTitle(selected == null ? "FxDecompiler" : "FxDecompiler - " + selected.getText());
        }
    }

    public record CodeTabPayload(
            String className,
            String fullPath,
            String sourceCode,
            com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum engine,
            byte[] classBytes
    ) implements java.io.Serializable {
    }

    private static final class DragCleanupHandler
            implements javafx.event.EventHandler<DragEvent> {

        private final Node source;
        private final String token;

        private DragCleanupHandler(Node source, String token) {
            this.source = source;
            this.token = token;
        }

        @Override
        public void handle(DragEvent event) {
            cleanupDragToken(token);
            source.removeEventHandler(DragEvent.DRAG_DONE, this);
        }
    }
}
