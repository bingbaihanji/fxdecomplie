package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import com.bingbaihanji.fxdecomplie.model.OpenFile;
import com.bingbaihanji.fxdecomplie.service.DecompilerRunner;
import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.fxdecomplie.ui.outline.OutlineParser;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.scene.control.*;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 单个代码标签页,内部委托给 CodeViewPanel 管理底部标签切换和 Split View
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class CodeEditorTab extends Tab {

    private static final Logger log = LoggerFactory.getLogger(CodeEditorTab.class);
    private static final int METADATA_SOURCE_THRESHOLD = 500_000;

    /** 代码视图面板 */
    private final CodeViewPanel codeViewPanel;
    /** 可拖拽的标签标题节点 */
    private final Label titleLabel;
    /** 默认字号 */
    private final int defaultFontSize;
    private final String fontFamily;
    private final boolean wrapText;
    private final boolean lineNumbersEnabled;
    /** 类文件原始字节码 */
    private final byte[] classBytes;
    /** 编辑器内搜索栏 */
    private final EditorSearchBar editorSearchBar;
    private VsCodeThemeLoader.ThemeData theme;
    /** 标签页显示标题 */
    private String displayTitle;
    /** Java 源码编辑器(兼容引用) */
    private CodeArea codeArea;
    /** 打开的文件 */
    private OpenFile openFile;
    /** Ctrl+Click 导航元数据 */
    private CodeMetadata metadata;
    /** Ctrl+Click 导航回调(null 时禁用) */
    private Consumer<CodeMetadata.Reference> onNavigate;
    /** 反编译源码是否已就绪 */
    private boolean sourceReady = true;
    /** 分屏请求回调(由外部设置,传入本 tab 以便在右侧分屏打开同 class 不同引擎) */
    private Consumer<CodeEditorTab> onSplitRequested;
    /** 切换引擎回调(由外部设置,传入目标引擎进行原地重反编译) */
    private Consumer<DecompilerTypeEnum> onSwitchEngine;

    /** 简化构造器,使用默认暗色主题和 Consolas 14pt 字体配置 */
    public CodeEditorTab(OpenFile openFile) {
        this(openFile, VsCodeThemeLoader.defaultDark(), "Consolas", 14, true, true, null, null, null);
    }

    /**
     * 完整构造器
     *
     * @param openFile            打开的文件元数据
     * @param theme               编辑器语法高亮主题
     * @param fontFamily          字体族名称
     * @param fontSize            默认字号
     * @param wrapText            是否自动换行
     * @param lineNumbersEnabled  是否显示行号
     * @param classBytes          类文件原始字节码
     * @param metadata            反编译元数据
     * @param onNavigate          Ctrl+Click 导航回调
     */
    public CodeEditorTab(OpenFile openFile, VsCodeThemeLoader.ThemeData theme, String fontFamily,
                         int fontSize, boolean wrapText, boolean lineNumbersEnabled, byte[] classBytes,
                         CodeMetadata metadata, Consumer<CodeMetadata.Reference> onNavigate) {
        this.openFile = Objects.requireNonNull(openFile, "openFile");
        this.displayTitle = displayTitleFor(openFile);
        this.defaultFontSize = fontSize;
        this.theme = theme;
        this.fontFamily = fontFamily;
        this.wrapText = wrapText;
        this.lineNumbersEnabled = lineNumbersEnabled;
        this.metadata = metadata != null ? metadata : new CodeMetadata(java.util.Map.of());
        this.onNavigate = onNavigate;
        this.classBytes = classBytes == null ? null : classBytes.clone();

        String source = openFile.sourceCode();
        SourceContentPanel srcPanel = new SourceContentPanel(source, theme,
                fontFamily, fontSize, wrapText, lineNumbersEnabled, this.metadata, onNavigate);

        CodeViewPanel viewPanel = new CodeViewPanel(source, this.classBytes, srcPanel,
                fontFamily, fontSize, lineNumbersEnabled);
        viewPanel.setDefaultFontSize(fontSize);
        viewPanel.bindSearchBar();
        // 分屏勾选框 → 触发 SplitEditorPane 分屏
        viewPanel.setOnSplitToggled(enabled -> {
            SplitEditorPane sep = getSplitEditorPane();
            if (sep == null) {
                return;
            }
            if (enabled) {
                requestSplit();
            } else {
                sep.closeAllSplits();
            }
        });
        this.codeViewPanel = viewPanel;
        // 初始主题传递到 CodeContentDeck(HEX 面板懒加载时需要)
        viewPanel.getDeck().setTheme(theme);

        this.codeArea = srcPanel.getCodeArea();
        this.editorSearchBar = viewPanel.getSearchBar();

        titleLabel = createTitleLabel(displayTitle);
        setText("");
        setGraphic(titleLabel);
        setContent(viewPanel);
    }

    /** 创建带省略号截断的标签页标题节点 */
    private static Label createTitleLabel(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("code-tab-title");
        label.setMaxWidth(240);
        label.setMinWidth(0);
        label.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
        return label;
    }

    /** 根据打开的文件生成标签页显示标题：类名 [引擎名] */
    private static String displayTitleFor(OpenFile openFile) {
        return openFile.className() + " [" + openFile.engine().name() + "]";
    }

    /** 构建切换引擎子菜单 */
    static Menu buildEngineSwitchMenu(CodeEditorTab tab) {
        Menu engineMenu = new Menu(I18nUtil.getString("context.switchEngine"));
        ToggleGroup group = new ToggleGroup();
        DecompilerTypeEnum currentEngine = tab.getOpenFile().engine();
        for (DecompilerTypeEnum engine : DecompilerTypeEnum.values()) {
            RadioMenuItem item = new RadioMenuItem(engine.name());
            item.setToggleGroup(group);
            item.setSelected(engine == currentEngine);
            item.setOnAction(e -> tab.switchEngine(engine));
            engineMenu.getItems().add(item);
        }
        return engineMenu;
    }

    /** 加载字体,优先级：内置 FiraCode → 指定字体族 → Consolas 回退 */
    private static javafx.scene.text.Font loadFont(String fontFamily, int fontSize) {
        try {
            java.net.URL url = CodeEditorTab.class.getResource("/ttf/FiraCode-Light.ttf");
            if (url != null) {
                return javafx.scene.text.Font.loadFont(url.toExternalForm(), fontSize);
            }
        } catch (Exception ignored) {
            log.debug("加载自定义字体失败,回退到系统字体", ignored);
        }
        if (fontFamily != null && !fontFamily.isBlank()) {
            return javafx.scene.text.Font.font(fontFamily, fontSize);
        }
        return javafx.scene.text.Font.font("Consolas", fontSize);
    }

    /** @return 所属的分屏编辑器,未设置时返回 null */
    public SplitEditorPane getSplitEditorPane() {
        return (SplitEditorPane) getProperties().get("splitEditorPane");
    }

    /** 设置所属的分屏编辑器(由 SplitEditorPane 在 tab 选中时调用) */
    public void setSplitEditorPane(SplitEditorPane pane) {
        if (pane == null) {
            clearSplitEditorPane();
            return;
        }
        getProperties().put("splitEditorPane", pane);
        // 同步分屏状态到勾选框
        pane.setOnSplitStateChanged(() -> {
            boolean hasSplit = pane.activeCellCount() > 1;
            codeViewPanel.setSplitToggleSelected(hasSplit);
        });
    }

    /** 清除所属分屏编辑器引用,用于移动到纯代码窗口等非分屏容器 */
    public void clearSplitEditorPane() {
        getProperties().remove("splitEditorPane");
        codeViewPanel.setSplitToggleSelected(false);
    }

    /** @return 分屏请求回调(包级可见,供外部读取) */
    Consumer<CodeEditorTab> getOnSplitRequested() {
        return onSplitRequested;
    }

    /** 设置分屏请求回调 */
    public void setOnSplitRequested(Consumer<CodeEditorTab> callback) {
        this.onSplitRequested = callback;
    }

    /** @return 切换引擎回调(包级可见,供外部读取) */
    Consumer<DecompilerTypeEnum> getOnSwitchEngine() {
        return onSwitchEngine;
    }

    /** 设置切换引擎回调 */
    public void setOnSwitchEngine(Consumer<DecompilerTypeEnum> callback) {
        this.onSwitchEngine = callback;
    }

    /** 触发切换引擎(由外部菜单调用) */
    public void switchEngine(DecompilerTypeEnum engine) {
        if (onSwitchEngine != null) {
            onSwitchEngine.accept(engine);
        }
    }

    /** 存储工作区和节点引用(用于分屏时查找同 class) */
    public void setWorkspaceContext(com.bingbaihanji.fxdecomplie.model.Workspace workspace,
                                    com.bingbaihanji.fxdecomplie.model.FileTreeNode node) {
        getProperties().put("workspace", workspace);
        getProperties().put("fileTreeNode", node);
    }

    /** 触发分屏(由 CodeViewPanel 勾选框或右键菜单触发) */
    void requestSplit() {
        if (onSplitRequested != null) {
            onSplitRequested.accept(this);
        }
    }

    /** 更新固定状态的标题文本 */
    public void updatePinnedDisplay(boolean pinned) {
        String title = pinned ? "📌 " + displayTitle : displayTitle;
        titleLabel.setText(title);
        setGraphic(null);
        setGraphic(titleLabel);
    }

    /** @return Java 源码编辑器 */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /** @return 代码视图面板 */
    public CodeViewPanel getCodeViewPanel() {
        return codeViewPanel;
    }

    /** @return 打开的文件元数据 */
    public OpenFile getOpenFile() {
        return openFile;
    }

    /** @return 反编译源码是否已可作为真实源码导出/传递 */
    public boolean isSourceReady() {
        return sourceReady;
    }

    /** 标记当前源码仍在后台反编译中 */
    public void setSourceReady(boolean sourceReady) {
        this.sourceReady = sourceReady;
    }

    /** 原地更新为真实反编译源码,保留当前代码标签和底部视图容器 */
    public void updateDecompiledContent(OpenFile updatedOpenFile, CodeMetadata updatedMetadata,
                                        Consumer<CodeMetadata.Reference> updatedOnNavigate) {
        this.openFile = Objects.requireNonNull(updatedOpenFile, "updatedOpenFile");
        this.displayTitle = displayTitleFor(updatedOpenFile);
        this.metadata = updatedMetadata != null ? updatedMetadata : new CodeMetadata(java.util.Map.of());
        this.onNavigate = updatedOnNavigate;
        String source = updatedOpenFile.sourceCode();
        SourceContentPanel srcPanel = new SourceContentPanel(source, theme,
                fontFamily, defaultFontSize, wrapText, lineNumbersEnabled, this.metadata, this.onNavigate);
        codeViewPanel.replaceSourcePanel(source, srcPanel);
        codeArea = srcPanel.getCodeArea();
        sourceReady = true;

        // 反编译失败/超时时自动切换到字节码视图,避免用户面对纯错误文本
        if (DecompilerRunner.isFailureOutput(source)) {
            codeViewPanel.getDeck().setSelected(CodeContentDeck.TAB_BYTECODE);
        }

        boolean pinned = Boolean.TRUE.equals(getProperties().get("pinned"));
        titleLabel.setText(pinned ? "📌 " + displayTitle : displayTitle);
        setGraphic(null);
        setGraphic(titleLabel);
    }

    /** 更新源码模型和可见 Code 面板,用于重命名等不需要重新反编译的场景 */
    public void updateSourceCode(String newClassName, String newSource) {
        String className = newClassName == null || newClassName.isBlank()
                ? openFile.className() : newClassName;
        this.openFile = new OpenFile(className, openFile.fullPath(),
                newSource == null ? "" : newSource, openFile.engine());
        this.displayTitle = displayTitleFor(openFile);
        this.metadata = openFile.sourceCode().length() <= METADATA_SOURCE_THRESHOLD
                ? OutlineParser.extractMetadata(openFile.sourceCode(), classBytes)
                : new CodeMetadata(java.util.Map.of());
        SourceContentPanel srcPanel = new SourceContentPanel(openFile.sourceCode(), theme,
                fontFamily, defaultFontSize, wrapText, lineNumbersEnabled, this.metadata, this.onNavigate);
        codeViewPanel.replaceSourcePanel(openFile.sourceCode(), srcPanel);
        codeArea = srcPanel.getCodeArea();
        sourceReady = true;
        boolean pinned = Boolean.TRUE.equals(getProperties().get("pinned"));
        titleLabel.setText(pinned ? "📌 " + displayTitle : displayTitle);
        setGraphic(null);
        setGraphic(titleLabel);
    }

    /**
     * 只更新当前编辑器可见源码,不改变 OpenFile 中保存的原始反编译源码
     * 用于显示用户注释等装饰内容,导出仍可基于原始源码重新计算
     */
    public void updateVisibleSource(String displaySource) {
        codeViewPanel.refreshDisplayedSource(displaySource == null ? "" : displaySource);
        codeArea = codeViewPanel.getSourceCodeArea();
    }

    /** @return 标签页显示标题 */
    public String getDisplayTitle() {
        return titleLabel.getText();
    }

    /** 更新固定标签视觉前缀 */
    public void setPinnedIndicator(boolean pinned) {
        titleLabel.setText(pinned ? "● " + displayTitle : displayTitle);
    }

    /** @return 类文件字节码副本 */
    public byte[] getClassBytes() {
        return classBytes == null ? null : classBytes.clone();
    }

    /** @return 代码导航元数据 */
    public CodeMetadata getMetadata() {
        return metadata;
    }

    /** 放大字号 */
    public void zoomIn() {
        codeViewPanel.zoomIn();
    }

    /** 缩小字号 */
    public void zoomOut() {
        codeViewPanel.zoomOut();
    }

    /** 重置字号为默认值 */
    public void resetZoom() {
        codeViewPanel.resetZoom();
    }

    /** 跳转到指定行(Ctrl+G) */
    public void goToLine() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        javafx.stage.Window owner = codeArea.getScene() != null ? codeArea.getScene().getWindow() : null;
        dialog.initOwner(owner);
        dialog.setTitle(I18nUtil.getString("editor.gotoLine.title"));
        dialog.setHeaderText(null);
        int totalLines = codeViewPanel.getLineCount();
        dialog.setContentText(I18nUtil.getString("editor.gotoLine.prompt", totalLines));
        DialogHelper.applyNativeStyle(dialog);

        dialog.showAndWait().ifPresent(input -> {
            try {
                int line = Integer.parseInt(input.trim());
                if (line < 1 || line > totalLines) {
                    throw new NumberFormatException();
                }
                TextPos pos = TextPos.ofLeading(line - 1, 0);
                codeArea.select(pos, pos);
                codeArea.requestFocus();
            } catch (NumberFormatException e) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.WARNING);
                alert.setTitle(I18nUtil.getString("editor.gotoLine.title"));
                alert.setHeaderText(null);
                alert.setContentText(I18nUtil.getString("editor.gotoLine.invalid"));
                alert.initOwner(owner);
                DialogHelper.applyNativeStyle(alert);
                alert.showAndWait();
            }
        });
    }

    /** 滚动编辑器到指定行号并选中该行 */
    public void revealLine(int lineNumber) {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        int totalLines = codeViewPanel.getLineCount();
        int line = Math.clamp(lineNumber, 1, totalLines);
        TextPos pos = TextPos.ofLeading(line - 1, 0);
        codeArea.select(pos, pos);
        codeArea.requestFocus();
    }

    /** 设置行号开关 */
    public void setLineNumbersEnabled(boolean enabled) {
        codeViewPanel.setLineNumbersEnabled(enabled);
    }

    /** 应用字体设置到所有已有面板(Code/Smali/Bytecode/Simple) */
    public void applyFontSettings(int fontSize, String fontFamily) {
        codeViewPanel.applyFontSettings(fontSize, fontFamily);
    }

    /** 重新应用编辑器语法高亮主题到源码面板和 HEX 面板 */
    public void reapplyTheme(VsCodeThemeLoader.ThemeData newTheme) {
        this.theme = newTheme;
        com.bingbaihanji.fxdecomplie.ui.code.SourceContentPanel srcPanel =
                codeViewPanel != null && codeViewPanel.getDeck() != null
                        ? codeViewPanel.getDeck().getSourcePanel()
                        : null;
        if (srcPanel != null) {
            srcPanel.reapplyTheme(newTheme);
        }
        // 同步主题到 CodeContentDeck,以便 HEX 面板配色跟随
        if (codeViewPanel != null && codeViewPanel.getDeck() != null) {
            codeViewPanel.getDeck().setTheme(newTheme);
        }
    }
}
