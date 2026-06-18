package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.ui.search.SearchProvider;
import com.bingbihanji.fxdecomplie.ui.search.SearchResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class SearchServiceConcurrentTest {

    @Test
    void concurrentAddProviderAndSearch() throws Exception {
        SearchService service = new SearchService();
        SearchProvider slowProvider = (query, cache) -> {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return List.of(new SearchResult("/test/Foo.class", "line with " + query, 1,
                    SearchResult.MatchType.CODE_TEXT));
        };
        service.addProvider(slowProvider);

        CountDownLatch searchStarted = new CountDownLatch(1);
        CountDownLatch providerAdded = new CountDownLatch(1);

        Thread searchThread = new Thread(() -> {
            searchStarted.countDown();
            List<SearchResult> results = service.searchAll("test", Map.of(), 100);
            assertNotNull(results);
        });

        Thread addThread = new Thread(() -> {
            try {
                searchStarted.await(1, TimeUnit.SECONDS);
                service.addProvider((q, c) -> List.of());
                providerAdded.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        searchThread.start();
        addThread.start();

        searchThread.join(5000);
        addThread.join(5000);

        assertFalse(searchThread.isAlive(), "search should complete without exception");
        assertTrue(providerAdded.await(1, TimeUnit.SECONDS), "provider should be added");
    }
}
