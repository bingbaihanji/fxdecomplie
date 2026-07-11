package com.bingbaihanji.fxdecomplie.model;

/**
 * 注释数据记录
 *
 * @param className       类全限定路径(内部形式, 如 "com/example/Foo.class")
 * @param memberSignature 成员签名(如 "bar()V"),类级注释为空字符串
 * @param line            行号(1-based),作为降级定位
 * @param sourceHash      保存注释时源码文本 hash
 * @param optionsHash     反编译引擎和选项 hash
 * @param style           注释样式(LINE/BLOCK)
 * @param text            注释文本
 * @param author          作者
 * @param time            创建/更新时间(ISO-8601)
 * @author bingbaihanji
 * @date 2026-06-21
 */
public record CommentData(
        String className,
        String memberSignature,
        int line,
        String sourceHash,
        String optionsHash,
        CommentStyle style,
        String text,
        String author,
        String time
) {
    public CommentData {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className must not be blank");
        }
        if (text == null) {
            text = "";
        }
        if (memberSignature == null) {
            memberSignature = "";
        }
        if (style == null) {
            style = CommentStyle.LINE;
        }
        if (time == null) {
            time = "";
        }
        if (author == null) {
            author = "";
        }
        if (sourceHash == null) {
            sourceHash = "";
        }
        if (optionsHash == null) {
            optionsHash = "";
        }
    }

    /** 注释样式 */
    public enum CommentStyle {
        /** 行尾注释(// ...),位于代码行末尾 */
        LINE,
        /** 块注释(/* ... *​/),可跨多行 */
        BLOCK
    }
}
