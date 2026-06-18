package com.bingbihanji.fxdecomplie.ui.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 搜索反编译源码中的注释行（以 // , /* , * 开头的行）。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class CommentSearchProvider implements SearchProvider {

    private static final int MAX_RESULTS = 500;

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        String lowerQuery = query.toLowerCase();
        for (var entry : sourceCache.entrySet()) {
            String[] lines = entry.getValue().split("\n");
            for (int i = 0; i < lines.length; i++) {
                String trimmed = lines[i].trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("* ")
                        || trimmed.startsWith("*\t") || trimmed.equals("*") || trimmed.startsWith("*/")) {
                    if (trimmed.toLowerCase().contains(lowerQuery)) {
                        results.add(new SearchResult(entry.getKey(), trimmed, i + 1,
                                SearchResult.MatchType.COMMENT_TEXT));
                    }
                }
                if (results.size() >= MAX_RESULTS) break;
            }
        }
        return results;
    }
}
