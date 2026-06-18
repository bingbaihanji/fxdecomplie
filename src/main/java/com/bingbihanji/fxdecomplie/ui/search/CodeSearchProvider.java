package com.bingbihanji.fxdecomplie.ui.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 跨所有已反编译源码全文搜索（逐行匹配） */
public class CodeSearchProvider implements SearchProvider {

    private static final int MAX_RESULTS = 500;

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        String lowerQuery = query.toLowerCase();
        for (var entry : sourceCache.entrySet()) {
            String[] lines = entry.getValue().split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].toLowerCase().contains(lowerQuery)) {
                    results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                            SearchResult.MatchType.CODE_TEXT));
                }
                if (results.size() >= MAX_RESULTS) break;
            }
        }
        return results;
    }
}
