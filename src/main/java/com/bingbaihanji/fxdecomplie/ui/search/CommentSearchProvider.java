package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 搜索反编译源码中的注释行(以 // , /* , * 开头的行)
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class CommentSearchProvider implements SearchProvider {

    private static final int MAX_RESULTS = 500;

    private static boolean isCommentLine(String trimmed) {
        return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("* ")
                || trimmed.startsWith("*\t") || trimmed.equals("*") || trimmed.startsWith("*/");
    }

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        String lowerQuery = query.toLowerCase();
        for (var entry : sourceCache.entrySet()) {
            if (results.size() >= MAX_RESULTS) break;
            String[] lines = entry.getValue().replace("\r\n", "\n").replace("\r", "\n").split("\n");
            for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                String trimmed = lines[i].trim();
                if (isCommentLine(trimmed) && trimmed.toLowerCase().contains(lowerQuery)) {
                    results.add(new SearchResult(entry.getKey(), trimmed, i + 1,
                            SearchResult.MatchType.COMMENT_TEXT));
                }
            }
        }
        return results;
    }

    @Override
    public boolean supports(SearchScope scope) {
        return scope == SearchScope.ALL || scope == SearchScope.COMMENT;
    }

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache,
                                     SearchOptions options) {
        if (SearchOptions.DEFAULT.equals(options)) {
            return search(query, sourceCache);
        }
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        for (var entry : sourceCache.entrySet()) {
            if (results.size() >= MAX_RESULTS) break;
            String[] lines = entry.getValue().replace("\r\n", "\n").replace("\r", "\n").split("\n");
            for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                String trimmed = lines[i].trim();
                if (isCommentLine(trimmed) && lineMatches(trimmed, query, options)) {
                    results.add(new SearchResult(entry.getKey(), trimmed, i + 1,
                            SearchResult.MatchType.COMMENT_TEXT));
                }
            }
        }
        return results;
    }
}
