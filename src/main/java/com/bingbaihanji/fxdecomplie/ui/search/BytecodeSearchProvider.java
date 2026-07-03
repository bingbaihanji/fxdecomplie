package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.model.SearchResult;
import com.bingbaihanji.fxdecomplie.model.SearchScope;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.SearchProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 搜索字节码/汇编文本视图(如 javap 输出)
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class BytecodeSearchProvider implements SearchProvider {

    /** 返回的字节码文本结果上限 */
    private static final int MAX_RESULTS = 500;

    /** 内部类名 → javap 风格反汇编文本 */
    private final Map<String, String> bytecodeCache;
    private final WorkspaceIndex index;

    public BytecodeSearchProvider(Map<String, String> bytecodeCache) {
        this.bytecodeCache = Objects.requireNonNull(bytecodeCache, "bytecodeCache");
        this.index = null;
    }

    public BytecodeSearchProvider(WorkspaceIndex index) {
        this.bytecodeCache = null;
        this.index = Objects.requireNonNull(index, "index");
    }

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        String lowerQuery = query.toLowerCase();
        forEachBytecodeText((path, text) -> {
            if (results.size() >= MAX_RESULTS) return;
            String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n");
            for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                if (lines[i].toLowerCase().contains(lowerQuery)) {
                    results.add(new SearchResult(path, lines[i].trim(), i + 1,
                            SearchResult.MatchType.BYTECODE_TEXT));
                }
            }
        });
        return results;
    }

    @Override
    public boolean supports(SearchScope scope) {
        return scope == SearchScope.ALL || scope == SearchScope.BYTECODE;
    }

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache,
                                     SearchOptions options) {
        if (SearchOptions.DEFAULT.equals(options)) {
            return search(query, sourceCache);
        }
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        forEachBytecodeText((path, text) -> {
            if (results.size() >= MAX_RESULTS) return;
            String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n");
            for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                if (lineMatches(lines[i], query, options)) {
                    results.add(new SearchResult(path, lines[i].trim(), i + 1,
                            SearchResult.MatchType.BYTECODE_TEXT));
                }
            }
        });
        return results;
    }

    private void forEachBytecodeText(BiConsumer<String, String> consumer) {
        if (index != null) {
            for (ClassIndexEntry cls : index.classes()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                consumer.accept(cls.fullPath(), cls.bytecodeText());
            }
            return;
        }
        for (var entry : bytecodeCache.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            consumer.accept(entry.getKey(), entry.getValue());
        }
    }
}
