package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import com.bingbaihanji.fxdecomplie.ui.theme.RegexHighlighter;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.fxdecomplie.utils.CodeLinkHandler;
import javafx.scene.Node;
import javafx.scene.text.Font;
import jfx.incubator.scene.control.richtext.CodeArea;

import java.util.function.Consumer;

/**
 * 源码内容面板，封装 CodeArea 的创建、样式、高亮和导航
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class SourceContentPanel extends AbstractCodeContentPanel {

    /** 超大源码禁用正则高亮和链接扫描 */
    private static final int LARGE_SOURCE_THRESHOLD = 500_000;
    /** Fira Code 字体资源路径 */
    private static final String FIRA_CODE_LIGHT = "/ttf/FiraCode-Light.ttf";

    private CodeArea codeArea;
    private String sourceCode;
    private final VsCodeThemeLoader.ThemeData theme;
    private final String fontFamily;
    private final int fontSize;
    private final boolean wrapText;
    private final boolean lineNumbersEnabled;
    private final CodeMetadata metadata;
    private final Consumer<CodeMetadata.Reference> onNavigate;
    private int cachedLineCount = -1;

    public SourceContentPanel(String sourceCode) {
        this(sourceCode, VsCodeThemeLoader.defaultDark(), "Consolas", 14, true, true, null, null);
    }

    public SourceContentPanel(String sourceCode, VsCodeThemeLoader.ThemeData theme,
                              String fontFamily, int fontSize, boolean wrapText,
                              boolean lineNumbersEnabled, CodeMetadata metadata,
                              Consumer<CodeMetadata.Reference> onNavigate) {
        this.sourceCode = sourceCode;
        this.theme = theme;
        this.fontFamily = fontFamily;
        this.fontSize = fontSize;
        this.wrapText = wrapText;
        this.lineNumbersEnabled = lineNumbersEnabled;
        this.metadata = metadata != null ? metadata : new CodeMetadata(java.util.Map.of());
        this.onNavigate = onNavigate;

        if (sourceCode != null) {
            buildCodeArea();
        }
    }

    private void buildCodeArea() {
        codeArea = new CodeArea();
        codeArea.getStyleClass().add("code-editor");
        codeArea.setStyle("""
                -fx-font-feature-settings: "calt" 1, "liga" 1;
                """);
        codeArea.setEditable(false);
        codeArea.setWrapText(wrapText);

        String src = sourceCode;
        boolean large = src != null && src.length() > LARGE_SOURCE_THRESHOLD;
        LineNumberGutter.setEnabled(codeArea, lineNumbersEnabled && !large);
        codeArea.setFont(loadFont());
        codeArea.setText(src == null ? "" : src);
        cachedLineCount = src != null ? (int) src.chars().filter(ch -> ch == '\n').count() + 1 : 1;

        if (!large) {
            codeArea.setSyntaxDecorator(new RegexHighlighter(theme));
            codeArea.setHighlightCurrentParagraph(true);
        }
        if (onNavigate != null && !large) {
            CodeLinkHandler.install(codeArea, metadata, onNavigate);
        }

        getChildren().setAll(codeArea);
    }

    private Font loadFont() {
        try {
            java.net.URL fontUrl = getClass().getResource(FIRA_CODE_LIGHT);
            if (fontUrl != null) return Font.loadFont(fontUrl.toExternalForm(), fontSize);
        } catch (Exception ignored) {
        }
        if (fontFamily != null && !fontFamily.isBlank()) {
            return Font.font(fontFamily, fontSize);
        }
        return Font.font("Consolas", fontSize);
    }

    /** 更新源码内容并重建 CodeArea */
    public void setSourceCode(String newSource) {
        this.sourceCode = newSource;
        getChildren().clear();
        buildCodeArea();
    }

    /** @return 源码编辑器 */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /** @return 总行数 */
    public int getLineCount() {
        if (cachedLineCount < 0 && codeArea != null) {
            String text = codeArea.getText();
            cachedLineCount = text != null ? (int) text.chars().filter(ch -> ch == '\n').count() + 1 : 1;
        }
        return cachedLineCount;
    }

    /** 放大字号 */
    public void zoomIn() {
        if (codeArea != null) codeArea.setFont(Font.font(codeArea.getFont().getFamily(), codeArea.getFont().getSize() + 1));
    }

    /** 缩小字号 */
    public void zoomOut() {
        if (codeArea != null) codeArea.setFont(Font.font(codeArea.getFont().getFamily(), Math.max(8, codeArea.getFont().getSize() - 1)));
    }

    /** 重置字号 */
    public void resetZoom(int defaultFontSize) {
        if (codeArea != null) codeArea.setFont(Font.font(codeArea.getFont().getFamily(), defaultFontSize));
    }

    public void setLineNumbersEnabled(boolean enabled) {
        if (codeArea != null) LineNumberGutter.setEnabled(codeArea, enabled);
    }

    @Override
    public String getContentType() {
        return "source";
    }

    @Override
    protected Node buildContentAsync(Object cancelToken) {
        return codeArea;
    }

    @Override
    public boolean isLoaded() {
        return codeArea != null;
    }

    @Override
    public void dispose() {
        super.dispose();
        codeArea = null;
    }
}
