package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import com.bingbaihanji.fxdecomplie.model.OpenFile;
import com.bingbaihanji.fxdecomplie.ui.theme.RegexHighlighter;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.fxdecomplie.utils.CodeLinkHandler;
import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.text.Font;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;

import java.util.function.Consumer;

/**
 * 单个代码标签页包含三个子标签：Java源码、字节码、类信息
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class CodeEditorTab extends Tab {

    /** Fira Code 字体资源路径 (回退 Light) */
    private static final String FIRA_CODE_LIGHT = "/ttf/FiraCode-Light.ttf";
    /** 超大源码禁用正则高亮和链接扫描,避免 JavaFX 线程长时间停顿 */
    private static final int LARGE_SOURCE_THRESHOLD = 500_000;

    /** Java 源码编辑器 */
    private final CodeArea codeArea;
    /** 打开的文件 */
    private final OpenFile openFile;
    /** 标签页显示标题 */
    private final String displayTitle;
    /** 可拖拽的标签标题节点 */
    private final Label titleLabel;
    /** 默认字号 */
    private final int defaultFontSize;
    /** Ctrl+Click 导航元数据 */
    private final CodeMetadata metadata;
    /** Ctrl+Click 导航回调(null 时禁用) */
    private final Consumer<CodeMetadata.Reference> onNavigate;
    /** 类文件原始字节码(用于复制标签到独立窗口) */
    private final byte[] classBytes;
    /** 字节码文本视图 */
    private CodeArea bytecodeArea;
    /** 类信息视图 */
    private javafx.scene.layout.VBox classInfoView;
    /** 缓存的总行数,避免 goToLine 时 O(n) 计数 */
    private volatile int cachedLineCount = -1;
    /** 编辑器内搜索栏 */
    private EditorSearchBar editorSearchBar;
    /** 延迟加载状态：避免打开源码标签时同步生成字节码/类信息视图 */
    private boolean bytecodeLoaded;
    private boolean classInfoLoaded;

    /** 简化构造器(使用默认主题和字体配置) */
    public CodeEditorTab(OpenFile openFile) {
        this(openFile, VsCodeThemeLoader.defaultDark(), "Consolas", 14, true, true, null, null, null);
    }

    /**
     * 完整构造器,按顺序装配三大区域
     *
     * @param openFile            打开的文件元数据
     * @param theme               编辑器语法高亮主题
     * @param fontFamily          字体族名称
     * @param fontSize            默认字号
     * @param wrapText            是否自动换行
     * @param lineNumbersEnabled  是否显示行号
     * @param classBytes  类文件原始字节码(用于字节码视图和类信息视图,可为 null)
     */
    public CodeEditorTab(OpenFile openFile, VsCodeThemeLoader.ThemeData theme, String fontFamily,
                         int fontSize, boolean wrapText, boolean lineNumbersEnabled, byte[] classBytes,
                         CodeMetadata metadata, Consumer<CodeMetadata.Reference> onNavigate) {
        this.openFile = openFile;
        this.displayTitle = openFile.className() + " [" + openFile.engine().name() + "]";
        this.defaultFontSize = fontSize;
        this.metadata = metadata != null ? metadata : new CodeMetadata(java.util.Map.of());
        this.onNavigate = onNavigate;
        this.classBytes = classBytes == null ? null : classBytes.clone();

        // 1. 创建 Java 源码编辑区
        codeArea = buildSourceArea(theme, fontFamily, fontSize, wrapText, lineNumbersEnabled);
        Tab sourceTab = new Tab(I18nUtil.getString("tab.source"), codeArea);
        sourceTab.setClosable(false);

        // 2. 字节码和类信息按需构建,避免快速切换 class 时阻塞 JavaFX 线程
        Tab bytecodeTab = new Tab(I18nUtil.getString("tab.bytecode"), lazyPlaceholder());
        bytecodeTab.setClosable(false);
        bytecodeTab.selectedProperty().addListener((obs, wasSelected, selected) -> {
            if (selected) {
                ensureBytecodeLoaded(bytecodeTab);
            }
        });

        // 3. 创建类元信息视图占位
        Tab infoTab = new Tab(I18nUtil.getString("tab.classinfo"), lazyPlaceholder());
        infoTab.setClosable(false);
        infoTab.selectedProperty().addListener((obs, wasSelected, selected) -> {
            if (selected) {
                ensureClassInfoLoaded(infoTab);
            }
        });

        // 4. 将三个子标签页组装到 TabPane
        TabPane subTabPane = buildSubTabPane(sourceTab, bytecodeTab, infoTab);

        // 5. 创建搜索栏(默认隐藏,Ctrl+F 唤醒)
        editorSearchBar = new EditorSearchBar(codeArea);
        editorSearchBar.setVisible(false);
        editorSearchBar.setManaged(false);

        // 6. 将搜索栏和子标签页包装到 VBox 中
        javafx.scene.layout.VBox wrapper = new javafx.scene.layout.VBox(editorSearchBar, subTabPane);
        javafx.scene.layout.VBox.setVgrow(subTabPane, javafx.scene.layout.Priority.ALWAYS);
        titleLabel = createTitleLabel(displayTitle);
        setText("");
        setGraphic(titleLabel);
        setContent(wrapper);
    }

    private static javafx.scene.Node lazyPlaceholder() {
        javafx.scene.layout.StackPane pane = new javafx.scene.layout.StackPane();
        pane.setMinHeight(120);
        pane.getStyleClass().add("code-editor");
        return pane;
    }

    private static Label createTitleLabel(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("code-tab-title");
        label.setMaxWidth(240);
        label.setMinWidth(0);
        label.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
        return label;
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

    /** 判断字符是否为括号类型 */
    private static boolean isBracket(char c) {
        return c == '(' || c == ')' || c == '{' || c == '}' || c == '[' || c == ']';
    }

    /** 在文本中查找与 pos 处括号配对的括号位置,找不到返回 -1 */
    private static int findMatchingBracket(String text, int pos) {
        if (pos < 0 || pos >= text.length()) return -1;
        char c = text.charAt(pos);
        if (!isBracket(c)) return -1;

        boolean forward = (c == '(' || c == '{' || c == '[');
        char open, close;
        switch (c) {
            case '(':
                open = '(';
                close = ')';
                break;
            case ')':
                open = '(';
                close = ')';
                break;
            case '{':
                open = '{';
                close = '}';
                break;
            case '}':
                open = '{';
                close = '}';
                break;
            case '[':
                open = '[';
                close = ']';
                break;
            case ']':
                open = '[';
                close = ']';
                break;
            default:
                return -1;
        }

        int depth = 0;
        if (forward) {
            for (int i = pos; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == open) depth++;
                else if (ch == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        } else {
            for (int i = pos; i >= 0; i--) {
                char ch = text.charAt(i);
                if (ch == close) depth++;
                else if (ch == open) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    /** 将 (行, 列) 坐标转换为文本中平坦字符索引 */
    private static int toFlatIndex(String text, int line, int col) {
        int idx = 0;
        for (int l = 0; l < line && idx < text.length(); idx++) {
            if (text.charAt(idx) == '\n') l++;
        }
        return idx + col;
    }

    /** 将平坦字符索引转换为 TextPos */
    private static TextPos toTextPos(String text, int idx) {
        if (idx <= 0) return TextPos.ZERO;
        int clamped = Math.min(idx, text.length());
        int line = 0;
        int lineStart = 0;
        for (int i = 0; i < clamped; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return TextPos.ofLeading(line, clamped - lineStart);
    }

    private static int lineStartOffset(String text, int lineNumber) {
        if (lineNumber <= 1) {
            return 0;
        }
        int currentLine = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                currentLine++;
                if (currentLine == lineNumber) {
                    return i + 1;
                }
            }
        }
        return text.length();
    }

    private static int lineEndOffset(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            if (text.charAt(i) == '\n' || text.charAt(i) == '\r') {
                return i;
            }
        }
        return text.length();
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

    private void ensureBytecodeLoaded(Tab bytecodeTab) {
        if (bytecodeLoaded) {
            return;
        }
        bytecodeLoaded = true;
        bytecodeArea = BytecodeViewTab.createView(this.classBytes);
        bytecodeArea.setFont(Font.font(bytecodeArea.getFont().getFamily(), codeArea.getFont().getSize()));
        bytecodeTab.setContent(bytecodeArea);
    }

    private void ensureClassInfoLoaded(Tab infoTab) {
        if (classInfoLoaded) {
            return;
        }
        classInfoLoaded = true;
        classInfoView = ClassInfoView.createView(this.classBytes);
        infoTab.setContent(classInfoView);
    }

    /** 构建源码编辑区 CodeArea,配置样式、字体、高亮器 */
    private CodeArea buildSourceArea(VsCodeThemeLoader.ThemeData theme, String fontFamily,
                                     int fontSize, boolean wrapText, boolean lineNumbersEnabled) {
        CodeArea area = new CodeArea();
        area.getStyleClass().add("code-editor");
        area.setStyle("""
                -fx-font-feature-settings: "calt" 1, "liga" 1;
                """);
        area.setEditable(false);
        area.setWrapText(wrapText);
        String source = openFile.sourceCode();
        boolean largeSource = source != null && source.length() > LARGE_SOURCE_THRESHOLD;
        LineNumberGutter.setEnabled(area, lineNumbersEnabled && !largeSource);
        area.setFont(loadCodeFont(fontFamily, fontSize));
        area.setText(source == null ? "" : source);
        cachedLineCount = source != null ? (int) source.chars().filter(ch -> ch == '\n').count() + 1 : 1;
        if (!largeSource) {
            area.setSyntaxDecorator(new RegexHighlighter(theme));
            area.setHighlightCurrentParagraph(true);
        } else {
            area.setHighlightCurrentParagraph(false);
        }

        // Keyboard shortcuts: Ctrl+F for search, Ctrl+G for goto line
        area.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.F) {
                if (editorSearchBar != null) {
                    editorSearchBar.show();
                }
                e.consume();
            } else if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.G) {
                CodeEditorTab.this.goToLine();
                e.consume();
            }
        });

        if (onNavigate != null && !largeSource) {
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

    /** 跳转到指定行(Ctrl+G) */
    public void goToLine() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        javafx.stage.Window owner = codeArea.getScene() != null ? codeArea.getScene().getWindow() : null;
        dialog.initOwner(owner);
        dialog.setTitle(I18nUtil.getString("editor.gotoLine.title"));
        dialog.setHeaderText(null);
        int totalLines = getLineCount();
        dialog.setContentText(I18nUtil.getString("editor.gotoLine.prompt", totalLines));
        dialog.setOnShown(e -> {
            var win = dialog.getDialogPane().getScene().getWindow();
            com.bingbaihanji.fxdecomplie.platform.FxTools.applyWindowDarkMode(win);
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

    /** 跳转并选中指定行 */
    private int getLineCount() {
        int c = cachedLineCount;
        if (c < 0) {
            String text = codeArea.getText();
            c = text != null ? (int) text.chars().filter(ch -> ch == '\n').count() + 1 : 1;
            cachedLineCount = c;
        }
        return c;
    }

    public void revealLine(int lineNumber) {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        int totalLines = getLineCount();
        int line = Math.clamp(lineNumber, 1, totalLines);
        int start = lineStartOffset(text, line);
        int end = lineEndOffset(text, start);
        TextPos startPos = toTextPos(text, start);
        TextPos endPos = toTextPos(text, end);
        codeArea.select(startPos, endPos);
        codeArea.requestFocus();
    }

    /** 设置行号开关 */
    public void setLineNumbersEnabled(boolean enabled) {
        LineNumberGutter.setEnabled(codeArea, enabled);
    }

    /** 更新 CodeArea 和字节码视图的字号 */
    private void setFontSize(double size) {
        Font current = codeArea.getFont();
        codeArea.setFont(Font.font(current.getFamily(), size));
        if (bytecodeArea != null) {
            bytecodeArea.setFont(Font.font(bytecodeArea.getFont().getFamily(), size));
        }
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

    /** 从 classpath 加载 Fira Code 字体(优先 Regular 字重,回退 Light) */
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
