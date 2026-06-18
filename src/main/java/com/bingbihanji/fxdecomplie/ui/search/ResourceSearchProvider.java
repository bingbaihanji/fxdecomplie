package com.bingbihanji.fxdecomplie.ui.search;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 搜索非 class 资源文件内容（XML/JSON/YML/properties/.java 等文本资源） */
public class ResourceSearchProvider implements SearchProvider {

    private static final int MAX_RESULTS = 500;

    private final Map<String, byte[]> resourceCache; // path to raw bytes

    public ResourceSearchProvider(Map<String, byte[]> resourceCache) {
        this.resourceCache = resourceCache;
    }

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        String lowerQuery = query.toLowerCase();
        for (var entry : resourceCache.entrySet()) {
            try {
                String text = new String(entry.getValue(), StandardCharsets.UTF_8);
                String[] lines = text.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].toLowerCase().contains(lowerQuery)) {
                        results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                                SearchResult.MatchType.RESOURCE_TEXT));
                    }
                    if (results.size() >= MAX_RESULTS) break;
                }
            } catch (Exception ignored) {
                // skip binary or unreadable resources
            }
        }
        return results;
    }
}
