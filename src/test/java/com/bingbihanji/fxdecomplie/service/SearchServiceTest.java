package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.ui.search.SearchResult;
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
}
