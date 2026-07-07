package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.model.SearchResult;
import com.bingbaihanji.fxdecomplie.model.SearchScope;
import com.bingbaihanji.fxdecomplie.service.SearchProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 按类名搜索 — 同时匹配原始类全路径和重命名/反混淆后的显示名
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class ClassSearchProvider implements SearchProvider {

    /** 结果上限,防止搜索耗时过长和内存溢出 */
    private static final int MAX_RESULTS = 500;

    /** 所有已知类名列表（fullPath 格式） */
    private final List<String> classNames;
    /** 原始 fullPath → 显示名（含 .class 后缀）,用于搜索反混淆/重命名后的名称 */
    private final Map<String, String> displayNamesByPath;

    /** 无参构造：使用空列表,仅从 sourceCache 中搜索 */
    public ClassSearchProvider() {
        this.classNames = List.of();
        this.displayNamesByPath = Map.of();
    }

    /** 仅使用类名列表构造（无显示名映射） */
    public ClassSearchProvider(List<String> classNames) {
        this.classNames = classNames != null ? List.copyOf(classNames) : List.of();
        this.displayNamesByPath = Map.of();
    }

    /**
     * 使用类名列表和显示名映射构造
     *
     * @param classNames       类全路径列表
     * @param displayNamesByPath fullPath 到显示名的映射（用于搜索反混淆/重命名后的类名）
     */
    public ClassSearchProvider(List<String> classNames,
                               Map<String, String> displayNamesByPath) {
        this.classNames = classNames != null ? List.copyOf(classNames) : List.of();
        this.displayNamesByPath = displayNamesByPath != null
                ? Map.copyOf(displayNamesByPath) : Map.of();
    }

    /** 基本搜索：同时匹配原始类名和显示名（不区分大小写） */
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

    /** 支持 ALL 和 CLASS 两种搜索范围 */
    @Override
    public boolean supports(SearchScope scope) {
        return scope == SearchScope.ALL || scope == SearchScope.CLASS;
    }

    /** 高级搜索：支持正则、大小写、全词匹配等选项的类名搜索 */
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

        Pattern precompiled = compileSearchPattern(options, query);

        for (String name : classNames) {
            if (results.size() >= MAX_RESULTS) {
                break;
            }
            String displayName = displayNamesByPath.getOrDefault(name, name);
            if (lineMatches(name, query, options, precompiled)
                    || lineMatches(displayName, query, options, precompiled)) {
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
                if (lineMatches(path, query, options, precompiled)
                        || lineMatches(displayName, query, options, precompiled)) {
                    results.add(new SearchResult(path, displayName, 1,
                            SearchResult.MatchType.CLASS_NAME));
                }
            }
        }
        return results;
    }
}
