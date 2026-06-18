package com.bingbihanji.fxdecomplie.ui.code;

import com.bingbihanji.fxdecomplie.model.CodeMetadata;
import com.bingbihanji.fxdecomplie.model.OpenFile;
import com.bingbihanji.fxdecomplie.ui.theme.RegexHighlighter;
import com.bingbihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbihanji.fxdecomplie.utils.CodeLinkHandler;
import com.bingbihanji.fxdecomplie.utils.I18nUtil;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.text.Font;
import jfx.incubator.scene.control.richtext.CodeArea;

import java.util.function.Consumer;

/**
 * 单个代码标签页。包含三个子标签：Java源码、字节码、类信息。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class CodeEditorTab extends Tab {

    /** Fira Code 字体资源路径 (回退 Light) */
    private static final String FIRA_CODE_LIGHT = "/ttf/FiraCode-Light.ttf";

    /** Java 源码编辑器 */
    private final CodeArea codeArea;
    /** 字节码文本视图 */
    private final CodeArea bytecodeArea;
    /** 打开的文件 */
    private final OpenFile openFile;
    /** 默认字号 */
    private final int defaultFontSize;
    /** Ctrl+Click 导航元数据 */
    private final CodeMetadata metadata;
    /** Ctrl+Click 导航回调（null 时禁用） */
    private final Consumer<CodeMetadata.Reference> onNavigate;
    /** 编辑器内搜索栏 */
    private EditorSearchBar editorSearchBar;

    /** 简化构造器（使用默认主题和字体配置） */
    public CodeEditorTab(OpenFile openFile) {
        this(openFile, VsCodeThemeLoader.defaultDark(), "Consolas", 14, true, true, null, null, null);
    }

    /**
     * 完整构造器，按顺序装配三大区域。
     *
     * @param openFile            打开的文件元数据
     * @param theme               编辑器语法高亮主题
     * @param fontFamily          字体族名称
     * @param fontSize            默认字号
     * @param wrapText            是否自动换行
     * @param lineNumbersEnabled  是否显示行号
     * @param classBytes  类文件原始字节码（用于字节码视图和类信息视图，可为 null）
     */
    public CodeEditorTab(OpenFile openFile, VsCodeThemeLoader.ThemeData theme, String fontFamily,
                         int fontSize, boolean wrapText, boolean lineNumbersEnabled, byte[] classBytes,
                         CodeMetadata metadata, Consumer<CodeMetadata.Reference> onNavigate) {
        this.openFile = openFile;
        this.defaultFontSize = fontSize;
        this.metadata = metadata != null ? metadata : new CodeMetadata(java.util.Map.of());
        this.onNavigate = onNavigate;

        // 1. 创建 Java 源码编辑区
        codeArea = buildSourceArea(theme, fontFamily, fontSize, wrapText, lineNumbersEnabled);
        Tab sourceTab = new Tab(I18nUtil.getString("tab.source"), codeArea);
        sourceTab.setClosable(false);

        // 2. 创建字节码汇编视图
        bytecodeArea = BytecodeViewTab.createView(classBytes);
        Tab bytecodeTab = new Tab(I18nUtil.getString("tab.bytecode"), bytecodeArea);
        bytecodeTab.setClosable(false);

        // 3. 创建类元信息视图
        javafx.scene.layout.VBox infoView = ClassInfoView.createView(classBytes);
        Tab infoTab = new Tab(I18nUtil.getString("tab.classinfo"), infoView);
        infoTab.setClosable(false);

        // 4. 将三个子标签页组装到 TabPane
        TabPane subTabPane = buildSubTabPane(sourceTab, bytecodeTab, infoTab);

        // 5. 创建搜索栏（默认隐藏，Ctrl+F 唤醒）
        editorSearchBar = new EditorSearchBar(codeArea);
        editorSearchBar.setVisible(false);
        editorSearchBar.setManaged(false);

        // 6. 将搜索栏和子标签页包装到 VBox 中
        javafx.scene.layout.VBox wrapper = new javafx.scene.layout.VBox(editorSearchBar, subTabPane);
        javafx.scene.layout.VBox.setVgrow(subTabPane, javafx.scene.layout.Priority.ALWAYS);
        setText(openFile.getClassName() + ".java");
        setContent(wrapper);
    }

    /** 将源码/字节码/类信息组装为不可关闭的子标签页面板 */
    private static TabPane buildSubTabPane(Tab sourceTab, Tab bytecodeTab, Tab infoTab) {
        TabPane pane = new TabPane();
        pane.getStyleClass().add("code-sub-tab-pane");
        pane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pane.getTabs().addAll(sourceTab, bytecodeTab, infoTab);
        pane.getSelectionModel().select(sourceTab);
        return pane;
    }

    /** 构建源码编辑区 CodeArea，配置样式、字体、高亮器 */
    private CodeArea buildSourceArea(VsCodeThemeLoader.ThemeData theme, String fontFamily,
                                     int fontSize, boolean wrapText, boolean lineNumbersEnabled) {
        CodeArea area = new CodeArea();
        area.getStyleClass().add("code-editor");
        area.setStyle("""
                -fx-font-feature-settings: "calt" 1, "liga" 1;
                """);
        area.setEditable(false);
        area.setWrapText(wrapText);
        LineNumberGutter.setEnabled(area, lineNumbersEnabled);
        area.setFont(loadCodeFont(fontFamily, fontSize));
        area.setText(openFile.getSourceCode());
        area.setSyntaxDecorator(new RegexHighlighter(theme));

        // Keyboard shortcuts: Ctrl+F for search, Ctrl+G for goto line
        area.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.F) {
                if (editorSearchBar != null) {
                    editorSearchBar.show();
                }
                e.consume();
            }
        });

        if (onNavigate != null) {
            CodeLinkHandler.install(area, this.metadata, onNavigate);
        }

        return area;
    }

    /** @return Java 源码编辑器 */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /** @return 打开的文件元数据 */
    public OpenFile getOpenFile() {
        return openFile;
    }

    /** 放大字号 */
    public void zoomIn() {
        setFontSize(codeArea.getFont().getSize() + 1);
    }

    /** 缩小字号 */
    public void zoomOut() {
        setFontSize(Math.max(8, codeArea.getFont().getSize() - 1));
    }

    /** 重置字号为默认值 */
    public void resetZoom() {
        setFontSize(defaultFontSize);
    }

    /** 设置行号开关 */
    public void setLineNumbersEnabled(boolean enabled) {
        LineNumberGutter.setEnabled(codeArea, enabled);
    }

    /** 更新 CodeArea 和字节码视图的字号 */
    private void setFontSize(double size) {
        Font current = codeArea.getFont();
        codeArea.setFont(Font.font(current.getFamily(), size));
        bytecodeArea.setFont(Font.font(bytecodeArea.getFont().getFamily(), size));
    }

    /** 加载代码字体：优先资源内嵌 Fira Code → 配置字体 → 系统 Consolas */
    private Font loadCodeFont(String configuredFamily, int fontSize) {
        Font resourceFont = loadResourceFont(fontSize);
        if (resourceFont != null) return resourceFont;
        if (configuredFamily != null && !configuredFamily.isBlank()) {
            return Font.font(configuredFamily, fontSize);
        }
        return Font.font("Consolas", fontSize);
    }

    /** 从 classpath 加载 Fira Code 字体（优先 Regular 字重，回退 Light） */
    private Font loadResourceFont(int fontSize) {
        try {
            java.net.URL fontUrl = CodeEditorTab.class.getResource(FIRA_CODE_LIGHT);

            if (fontUrl == null) return null;
            return Font.loadFont(fontUrl.toExternalForm(), fontSize);
        } catch (Exception ignored) {
            return null;
        }
    }
}
