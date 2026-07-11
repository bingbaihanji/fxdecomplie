package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.model.SearchResult;
import com.bingbaihanji.fxdecomplie.model.SearchScope;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 搜索策略接口每种实现对应一种搜索维度
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface SearchProvider {
    /** 在给定的源码缓存中搜索匹配 {@code query} 的结果 */
    List<SearchResult> search(String query, Map<String, String> sourceCache);

    /** @return 当前 provider 是否支持指定搜索范围(默认支持 ALL 和 null) */
    default boolean supports(SearchScope scope) {
        return scope == null || scope == SearchScope.ALL;
    }

    /** 带搜索选项的搜索(默认忽略选项,向后兼容) */
    default List<SearchResult> search(String query, Map<String, String> sourceCache,
                                      SearchOptions options) {
        return search(query, sourceCache);
    }

    /**
     * 预编译搜索模式,避免在循环中重复编译 Pattern
     *
     * @param options 搜索选项
     * @param query   搜索查询字符串
     * @return 正则模式下返回编译后的 Pattern,否则返回 null
     */
    default Pattern compileSearchPattern(SearchOptions options, String query) {
        if (options == null || !options.regex()) {
            return null;
        }
        try {
            int flags = options.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
            return Pattern.compile(query, flags);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    /**
     * 检查某一行是否匹配查询,考虑所有搜索选项
     *
     * <p>正则模式下忽略整词匹配选项(用户自行在正则中包含 {@code \b}),
     * 避免 {@code \b^foo\b} 等语义矛盾的组合</p>
     *
     * @param line    待匹配的行文本
     * @param query   搜索字符串或正则
     * @param options 搜索选项(正则、大小写、整词匹配)
     * @return 匹配成功返回 true
     */
    default boolean lineMatches(String line, String query, SearchOptions options) {
        return lineMatches(line, query, options, null);
    }

    /**
     * 检查某一行是否匹配查询,使用预编译的 Pattern 避免重复编译
     *
     * <p>正则模式下忽略整词匹配选项(用户自行在正则中包含 {@code \b}),
     * 避免 {@code \b^foo\b} 等语义矛盾的组合</p>
     *
     * @param line         待匹配的行文本
     * @param query        搜索字符串或正则
     * @param options      搜索选项(正则、大小写、整词匹配)
     * @param precompiled  预编译的 Pattern(由 {@link #compileSearchPattern} 生成),
     *                     非正则模式时为 null
     * @return 匹配成功返回 true
     */
    default boolean lineMatches(String line, String query, SearchOptions options,
                                Pattern precompiled) {
        if (line == null || query == null || options == null) {
            return false;
        }

        if (options.regex()) {
            if (precompiled != null) {
                return precompiled.matcher(line).find();
            }
            try {
                int flags = options.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
                return Pattern.compile(query, flags).matcher(line).find();
            } catch (PatternSyntaxException e) {
                return false;
            }
        }

        String content = options.caseSensitive() ? line : line.toLowerCase(java.util.Locale.ROOT);
        String q = options.caseSensitive() ? query : query.toLowerCase(java.util.Locale.ROOT);

        if (options.wholeWord()) {
            // 使用 Unicode 感知的 Java 标识符边界替代 \w
            String pattern = "(?<![\\p{javaJavaIdentifierPart}])"
                    + Pattern.quote(q)
                    + "(?![\\p{javaJavaIdentifierPart}])";
            return Pattern.compile(pattern).matcher(content).find();
        }

        return content.contains(q);
    }
}
