package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.MemberIndexEntry;
import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.model.SearchResult;
import com.bingbaihanji.fxdecomplie.model.SearchScope;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
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

    private static final int MAX_RESULTS = 500;

    private final WorkspaceIndex index;

    public IndexedMemberSearchProvider(WorkspaceIndex index) {
        this.index = index;
    }

    private void addMatches(List<SearchResult> results, List<MemberIndexEntry> members,
                            String lowerQuery, SearchResult.MatchType type) {
        for (MemberIndexEntry member : members) {
            if (member.name().toLowerCase().contains(lowerQuery)
                    || member.displayName().toLowerCase().contains(lowerQuery)) {
                results.add(new SearchResult(member.ownerPath(), member.displayName(), 1, type));
            }
            if (results.size() >= MAX_RESULTS) {
                return;
            }
        }
    }

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

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank() || index == null) {
            return results;
        }
        String lowerQuery = query.toLowerCase();
        for (ClassIndexEntry cls : index.classes()) {
            if (Thread.currentThread().isInterrupted()) break;
            addMatches(results, cls.methods(), lowerQuery, SearchResult.MatchType.METHOD_NAME);
            addMatches(results, cls.fields(), lowerQuery, SearchResult.MatchType.FIELD_NAME);
            if (results.size() >= MAX_RESULTS) {
                break;
            }
        }
        return results;
    }

    @Override
    public boolean supports(SearchScope scope) {
        return scope == SearchScope.ALL || scope == SearchScope.METHOD;
    }

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
