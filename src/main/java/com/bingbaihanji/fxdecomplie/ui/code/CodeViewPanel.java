package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * 代码视图顶层容器，管理底部标签切换与 Split View 分屏
 *
 * <p>内部组件：EditorSearchBar（Ctrl+F 激活）→ 内容区 → 底部标签栏 + Split 开关</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class CodeViewPanel extends VBox {

    private final CodeContentDeck leftDeck;
    private CodeContentDeck rightDeck;
    private final EditorSearchBar searchBar;
    private final StackPane viewContainer;
    private final SplitPane splitPane;
    private final CheckBox splitToggle;
    private boolean splitActive;
    private int defaultFontSize = 14;

    public CodeViewPanel(String sourceCode, byte[] classBytes) {
        this.leftDeck = new CodeContentDeck(sourceCode, classBytes);
        this.searchBar = new EditorSearchBar(leftDeck.getSourcePanel() != null
                ? leftDeck.getSourcePanel().getCodeArea() : null);
        this.viewContainer = new StackPane();
        this.splitPane = new SplitPane();
        this.splitToggle = new CheckBox(I18nUtil.getString("split.view"));

        splitToggle.getStyleClass().add("split-toggle");
        splitToggle.setOnAction(e -> setSplitMode(splitToggle.isSelected()));

        // 将 Split 开关添加到左侧 deck 底部栏的末尾
        leftDeck.getBottomBar().getChildren().add(splitToggle);

        searchBar.setVisible(false);
        searchBar.setManaged(false);

        // 单视图模式：显示 leftDeck（内容区 + 底部栏）
        showSingleView();

        VBox.setVgrow(viewContainer, Priority.ALWAYS);
        getChildren().setAll(searchBar, viewContainer);
        getStyleClass().add("code-view-panel");
    }

    /** 单视图：leftDeck 完整展示 */
    private void showSingleView() {
        viewContainer.getChildren().setAll(leftDeck);
        splitActive = false;
    }

    /** Split 视图：左右独立 deck，右侧默认 Bytecode */
    private void showSplitView() {
        if (rightDeck == null) {
            rightDeck = new CodeContentDeck(leftDeck.getSourcePanel() != null
                    ? leftDeck.getSourcePanel().getCodeArea().getText() : "",
                    null); // TODO: 传入 classBytes
            // 右侧默认选 Bytecode
            rightDeck.setSelected(CodeContentDeck.TAB_BYTECODE);
            // 右侧底部栏也加入 split toggle（关闭用）
            rightDeck.getBottomBar().getChildren().add(createRightSplitToggle());
        }

        // 左右分别展示其 contentArea，但底部栏各自管理
        splitPane.getItems().clear();
        splitPane.getItems().addAll(leftDeck, rightDeck);
        splitPane.setDividerPositions(0.5);

        viewContainer.getChildren().setAll(splitPane);
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

    /** 用新反编译源码更新视图 */
    public void refreshWithNewSource(String newSource) {
        leftDeck.updateSource(newSource);
        bindSearchBar();
    }

    /** @return 源码总行数 */
    public int getLineCount() {
        SourceContentPanel sp = leftDeck.getSourcePanel();
        return sp != null ? sp.getLineCount() : 0;
    }

    /** 放大字号 */
    public void zoomIn() {
        SourceContentPanel sp = leftDeck.getSourcePanel();
        if (sp != null) sp.zoomIn();
    }

    /** 缩小字号 */
    public void zoomOut() {
        SourceContentPanel sp = leftDeck.getSourcePanel();
        if (sp != null) sp.zoomOut();
    }

    /** 重置字号 */
    public void resetZoom() {
        SourceContentPanel sp = leftDeck.getSourcePanel();
        if (sp != null) sp.resetZoom(defaultFontSize);
    }

    public void setDefaultFontSize(int size) {
        this.defaultFontSize = size;
    }

    /** 行号开关 */
    public void setLineNumbersEnabled(boolean enabled) {
        SourceContentPanel sp = leftDeck.getSourcePanel();
        if (sp != null) sp.setLineNumbersEnabled(enabled);
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
