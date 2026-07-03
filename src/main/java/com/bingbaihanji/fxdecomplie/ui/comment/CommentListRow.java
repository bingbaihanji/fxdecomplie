package com.bingbaihanji.fxdecomplie.ui.comment;

/**
 * 注释列表表格行数据，将 {@link com.bingbaihanji.fxdecomplie.model.CommentData} 转为扁平展示模型。
 *
 * @param line      行号
 * @param member    成员签名（类级注释显示 "—"）
 * @param summary   注释摘要（前 40 字符）
 * @param time      时间和作者（"2026-07-03 by author" 格式）
 * @param commentData 原始注释数据（用于编辑/删除操作）
 * @author bingbaihanji
 * @date 2026-07-03
 */
public record CommentListRow(
        int line,
        String member,
        String summary,
        String time,
        com.bingbaihanji.fxdecomplie.model.CommentData commentData
) {
    /**
     * 从 CommentData 创建表格行。
     *
     * @param data 原始注释数据
     * @return 表格行展示对象
     */
    public static CommentListRow from(com.bingbaihanji.fxdecomplie.model.CommentData data) {
        String member = data.memberSignature() != null && !data.memberSignature().isBlank()
                ? data.memberSignature() : "—"; // em dash
        String text = data.text() != null ? data.text() : "";
        String summary = text.length() > 40 ? text.substring(0, 40) + "…" : text;
        String time = data.time() != null && !data.time().isBlank()
                ? data.time().substring(0, Math.min(10, data.time().length())) + " by " + data.author()
                : data.author();
        return new CommentListRow(data.line(), member, summary, time, data);
    }
}
