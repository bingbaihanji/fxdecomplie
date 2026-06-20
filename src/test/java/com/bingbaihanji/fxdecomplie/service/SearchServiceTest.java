package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.ui.search.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchServiceTest {

    @Test
    void honorsConfiguredResultLimit() {
        SearchService service = new SearchService();
        service.addProvider((query, sourceCache) -> java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> new SearchResult("C" + i + ".class", "match " + i,
                        i + 1, SearchResult.MatchType.CODE_TEXT))
                .toList());

        List<SearchResult> results = service.searchAll("match", Map.of(), 7);

        assertEquals(7, results.size());
    }

    @Test
    void excludePatternsUseGlobWildcards() {
        SearchService service = new SearchService();
        service.setExcludePatterns(List.of("*/internal/*", "*Generated.class"));
        service.addProvider((query, sourceCache) -> List.of(
                new SearchResult("com/example/App.class", "match", 1, SearchResult.MatchType.CLASS_NAME),
                new SearchResult("com/example/internal/Hidden.class", "match", 1, SearchResult.MatchType.CLASS_NAME),
                new SearchResult("com/example/FooGenerated.class", "match", 1, SearchResult.MatchType.CLASS_NAME)));

        List<SearchResult> results = service.searchAll("match", Map.of(), 10);

        assertEquals(1, results.size());
        assertEquals("com/example/App.class", results.getFirst().fullPath());
    }
}
