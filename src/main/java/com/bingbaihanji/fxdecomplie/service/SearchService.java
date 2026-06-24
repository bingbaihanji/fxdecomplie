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

    /** 已注册的搜索策略提供器,按插入顺序调用 */
    private final List<SearchProvider> providers = new CopyOnWriteArrayList<>();

    /** 搜索结果中要排除的模式(简单通配符匹配) */
    private volatile List<String> excludePatterns = List.of();
    /** 预编译的排除模式,用于性能优化 */
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
     * 运行所有已注册的提供器并合并结果,按匹配类型优先级和行号排序
     *
     * @param query       搜索字符串
     * @param sourceCache 类路径到反编译源码的映射
     * @return 合并排序后的结果,限制在给定的上限内
     */
    public List<SearchResult> searchAll(String query, Map<String, String> sourceCache) {
        return searchAll(query, sourceCache, 500);
    }

    public List<SearchResult> searchAll(String query, Map<String, String> sourceCache, int limit) {
        if (query == null || query.isBlank()) return List.of();
        int resultLimit = Math.max(1, limit);
        int softLimit = resultLimit * 2;
        List<SearchResult> all = new ArrayList<>();
        for (SearchProvider provider : providers) {
            if (Thread.currentThread().isInterrupted()) return List.of();
            if (all.size() >= softLimit) break;
            List<SearchResult> results = provider.search(query, sourceCache);
            // 单 provider 结果也裁剪到 softLimit
            if (results.size() > softLimit - all.size()) {
                results = results.subList(0, Math.max(0, softLimit - all.size()));
            }
            all.addAll(results);
        }
        // 根据排除模式过滤结果
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
        // 提前停止：结果数达到 2 倍限制后跳过剩余 provider（避免全量搜索后截断浪费）
        int softLimit = resultLimit * 2;
        for (SearchProvider provider : providers) {
            if (Thread.currentThread().isInterrupted()) return List.of();
            if (!provider.supports(effectiveScope)) {
                continue;
            }
            if (all.size() >= softLimit) break;
            List<SearchResult> results = provider.search(query, sourceCache, options);
            all.addAll(results);
        }
        // 根据排除模式过滤结果
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
