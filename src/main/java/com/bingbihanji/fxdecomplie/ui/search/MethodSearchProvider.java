package com.bingbihanji.fxdecomplie.ui.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按方法名搜索（匹配已反编译源码中的方法声明）。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class MethodSearchProvider implements SearchProvider {

    private static final int MAX_RESULTS = 500;

    private static final Set<String> NON_METHOD_KEYWORDS = Set.of(
            "if", "else", "while", "for", "do", "switch", "case", "catch", "finally",
            "try", "synchronized", "return", "throw", "new", "class", "interface",
            "enum", "record", "assert", "break", "continue", "this", "super",
            "instanceof", "import", "package");

    private static final Pattern METHOD_DECL = Pattern.compile(
            "^\\s*(?:public|protected|private|static|final|synchronized|abstract|native)" +
                    "(?:\\s+(?:static|final|synchronized|abstract|native))*\\s+" +
                    "(?:<[\\w\\s,<>?]+>\\s+)?" +  // optional generic type params
                    "(\\w+)\\s*\\(",
            Pattern.MULTILINE);

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;

        String lowerQuery = query.toLowerCase();
        for (var entry : sourceCache.entrySet()) {
            String[] lines = entry.getValue().split("\n");
            for (int i = 0; i < lines.length; i++) {
                Matcher m = METHOD_DECL.matcher(lines[i]);
                if (m.find()) {
                    String methodName = m.group(1);
                    if (NON_METHOD_KEYWORDS.contains(methodName)) continue; // skip keywords
                    if (methodName.toLowerCase().contains(lowerQuery)) {
                        results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                                SearchResult.MatchType.METHOD_NAME));
                    }
                }
                if (results.size() >= MAX_RESULTS) break;
            }
        }
        return results;
    }
}
