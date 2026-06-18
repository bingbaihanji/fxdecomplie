package com.bingbihanji.fxdecomplie.ui.search;

import com.bingbihanji.fxdecomplie.model.SearchOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 搜索字节码/汇编文本视图（如 javap 输出）。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class BytecodeSearchProvider implements SearchProvider {

    /** Maximum number of bytecode text results to return */
    private static final int MAX_RESULTS = 500;

    /** Internal class name to javap-style disassembly text */
    private final Map<String, String> bytecodeCache;

    public BytecodeSearchProvider(Map<String, String> bytecodeCache) {
        this.bytecodeCache = Objects.requireNonNull(bytecodeCache, "bytecodeCache");
    }

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        String lowerQuery = query.toLowerCase();
        for (var entry : bytecodeCache.entrySet()) {
            String[] lines = entry.getValue().split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].toLowerCase().contains(lowerQuery)) {
                    results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                            SearchResult.MatchType.BYTECODE_TEXT));
                }
                if (results.size() >= MAX_RESULTS) break;
            }
        }
        return results;
    }

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache,
                                      SearchOptions options) {
        if (SearchOptions.DEFAULT.equals(options)) {
            return search(query, sourceCache);
        }
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        for (var entry : bytecodeCache.entrySet()) {
            String[] lines = entry.getValue().split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lineMatches(lines[i], query, options)) {
                    results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                            SearchResult.MatchType.BYTECODE_TEXT));
                }
                if (results.size() >= MAX_RESULTS) break;
            }
        }
        return results;
    }
}
