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
 * 跨所有已反编译源码全文搜索(逐行匹配)
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class CodeSearchProvider implements SearchProvider {

    /** 返回的代码文本结果上限,防止搜索耗时过长和内存溢出 */
    private static final int MAX_RESULTS = 500;

    /**
     * 基本搜索：遍历所有已反编译源码文件,逐行匹配关键字（不区分大小写）
     * 支持中断检测,以便搜索任务取消后立即停止
     */
    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return results;
        }

        String lowerQuery = query.toLowerCase();
        for (var entry : sourceCache.entrySet()) {
            if (Thread.currentThread().isInterrupted() || results.size() >= MAX_RESULTS) {
                break;
            }
            String[] lines = entry.getValue().replace("\r\n", "\n").replace("\r", "\n").split("\n");
            for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                if (lines[i].toLowerCase().contains(lowerQuery)) {
                    results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                            SearchResult.MatchType.CODE_TEXT));
                }
            }
        }
        return results;
    }

    /** 支持 ALL 和 CODE 两种搜索范围 */
    @Override
    public boolean supports(SearchScope scope) {
        return scope == SearchScope.ALL || scope == SearchScope.CODE;
    }

    /** 高级搜索：支持正则、大小写、全词匹配等选项的源码全文搜索 */
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

        for (var entry : sourceCache.entrySet()) {
            if (results.size() >= MAX_RESULTS) {
                break;
            }
            String[] lines = entry.getValue().replace("\r\n", "\n").replace("\r", "\n").split("\n");
            for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                if (lineMatches(lines[i], query, options, precompiled)) {
                    results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                            SearchResult.MatchType.CODE_TEXT));
                }
            }
        }
        return results;
    }
}
