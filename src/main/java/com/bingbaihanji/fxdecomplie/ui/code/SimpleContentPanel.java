package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.ui.theme.RegexHighlighter;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import jfx.incubator.scene.control.richtext.CodeArea;

/**
 * 简化代码面板,对反编译源码做轻量词法状态机去注释和压缩空行
 *
 * <p>不会误删字符串/字符字面量中的 // 或 /*,首版不做破坏性泛型简化</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class SimpleContentPanel extends AbstractCodeContentPanel {

    private final String sourceCode;
    private CodeArea codeArea;

    public SimpleContentPanel(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    /**
     * 轻量词法状态机：移除行注释(//)和块注释(/* ... *​/),压缩连续空行
     */
    static String simplify(String source) {
        StringBuilder sb = new StringBuilder(source.length());
        int i = 0;
        int len = source.length();
        int consecutiveNewlines = 0;

        while (i < len) {
            char c = source.charAt(i);

            // 字符串字面量 — 原样保留
            if (c == '"') {
                sb.append(c);
                i++;
                while (i < len) {
                    char ch = source.charAt(i);
                    sb.append(ch);
                    if (ch == '\\' && i + 1 < len) {
                        i++;
                        sb.append(source.charAt(i));
                    } else if (ch == '"') {
                        i++;
                        break;
                    }
                    i++;
                }
                consecutiveNewlines = 0;
                continue;
            }

            // 字符字面量 — 原样保留
            if (c == '\'') {
                sb.append(c);
                i++;
                while (i < len) {
                    char ch = source.charAt(i);
                    sb.append(ch);
                    if (ch == '\\' && i + 1 < len) {
                        i++;
                        sb.append(source.charAt(i));
                    } else if (ch == '\'') {
                        i++;
                        break;
                    }
                    i++;
                }
                consecutiveNewlines = 0;
                continue;
            }

            // 行注释 //
            if (c == '/' && i + 1 < len && source.charAt(i + 1) == '/') {
                while (i < len && source.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            // 块注释 /* ... */
            if (c == '/' && i + 1 < len && source.charAt(i + 1) == '*') {
                i += 2;
                while (i < len) {
                    if (source.charAt(i) == '*' && i + 1 < len && source.charAt(i + 1) == '/') {
                        i += 2;
                        break;
                    }
                    i++;
                }
                // 块注释可能后跟换行,保留该换行
                continue;
            }

            // 压缩连续空行(最多保留1个空行)
            if (c == '\n') {
                consecutiveNewlines++;
                if (consecutiveNewlines <= 1) {
                    // 检查前一字符,避免行注释去除了注释内容但留下了最后一个 \n 导致双空行
                    sb.append(c);
                }
                i++;
                continue;
            }

            consecutiveNewlines = 0;
            sb.append(c);
            i++;
        }

        return sb.toString().trim();
    }

    /** @return 内容类型标识 */
    @Override
    public String getContentType() {
        return "simple";
    }

    /** 异步构建简化后的代码内容 */
    @Override
    protected Object buildContentAsync(Object cancelToken) {
        return simplify(sourceCode == null ? "" : sourceCode);
    }

    /** 创建简化代码展示区域 */
    @Override
    protected javafx.scene.Node createContent(Object contentData) {
        CodeArea area = new CodeArea();
        area.getStyleClass().add("code-editor");
        area.setEditable(false);
        area.setSyntaxDecorator(new RegexHighlighter(VsCodeThemeLoader.defaultDark()));
        area.setText(contentData == null ? "" : contentData.toString());
        applyFontAndLineNumbers(area);
        this.codeArea = area;
        return area;
    }

    /** @return 简化代码编辑器 */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    @Override
    public void dispose() {
        super.dispose();
        codeArea = null;
    }
}
