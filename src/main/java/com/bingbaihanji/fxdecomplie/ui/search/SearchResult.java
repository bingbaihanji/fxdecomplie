package com.bingbaihanji.fxdecomplie.ui.search;

/**
 * 单条搜索结果记录。
 *
 * @param fullPath   所属类的完整路径（如 "com/example/Main.class"）
 * @param matchLine  匹配行内容
 * @param lineNumber 行号（1-based）
 * @param matchType  匹配类型
 * @author bingbaihanji
 * @date 2026-06-17
 */
public record SearchResult(
        String fullPath,
        String matchLine,
        int lineNumber,
        MatchType matchType
) {
    /** 匹配类型 */
    public enum MatchType {
        /** 类名匹配 */ CLASS_NAME,
        /** 方法名匹配 */ METHOD_NAME,
        /** 字段名匹配 */ FIELD_NAME,
        /** 代码文本匹配 */ CODE_TEXT,
        /** 资源文件匹配 */ RESOURCE_TEXT,
        /** 注释匹配 */ COMMENT_TEXT,
        /** 字节码文本匹配 */ BYTECODE_TEXT
    }
}
