package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import com.bingbaihanji.fxdecomplie.ui.CodeLinkHandler;
import com.bingbaihanji.fxdecomplie.ui.theme.RegexHighlighter;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import javafx.scene.Node;
import javafx.scene.text.Font;
import jfx.incubator.scene.control.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 源码内容面板,封装 CodeArea 的创建、样式、高亮和导航
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class SourceContentPanel extends AbstractCodeContentPanel {

    private static final Logger log = LoggerFactory.getLogger(SourceContentPanel.class);

    /** 超大源码禁用正则高亮和链接扫描 */
    private static final int LARGE_SOURCE_THRESHOLD = 500_000;
    /** Fira Code 字体资源路径 */
    private static final String FIRA_CODE_LIGHT = "/ttf/FiraCode-Light.ttf";
    private final VsCodeThemeLoader.ThemeData theme;
    private final String fontFamily;
    private final int fontSize;
    private final boolean wrapText;
    private final boolean lineNumbersEnabled;
    private final CodeMetadata metadata;
    private final Consumer<CodeMetadata.Reference> onNavigate;
    private CodeArea codeArea;
    private BracketHighlighter bracketHighlighter;
    private String sourceCode;
    private BiConsumer<Integer, String> onTokenNavigate;
    /** CodeArea 重建后的回调,用于父组件重新安装事件处理器 */
    private Runnable onCodeAreaRebuilt;
    private boolean linkNavigationEnabled;
    private int cachedLineCount = -1;

    /** 使用默认主题和字体创建源码面板 */
    public SourceContentPanel(String sourceCode) {
        this(sourceCode, VsCodeThemeLoader.defaultDark(), "Consolas", 14, true, true, null, null);
    }

    /**
     * 完整参数构造源码面板
     *
     * @param sourceCode         源码文本
     * @param theme              VS Code 主题数据
     * @param fontFamily         字体族名
     * @param fontSize           字号
     * @param wrapText           是否自动换行
     * @param lineNumbersEnabled 是否显示行号
     * @param metadata           代码元数据（用于 Ctrl+Click 导航）
     * @param onNavigate         导航回调
     */
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

    /**
     * 构建 CodeArea,配置字体、换行、行号、语法高亮和括号匹配
     * 超大源码（超过 500KB）禁用正则高亮和链接导航以提升性能
     */
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
        linkNavigationEnabled = !large;
        LineNumberGutter.setEnabled(codeArea, lineNumbersEnabled && !large);
        codeArea.setFont(loadFont());

        if (!large) {
            RegexHighlighter regexHighlighter = new RegexHighlighter(theme);
            codeArea.setSyntaxDecorator(regexHighlighter);
            codeArea.setHighlightCurrentParagraph(true);
            bracketHighlighter = new BracketHighlighter(codeArea, regexHighlighter);
            bracketHighlighter.install();
        }
        codeArea.setText(src == null ? "" : src);
        cachedLineCount = -1;
        installLinkNavigation();

        getChildren().setAll(codeArea);
    }

    /** 安装或卸载 Ctrl+Click 链接导航处理器 */
    private void installLinkNavigation() {
        if (codeArea == null) {
            return;
        }
        if (!linkNavigationEnabled || (onNavigate == null && onTokenNavigate == null)) {
            CodeLinkHandler.uninstall(codeArea);
            return;
        }
        CodeLinkHandler.install(codeArea, metadata, onTokenNavigate, onNavigate);
    }

    /** 加载字体,优先 Fira Code Light,失败则用系统字体,最后回退到 Consolas */
    private Font loadFont() {
        try {
            java.net.URL fontUrl = getClass().getResource(FIRA_CODE_LIGHT);
            if (fontUrl != null) {
                return Font.loadFont(fontUrl.toExternalForm(), fontSize);
            }
        } catch (Exception ignored) {
            log.debug("加载自定义字体失败,回退到系统字体", ignored);
        }
        if (fontFamily != null && !fontFamily.isBlank()) {
            return Font.font(fontFamily, fontSize);
        }
        return Font.font("Consolas", fontSize);
    }

    /** 更新源码内容并重建 CodeArea */
    public void setSourceCode(String newSource) {
        this.sourceCode = newSource;
        // 先释放旧的括号高亮器,避免动画定时器持有旧 CodeArea 引用
        if (bracketHighlighter != null) {
            bracketHighlighter.dispose();
            bracketHighlighter = null;
        }
        getChildren().clear();
        buildCodeArea();
        if (onCodeAreaRebuilt != null) {
            onCodeAreaRebuilt.run();
        }
    }

    /** 设置 CodeArea 重建后的回调 */
    public void setOnCodeAreaRebuilt(Runnable callback) {
        this.onCodeAreaRebuilt = callback;
    }

    /** 仅替换当前 CodeArea 文本,保留右键菜单、光标监听和搜索栏绑定 */
    public void setDisplayedSourceCode(String newSource) {
        this.sourceCode = newSource;
        cachedLineCount = -1;
        if (codeArea == null) {
            buildCodeArea();
            return;
        }
        codeArea.setText(newSource == null ? "" : newSource);
    }

    /** @return 源码编辑器 */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /**
     * 用新主题数据重建语法高亮器并应用到 CodeArea
     * 仅在 codeArea 已创建时生效
     */
    public void reapplyTheme(VsCodeThemeLoader.ThemeData newTheme) {
        if (codeArea == null) {
            return;
        }
        try {
            RegexHighlighter newHighlighter = new RegexHighlighter(newTheme);
            codeArea.setSyntaxDecorator(newHighlighter);
            // 重建括号高亮器,使其引用新的高亮器实例
            if (bracketHighlighter != null) {
                bracketHighlighter.dispose();
            }
            bracketHighlighter = new BracketHighlighter(codeArea, newHighlighter);
            bracketHighlighter.install();
        } catch (Exception e) {
            log.warn("重新应用编辑器主题失败", e);
        }
    }

    /** 安装基于点击处 token 的项目内跳转处理器 */
    public void setTokenNavigateHandler(BiConsumer<Integer, String> onTokenNavigate) {
        this.onTokenNavigate = onTokenNavigate;
        installLinkNavigation();
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
        if (codeArea != null) {
            codeArea.setFont(Font.font(codeArea.getFont().getFamily(), codeArea.getFont().getSize() + 1));
        }
    }

    /** 缩小字号 */
    public void zoomOut() {
        if (codeArea != null) {
            codeArea.setFont(Font.font(codeArea.getFont().getFamily(), Math.max(8, codeArea.getFont().getSize() - 1)));
        }
    }

    /** 重置字号 */
    public void resetZoom(int defaultFontSize) {
        if (codeArea != null) {
            codeArea.setFont(Font.font(codeArea.getFont().getFamily(), defaultFontSize));
        }
    }

    /** 动态开启/关闭行号显示 */
    public void setLineNumbersEnabled(boolean enabled) {
        if (codeArea != null) {
            LineNumberGutter.setEnabled(codeArea, enabled);
        }
    }

    /** @return 内容类型标识 */
    @Override
    public String getContentType() {
        return "source";
    }

    /** 源码面板在构造时即同步创建 CodeArea,异步构建直接返回已有的 codeArea */
    @Override
    protected Object buildContentAsync(Object cancelToken) {
        return codeArea;
    }

    /** 返回 CodeArea 作为展示节点 */
    @Override
    protected Node createContent(Object contentData) {
        return codeArea;
    }

    /** @return codeArea 是否已创建 */
    @Override
    public boolean isLoaded() {
        return codeArea != null;
    }

    /** 释放资源：清理括号高亮器并置空 codeArea */
    @Override
    public void dispose() {
        super.dispose();
        if (bracketHighlighter != null) {
            bracketHighlighter.dispose();
            bracketHighlighter = null;
        }
        codeArea = null;
    }
}
