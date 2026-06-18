package com.bingbihanji.fxdecomplie.ui.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 搜索字节码/汇编文本视图（如 javap 输出） */
public class BytecodeSearchProvider implements SearchProvider {

    private static final int MAX_RESULTS = 500;

    private final Map<String, String> bytecodeCache; // internalName to javap text

    public BytecodeSearchProvider(Map<String, String> bytecodeCache) {
        this.bytecodeCache = bytecodeCache;
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
}
