package com.bingbaihanji.fxdecomplie.model;

/**
 * 搜索选项：控制正则、大小写、全词匹配。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public record SearchOptions(
        boolean regex,
        boolean caseSensitive,
        boolean wholeWord
) {
    public static final SearchOptions DEFAULT = new SearchOptions(false, false, false);
}
