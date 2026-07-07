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

    /** 结果上限,防止搜索耗时过长和内存溢出 */
    private static final int MAX_RESULTS = 500;

    /**
     * 非方法关键字集合,用于过滤正则误匹配的 Java 关键字/控制流语句
     * 方法声明正则可能将 if/while/for 等关键字也匹配为"方法名",此处排除
     */
    private static final Set<String> NON_METHOD_KEYWORDS = Set.of(
            "if", "else", "while", "for", "do", "switch", "case", "catch", "finally",
            "try", "synchronized", "return", "throw", "new", "class", "interface",
            "enum", "record", "assert", "break", "continue", "this", "super",
            "instanceof", "import", "package");

    /**
     * 方法声明正则模式：
     * - 可选注解（@Override 等）
     * - 可选修饰符（public/static/final 等）
     * - 可选类型参数（如 <T>）
     * - 可选返回类型（如 List<String>）
     * - 捕获组1：方法名
     * - 方法名后跟 (
     */
    private static final String METHOD_DECL_PATTERN =
            "^(?:@\\w+(?:\\([^)]*\\))?\\s*)*\\s*" +
                    "(?:(?:public|protected|private|static|final|synchronized|abstract|native)" +
                    "(?:\\s+(?:static|final|synchronized|abstract|native))*\\s+)?" +
                    "(?:<[\\w\\s,<>?]+>\\s+)?" +  // 可选的方法类型参数(如 <T>)
                    "(?:[\\w.]+(?:<[^>]+>)?(?:\\[\\])*\\s+)?" + // 可选的返回类型(如 List<String>)
                    "(\\w+)\\s*\\(";             // 捕获方法名

    /** 预编译的方法声明正则（区分大小写） */
    private static final Pattern METHOD_DECL = Pattern.compile(METHOD_DECL_PATTERN,
            Pattern.MULTILINE);

    /** 预编译的方法声明正则（不区分大小写）,用于高级搜索选项为非大小写敏感时 */
    private static final Pattern METHOD_DECL_CASE_INSENSITIVE = Pattern.compile(
            METHOD_DECL_PATTERN, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    /**
     * 基本搜索：用正则匹配方法声明,提取方法名与关键字比较（不区分大小写）
     * 自动过滤 if/while/for 等非方法关键字的误匹配
     */
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

    /** 支持 ALL 和 METHOD 两种搜索范围 */
    @Override
    public boolean supports(SearchScope scope) {
        return scope == SearchScope.ALL || scope == SearchScope.METHOD;
    }

    /** 高级搜索：支持正则、大小写、全词匹配等选项的方法名搜索 */
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

        // 根据搜索选项选择合适的方法声明正则（区分/不区分大小写）
        Pattern methodDecl = options.caseSensitive()
                ? METHOD_DECL : METHOD_DECL_CASE_INSENSITIVE;
        Pattern precompiled = compileSearchPattern(options, query);

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
                    if (lineMatches(methodName, query, options, precompiled)) {
                        results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                                SearchResult.MatchType.METHOD_NAME));
                    }
                }
            }
        }
        return results;
    }
}
