package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.scene.control.CheckBox;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 代码视图顶层容器，管理底部标签切换与 Split View 分屏
 *
 * <p>内部组件：EditorSearchBar（Ctrl+F 激活）→ 内容区（CodeContentDeck / SplitPane）</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class CodeViewPanel extends VBox {

    private final CodeContentDeck leftDeck;
    private final EditorSearchBar searchBar;
    private final SplitPane splitPane;
    private final CheckBox splitToggle;
    private final byte[] classBytes;
    private CodeContentDeck rightDeck;
    private boolean splitActive;
    private int defaultFontSize = 14;
    private String fontFamily = "Consolas";
    private boolean lineNumbersEnabled = true;
    private CodeViewContext contextMenuContext;
    private CodeActionHandler contextMenuHandler;

    public CodeViewPanel(String sourceCode, byte[] classBytes) {
        this(sourceCode, classBytes, null, "Consolas", 14, true);
    }

    public CodeViewPanel(String sourceCode, byte[] classBytes, SourceContentPanel sourcePanel) {
        this(sourceCode, classBytes, sourcePanel, "Consolas", 14, true);
    }

    public CodeViewPanel(String sourceCode, byte[] classBytes, SourceContentPanel sourcePanel,
                         String fontFamily, int fontSize, boolean lineNumbersEnabled) {
        this.classBytes = classBytes == null ? null : classBytes.clone();
        this.defaultFontSize = fontSize;
        this.fontFamily = fontFamily;
        this.lineNumbersEnabled = lineNumbersEnabled;
        this.leftDeck = new CodeContentDeck(sourceCode, classBytes, sourcePanel,
                fontFamily, fontSize, lineNumbersEnabled);
        this.searchBar = new EditorSearchBar(leftDeck.getSourcePanel() != null
                ? leftDeck.getSourcePanel().getCodeArea() : null);
        this.splitPane = new SplitPane();
        this.splitToggle = new CheckBox(I18nUtil.getString("split.view"));

        splitToggle.getStyleClass().add("split-toggle");
        splitToggle.setOnAction(e -> setSplitMode(splitToggle.isSelected()));

        leftDeck.getBottomBar().getChildren().add(splitToggle);

        searchBar.setVisible(false);
        searchBar.setManaged(false);

        getChildren().add(searchBar);
        showSingleView();
        getStyleClass().add("code-view-panel");
    }

    /** 单视图：leftDeck 填充全部空间 */
    private void showSingleView() {
        getChildren().remove(splitPane);
        if (!getChildren().contains(leftDeck)) {
            getChildren().add(leftDeck);
        }
        VBox.setVgrow(leftDeck, Priority.ALWAYS);
        splitActive = false;
    }

    /** Split 视图：左右独立 deck，右侧默认 Bytecode */
    private void showSplitView() {
        if (rightDeck == null) {
            rightDeck = new CodeContentDeck(leftDeck.getSourcePanel() != null
                    ? leftDeck.getSourcePanel().getCodeArea().getText() : "",
                    classBytes, null, fontFamily, defaultFontSize, lineNumbersEnabled);
            rightDeck.setSelected(CodeContentDeck.TAB_BYTECODE);
            rightDeck.getBottomBar().getChildren().add(createRightSplitToggle());
        }

        splitPane.getItems().setAll(leftDeck, rightDeck);
        splitPane.setDividerPositions(0.5);

        getChildren().remove(leftDeck);
        if (!getChildren().contains(splitPane)) {
            getChildren().add(splitPane);
        }
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        splitActive = true;
    }

    private CheckBox createRightSplitToggle() {
        CheckBox cb = new CheckBox(I18nUtil.getString("split.view"));
        cb.getStyleClass().add("split-toggle");
        cb.setSelected(true);
        cb.setOnAction(e -> setSplitMode(cb.isSelected()));
        return cb;
    }

    /** 切换分屏模式 */
    public void setSplitMode(boolean enabled) {
        if (enabled == splitActive) return;
        if (enabled) {
            showSplitView();
        } else {
            showSingleView();
        }
        splitToggle.setSelected(enabled);
    }

    /** @return 是否处于分屏模式 */
    public boolean isSplitActive() {
        return splitActive;
    }

    /** @return 左侧源码 CodeArea */
    public jfx.incubator.scene.control.richtext.CodeArea getSourceCodeArea() {
        SourceContentPanel sp = leftDeck.getSourcePanel();
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

    private void installContextMenuOnCurrentSource() {
        SourceContentPanel sourcePanel = leftDeck.getSourcePanel();
        var area = sourcePanel == null ? null : sourcePanel.getCodeArea();
        if (area == null) return;
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
        leftDeck.replaceSourcePanel(newSource, sourcePanel);
        if (rightDeck != null) {
            rightDeck.updateSource(newSource);
        }
        bindSearchBar();
        installContextMenuOnCurrentSource();
    }

    /** 用新反编译源码更新视图 */
    public void refreshWithNewSource(String newSource) {
        leftDeck.updateSource(newSource);
        if (rightDeck != null) {
            rightDeck.updateSource(newSource);
        }
        bindSearchBar();
        installContextMenuOnCurrentSource();
    }

    /** 只刷新 Code 视图显示文本，保留底层真实源码和其它派生视图 */
    public void refreshDisplayedSource(String displaySource) {
        leftDeck.updateDisplayedSource(displaySource);
        if (rightDeck != null) {
            rightDeck.updateDisplayedSource(displaySource);
        }
        bindSearchBar();
        installContextMenuOnCurrentSource();
    }

    /** @return 源码总行数 */
    public int getLineCount() {
        SourceContentPanel sp = leftDeck.getSourcePanel();
        return sp != null ? sp.getLineCount() : 0;
    }

    /** 放大字号（所有面板统一） */
    public void zoomIn() {
        defaultFontSize = Math.min(48, defaultFontSize + 1);
        applyZoomToAllDecks();
    }

    /** 缩小字号（所有面板统一） */
    public void zoomOut() {
        defaultFontSize = Math.max(8, defaultFontSize - 1);
        applyZoomToAllDecks();
    }

    /** 重置字号（所有面板统一） */
    public void resetZoom() {
        applyZoomToAllDecks();
    }

    private void applyZoomToAllDecks() {
        leftDeck.applyFontSettings(defaultFontSize, fontFamily);
        if (rightDeck != null) rightDeck.applyFontSettings(defaultFontSize, fontFamily);
    }

    public void setDefaultFontSize(int size) {
        this.defaultFontSize = size;
    }

    /** 行号开关（所有面板统一） */
    public void setLineNumbersEnabled(boolean enabled) {
        this.lineNumbersEnabled = enabled;
        leftDeck.setLineNumbersEnabled(enabled);
        if (rightDeck != null) rightDeck.setLineNumbersEnabled(enabled);
    }

    /** 统一应用字体设置到所有面板 */
    public void applyFontSettings(int fontSize, String fontFamily) {
        this.defaultFontSize = fontSize;
        this.fontFamily = fontFamily;
        leftDeck.applyFontSettings(fontSize, fontFamily);
        if (rightDeck != null) rightDeck.applyFontSettings(fontSize, fontFamily);
    }

    /** @return 左侧 CodeContentDeck */
    public CodeContentDeck getLeftDeck() {
        return leftDeck;
    }

    /** 释放资源 */
    public void dispose() {
        leftDeck.dispose();
        if (rightDeck != null) rightDeck.dispose();
    }
}
