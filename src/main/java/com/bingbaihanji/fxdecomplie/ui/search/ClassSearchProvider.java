package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按类名搜索(匹配 sourceCache 的 key 即全路径中的类名部分,以及独立的类名列表)
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class ClassSearchProvider implements SearchProvider {

    /** Maximum number of class name results to return */
    private static final int MAX_RESULTS = 500;

    /** Pre-indexed list of class full paths (from workspace index) */
    private final List<String> classNames;

    public ClassSearchProvider() {
        this.classNames = List.of();
    }

    public ClassSearchProvider(List<String> classNames) {
        this.classNames = classNames != null ? List.copyOf(classNames) : List.of();
    }

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        String lowerQuery = query.toLowerCase();

        // 先搜索显式传入的类名列表(可能包含尚未在 sourceCache 中打开的类)
        for (String name : classNames) {
            if (results.size() >= MAX_RESULTS) break;
            if (name.toLowerCase().contains(lowerQuery)) {
                results.add(new SearchResult(name, name, 1, SearchResult.MatchType.CLASS_NAME));
            }
        }

        // 再搜索 sourceCache 中的路径
        if (results.size() < MAX_RESULTS) {
            for (String path : sourceCache.keySet()) {
                if (results.size() >= MAX_RESULTS) break;
                if (path.toLowerCase().contains(lowerQuery)) {
                    results.add(new SearchResult(path, path, 1, SearchResult.MatchType.CLASS_NAME));
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
        if (query == null || query.isBlank()) return results;

        // 先搜索显式传入的类名列表
        for (String name : classNames) {
            if (results.size() >= MAX_RESULTS) break;
            if (lineMatches(name, query, options)) {
                results.add(new SearchResult(name, name, 1, SearchResult.MatchType.CLASS_NAME));
            }
        }

        // 再搜索 sourceCache 中的路径
        if (results.size() < MAX_RESULTS) {
            for (String path : sourceCache.keySet()) {
                if (results.size() >= MAX_RESULTS) break;
                if (lineMatches(path, query, options)) {
                    results.add(new SearchResult(path, path, 1, SearchResult.MatchType.CLASS_NAME));
                }
            }
        }
        return results;
    }
}
