package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.model.SearchResult;
import com.bingbaihanji.fxdecomplie.model.SearchScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /** 已注册的搜索策略提供器,按插入顺序调用 */
    private final List<SearchProvider> providers = new CopyOnWriteArrayList<>();

    /** 搜索结果中要排除的模式(简单通配符匹配) */
    private volatile List<String> excludePatterns = List.of();
    /** 预编译的排除模式,用于性能优化 */
    private volatile List<Pattern> compiledExcludePatterns = List.of();

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
        this.excludePatterns = patterns == null
                ? List.of()
                : patterns.stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .map(pattern -> pattern.replace('\\', '/'))
                .toList();
        this.compiledExcludePatterns = this.excludePatterns.stream()
                .map(SearchService::globContainsPattern)
                .collect(Collectors.toList());
    }

    private boolean isExcluded(SearchResult result) {
        if (result == null || compiledExcludePatterns.isEmpty()) {
            return false;
        }
        String path = result.fullPath();
        if (path == null || path.isBlank()) {
            return false;
        }
        path = path.replace('\\', '/');
        for (Pattern pattern : compiledExcludePatterns) {
            if (pattern.matcher(path).find()) {
                return true;
            }
        }
        return false;
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
        int globalSoftLimit = resultLimit * 2;
        int providerCount = Math.max(1, providers.size());
        int perProviderQuota = Math.max(1, globalSoftLimit / providerCount);
        List<SearchResult> all = new ArrayList<>();
        Set<String> addedKeys = new HashSet<>();
        for (SearchProvider provider : providers) {
            if (Thread.currentThread().isInterrupted()) return List.of();
            if (all.size() >= globalSoftLimit) break;
            List<SearchResult> results = searchProvider(provider, query, sourceCache, null);
            int providerAdded = 0;
            for (SearchResult result : results) {
                if (all.size() >= globalSoftLimit || providerAdded >= perProviderQuota) {
                    break;
                }
                if (!isExcluded(result)) {
                    String key = result.fullPath() + ":" + result.lineNumber() + ":" + result.matchType().name();
                    if (addedKeys.add(key)) {
                        all.add(result);
                        providerAdded++;
                    }
                }
            }
        }
        all.sort((a, b) -> {
            int typeCmp = Integer.compare(a.matchType().ordinal(), b.matchType().ordinal());
            if (typeCmp != 0) {
                return typeCmp;
            }
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
        int activeProviderCount = 0;
        for (SearchProvider provider : providers) {
            if (provider.supports(effectiveScope)) {
                activeProviderCount++;
            }
        }
        int globalSoftLimit = resultLimit * 2;
        int perProviderQuota = Math.max(1, globalSoftLimit / Math.max(1, activeProviderCount));
        Set<String> addedKeys = new HashSet<>();
        for (SearchProvider provider : providers) {
            if (Thread.currentThread().isInterrupted()) return List.of();
            if (!provider.supports(effectiveScope)) {
                continue;
            }
            if (all.size() >= globalSoftLimit) {
                break;
            }
            List<SearchResult> results = searchProvider(provider, query, sourceCache, options);
            int providerAdded = 0;
            for (SearchResult result : results) {
                if (all.size() >= globalSoftLimit || providerAdded >= perProviderQuota) {
                    break;
                }
                if (!isExcluded(result)) {
                    String key = result.fullPath() + ":" + result.lineNumber() + ":" + result.matchType().name();
                    if (addedKeys.add(key)) {
                        all.add(result);
                        providerAdded++;
                    }
                }
            }
        }
        all.sort((a, b) -> {
            int typeCmp = Integer.compare(a.matchType().ordinal(), b.matchType().ordinal());
            if (typeCmp != 0) {
                return typeCmp;
            }
            return Integer.compare(a.lineNumber(), b.lineNumber());
        });
        return all.size() > resultLimit ? all.subList(0, resultLimit) : all;
    }

    private List<SearchResult> searchProvider(SearchProvider provider, String query,
                                              Map<String, String> sourceCache,
                                              SearchOptions options) {
        try {
            List<SearchResult> results = options == null
                    ? provider.search(query, sourceCache)
                    : provider.search(query, sourceCache, options);
            return results == null ? List.of() : results;
        } catch (RuntimeException e) {
            log.warn("搜索提供器执行失败: {}", provider.getClass().getSimpleName(), e);
            return List.of();
        }
    }
}
