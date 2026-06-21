package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import com.bingbaihanji.fxdecomplie.model.OpenFile;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 单个代码标签页，内部委托给 CodeViewPanel 管理底部标签切换和 Split View
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class CodeEditorTab extends Tab {

    /** Java 源码编辑器（兼容引用） */
    private CodeArea codeArea;
    /** 代码视图面板 */
    private final CodeViewPanel codeViewPanel;
    /** 打开的文件 */
    private OpenFile openFile;
    /** 标签页显示标题 */
    private final String displayTitle;
    /** 可拖拽的标签标题节点 */
    private final Label titleLabel;
    /** 默认字号 */
    private final int defaultFontSize;
    private final VsCodeThemeLoader.ThemeData theme;
    private final String fontFamily;
    private final boolean wrapText;
    private final boolean lineNumbersEnabled;
    /** Ctrl+Click 导航元数据 */
    private CodeMetadata metadata;
    /** Ctrl+Click 导航回调(null 时禁用) */
    private Consumer<CodeMetadata.Reference> onNavigate;
    /** 类文件原始字节码 */
    private final byte[] classBytes;
    /** 编辑器内搜索栏 */
    private final EditorSearchBar editorSearchBar;
    /** 反编译源码是否已就绪 */
    private boolean sourceReady = true;

    /** 简化构造器(使用默认主题和字体配置) */
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
        this.displayTitle = openFile.className() + " [" + openFile.engine().name() + "]";
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

        CodeViewPanel viewPanel = new CodeViewPanel(source, this.classBytes, srcPanel);
        viewPanel.setDefaultFontSize(fontSize);
        viewPanel.bindSearchBar();
        this.codeViewPanel = viewPanel;

        this.codeArea = srcPanel.getCodeArea();
        this.editorSearchBar = viewPanel.getSearchBar();

        titleLabel = createTitleLabel(displayTitle);
        setText("");
        setGraphic(titleLabel);
        setContent(viewPanel);
    }

    private static Label createTitleLabel(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("code-tab-title");
        label.setMaxWidth(240);
        label.setMinWidth(0);
        label.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
        return label;
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

    /** 原地更新为真实反编译源码，保留当前代码标签和底部视图容器 */
    public void updateDecompiledContent(OpenFile updatedOpenFile, CodeMetadata updatedMetadata,
                                        Consumer<CodeMetadata.Reference> updatedOnNavigate) {
        this.openFile = Objects.requireNonNull(updatedOpenFile, "updatedOpenFile");
        this.metadata = updatedMetadata != null ? updatedMetadata : new CodeMetadata(java.util.Map.of());
        this.onNavigate = updatedOnNavigate;
        String source = updatedOpenFile.sourceCode();
        SourceContentPanel srcPanel = new SourceContentPanel(source, theme,
                fontFamily, defaultFontSize, wrapText, lineNumbersEnabled, this.metadata, this.onNavigate);
        codeViewPanel.replaceSourcePanel(source, srcPanel);
        codeArea = srcPanel.getCodeArea();
        sourceReady = true;
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
        dialog.setOnShown(e -> {
            var win = dialog.getDialogPane().getScene().getWindow();
            DefaultWindowTheme.applyWindowDarkMode(win);
            if (win instanceof javafx.stage.Stage s) setDialogIcon(s);
        });

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
                alert.showAndWait();
            }
        });
    }

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

    private static void setDialogIcon(javafx.stage.Stage stage) {
        try {
            var stream = CodeEditorTab.class.getResourceAsStream("/icon/logo.png");
            if (stream != null) {
                stage.getIcons().add(new javafx.scene.image.Image(stream));
            }
        } catch (Exception ignored) {
        }
    }
}
