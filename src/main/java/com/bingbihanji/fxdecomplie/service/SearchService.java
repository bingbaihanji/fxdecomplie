package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.model.SearchOptions;
import com.bingbihanji.fxdecomplie.ui.search.SearchProvider;
import com.bingbihanji.fxdecomplie.ui.search.SearchResult;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final List<SearchProvider> providers = new CopyOnWriteArrayList<>();

    /** Patterns to exclude from search results (simple wildcard matching) */
    private volatile List<String> excludePatterns = List.of();

    public void addProvider(SearchProvider provider) {
        providers.add(provider);
    }

    public void clearProviders() {
        providers.clear();
    }

    public void setExcludePatterns(List<String> patterns) {
        this.excludePatterns = patterns != null ? List.copyOf(patterns) : List.of();
    }

    /**
     * Run all registered providers and merge results, sorted by match type priority then line number.
     *
     * @param query       search string
     * @param sourceCache map of class paths to decompiled source code
     * @return merged and sorted results, capped at the given limit
     */
    public List<SearchResult> searchAll(String query, Map<String, String> sourceCache) {
        return searchAll(query, sourceCache, 500);
    }

    public List<SearchResult> searchAll(String query, Map<String, String> sourceCache, int limit) {
        if (query == null || query.isBlank()) return List.of();
        int resultLimit = Math.max(1, limit);
        List<SearchResult> all = new ArrayList<>();
        for (SearchProvider provider : providers) {
            if (Thread.currentThread().isInterrupted()) {
                return List.of();
            }
            List<SearchResult> results = provider.search(query, sourceCache);
            all.addAll(results);
        }
        // Filter by exclude patterns if any
        if (!excludePatterns.isEmpty()) {
            all.removeIf(result -> matchesExcludePattern(result.fullPath(), excludePatterns));
        }
        all.sort((a, b) -> {
            int typeCmp = Integer.compare(a.matchType().ordinal(), b.matchType().ordinal());
            if (typeCmp != 0) return typeCmp;
            return Integer.compare(a.lineNumber(), b.lineNumber());
        });
        return all.size() > resultLimit ? all.subList(0, resultLimit) : all;
    }

    public List<SearchResult> searchAll(String query, Map<String, String> sourceCache,
                                         SearchOptions options, int limit) {
        if (query == null || query.isBlank()) return List.of();
        int resultLimit = Math.max(1, limit);
        List<SearchResult> all = new ArrayList<>();
        for (SearchProvider provider : providers) {
            if (Thread.currentThread().isInterrupted()) return List.of();
            List<SearchResult> results = provider.search(query, sourceCache, options);
            all.addAll(results);
        }
        // Filter by exclude patterns if any
        if (!excludePatterns.isEmpty()) {
            all.removeIf(result -> matchesExcludePattern(result.fullPath(), excludePatterns));
        }
        all.sort((a, b) -> {
            int typeCmp = Integer.compare(a.matchType().ordinal(), b.matchType().ordinal());
            if (typeCmp != 0) return typeCmp;
            return Integer.compare(a.lineNumber(), b.lineNumber());
        });
        return all.size() > resultLimit ? all.subList(0, resultLimit) : all;
    }

    private static boolean matchesExcludePattern(String path, List<String> patterns) {
        if (path == null) return false;
        for (String pattern : patterns) {
            try {
                // Convert glob-style wildcards to regex, escape everything else
                String regex = java.util.regex.Pattern.quote(pattern)
                        .replace("\\*", "\\E.*\\Q")
                        .replace("\\?", "\\E.\\Q");
                if (path.matches(".*" + regex + ".*")) return true;
            } catch (java.util.regex.PatternSyntaxException e) {
                // Invalid pattern — skip it
            }
        }
        return false;
    }
}
