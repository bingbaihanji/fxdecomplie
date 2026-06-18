package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.ui.search.SearchProvider;
import com.bingbihanji.fxdecomplie.ui.search.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 全文搜索服务。管理多个 SearchProvider 并聚合结果。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class SearchService {

    /** Registered search strategy providers, invoked in insertion order */
    private final List<SearchProvider> providers = new ArrayList<>();

    public void addProvider(SearchProvider provider) {
        providers.add(provider);
    }

    public void clearProviders() {
        providers.clear();
    }

    /**
     * Run all registered providers and merge results, sorted by match type priority then line number.
     *
     * @param query       search string
     * @param sourceCache map of class paths to decompiled source code
     * @return merged and sorted results, capped at 500
     */
    public List<SearchResult> searchAll(String query, Map<String, String> sourceCache) {
        if (query == null || query.isBlank()) return List.of();
        List<SearchResult> all = new ArrayList<>();
        for (SearchProvider provider : providers) {
            List<SearchResult> results = provider.search(query, sourceCache);
            all.addAll(results);
        }
        all.sort((a, b) -> {
            int typeCmp = Integer.compare(a.matchType().ordinal(), b.matchType().ordinal());
            if (typeCmp != 0) return typeCmp;
            return Integer.compare(a.lineNumber(), b.lineNumber());
        });
        return all.size() > 500 ? all.subList(0, 500) : all;
    }
}
