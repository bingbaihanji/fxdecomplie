package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.mcp.job.McpJob;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.CommentData;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.CommentManager;
import com.bingbaihanji.fxdecomplie.service.WorkspaceIndexService;
import com.bingbaihanji.fxdecomplie.service.decompile.ClassSourceRequest;
import com.bingbaihanji.fxdecomplie.service.decompile.ClassSourceResult;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceSession;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 异步全文搜索：源码、注释与文本资源。
 *
 * @author bingbaihanji
 */
public final class FullTextSearchTools {

    private static final int MAX_MATCHES = 2000;

    private FullTextSearchTools() {
        throw new AssertionError("utility class");
    }

    public static List<McpToolDefinition> definitions() {
        return List.of(startFullTextSearch());
    }

    private static McpToolDefinition startFullTextSearch() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "query", Map.of("type", "string"),
                "scopes", Map.of("type", "array",
                        "items", Map.of("type", "string",
                                "enum", List.of("code", "comment", "resource"))),
                "engine", Map.of("type", "string"),
                "regex", Map.of("type", "boolean"),
                "caseSensitive", Map.of("type", "boolean"),
                "wholeWord", Map.of("type", "boolean"),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 500)));
        schema.put("required", List.of("query"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "start_full_text_search",
                "Start an asynchronous full-text search over decompiled source, comments and text resources. Poll with get_job_result.",
                schema,
                (context, args) -> {
                    WorkspaceSession session = ClassResolver.session(context, args);
                    Workspace workspace = session.workspace();
                    String query = McpArgs.requireString(args, "query");
                    List<String> scopes = McpArgs.stringList(args, "scopes");
                    if (scopes.isEmpty()) {
                        scopes = List.of("code");
                    }
                    for (String scope : scopes) {
                        if (!List.of("code", "comment", "resource").contains(scope)) {
                            throw new McpToolException("INVALID_ARGUMENT",
                                    "Unknown scope: " + scope, Map.of("scope", scope));
                        }
                    }
                    SearchOptions options = new SearchOptions(
                            McpArgs.booleanValue(args, "regex", false),
                            McpArgs.booleanValue(args, "caseSensitive", false),
                            McpArgs.booleanValue(args, "wholeWord", false));
                    String engineValue = McpArgs.optionalString(args, "engine", "");
                    DecompilerTypeEnum engine = engineValue.isBlank()
                            ? context.config().decompiler().defaultEngine()
                            : parseEngine(engineValue);
                    int limit = McpArgs.intValue(args, "limit", 200, 1, 500);
                    List<String> finalScopes = scopes;
                    McpJob job = context.jobs().submit("FULL_TEXT_SEARCH", session.id(), running -> {
                        WorkspaceIndex index = awaitIndex(workspace, 60);
                        List<Map<String, Object>> matches = new ArrayList<>();
                        int total = index.classes().size();
                        int processed = 0;
                        if (finalScopes.contains("code")) {
                            for (ClassIndexEntry cls : index.classes()) {
                                if (running.isCancelled() || workspace.isClosed()
                                        || matches.size() >= Math.min(limit, MAX_MATCHES)) {
                                    break;
                                }
                                processed++;
                                running.updateProgress(processed, total, cls.fullPath());
                                ClassSourceResult result = context.classSource().load(
                                        new ClassSourceRequest(workspace, cls.fullPath(), null,
                                                engine, Map.of(), false, 30,
                                                () -> !running.isCancelled()));
                                if (!result.failure()) {
                                    searchLines(cls.fullPath(), result.sourceCode(), query, options,
                                            "CODE_TEXT", matches, limit);
                                }
                            }
                        }
                        if (finalScopes.contains("comment")) {
                            String hash = CommentScope.workspaceHash(workspace);
                            for (ClassIndexEntry cls : index.classes()) {
                                if (running.isCancelled()
                                        || matches.size() >= Math.min(limit, MAX_MATCHES)) {
                                    break;
                                }
                                List<CommentData> comments = CommentManager.load(hash,
                                        cls.fullPath());
                                for (CommentData comment : comments) {
                                    if (matches(comment.text(), query, options)) {
                                        matches.add(item(cls.fullPath(), comment.text(),
                                                comment.line(), "COMMENT_TEXT"));
                                        if (matches.size() >= MAX_MATCHES) {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (finalScopes.contains("resource")) {
                            for (var resource : index.resources()) {
                                if (running.isCancelled()
                                        || matches.size() >= Math.min(limit, MAX_MATCHES)) {
                                    break;
                                }
                                if (!resource.text()) {
                                    continue;
                                }
                                String text = new String(resource.bytes(), StandardCharsets.UTF_8);
                                searchLines(resource.fullPath(), text, query, options,
                                        "RESOURCE_TEXT", matches, limit);
                            }
                        }
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("query", query);
                        data.put("items", matches);
                        data.put("total", matches.size());
                        data.put("limit", limit);
                        data.put("truncated", matches.size() >= MAX_MATCHES);
                        data.put("engine", engine.name());
                        running.updateProgress(total, total, "search finished");
                        running.complete(data);
                    });
                    return McpToolResult.ok(Map.of("jobId", job.id(), "state", job.state().name()));
                });
    }

    private static void searchLines(String fullPath, String text, String query,
                                    SearchOptions options, String matchType,
                                    List<Map<String, Object>> matches, int limit) {
        if (text == null) {
            return;
        }
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (matches(lines[i], query, options)) {
                matches.add(item(fullPath, lines[i].trim(), i + 1, matchType));
                if (matches.size() >= Math.min(limit, MAX_MATCHES)) {
                    return;
                }
            }
        }
    }

    private static WorkspaceIndex awaitIndex(Workspace workspace, int seconds) {
        if (workspace.isClosed()) {
            throw new IllegalStateException("workspace is closed");
        }
        if (workspace.isIndexReady()) {
            return workspace.getIndex();
        }
        WorkspaceIndexService.ensureIndexingStarted(workspace);
        try {
            WorkspaceIndex index = workspace.getIndexFuture().get(seconds, TimeUnit.SECONDS);
            if (index != null && index != WorkspaceIndex.EMPTY) {
                return index;
            }
        } catch (Exception e) {
            throw new IllegalStateException("workspace index failed or timed out", e);
        }
        throw new IllegalStateException("workspace index is still building");
    }

    private static boolean matches(String text, String query, SearchOptions options) {
        if (text == null || query == null) {
            return false;
        }
        if (options.regex()) {
            try {
                int flags = options.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
                return Pattern.compile(query, flags).matcher(text).find();
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("invalid regex: " + e.getMessage(), e);
            }
        }
        String value = options.caseSensitive() ? text : text.toLowerCase(Locale.ROOT);
        String needle = options.caseSensitive() ? query : query.toLowerCase(Locale.ROOT);
        if (!options.wholeWord()) {
            return value.contains(needle);
        }
        return Pattern.compile("(?<![\\p{javaJavaIdentifierPart}])"
                + Pattern.quote(needle) + "(?![\\p{javaJavaIdentifierPart}])")
                .matcher(value).find();
    }

    private static Map<String, Object> item(String fullPath, String line, int lineNumber,
                                            String matchType) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fullPath", fullPath);
        item.put("matchLine", line);
        item.put("lineNumber", lineNumber);
        item.put("matchType", matchType);
        return item;
    }

    private static DecompilerTypeEnum parseEngine(String value) {
        try {
            return DecompilerTypeEnum.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new McpToolException("INVALID_ARGUMENT",
                    "Unknown engine: " + value, Map.of("engine", value));
        }
    }
}
