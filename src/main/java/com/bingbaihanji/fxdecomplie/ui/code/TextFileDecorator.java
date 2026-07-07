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

    /** 私有构造函数,单例模式 */
    private TextFileDecorator() {
    }

    /** @return 获取单例 */
    public static TextFileDecorator instance() {
        return INSTANCE;
    }

    /** 为指定段落创建应用默认浅色样式的富文本段落 */
    @Override
    public RichParagraph createRichParagraph(CodeTextModel model, int paragraphIndex) {
        String text = model.getPlainText(paragraphIndex);
        if (text == null || text.isEmpty()) {
            return RichParagraph.builder().build();
        }
        return RichParagraph.builder().addSegment(text, DEFAULT_STYLE).build();
    }

    /**
     * 处理文本变更事件（文本文件无需增量更新,空实现）
     *
     * @param model        代码文本模型
     * @param start        变更起始位置
     * @param end          变更结束位置
     * @param linesRemoved 移除的行数
     * @param linesAdded   新增的行数
     * @param charIndex    变更字符索引
     */
    @Override
    public void handleChange(CodeTextModel model, TextPos start, TextPos end,
                             int linesRemoved, int linesAdded, int charIndex) {
    }
}
