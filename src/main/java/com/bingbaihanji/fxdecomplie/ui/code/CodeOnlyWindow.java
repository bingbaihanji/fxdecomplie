package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.FxDecompilerApp;
import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.model.OpenFile;
import com.bingbaihanji.fxdecomplie.platform.FxTools;
import com.bingbaihanji.fxdecomplie.ui.outline.OutlineParser;
import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight code-only window used for detached editor tabs.
 * Supports cross-window tab drag-and-drop with the main window.
 */
public final class CodeOnlyWindow {

    public static final DataFormat CODE_TAB_FORMAT =
            new DataFormat("application/x-fxdecomplie-code-tab");
    private static final String DRAG_SOURCE_INSTALLED =
            "fxdecomplie.codeTabDragSourceInstalled";
    private static final String TOKEN_PREFIX = "fxdecomplie-code-tab:";
    private static final int MAX_DRAG_PAYLOADS = 128;
    private static final Map<String, CodeTabPayload> DRAG_PAYLOADS =
            new ConcurrentHashMap<>();
    private static final Map<String, TabPane> DRAG_SOURCES =
            new ConcurrentHashMap<>();
    private static final Map<String, CodeEditorTab> DRAG_SOURCE_TABS =
            new ConcurrentHashMap<>();

    private final AppConfig config;
    private final VsCodeThemeLoader.ThemeData editorTheme;
    private final TabPane tabPane = new TabPane();
    private final Stage stage;

    private CodeOnlyWindow(AppConfig config, Stage stage) {
        this.config = config;
        this.editorTheme = AppTheme.loadEditorTheme(config);
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

    public void addTab(CodeEditorTab tab) {
        tabPane.getTabs().add(tab);
        enableTabDrag(tab);
        tabPane.getSelectionModel().select(tab);
        updateTitle();
    }

    private void show() {
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // Accept drops from OTHER TabPanes only (prevent self-drop)
        tabPane.addEventFilter(DragEvent.DRAG_OVER, event -> {
            TabPane sourcePane = resolveSourcePane(event.getDragboard(), false);
            if (sourcePane != tabPane && hasPayload(event.getDragboard())) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        tabPane.addEventFilter(DragEvent.DRAG_DROPPED, event -> {
            String token = getDragToken(event.getDragboard());
            TabPane sourcePane = resolveSourcePane(token, true);
            CodeEditorTab sourceTab = resolveSourceTab(token, true);
            CodeTabPayload payload = resolvePayload(token, true);
            if (payload != null && sourcePane != tabPane) {
                CodeEditorTab newTab = createTab(payload, config, editorTheme);
                if (sourcePane != null) {
                    removeSourceTab(sourcePane, sourceTab, payload);
                }
                addTab(newTab);
                event.setDropCompleted(true);
                event.consume();
                return;
            }
            event.setDropCompleted(false);
        });

        tabPane.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> updateTitle());

        BorderPane root = new BorderPane(tabPane);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, stage.getWidth(), stage.getHeight());
        scene.setFill(Color.web("#1e1e1e"));
        scene.getStylesheets().add(AppTheme.darkStylesheet());

        updateTitle();
        stage.setScene(scene);
        stage.show();
        FxTools.applyWindowDarkMode(stage);
    }

    private void updateTitle() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected instanceof CodeEditorTab codeTab) {
            stage.setTitle("FxDecompiler - " + codeTab.getDisplayTitle());
        } else {
            stage.setTitle(selected == null ? "FxDecompiler" : "FxDecompiler - " + selected.getText());
        }
    }

    // ==================== Drag Source ====================

    /** Enable drag on a single CodeEditorTab header. */
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

    // ==================== Drag Target (shared, usable by any TabPane) ====================

    /**
     * Install cross-window tab drag-drop handlers on any TabPane.
     * Called by main window and secondary windows.
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
            TabPane sourcePane = resolveSourcePane(token, true);
            CodeEditorTab sourceTab = resolveSourceTab(token, true);
            CodeTabPayload payload = resolvePayload(token, true);
            if (payload != null && sourcePane != tabPane) {
                CodeEditorTab newTab = createTab(payload, config, editorTheme);
                if (sourcePane != null) {
                    removeSourceTab(sourcePane, sourceTab, payload);
                }
                tabPane.getTabs().add(newTab);
                tabPane.getSelectionModel().select(newTab);
                enableTabDrag(newTab);
                event.setDropCompleted(true);
                event.consume();
                return;
            }
            event.setDropCompleted(false);
        });
        enableTabDragListener(tabPane);
    }

    /**
     * Add a listener that calls enableTabDrag on every CodeEditorTab added to the TabPane.
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

    // ==================== Payload helpers ====================

    private static boolean hasPayload(Dragboard dragboard) {
        return resolvePayload(dragboard, false) != null;
    }

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
        if (token == null) return null;
        return remove ? DRAG_PAYLOADS.remove(token) : DRAG_PAYLOADS.get(token);
    }

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
        if (token == null) return null;
        return remove ? DRAG_SOURCES.remove(token) : DRAG_SOURCES.get(token);
    }

    private static CodeEditorTab resolveSourceTab(String token, boolean remove) {
        if (token == null) return null;
        return remove ? DRAG_SOURCE_TABS.remove(token) : DRAG_SOURCE_TABS.get(token);
    }

    private static void removeSourceTab(TabPane pane, CodeEditorTab sourceTab,
                                        CodeTabPayload payload) {
        if (pane == null || payload == null) return;
        Platform.runLater(() -> {
            if (sourceTab != null && pane.getTabs().remove(sourceTab)) {
                return;
            }
            removeMatchingTabNow(pane, payload);
        });
    }

    private static void removeMatchingTabNow(TabPane pane, CodeTabPayload payload) {
        if (pane == null || payload == null) return;
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
        if (token == null) return;
        DRAG_PAYLOADS.remove(token);
        DRAG_SOURCES.remove(token);
        DRAG_SOURCE_TABS.remove(token);
    }

    // ==================== Tab creation ====================

    public static CodeEditorTab copyTab(CodeEditorTab sourceTab, AppConfig config,
                                        VsCodeThemeLoader.ThemeData editorTheme) {
        return createTab(toPayload(sourceTab), config, editorTheme);
    }

    private static CodeEditorTab createTab(CodeTabPayload payload, AppConfig config,
                                           VsCodeThemeLoader.ThemeData editorTheme) {
        OpenFile openFile = new OpenFile(payload.className(), payload.fullPath(),
                payload.sourceCode(), payload.engine());
        return new CodeEditorTab(openFile, editorTheme,
                config.theme().fontFamily(), config.theme().fontSize(),
                config.decompiler().wrapText(), config.decompiler().lineNumbersEnabled(),
                payload.classBytes(), OutlineParser.extractMetadata(payload.sourceCode()), null);
    }

    private static CodeTabPayload toPayload(CodeEditorTab tab) {
        OpenFile openFile = tab.getOpenFile();
        return new CodeTabPayload(openFile.className(), openFile.fullPath(),
                openFile.sourceCode(), openFile.engine(), tab.getClassBytes());
    }

    // ==================== App icon ====================

    private static void setAppIcon(Stage stage) {
        try {
            var stream = CodeOnlyWindow.class.getResourceAsStream("/icon/logo.png");
            if (stream != null) {
                stage.getIcons().add(new Image(stream));
            }
        } catch (Exception ignored) {
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
