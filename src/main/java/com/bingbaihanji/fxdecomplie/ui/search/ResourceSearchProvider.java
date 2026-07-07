package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.model.SearchResult;
import com.bingbaihanji.fxdecomplie.model.SearchScope;
import com.bingbaihanji.fxdecomplie.service.SearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 搜索非 class 资源文件内容(XML/JSON/YML/properties/.java 等文本资源)
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class ResourceSearchProvider implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(ResourceSearchProvider.class);
    /** 结果上限,防止搜索耗时过长和内存溢出 */
    private static final int MAX_RESULTS = 500;

    /** 资源路径 → 原始字节（如 XML/JSON/properties 等非 class 文件内容） */
    private final Map<String, byte[]> resourceCache;

    /**
     * 使用资源缓存构造
     *
     * @param resourceCache 资源路径到原始字节数组的映射
     */
    public ResourceSearchProvider(Map<String, byte[]> resourceCache) {
        this.resourceCache = Objects.requireNonNull(resourceCache, "resourceCache");
    }

    /**
     * 基本搜索：将资源字节以 UTF-8 解码为文本,逐行匹配关键字（不区分大小写）
     * 不可解码的资源跳过并记录 debug 日志
     */
    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return results;
        }

        String lowerQuery = query.toLowerCase();
        for (var entry : resourceCache.entrySet()) {
            if (Thread.currentThread().isInterrupted() || results.size() >= MAX_RESULTS) {
                break;
            }
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
                log.debug("跳过不可读资源(基本搜索)", ignored);
            }
        }
        return results;
    }

    /** 支持 ALL 和 RESOURCE 两种搜索范围 */
    @Override
    public boolean supports(SearchScope scope) {
        return scope == SearchScope.ALL || scope == SearchScope.RESOURCE;
    }

    /** 高级搜索：支持正则、大小写、全词匹配等选项的资源文件搜索 */
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

        Pattern precompiled = compileSearchPattern(options, query);

        for (var entry : resourceCache.entrySet()) {
            if (results.size() >= MAX_RESULTS) {
                break;
            }
            try {
                String text = new String(entry.getValue(), StandardCharsets.UTF_8);
                String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n");
                for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                    if (lineMatches(lines[i], query, options, precompiled)) {
                        results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                                SearchResult.MatchType.RESOURCE_TEXT));
                    }
                }
            } catch (Exception ignored) {
                log.debug("跳过不可读资源(高级搜索)", ignored);
            }
        }
        return results;
    }
}
