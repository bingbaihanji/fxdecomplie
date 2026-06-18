package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 搜索策略接口。每种实现对应一种搜索维度。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface SearchProvider {
    List<SearchResult> search(String query, Map<String, String> sourceCache);

    /** 带搜索选项的搜索（默认忽略选项，向后兼容） */
    default List<SearchResult> search(String query, Map<String, String> sourceCache,
                                      SearchOptions options) {
        return search(query, sourceCache);
    }

    /**
     * 检查某一行是否匹配查询，考虑所有搜索选项（正则、大小写、全词匹配）。
     * 供子类在逐行遍历源码时调用。
     */
    default boolean lineMatches(String line, String query, SearchOptions options) {
        if (line == null || query == null) return false;

        if (options.regex()) {
            try {
                int flags = options.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
                String pattern = options.wholeWord() ? "\\b" + query + "\\b" : query;
                return Pattern.compile(pattern, flags).matcher(line).find();
            } catch (PatternSyntaxException e) {
                return false;
            }
        }

        String content = options.caseSensitive() ? line : line.toLowerCase();
        String q = options.caseSensitive() ? query : query.toLowerCase();

        if (options.wholeWord()) {
            String pattern = "(?<!\\w)" + Pattern.quote(q) + "(?!\\w)";
            return Pattern.compile(pattern).matcher(content).find();
        }

        return content.contains(q);
    }
}
