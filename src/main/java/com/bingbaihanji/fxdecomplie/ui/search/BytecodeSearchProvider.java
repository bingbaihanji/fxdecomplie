package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.*;
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

    /** 内部类名 → javap 风格反汇编文本（简单模式） */
    private final Map<String, String> bytecodeCache;
    /** 工作区字节码索引（高级模式,支持 SearchOptions） */
    private final WorkspaceIndex index;

    /**
     * 使用字节码缓存构造（简单搜索模式）
     *
     * @param bytecodeCache 类路径到字节码文本的映射
     */
    public BytecodeSearchProvider(Map<String, String> bytecodeCache) {
        this.bytecodeCache = Objects.requireNonNull(bytecodeCache, "bytecodeCache");
        this.index = null;
    }

    /**
     * 使用工作区索引构造（高级搜索模式,支持 SearchOptions）
     *
     * @param index 工作区索引
     */
    public BytecodeSearchProvider(WorkspaceIndex index) {
        this.bytecodeCache = null;
        this.index = Objects.requireNonNull(index, "index");
    }

    /** 基本搜索：逐行匹配字节码文本中的关键字（不区分大小写） */
    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return results;
        }

        String lowerQuery = query.toLowerCase();
        forEachBytecodeText((path, text) -> {
            if (results.size() >= MAX_RESULTS) {
                return;
            }
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

    /** 支持 ALL 和 BYTECODE 两种搜索范围 */
    @Override
    public boolean supports(SearchScope scope) {
        return scope == SearchScope.ALL || scope == SearchScope.BYTECODE;
    }

    /** 高级搜索：支持正则、大小写、全词匹配等选项的字节码搜索 */
    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache,
                                     SearchOptions options) {
        if (SearchOptions.DEFAULT.equals(options)) {
            return search(query, sourceCache);
        }
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return results;
        }

        forEachBytecodeText((path, text) -> {
            if (results.size() >= MAX_RESULTS) {
                return;
            }
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

    /**
     * 遍历所有可用的字节码文本,对每个条目调用 consumer
     * 优先使用工作区索引（index）,回退到字节码缓存（bytecodeCache）
     * 支持中断检测,以便搜索任务取消后立即停止遍历
     */
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
