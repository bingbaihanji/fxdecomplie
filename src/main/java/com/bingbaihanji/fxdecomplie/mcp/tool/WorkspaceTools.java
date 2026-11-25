package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceSession;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 工作区相关 MCP 工具。
 *
 * @author bingbaihanji
 */
public final class WorkspaceTools {

    private WorkspaceTools() {
        throw new AssertionError("utility class");
    }

    public static List<McpToolDefinition> definitions() {
        return List.of(serverInfo(), listWorkspaces(), openWorkspace(),
                selectWorkspace(), workspaceSummary(), setEngine());
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
