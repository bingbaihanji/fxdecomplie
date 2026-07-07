package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.*;
import com.bingbaihanji.fxdecomplie.service.SearchProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从工作区字节码索引中搜索方法和字段名
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class IndexedMemberSearchProvider implements SearchProvider {

    /** 结果上限,防止搜索耗时过长和内存溢出 */
    private static final int MAX_RESULTS = 500;

    /** 工作区字节码索引,包含所有类和成员信息 */
    private final WorkspaceIndex index;

    /**
     * 使用工作区索引构造
     *
     * @param index 工作区索引（可为 null,此时搜索返回空结果）
     */
    public IndexedMemberSearchProvider(WorkspaceIndex index) {
        this.index = index;
    }

    /**
     * 基本匹配辅助方法：将成员名称与关键字逐一比对（不区分大小写）,匹配则加入结果列表
     *
     * @param results    结果累积列表
     * @param members    待搜索的成员列表（方法或字段）
     * @param lowerQuery 已转为小写的查询关键字
     * @param type       匹配类型（METHOD_NAME 或 FIELD_NAME）
     */
    private void addMatches(List<SearchResult> results, List<MemberIndexEntry> members,
                            String lowerQuery, SearchResult.MatchType type) {
        for (MemberIndexEntry member : members) {
            // 同时匹配原始名和显示名（支持搜索反混淆/重命名后的名称）
            if (member.name().toLowerCase().contains(lowerQuery)
                    || member.displayName().toLowerCase().contains(lowerQuery)) {
                results.add(new SearchResult(member.ownerPath(), member.displayName(), 1, type));
            }
            if (results.size() >= MAX_RESULTS) {
                return;
            }
        }
    }

    /**
     * 高级匹配辅助方法：使用 SearchOptions 进行正则/大小写/全词匹配
     */
    private void addMatchesOptions(List<SearchResult> results, List<MemberIndexEntry> members,
                                   String query, SearchOptions options, SearchResult.MatchType type) {
        for (MemberIndexEntry member : members) {
            if (lineMatches(member.name(), query, options)
                    || lineMatches(member.displayName(), query, options)) {
                results.add(new SearchResult(member.ownerPath(), member.displayName(), 1, type));
            }
            if (results.size() >= MAX_RESULTS) {
                return;
            }
        }
    }

    /** 基本搜索：遍历工作区索引中所有类的方法和字段,逐名称匹配（不区分大小写） */
    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank() || index == null) {
            return results;
        }
        String lowerQuery = query.toLowerCase();
        for (ClassIndexEntry cls : index.classes()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            addMatches(results, cls.methods(), lowerQuery, SearchResult.MatchType.METHOD_NAME);
            addMatches(results, cls.fields(), lowerQuery, SearchResult.MatchType.FIELD_NAME);
            if (results.size() >= MAX_RESULTS) {
                break;
            }
        }
        return results;
    }

    /** 支持 ALL 和 METHOD 两种搜索范围 */
    @Override
    public boolean supports(SearchScope scope) {
        return scope == SearchScope.ALL || scope == SearchScope.METHOD;
    }

    /** 高级搜索：使用 SearchOptions 进行正则/大小写/全词匹配的索引成员搜索 */
    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache,
                                     SearchOptions options) {
        if (SearchOptions.DEFAULT.equals(options)) {
            return search(query, sourceCache);
        }
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank() || index == null) {
            return results;
        }
        for (ClassIndexEntry cls : index.classes()) {
            addMatchesOptions(results, cls.methods(), query, options,
                    SearchResult.MatchType.METHOD_NAME);
            addMatchesOptions(results, cls.fields(), query, options,
                    SearchResult.MatchType.FIELD_NAME);
            if (results.size() >= MAX_RESULTS) {
                break;
            }
        }
        return results;
    }
}
