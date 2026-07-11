package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * 代码视图顶层容器,管理底部标签切换与分屏勾选框
 *
 * <p>内部组件：EditorSearchBar(Ctrl+F 激活)→ 内容区(CodeContentDeck)
 * 分屏由外部 SplitEditorPane 管理,本类仅提供勾选框并回调外部</p>
 *
 * @author bingbaihanji
 * @date 2026-06-23
 */
public class CodeViewPanel extends VBox {

    private final CodeContentDeck deck;
    private final EditorSearchBar searchBar;
    private final CheckBox splitToggle;
    private int defaultFontSize = 14;
    /** 用户配置的原始字号(用于 resetZoom 恢复) */
    private int originalFontSize = 14;
    private String fontFamily = "Consolas";
    private boolean lineNumbersEnabled = true;
    private CodeViewContext contextMenuContext;
    private CodeActionHandler contextMenuHandler;
    /** 分屏开关回调(true=开启, false=关闭) */
    private Consumer<Boolean> onSplitToggled;
    /** Ctrl+; 快捷键处理器引用(用于先移除再添加,防止重复注册) */
    private javafx.event.EventHandler<javafx.scene.input.KeyEvent> commentKeyHandler;
    private jfx.incubator.scene.control.richtext.CodeArea commentKeyArea;
    /** Ctrl+F 文件内查找处理器引用 */
    private javafx.event.EventHandler<javafx.scene.input.KeyEvent> findKeyHandler;
    private jfx.incubator.scene.control.richtext.CodeArea findKeyArea;

    /** 便捷构造器：使用默认 SourcePanel、字体和行号 */
    public CodeViewPanel(String sourceCode, byte[] classBytes) {
        this(sourceCode, classBytes, null, "Consolas", 14, true);
    }

    /** 便捷构造器：指定 SourcePanel,字体和行号使用默认值 */
    public CodeViewPanel(String sourceCode, byte[] classBytes, SourceContentPanel sourcePanel) {
        this(sourceCode, classBytes, sourcePanel, "Consolas", 14, true);
    }

    /**
     * 完整参数构造器：指定源码、字节码、SourcePanel、字体族、字号和行号开关
     *
     * @param sourceCode          反编译源码文本
     * @param classBytes          类文件原始字节码
     * @param sourcePanel         已构建的源码面板(可为 null,内部自动创建)
     * @param fontFamily          字体族名(如 "Consolas")
     * @param fontSize            初始字号
     * @param lineNumbersEnabled  是否显示行号
     */
    public CodeViewPanel(String sourceCode, byte[] classBytes, SourceContentPanel sourcePanel,
                         String fontFamily, int fontSize, boolean lineNumbersEnabled) {
        this.defaultFontSize = fontSize;
        this.originalFontSize = fontSize;
        this.fontFamily = fontFamily;
        this.lineNumbersEnabled = lineNumbersEnabled;
        this.deck = new CodeContentDeck(sourceCode, classBytes, sourcePanel,
                fontFamily, fontSize, lineNumbersEnabled);
        this.searchBar = new EditorSearchBar(deck.getSourcePanel() != null
                ? deck.getSourcePanel().getCodeArea() : null);
        this.splitToggle = new CheckBox(I18nUtil.getString("split.view"));

        splitToggle.getStyleClass().add("split-toggle");
        splitToggle.setOnAction(e -> {
            if (onSplitToggled != null) {
                onSplitToggled.accept(splitToggle.isSelected());
            }
        });

        deck.getBottomBar().getChildren().add(splitToggle);

        searchBar.setVisible(false);
        searchBar.setManaged(false);

        getChildren().addAll(searchBar, deck);
        VBox.setVgrow(deck, Priority.ALWAYS);
        getStyleClass().add("code-view-panel");
        bindSearchBar();
    }

    /**
     * 获取 CodeArea 的选中文本,过滤空/过长/多行文本后返回
     * 使用反射调用:jfx.incubator CodeArea 暴露 getSelectedText() 但未在公共 API 中声明,
     * 升级 jfx-incubator-richtext 版本后应改为直接调用 area.getSelectedText()
     */
    private static String selectedText(jfx.incubator.scene.control.richtext.CodeArea area) {
        if (area == null) {
            return "";
        }
        try {
            Object value = area.getClass().getMethod("getSelectedText").invoke(area);
            if (!(value instanceof String text) || text.isBlank()
                    || text.length() > 200 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
                return "";
            }
            return text;
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    /** 设置分屏开关回调 */
    public void setOnSplitToggled(Consumer<Boolean> callback) {
        this.onSplitToggled = callback;
    }

    /** 程序化更新勾选框状态(由外部 SplitEditorPane 同步) */
    public void setSplitToggleSelected(boolean selected) {
        splitToggle.setSelected(selected);
    }

    /** @return 左侧源码 CodeArea */
    public jfx.incubator.scene.control.richtext.CodeArea getSourceCodeArea() {
        SourceContentPanel sp = deck.getSourcePanel();
        return sp != null ? sp.getCodeArea() : null;
    }

    /**
     * 在源码 CodeArea 上安装右键上下文菜单和快捷键(Ctrl+; 注释、Shift+F6 重命名)
     *
     * @param ctx     代码视图上下文
     * @param handler 右键菜单和快捷键的操作处理器
     */
    public void installContextMenu(CodeViewContext ctx, CodeActionHandler handler) {
        contextMenuContext = ctx;
        contextMenuHandler = handler;
        installContextMenuOnCurrentSource();
    }

    /** @return 当前源码右键菜单上下文,供标签复制/迁移时复用 */
    public CodeViewContext getContextMenuContext() {
        return contextMenuContext;
    }

    /** @return 当前源码右键菜单处理器,供标签复制/迁移时复用 */
    public CodeActionHandler getContextMenuHandler() {
        return contextMenuHandler;
    }

    private void installContextMenuOnCurrentSource() {
        SourceContentPanel sourcePanel = deck.getSourcePanel();
        var area = sourcePanel == null ? null : sourcePanel.getCodeArea();
        if (area == null) {
            return;
        }
        if (commentKeyArea != null && commentKeyHandler != null) {
            commentKeyArea.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, commentKeyHandler);
            commentKeyArea = null;
            commentKeyHandler = null;
        }
        if (contextMenuContext == null || contextMenuHandler == null) {
            sourcePanel.setTokenNavigateHandler(null);
            return;
        }

        sourcePanel.setTokenNavigateHandler((line, token) ->
                contextMenuHandler.goToDeclaration(contextMenuContext, line, token));
        CodeAreaContextMenu menu = new CodeAreaContextMenu(area, contextMenuContext, contextMenuHandler);
        area.setContextMenu(menu);
        area.setOnContextMenuRequested(e -> {
            menu.prepare(e);
            menu.show(area, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        // Ctrl+; 快捷键：先移除旧处理器再添加,防止重复调用时堆积
        commentKeyArea = area;
        commentKeyHandler = e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.SEMICOLON) {
                e.consume();
                contextMenuHandler.addOrUpdateComment(contextMenuContext, area.getCaretPosition());
                return;
            }
            if (e.getCode() == javafx.scene.input.KeyCode.F6 && e.isShiftDown()) {
                e.consume();
                contextMenuHandler.renameAtCaret(contextMenuContext, area.getCaretPosition());
                return;
            }
            // Alt+Ctrl+Shift+C: 复制引用(类全限定名 / 字段引用 / 方法签名 / 路径:行号)
            if (e.isAltDown() && e.isControlDown() && e.isShiftDown()
                    && e.getCode() == javafx.scene.input.KeyCode.C) {
                e.consume();
                String ref = com.bingbaihanji.fxdecomplie.util.reference.CopyReferenceHelper
                        .getReferenceString(contextMenuContext.openFile(), area);
                if (!ref.isEmpty()) {
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                            new javafx.scene.input.ClipboardContent() {{
                                putString(ref);
                            }});
                    contextMenuHandler.copyReference(ref);
                }
            }
        };
        area.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, commentKeyHandler);
    }

    /** 更新编辑器搜索栏关联的 CodeArea */
    public void bindSearchBar() {
        var area = getSourceCodeArea();
        if (area != null && searchBar != null) {
            searchBar.rebind(area);
            installFindShortcut(area);
        }
    }

    /** @return 编辑器搜索栏 */
    public EditorSearchBar getSearchBar() {
        return searchBar;
    }

    /**
     * 用带完整主题的新源码面板替换 Code 视图(用于代码 Tab 间切换复用面板)
     *
     * @param newSource   新的反编译源码文本
     * @param sourcePanel 已应用主题的新 SourcePanel
     */
    public void replaceSourcePanel(String newSource, SourceContentPanel sourcePanel) {
        deck.replaceSourcePanel(newSource, sourcePanel);
        bindSearchBar();
        installContextMenuOnCurrentSource();
    }

    /**
     * 用新反编译源码更新视图(替换 CodeArea 文本并刷新搜索栏绑定和上下文菜单)
     *
     * @param newSource 新的反编译源码文本
     */
    public void refreshWithNewSource(String newSource) {
        deck.updateSource(newSource);
        bindSearchBar();
        installContextMenuOnCurrentSource();
    }

    /** 只刷新 Code 视图显示文本 */
    public void refreshDisplayedSource(String displaySource) {
        deck.updateDisplayedSource(displaySource);
        bindSearchBar();
        installContextMenuOnCurrentSource();
    }

    /** @return 源码总行数 */
    public int getLineCount() {
        SourceContentPanel sp = deck.getSourcePanel();
        return sp != null ? sp.getLineCount() : 0;
    }

    /** 放大字号 */
    public void zoomIn() {
        defaultFontSize = Math.min(48, defaultFontSize + 1);
        deck.applyFontSettings(defaultFontSize, fontFamily);
    }

    /** 缩小字号 */
    public void zoomOut() {
        defaultFontSize = Math.max(8, defaultFontSize - 1);
        deck.applyFontSettings(defaultFontSize, fontFamily);
    }

    /** 重置字号到用户配置的原始值 */
    public void resetZoom() {
        defaultFontSize = originalFontSize;
        deck.applyFontSettings(defaultFontSize, fontFamily);
    }

    /** 设置默认字号(不立即生效,仅更新内部记录) */
    public void setDefaultFontSize(int size) {
        this.defaultFontSize = size;
    }

    /** 行号开关 */
    public void setLineNumbersEnabled(boolean enabled) {
        this.lineNumbersEnabled = enabled;
        deck.setLineNumbersEnabled(enabled);
    }

    /**
     * 统一应用字体设置到所有代码视图面板
     *
     * @param fontSize   字号
     * @param fontFamily 字体族名
     */
    public void applyFontSettings(int fontSize, String fontFamily) {
        this.defaultFontSize = fontSize;
        this.originalFontSize = fontSize;
        this.fontFamily = fontFamily;
        deck.applyFontSettings(fontSize, fontFamily);
    }

    /** @return CodeContentDeck */
    public CodeContentDeck getDeck() {
        return deck;
    }

    /** 释放资源 */
    public void dispose() {
        if (commentKeyArea != null && commentKeyHandler != null) {
            commentKeyArea.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, commentKeyHandler);
        }
        if (findKeyArea != null && findKeyHandler != null) {
            findKeyArea.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, findKeyHandler);
        }
        deck.dispose();
    }

    /**
     * 在 CodeArea 上安装 Ctrl+F / F3 / Escape 快捷键绑定
     * <p>先移除旧处理器再注册新的,防止源码刷新时重复累积事件处理器</p>
     */
    private void installFindShortcut(jfx.incubator.scene.control.richtext.CodeArea area) {
        if (findKeyArea != null && findKeyHandler != null) {
            findKeyArea.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, findKeyHandler);
        }
        findKeyArea = area;
        findKeyHandler = e -> {
            if (e.isControlDown() && !e.isAltDown() && !e.isShiftDown()
                    && e.getCode() == javafx.scene.input.KeyCode.F) {
                e.consume();
                searchBar.show(selectedText(area));
                return;
            }
            if (searchBar.isVisible() && e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                e.consume();
                searchBar.hide();
                area.requestFocus();
                return;
            }
            if (searchBar.isVisible() && e.getCode() == javafx.scene.input.KeyCode.F3) {
                e.consume();
                if (e.isShiftDown()) {
                    searchBar.navigatePrevious();
                } else {
                    searchBar.navigateNext();
                }
            }
        };
        area.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, findKeyHandler);
    }
}
