package com.bingbihanji.fxdecomplie.ui.search;

import java.util.List;
import java.util.Map;

/**
 * 搜索策略接口。每种实现对应一种搜索维度。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
@FunctionalInterface
public interface SearchProvider {
    List<SearchResult> search(String query, Map<String, String> sourceCache);
}
