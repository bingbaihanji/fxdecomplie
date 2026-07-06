package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.model.SearchResult;
import com.bingbaihanji.fxdecomplie.model.SearchScope;
import com.bingbaihanji.fxdecomplie.service.SearchProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按方法名搜索(匹配已反编译源码中的方法声明)
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

    private static final String METHOD_DECL_PATTERN =
            "^(?:@\\w+(?:\\([^)]*\\))?\\s*)*\\s*" +
                    "(?:(?:public|protected|private|static|final|synchronized|abstract|native)" +
                    "(?:\\s+(?:static|final|synchronized|abstract|native))*\\s+)?" +
                    "(?:<[\\w\\s,<>?]+>\\s+)?" +  // 可选的方法类型参数(如 <T>)
                    "(?:[\\w.]+(?:<[^>]+>)?(?:\\[\\])*\\s+)?" + // 可选的返回类型(如 List<String>)
                    "(\\w+)\\s*\\(";

    private static final Pattern METHOD_DECL = Pattern.compile(METHOD_DECL_PATTERN,
            Pattern.MULTILINE);

    private static final Pattern METHOD_DECL_CASE_INSENSITIVE = Pattern.compile(
            METHOD_DECL_PATTERN, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    @Override
    public List<SearchResult> search(String query, Map<String, String> sourceCache) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return results;
        }

        String lowerQuery = query.toLowerCase();
        for (var entry : sourceCache.entrySet()) {
            if (results.size() >= MAX_RESULTS) {
                break;
            }
            String[] lines = entry.getValue().replace("\r\n", "\n").replace("\r", "\n").split("\n");
            for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                Matcher m = METHOD_DECL.matcher(lines[i]);
                if (m.find()) {
                    String methodName = m.group(1);
                    if (NON_METHOD_KEYWORDS.contains(methodName)) {
                        continue;
                    }
                    if (methodName.toLowerCase().contains(lowerQuery)) {
                        results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                                SearchResult.MatchType.METHOD_NAME));
                    }
                }
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
        if (query == null || query.isBlank()) {
            return results;
        }

        Pattern methodDecl = options.caseSensitive()
                ? METHOD_DECL : METHOD_DECL_CASE_INSENSITIVE;

        for (var entry : sourceCache.entrySet()) {
            if (results.size() >= MAX_RESULTS) {
                break;
            }
            String[] lines = entry.getValue().replace("\r\n", "\n").replace("\r", "\n").split("\n");
            for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                Matcher m = methodDecl.matcher(lines[i]);
                if (m.find()) {
                    String methodName = m.group(1);
                    if (NON_METHOD_KEYWORDS.contains(methodName)) {
                        continue;
                    }
                    if (lineMatches(methodName, query, options)) {
                        results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                                SearchResult.MatchType.METHOD_NAME));
                    }
                }
            }
        }
        return results;
    }
}
