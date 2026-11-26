package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.rename.RenameEntry;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceSession;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 工作区相关 MCP 工具.
 *
 * @author bingbaihanji
 */
public final class WorkspaceTools {

    private WorkspaceTools() {
        throw new AssertionError("utility class");
    }

    public static List<McpToolDefinition> definitions() {
        return List.of(serverInfo(), listWorkspaces(), openWorkspace(),
                selectWorkspace(), workspaceSummary(), setEngine(), listPackages(),
                listResources(), renameMapping());
    }

    private static McpToolDefinition serverInfo() {
        return new McpToolDefinition(
                "get_server_info",
                "Return FxDecompiler version, available decompiler engines, protocol endpoints and tool count.",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                (context, args) -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("name", "fxdecompiler");
                    data.put("version", context.serverVersion());
                    data.put("engines", Arrays.stream(DecompilerTypeEnum.values())
                            .map(Enum::name).toList());
                    data.put("defaultEngine", context.config().decompiler().defaultEngine().name());
                    data.put("streamableHttpEnabled",
                            context.config().mcp().streamableHttpEnabled());
                    data.put("legacySseEnabled", context.config().mcp().legacySseEnabled());
                    data.put("baseUrl", "http://" + context.config().mcp().advertisedHost()
                            + ":" + context.config().mcp().port());
                    data.put("streamableHttpEndpoint", "/mcp");
                    data.put("legacySseEndpoint", "/sse");
                    data.put("workspaceCount", context.sessions().sessions().size());
                    data.put("toolCount", context.toolCount());
                    return McpToolResult.ok(data);
                });
    }

    private static McpToolDefinition listWorkspaces() {
        return new McpToolDefinition(
                "list_workspaces",
                "List open workspaces with ids, paths, archive flag and active state.",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                (context, args) -> {
                    List<Map<String, Object>> items = new ArrayList<>();
                    String activeId = context.sessions().active()
                            .map(WorkspaceSession::id).orElse(null);
                    for (WorkspaceSession session : context.sessions().sessions()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", session.id());
                        item.put("name", session.workspace().getName());
                        item.put("path", session.workspace().getSourceFile().getAbsolutePath());
                        item.put("archive", session.workspace().isArchive());
                        item.put("active", session.id().equals(activeId));
                        item.put("indexReady", session.workspace().isIndexReady());
                        items.add(item);
                    }
                    return McpToolResult.ok(Map.of("items", items, "total", items.size()));
                });
    }

    private static McpToolDefinition openWorkspace() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string",
                                "description", "Absolute path to a JAR, ZIP, class file or directory")),
                "required", List.of("path"),
                "additionalProperties", false);
        return new McpToolDefinition(
                "open_workspace",
                "Open a local JAR/ZIP/class/directory, register it and show a workspace tab in the GUI.",
                schema,
                (context, args) -> {
                    Object rawPath = args.get("path");
                    if (rawPath == null || rawPath.toString().isBlank()) {
                        return McpToolResult.error("INVALID_ARGUMENT",
                                "path is required", Map.of());
                    }
                    Path path;
                    try {
                        path = Path.of(rawPath.toString());
                    } catch (RuntimeException e) {
                        return McpToolResult.error("INVALID_ARGUMENT",
                                "Invalid path: " + rawPath, Map.of());
                    }
                    try {
                        WorkspaceSession session = context.sessions().open(path)
                                .get(60, TimeUnit.SECONDS);
                        return McpToolResult.ok(Map.of(
                                "id", session.id(),
                                "name", session.workspace().getName(),
                                "path", session.workspace().getSourceFile().getAbsolutePath(),
                                "archive", session.workspace().isArchive()));
                    } catch (java.util.concurrent.TimeoutException e) {
                        return McpToolResult.error("TIMEOUT",
                                "Workspace load timed out", Map.of("path", path.toString()));
                    } catch (Exception e) {
                        Throwable cause = e.getCause() == null ? e : e.getCause();
                        return McpToolResult.error("WORKSPACE_OPEN_FAILED",
                                cause.getMessage() == null
                                        ? cause.getClass().getSimpleName() : cause.getMessage(),
                                Map.of("path", path.toString()));
                    }
                });
    }

    private static McpToolDefinition selectWorkspace() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("workspaceId", Map.of("type", "string")),
                "required", List.of("workspaceId"),
                "additionalProperties", false);
        return new McpToolDefinition(
                "select_workspace",
                "Make a workspace active and select its tab in the GUI.",
                schema,
                (context, args) -> {
                    String id = McpArgs.requireString(args, "workspaceId");
                    WorkspaceSession session = context.sessions().find(id)
                            .orElseThrow(() -> new McpToolException("WORKSPACE_NOT_FOUND",
                                    "Workspace not found", Map.of("workspaceId", id)));
                    context.sessions().markActive(session.workspace());
                    return McpToolResult.ok(Map.of("workspaceId", id, "active", true));
                });
    }

    private static McpToolDefinition workspaceSummary() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "includePackages", Map.of("type", "boolean"),
                "topPackages", Map.of("type", "integer", "minimum", 1, "maximum", 100)));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "get_workspace_summary",
                "Return class/resource counts, top packages, index state, current engine and active job count.",
                schema,
                (context, args) -> {
                    WorkspaceSession session = ClassResolver.session(context, args);
                    var workspace = session.workspace();
                    var classNodes = ClassResolver.classNodes(workspace);
                    int resourceCount = 0;
                    for (var node : allNodes(workspace)) {
                        if (!node.isClassFile() && !node.getNodeType().name().equals("PACKAGE")) {
                            resourceCount++;
                        }
                    }
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("workspaceId", session.id());
                    data.put("name", workspace.getName());
                    data.put("path", workspace.getSourceFile().getAbsolutePath());
                    data.put("archive", workspace.isArchive());
                    data.put("classCount", classNodes.size());
                    data.put("resourceCount", resourceCount);
                    data.put("indexReady", workspace.isIndexReady());
                    data.put("indexing", com.bingbaihanji.fxdecomplie.service.WorkspaceIndexService
                            .isIndexing(workspace));
                    data.put("currentEngine", context.config().decompiler().defaultEngine().name());
                    data.put("activeJobs", context.jobs().activeCount());
                    if (McpArgs.booleanValue(args, "includePackages", false)) {
                        int top = McpArgs.intValue(args, "topPackages", 20, 1, 100);
                        data.put("topPackages", topPackages(workspace, classNodes, top));
                    }
                    return McpToolResult.ok(data);
                });
    }

    private static McpToolDefinition setEngine() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "workspaceId", Map.of("type", "string"),
                        "engine", Map.of("type", "string",
                                "enum", List.of("PROCYON", "CFR", "VINEFLOWER", "JD", "JADX", "BDEC"))),
                "required", List.of("engine"),
                "additionalProperties", false);
        return new McpToolDefinition(
                "set_engine",
                "Switch the current decompiler engine, persist it and refresh the active GUI tab.",
                schema,
                (context, args) -> {
                    DecompilerTypeEnum engine = parseEngine(McpArgs.requireString(args, "engine"));
                    DecompilerTypeEnum previous = context.config().decompiler().defaultEngine();
                    context.config().decompiler().defaultEngine(engine);
                    context.config().save();
                    context.sessions().requestEngine(engine);
                    return McpToolResult.ok(Map.of(
                            "previous", previous.name(),
                            "engine", engine.name()));
                });
    }

    // 列出包及类/资源数量,支持前缀和分页
    private static McpToolDefinition listPackages() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "prefix", Map.of("type", "string"),
                "offset", Map.of("type", "integer", "minimum", 0),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 500)));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "list_packages",
                "List packages with class/resource counts, filtered by package prefix.",
                schema,
                (context, args) -> {
                    WorkspaceSession session = ClassResolver.session(context, args);
                    var workspace = session.workspace();
                    String prefix = McpArgs.optionalString(args, "prefix", "")
                            .replace('\\', '/').replace('.', '/');
                    int offset = McpArgs.intValue(args, "offset", 0, 0, Integer.MAX_VALUE - 1);
                    int limit = McpArgs.intValue(args, "limit", 100, 1, 500);
                    Map<String, int[]> counts = new LinkedHashMap<>();
                    for (var node : allNodes(workspace)) {
                        String path = node.getFullPath().replace('\\', '/');
                        String pkg;
                        if (node.isClassFile()) {
                            String internal = com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil
                                    .normalizeInternalName(path);
                            pkg = internal.lastIndexOf('/') < 0
                                    ? "" : internal.substring(0, internal.lastIndexOf('/'));
                        } else if (node.getNodeType().name().equals("PACKAGE")) {
                            continue;
                        } else {
                            pkg = path.lastIndexOf('/') < 0
                                    ? "" : path.substring(0, path.lastIndexOf('/'));
                        }
                        counts.computeIfAbsent(pkg, key -> new int[2])
                                [node.isClassFile() ? 0 : 1]++;
                    }
                    List<Map<String, Object>> all = new ArrayList<>();
                    for (var entry : counts.entrySet()) {
                        String pkg = entry.getKey();
                        if (!prefix.isBlank() && !pkg.equals(prefix)
                                && !pkg.startsWith(prefix + "/")) {
                            continue;
                        }
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("package", pkg);
                        item.put("classCount", entry.getValue()[0]);
                        item.put("resourceCount", entry.getValue()[1]);
                        all.add(item);
                    }
                    all.sort(java.util.Comparator.comparing(
                            item -> ((String) item.get("package"))));
                    int total = all.size();
                    int end = Math.min(total, offset + limit);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("items", offset >= total ? List.of() : all.subList(offset, end));
                    data.put("total", total);
                    data.put("offset", offset);
                    data.put("limit", limit);
                    data.put("hasMore", end < total);
                    data.put("nextOffset", end < total ? end : null);
                    return McpToolResult.ok(data);
                });
    }

    // 列出非 class 资源,支持文本/二进制筛选和分页
    private static McpToolDefinition listResources() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "prefix", Map.of("type", "string"),
                "query", Map.of("type", "string"),
                "type", Map.of("type", "string", "enum", List.of("all", "text", "binary")),
                "offset", Map.of("type", "integer", "minimum", 0),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 500)));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "list_resources",
                "List non-class resources with text/binary type, size and path filters.",
                schema,
                (context, args) -> {
                    WorkspaceSession session = ClassResolver.session(context, args);
                    var workspace = session.workspace();
                    String prefix = McpArgs.optionalString(args, "prefix", "")
                            .replace('\\', '/').replace('.', '/');
                    String query = McpArgs.optionalString(args, "query", "")
                            .toLowerCase(java.util.Locale.ROOT);
                    String type = McpArgs.optionalString(args, "type", "all")
                            .toLowerCase(java.util.Locale.ROOT);
                    if (!List.of("all", "text", "binary").contains(type)) {
                        throw new McpToolException("INVALID_ARGUMENT",
                                "type must be all, text or binary", Map.of("type", type));
                    }
                    int offset = McpArgs.intValue(args, "offset", 0, 0, Integer.MAX_VALUE - 1);
                    int limit = McpArgs.intValue(args, "limit", 100, 1, 500);
                    List<Map<String, Object>> all = new ArrayList<>();
                    for (var node : allNodes(workspace)) {
                        if (node.isClassFile() || node.getNodeType().name().equals("PACKAGE")) {
                            continue;
                        }
                        String path = node.getFullPath().replace('\\', '/');
                        if (!prefix.isBlank() && !path.startsWith(prefix)) {
                            continue;
                        }
                        if (!query.isBlank()
                                && !path.toLowerCase(java.util.Locale.ROOT).contains(query)) {
                            continue;
                        }
                        boolean text = node.isTextFile();
                        if ("text".equals(type) && !text) {
                            continue;
                        }
                        if ("binary".equals(type) && text) {
                            continue;
                        }
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("path", path);
                        item.put("name", node.getName());
                        item.put("text", text);
                        item.put("size", node.getSize());
                        item.put("extension", extensionOf(path));
                        all.add(item);
                    }
                    all.sort(java.util.Comparator.comparing(
                            item -> ((String) item.get("path"))));
                    int total = all.size();
                    int end = Math.min(total, offset + limit);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("items", offset >= total ? List.of() : all.subList(offset, end));
                    data.put("total", total);
                    data.put("offset", offset);
                    data.put("limit", limit);
                    data.put("hasMore", end < total);
                    data.put("nextOffset", end < total ? end : null);
                    return McpToolResult.ok(data);
                });
    }

    // 只读读取当前重命名映射
    private static McpToolDefinition renameMapping() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "format", Map.of("type", "string", "enum", List.of("json", "proguard")),
                "offset", Map.of("type", "integer", "minimum", 0),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 500),
                "maxChars", Map.of("type", "integer", "minimum", 1000, "maximum", 500_000)));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "get_rename_mapping",
                "Read the current project rename mapping as paged JSON entries or ProGuard text. Read-only.",
                schema,
                (context, args) -> {
                    WorkspaceSession session = ClassResolver.session(context, args);
                    String workspaceHash = CommentScope.workspaceHash(session.workspace());
                    String format = McpArgs.optionalString(args, "format", "json")
                            .toLowerCase(java.util.Locale.ROOT);
                    if (format.equals("proguard")) {
                        int maxChars = McpArgs.intValue(args, "maxChars", 100_000, 1000,
                                500_000);
                        String mapping = RenameService.exportProGuard(workspaceHash);
                        int totalChars = mapping.length();
                        boolean truncated = totalChars > maxChars;
                        if (truncated) {
                            mapping = mapping.substring(0, maxChars);
                        }
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("workspaceId", session.id());
                        data.put("format", "proguard");
                        data.put("content", mapping);
                        data.put("truncated", truncated);
                        data.put("totalChars", totalChars);
                        return McpToolResult.ok(data);
                    }
                    if (!format.equals("json")) {
                        throw new McpToolException("INVALID_ARGUMENT",
                                "format must be json or proguard", Map.of("format", format));
                    }
                    int offset = McpArgs.intValue(args, "offset", 0, 0, Integer.MAX_VALUE - 1);
                    int limit = McpArgs.intValue(args, "limit", 100, 1, 500);
                    List<RenameEntry> entries = RenameService.loadAll(workspaceHash);
                    int total = entries.size();
                    int end = Math.min(total, offset + limit);
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (RenameEntry entry : entries.subList(Math.min(offset, total), end)) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("type", entry.type());
                        item.put("className", entry.className());
                        item.put("oldName", entry.oldName());
                        item.put("newName", entry.newName());
                        item.put("descriptor", entry.desc());
                        items.add(item);
                    }
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("workspaceId", session.id());
                    data.put("format", "json");
                    data.put("items", items);
                    data.put("total", total);
                    data.put("offset", offset);
                    data.put("limit", limit);
                    data.put("hasMore", end < total);
                    data.put("nextOffset", end < total ? end : null);
                    return McpToolResult.ok(data);
                });
    }

    // 提取路径扩展名
    private static String extensionOf(String path) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        return dot > slash ? path.substring(dot + 1) : "";
    }

    private static DecompilerTypeEnum parseEngine(String value) {
        try {
            return DecompilerTypeEnum.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new McpToolException("INVALID_ARGUMENT",
                    "Unknown engine: " + value, Map.of("engine", value));
        }
    }

    private static List<com.bingbaihanji.fxdecomplie.model.FileTreeNode> allNodes(
            com.bingbaihanji.fxdecomplie.model.Workspace workspace) {
        List<com.bingbaihanji.fxdecomplie.model.FileTreeNode> nodes = new ArrayList<>();
        collect(workspace.getTreeRoot(), nodes);
        return nodes;
    }

    private static void collect(com.bingbaihanji.fxdecomplie.model.FileTreeModel model,
                                List<com.bingbaihanji.fxdecomplie.model.FileTreeNode> nodes) {
        if (model == null) {
            return;
        }
        if (model.getValue() != null) {
            nodes.add(model.getValue());
        }
        for (var child : model.getChildren()) {
            collect(child, nodes);
        }
    }

    private static List<Map<String, Object>> topPackages(
            com.bingbaihanji.fxdecomplie.model.Workspace workspace,
            List<com.bingbaihanji.fxdecomplie.model.FileTreeNode> classNodes, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var node : classNodes) {
            String internal = com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil
                    .normalizeInternalName(node.getFullPath());
            String pkg = internal.lastIndexOf('/') < 0
                    ? "" : internal.substring(0, internal.lastIndexOf('/'));
            counts.merge(pkg, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> Map.<String, Object>of(
                        "package", entry.getKey(), "classCount", entry.getValue()))
                .toList();
    }
}
