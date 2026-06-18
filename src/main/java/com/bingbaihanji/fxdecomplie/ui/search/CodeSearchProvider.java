package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 跨所有已反编译源码全文搜索（逐行匹配）。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class CodeSearchProvider implements SearchProvider {

    /** Maximum number of code text results to return */
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

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache,
                                     SearchOptions options) {
        if (SearchOptions.DEFAULT.equals(options)) {
            return search(query, sourceCache);
        }
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        for (var entry : sourceCache.entrySet()) {
            String[] lines = entry.getValue().split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lineMatches(lines[i], query, options)) {
                    results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                            SearchResult.MatchType.CODE_TEXT));
                }
                if (results.size() >= MAX_RESULTS) break;
            }
        }
        return results;
    }
}
