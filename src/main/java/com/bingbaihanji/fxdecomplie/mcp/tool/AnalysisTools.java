package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.model.UsageResult;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceGroup;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceNode;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceTree;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.service.UsageSearchService;
import com.bingbaihanji.fxdecomplie.service.WorkspaceIndexService;
import com.bingbaihanji.fxdecomplie.service.reference.InheritanceReferenceIndexService;
import com.bingbaihanji.fxdecomplie.service.reference.InheritanceReferenceService;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceSession;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 分析类 MCP 工具：继承、用法、符号/字节码/资源搜索。
 *
 * @author bingbaihanji
 */
public final class AnalysisTools {

    private AnalysisTools() {
        throw new AssertionError("utility class");
    }

    public static List<McpToolDefinition> definitions() {
        return List.of(inheritance(), findUsages(), searchSymbols());
    }

    private static McpToolDefinition inheritance() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "class", Map.of("type", "string"),
                "maxNodes", Map.of("type", "integer", "minimum", 1, "maximum", 500)));
        schema.put("required", List.of("class"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "get_inheritance",
                "Return the inheritance reference tree: superclass, interfaces, annotations, subclasses and overrides.",
                schema,
                (context, args) -> {
                    ClassResolver.ResolvedClass resolved = ClassResolver.resolve(context, args);
                    Workspace workspace = resolved.workspace();
                    WorkspaceIndexService.ensureIndexingStarted(workspace);
                    InheritanceReferenceIndexService.getOrStart(workspace);
                    try {
                        InheritanceReferenceIndexService.getFuture(workspace)
                                .get(15, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                        // 索引仍在构建时返回 partial 结果
                    }
                    byte[] bytes = readBytes(resolved);
                    InheritanceReferenceTree tree = InheritanceReferenceService.buildTree(
                            workspace, resolved.fullPath(), bytes);
                    int maxNodes = McpArgs.intValue(args, "maxNodes", 300, 1, 500);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("fullPath", resolved.fullPath());
                    data.put("displayName", resolved.displayName());
                    data.put("partial", tree.partial());
                    data.put("statusMessage", tree.statusMessage());
                    data.put("indexReady", workspace.isIndexReady());
                    data.put("root", node(tree.root()));
                    data.put("groups", groups(tree.groups(), maxNodes));
                    return McpToolResult.ok(data);
                });
    }

    private static McpToolDefinition findUsages() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "target", Map.of("type", "string",
                        "description", "Class internal name, or class#member"),
                "offset", Map.of("type", "integer", "minimum", 0),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 500)));
        schema.put("required", List.of("target"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "find_usages",
                "Find class references, method calls and field accesses in workspace bytecode.",
                schema,
                (context, args) -> {
                    WorkspaceSession session = ClassResolver.session(context, args);
                    Workspace workspace = session.workspace();
                    WorkspaceIndex index = awaitIndex(workspace, 15);
                    String target = McpArgs.requireString(args, "target").replace('\\', '/');
                    int hash = target.indexOf('#');
                    if (hash < 0) {
                        ClassResolver.ResolvedClass resolved = ClassResolver.resolve(workspace, target);
                        target = resolved.internalName();
                    } else {
                        ClassResolver.ResolvedClass resolved = ClassResolver.resolve(
                                workspace, target.substring(0, hash));
                        target = resolved.internalName() + target.substring(hash);
                    }
                    int offset = McpArgs.intValue(args, "offset", 0, 0, Integer.MAX_VALUE - 1);
                    int limit = McpArgs.intValue(args, "limit", 100, 1, 500);
                    List<UsageResult> usages = UsageSearchService.findUsages(index, target);
                    int total = usages.size();
                    int end = Math.min(total, offset + limit);
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (UsageResult usage : usages.subList(Math.min(offset, total), end)) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("sourcePath", usage.sourcePath());
                        item.put("lineNumber", usage.lineNumber());
                        item.put("type", usage.type().name());
                        item.put("displayText", usage.displayText());
                        items.add(item);
                    }
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("target", target);
                    data.put("items", items);
                    data.put("total", total);
                    data.put("offset", offset);
                    data.put("limit", limit);
                    data.put("hasMore", end < total);
                    data.put("nextOffset", end < total ? end : null);
                    return McpToolResult.ok(data);
                });
    }

    private static McpToolDefinition searchSymbols() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "query", Map.of("type", "string"),
                "scopes", Map.of("type", "array",
                        "items", Map.of("type", "string",
                                "enum", List.of("class", "member", "bytecode", "resource"))),
                "regex", Map.of("type", "boolean"),
                "caseSensitive", Map.of("type", "boolean"),
                "wholeWord", Map.of("type", "boolean"),
                "offset", Map.of("type", "integer", "minimum", 0),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 200)));
        schema.put("required", List.of("query"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "search_symbols",
                "Search class names, members, bytecode text and text resources. Use start_full_text_search for source/comment text.",
                schema,
                (context, args) -> {
                    WorkspaceSession session = ClassResolver.session(context, args);
                    Workspace workspace = session.workspace();
                    WorkspaceIndex index = awaitIndex(workspace, 15);
                    String query = McpArgs.requireString(args, "query");
                    SearchOptions options = new SearchOptions(
                            McpArgs.booleanValue(args, "regex", false),
                            McpArgs.booleanValue(args, "caseSensitive", false),
                            McpArgs.booleanValue(args, "wholeWord", false));
                    List<String> scopes = McpArgs.stringList(args, "scopes");
                    if (scopes.isEmpty()) {
                        scopes = List.of("class", "member");
                    }
                    for (String scope : scopes) {
                        if (!List.of("class", "member", "bytecode", "resource")
                                .contains(scope)) {
                            throw new McpToolException("INVALID_ARGUMENT",
                                    "Unknown scope: " + scope, Map.of("scope", scope));
                        }
                    }
                    int offset = McpArgs.intValue(args, "offset", 0, 0, Integer.MAX_VALUE - 1);
                    int limit = McpArgs.intValue(args, "limit", 50, 1, 200);
                    int maxCollect = Math.min(5000, Math.max(offset + limit, limit * 4));
                    List<Map<String, Object>> all = new ArrayList<>();
                    if (scopes.contains("class")) {
                        searchClasses(index, workspace, query, options, all, maxCollect);
                    }
                    if (scopes.contains("member")) {
                        searchMembers(index, query, options, all, maxCollect);
                    }
                    if (scopes.contains("bytecode")) {
                        searchBytecode(index, query, options, all, maxCollect);
                    }
                    if (scopes.contains("resource")) {
                        searchResources(index, query, options, all, maxCollect);
                    }
                    all.sort(Comparator.comparingInt((Map<String, Object> item) ->
                                    ((Number) item.get("rank")).intValue())
                            .thenComparing(item -> item.get("fullPath").toString()));
                    int total = all.size();
                    int end = Math.min(total, offset + limit);
                    List<Map<String, Object>> items = all.subList(Math.min(offset, total), end)
                            .stream().peek(item -> item.remove("rank")).toList();
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("query", query);
                    data.put("items", items);
                    data.put("total", total);
                    data.put("offset", offset);
                    data.put("limit", limit);
                    data.put("hasMore", end < total);
                    data.put("nextOffset", end < total ? end : null);
                    data.put("resultCapped", all.size() >= maxCollect);
                    return McpToolResult.ok(data);
                });
    }

    private static void searchClasses(WorkspaceIndex index, Workspace workspace, String query,
                                      SearchOptions options, List<Map<String, Object>> out,
                                      int maxCollect) {
        String hash = CommentScope.workspaceHash(workspace);
        for (String path : index.classPaths()) {
            if (out.size() >= maxCollect) {
                return;
            }
            String internal = ClassNameUtil.normalizeInternalName(path);
            String simple = internal.substring(internal.lastIndexOf('/') + 1);
            String display = RenameService.displayClassName(internal, hash);
            if (matches(internal, query, options) || matches(simple, query, options)
                    || matches(display, query, options)) {
                out.add(item(path, display, 1, "CLASS_NAME", 0));
            }
        }
    }

    private static void searchMembers(WorkspaceIndex index, String query,
                                      SearchOptions options, List<Map<String, Object>> out,
                                      int maxCollect) {
        for (ClassIndexEntry cls : index.classes()) {
            if (out.size() >= maxCollect) {
                return;
            }
            for (var member : cls.methods()) {
                if (out.size() >= maxCollect) {
                    return;
                }
                if (matches(member.displayName(), query, options)
                        || matches(member.name(), query, options)) {
                    out.add(item(member.ownerPath(), member.displayName(), 1, "METHOD_NAME", 1));
                }
            }
            for (var member : cls.fields()) {
                if (out.size() >= maxCollect) {
                    return;
                }
                if (matches(member.displayName(), query, options)
                        || matches(member.name(), query, options)) {
                    out.add(item(member.ownerPath(), member.displayName(), 1, "FIELD_NAME", 2));
                }
            }
        }
    }

    private static void searchBytecode(WorkspaceIndex index, String query,
                                       SearchOptions options, List<Map<String, Object>> out,
                                       int maxCollect) {
        for (ClassIndexEntry cls : index.classes()) {
            if (out.size() >= maxCollect) {
                return;
            }
            String text = cls.bytecodeText();
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (matches(lines[i], query, options)) {
                    out.add(item(cls.fullPath(), lines[i].trim(), i + 1, "BYTECODE_TEXT", 3));
                    break;
                }
            }
        }
    }

    private static void searchResources(WorkspaceIndex index, String query,
                                       SearchOptions options, List<Map<String, Object>> out,
                                       int maxCollect) {
        for (var resource : index.resources()) {
            if (out.size() >= maxCollect) {
                return;
            }
            if (!resource.text()) {
                continue;
            }
            String text = new String(resource.bytes(), StandardCharsets.UTF_8);
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (matches(lines[i], query, options)) {
                    out.add(item(resource.fullPath(), lines[i].trim(), i + 1,
                            "RESOURCE_TEXT", 4));
                    break;
                }
            }
        }
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
                throw new McpToolException("INVALID_ARGUMENT",
                        "Invalid regex: " + e.getMessage(), Map.of("query", query));
            }
        }
        String value = options.caseSensitive() ? text : text.toLowerCase(Locale.ROOT);
        String needle = options.caseSensitive() ? query : query.toLowerCase(Locale.ROOT);
        if (!options.wholeWord()) {
            return value.contains(needle);
        }
        String pattern = "(?<![\\p{javaJavaIdentifierPart}])"
                + Pattern.quote(needle) + "(?![\\p{javaJavaIdentifierPart}])";
        return Pattern.compile(pattern).matcher(value).find();
    }

    private static Map<String, Object> item(String fullPath, String line, int lineNumber,
                                            String matchType, int rank) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fullPath", fullPath);
        item.put("matchLine", line);
        item.put("lineNumber", lineNumber);
        item.put("matchType", matchType);
        item.put("rank", rank);
        return item;
    }

    private static WorkspaceIndex awaitIndex(Workspace workspace, int seconds) {
        if (workspace.isClosed()) {
            throw new McpToolException("WORKSPACE_CLOSED", "Workspace is closed", Map.of());
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
            if (workspace.isClosed()) {
                throw new McpToolException("WORKSPACE_CLOSED", "Workspace is closed", Map.of());
            }
        }
        var progress = WorkspaceIndexService.getProgressSnapshot();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("indexReady", workspace.isIndexReady());
        if (progress != null) {
            details.put("phase", progress.phase());
            details.put("processed", progress.processed());
            details.put("total", progress.total());
            details.put("progress", progress.progress());
        }
        throw new McpToolException("INDEX_BUILDING", "Workspace index is still building", details);
    }

    private static byte[] readBytes(ClassResolver.ResolvedClass resolved) {
        try {
            byte[] bytes = com.bingbaihanji.fxdecomplie.service.WorkspaceByteReader
                    .readNodeBytes(resolved.workspace(), resolved.node(), true);
            if (bytes == null || bytes.length == 0) {
                throw new McpToolException("CLASS_NOT_FOUND",
                        "Unable to read class bytes: " + resolved.fullPath(), Map.of());
            }
            return bytes;
        } catch (java.io.IOException e) {
            throw new McpToolException("CLASS_NOT_FOUND",
                    "Unable to read class bytes: " + resolved.fullPath(), Map.of());
        }
    }

    private static Map<String, Object> node(InheritanceReferenceNode node) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("className", node.className());
        item.put("displayName", node.displayName());
        item.put("kind", node.kind().name());
        item.put("fullPath", node.fullPath());
        item.put("depth", node.depth());
        item.put("navigable", node.navigable());
        item.put("descriptor", node.descriptor());
        item.put("ownerClassName", node.ownerClassName());
        item.put("lineNumber", node.lineNumber());
        return item;
    }

    private static List<Map<String, Object>> groups(List<InheritanceReferenceGroup> groups,
                                                    int maxNodes) {
        List<Map<String, Object>> result = new ArrayList<>();
        int remaining = maxNodes;
        for (InheritanceReferenceGroup group : groups) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (InheritanceReferenceNode child : group.children()) {
                if (remaining <= 0) {
                    break;
                }
                children.add(node(child));
                remaining--;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", group.kind().name());
            item.put("title", group.title());
            item.put("collapsible", group.collapsible());
            item.put("children", children);
            result.add(item);
            if (remaining <= 0) {
                break;
            }
        }
        return result;
    }
}
