package com.bingbihanji.fxdecomplie.ui.search;

import com.bingbihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbihanji.fxdecomplie.model.MemberIndexEntry;
import com.bingbihanji.fxdecomplie.model.WorkspaceIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Searches method and field names from the workspace bytecode index.
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

    private static void addMatches(List<SearchResult> results, List<MemberIndexEntry> members,
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

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank() || index == null) {
            return results;
        }
        String lowerQuery = query.toLowerCase();
        for (ClassIndexEntry cls : index.classes()) {
            addMatches(results, cls.methods(), lowerQuery, SearchResult.MatchType.METHOD_NAME);
            addMatches(results, cls.fields(), lowerQuery, SearchResult.MatchType.FIELD_NAME);
            if (results.size() >= MAX_RESULTS) {
                break;
            }
        }
        return results;
    }
}
