package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * 代码视图顶层容器，管理底部标签切换与分屏勾选框。
 *
 * <p>内部组件：EditorSearchBar（Ctrl+F 激活）→ 内容区（CodeContentDeck）。
 * 分屏由外部 SplitEditorPane 管理，本类仅提供勾选框并回调外部。</p>
 *
 * @author bingbaihanji
 * @date 2026-06-23
 */
public class CodeViewPanel extends VBox {

    private final CodeContentDeck deck;
    private final EditorSearchBar searchBar;
    private final CheckBox splitToggle;
    private int defaultFontSize = 14;
    private String fontFamily = "Consolas";
    private boolean lineNumbersEnabled = true;
    private CodeViewContext contextMenuContext;
    private CodeActionHandler contextMenuHandler;
    /** 分屏开关回调（true=开启, false=关闭） */
    private Consumer<Boolean> onSplitToggled;

    public CodeViewPanel(String sourceCode, byte[] classBytes) {
        this(sourceCode, classBytes, null, "Consolas", 14, true);
    }

    public CodeViewPanel(String sourceCode, byte[] classBytes, SourceContentPanel sourcePanel) {
        this(sourceCode, classBytes, sourcePanel, "Consolas", 14, true);
    }

    public CodeViewPanel(String sourceCode, byte[] classBytes, SourceContentPanel sourcePanel,
                         String fontFamily, int fontSize, boolean lineNumbersEnabled) {
        this.defaultFontSize = fontSize;
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
    }

    /** 设置分屏开关回调 */
    public void setOnSplitToggled(Consumer<Boolean> callback) {
        this.onSplitToggled = callback;
    }

    /** 程序化更新勾选框状态（由外部 SplitEditorPane 同步） */
    public void setSplitToggleSelected(boolean selected) {
        splitToggle.setSelected(selected);
    }

    /** @return 左侧源码 CodeArea */
    public jfx.incubator.scene.control.richtext.CodeArea getSourceCodeArea() {
        SourceContentPanel sp = deck.getSourcePanel();
        return sp != null ? sp.getCodeArea() : null;
    }

    /**
     * 在源码 CodeArea 上安装右键上下文菜单
     */
    public void installContextMenu(CodeViewContext ctx, CodeActionHandler handler) {
        contextMenuContext = ctx;
        contextMenuHandler = handler;
        installContextMenuOnCurrentSource();
    }

    /** @return 当前源码右键菜单上下文，供标签复制/迁移时复用 */
    public CodeViewContext getContextMenuContext() {
        return contextMenuContext;
    }

    /** @return 当前源码右键菜单处理器，供标签复制/迁移时复用 */
    public CodeActionHandler getContextMenuHandler() {
        return contextMenuHandler;
    }

    private void installContextMenuOnCurrentSource() {
        SourceContentPanel sourcePanel = deck.getSourcePanel();
        var area = sourcePanel == null ? null : sourcePanel.getCodeArea();
        if (area == null) {
            return;
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
    }

    /** 更新编辑器搜索栏关联的 CodeArea */
    public void bindSearchBar() {
        var area = getSourceCodeArea();
        if (area != null && searchBar != null) {
            searchBar.rebind(area);
        }
    }

    /** @return 编辑器搜索栏 */
    public EditorSearchBar getSearchBar() {
        return searchBar;
    }

    /** 用带完整主题的新源码面板替换 Code 视图 */
    public void replaceSourcePanel(String newSource, SourceContentPanel sourcePanel) {
        deck.replaceSourcePanel(newSource, sourcePanel);
        bindSearchBar();
        installContextMenuOnCurrentSource();
    }

    /** 用新反编译源码更新视图 */
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

    /** 重置字号 */
    public void resetZoom() {
        deck.applyFontSettings(defaultFontSize, fontFamily);
    }

    public void setDefaultFontSize(int size) {
        this.defaultFontSize = size;
    }

    /** 行号开关 */
    public void setLineNumbersEnabled(boolean enabled) {
        this.lineNumbersEnabled = enabled;
        deck.setLineNumbersEnabled(enabled);
    }

    /** 统一应用字体设置 */
    public void applyFontSettings(int fontSize, String fontFamily) {
        this.defaultFontSize = fontSize;
        this.fontFamily = fontFamily;
        deck.applyFontSettings(fontSize, fontFamily);
    }

    /** @return CodeContentDeck */
    public CodeContentDeck getDeck() {
        return deck;
    }

    /** 释放资源 */
    public void dispose() {
        deck.dispose();
    }
}
