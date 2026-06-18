package com.bingbihanji.fxdecomplie.ui.search;

import com.bingbihanji.fxdecomplie.model.SearchOptions;

import java.util.List;
import java.util.Map;

/**
 * 搜索策略接口。每种实现对应一种搜索维度。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface SearchProvider {
    List<SearchResult> search(String query, Map<String, String> sourceCache);

    /** 带搜索选项的搜索（默认忽略选项，向后兼容） */
    default List<SearchResult> search(String query, Map<String, String> sourceCache,
                                       SearchOptions options) {
        return search(query, sourceCache);
    }
}
