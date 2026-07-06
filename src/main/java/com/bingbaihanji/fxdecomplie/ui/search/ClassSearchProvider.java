package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.model.SearchResult;
import com.bingbaihanji.fxdecomplie.model.SearchScope;
import com.bingbaihanji.fxdecomplie.service.SearchProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按类名搜索 — 同时匹配原始类全路径和重命名/反混淆后的显示名。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class ClassSearchProvider implements SearchProvider {

    private static final int MAX_RESULTS = 500;

    private final List<String> classNames;
    /** 原始 fullPath → 显示名（含 .class 后缀），用于搜索反混淆/重命名后的名称 */
    private final Map<String, String> displayNamesByPath;

    public ClassSearchProvider() {
        this.classNames = List.of();
        this.displayNamesByPath = Map.of();
    }

    public ClassSearchProvider(List<String> classNames) {
        this.classNames = classNames != null ? List.copyOf(classNames) : List.of();
        this.displayNamesByPath = Map.of();
    }

    public ClassSearchProvider(List<String> classNames,
                               Map<String, String> displayNamesByPath) {
        this.classNames = classNames != null ? List.copyOf(classNames) : List.of();
        this.displayNamesByPath = displayNamesByPath != null
                ? Map.copyOf(displayNamesByPath) : Map.of();
    }

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return results;
        }

        String lowerQuery = query.toLowerCase();

        for (String name : classNames) {
            if (results.size() >= MAX_RESULTS) {
                break;
            }
            String displayName = displayNamesByPath.getOrDefault(name, name);
            if (name.toLowerCase().contains(lowerQuery)
                    || displayName.toLowerCase().contains(lowerQuery)) {
                results.add(new SearchResult(name, displayName, 1,
                        SearchResult.MatchType.CLASS_NAME));
            }
        }

        if (results.size() < MAX_RESULTS) {
            for (String path : sourceCache.keySet()) {
                if (results.size() >= MAX_RESULTS) {
                    break;
                }
                String displayName = displayNamesByPath.getOrDefault(path, path);
                if (path.toLowerCase().contains(lowerQuery)
                        || displayName.toLowerCase().contains(lowerQuery)) {
                    results.add(new SearchResult(path, displayName, 1,
                            SearchResult.MatchType.CLASS_NAME));
                }
            }
        }
        return results;
    }

    @Override
    public boolean supports(SearchScope scope) {
        return scope == SearchScope.ALL || scope == SearchScope.CLASS;
    }

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache,
                                     SearchOptions options) {
        if (SearchOptions.DEFAULT.equals(options)) {
            return search(query, sourceCache);
        }
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return results;
        }

        for (String name : classNames) {
            if (results.size() >= MAX_RESULTS) {
                break;
            }
            String displayName = displayNamesByPath.getOrDefault(name, name);
            if (lineMatches(name, query, options)
                    || lineMatches(displayName, query, options)) {
                results.add(new SearchResult(name, displayName, 1,
                        SearchResult.MatchType.CLASS_NAME));
            }
        }

        if (results.size() < MAX_RESULTS) {
            for (String path : sourceCache.keySet()) {
                if (results.size() >= MAX_RESULTS) {
                    break;
                }
                String displayName = displayNamesByPath.getOrDefault(path, path);
                if (lineMatches(path, query, options)
                        || lineMatches(displayName, query, options)) {
                    results.add(new SearchResult(path, displayName, 1,
                            SearchResult.MatchType.CLASS_NAME));
                }
            }
        }
        return results;
    }
}
