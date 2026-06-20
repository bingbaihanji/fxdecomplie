package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.ui.search.SearchProvider;
import com.bingbaihanji.fxdecomplie.ui.search.SearchResult;
import com.bingbaihanji.fxdecomplie.ui.search.SearchScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 全文搜索服务管理多个 SearchProvider 并聚合结果
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class SearchService {

    /** Registered search strategy providers, invoked in insertion order */
    private final List<SearchProvider> providers = new CopyOnWriteArrayList<>();

    /** Patterns to exclude from search results (simple wildcard matching) */
    private volatile List<String> excludePatterns = List.of();
    /** Pre-compiled exclude patterns for performance */
    private volatile List<Pattern> compiledExcludePatterns = List.of();

    private static boolean matchesExcludePattern(String path, List<String> patterns) {
        if (path == null) return false;
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (globContainsPattern(pattern).matcher(path).find()) return true;
        }
        return false;
    }

    private static Pattern globContainsPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char ch = glob.charAt(i);
            if (ch == '*') {
                regex.append(".*");
            } else if (ch == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    public void addProvider(SearchProvider provider) {
        providers.add(provider);
    }

    public void clearProviders() {
        providers.clear();
    }

    public void setExcludePatterns(List<String> patterns) {
        this.excludePatterns = patterns != null ? List.copyOf(patterns) : List.of();
        this.compiledExcludePatterns = this.excludePatterns.stream()
                .map(SearchService::globContainsPattern)
                .collect(Collectors.toList());
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
        if (!compiledExcludePatterns.isEmpty()) {
            all.removeIf(result -> {
                String path = result.fullPath();
                for (Pattern pattern : compiledExcludePatterns) {
                    if (pattern.matcher(path).find()) return true;
                }
                return false;
            });
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
        return searchAll(query, sourceCache, options, limit, SearchScope.ALL);
    }

    public List<SearchResult> searchAll(String query, Map<String, String> sourceCache,
                                        SearchOptions options, int limit, SearchScope scope) {
        if (query == null || query.isBlank()) return List.of();
        int resultLimit = Math.max(1, limit);
        List<SearchResult> all = new ArrayList<>();
        SearchScope effectiveScope = scope == null ? SearchScope.ALL : scope;
        for (SearchProvider provider : providers) {
            if (Thread.currentThread().isInterrupted()) return List.of();
            if (!provider.supports(effectiveScope)) {
                continue;
            }
            List<SearchResult> results = provider.search(query, sourceCache, options);
            all.addAll(results);
        }
        // Filter by exclude patterns if any
        if (!compiledExcludePatterns.isEmpty()) {
            all.removeIf(result -> {
                String path = result.fullPath();
                for (Pattern pattern : compiledExcludePatterns) {
                    if (pattern.matcher(path).find()) return true;
                }
                return false;
            });
        }
        all.sort((a, b) -> {
            int typeCmp = Integer.compare(a.matchType().ordinal(), b.matchType().ordinal());
            if (typeCmp != 0) return typeCmp;
            return Integer.compare(a.lineNumber(), b.lineNumber());
        });
        return all.size() > resultLimit ? all.subList(0, resultLimit) : all;
    }
}
