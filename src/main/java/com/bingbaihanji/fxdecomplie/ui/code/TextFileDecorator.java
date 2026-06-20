package com.bingbaihanji.fxdecomplie.ui.code;

import javafx.scene.paint.Color;
import jfx.incubator.scene.control.richtext.SyntaxDecorator;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.RichParagraph;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;

/**
 * 文本文件语法装饰器将所有文本应用浅色前景色以适配暗色主题
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class TextFileDecorator implements SyntaxDecorator {

    /** 默认前景色 */
    private static final Color LIGHT_TEXT = Color.web("#9aa7b0");
    /** 浅色文本样式 */
    private static final StyleAttributeMap DEFAULT_STYLE = StyleAttributeMap.builder()
            .setTextColor(LIGHT_TEXT).build();

    private static final TextFileDecorator INSTANCE = new TextFileDecorator();

    private TextFileDecorator() {
    }

    /** 获取单例 */
    public static TextFileDecorator instance() {
        return INSTANCE;
    }

    @Override
    public RichParagraph createRichParagraph(CodeTextModel model, int paragraphIndex) {
        String text = model.getPlainText(paragraphIndex);
        if (text == null || text.isEmpty()) return RichParagraph.builder().build();
        return RichParagraph.builder().addSegment(text, DEFAULT_STYLE).build();
    }

    @Override
    public void handleChange(CodeTextModel model, TextPos start, TextPos end,
                             int linesRemoved, int linesAdded, int charIndex) {
    }
}
