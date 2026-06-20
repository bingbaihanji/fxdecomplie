package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 搜索非 class 资源文件内容（XML/JSON/YML/properties/.java 等文本资源）。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class ResourceSearchProvider implements SearchProvider {

    private static final int MAX_RESULTS = 500;

    private final Map<String, byte[]> resourceCache; // path to raw bytes

    public ResourceSearchProvider(Map<String, byte[]> resourceCache) {
        this.resourceCache = Objects.requireNonNull(resourceCache, "resourceCache");
    }

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        String lowerQuery = query.toLowerCase();
        for (var entry : resourceCache.entrySet()) {
            if (results.size() >= MAX_RESULTS) break;
            try {
                String text = new String(entry.getValue(), StandardCharsets.UTF_8);
                String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n");
                for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                    if (lines[i].toLowerCase().contains(lowerQuery)) {
                        results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                                SearchResult.MatchType.RESOURCE_TEXT));
                    }
                }
            } catch (Exception ignored) {
                // skip binary or unreadable resources
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

        for (var entry : resourceCache.entrySet()) {
            if (results.size() >= MAX_RESULTS) break;
            try {
                String text = new String(entry.getValue(), StandardCharsets.UTF_8);
                String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n");
                for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                    if (lineMatches(lines[i], query, options)) {
                        results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                                SearchResult.MatchType.RESOURCE_TEXT));
                    }
                }
            } catch (Exception ignored) {
                // skip binary or unreadable resources
            }
        }
        return results;
    }
}
