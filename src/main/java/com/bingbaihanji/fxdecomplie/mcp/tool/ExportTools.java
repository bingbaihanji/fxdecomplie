package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.mcp.job.McpJob;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.model.ExportResult;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.service.DecompilerOptions;
import com.bingbaihanji.fxdecomplie.service.ExportService;
import com.bingbaihanji.fxdecomplie.service.WorkspaceExportCache;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceClassBytecodeProvider;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceSession;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 异步导出 MCP 工具.
 *
 * @author bingbaihanji
 */
public final class ExportTools {

    private ExportTools() {
        throw new AssertionError("utility class");
    }

    public static List<McpToolDefinition> definitions() {
        return List.of(startExport());
    }

    private static McpToolDefinition startExport() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("workspaceId", Map.of("type", "string"));
        properties.put("outputPath", Map.of("type", "string",
                "description", "Absolute output directory or ZIP path"));
        properties.put("format", Map.of("type", "string", "enum", List.of("DIR", "ZIP")));
        properties.put("engine", Map.of("type", "string",
                "description", "PROCYON, CFR, VINEFLOWER, JD, JADX or BDEC"));
        properties.put("options", Map.of("type", "object",
                "description", "Optional engine option overrides"));
        properties.put("scope", Map.of("type", "string",
                "enum", List.of("all", "package", "class", "classes")));
        properties.put("target", Map.of("type", "string",
                "description", "Package prefix or class selector when scope is not all"));
        properties.put("targets", Map.of("type", "array",
                "items", Map.of("type", "string"),
                "description", "Class selectors for scope=classes"));
        properties.put("exportResources", Map.of("type", "boolean"));
        properties.put("conflictPolicy", Map.of("type", "string",
                "enum", List.of("SKIP", "OVERWRITE", "RENAME")));
        properties.put("overwriteOutput", Map.of("type", "boolean"));
        properties.put("includeComments", Map.of("type", "boolean"));
        schema.put("properties", properties);
        schema.put("required", List.of("outputPath"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "start_export",
                "Start an asynchronous export of decompiled sources and resources. Poll with get_job.",
                schema,
                (context, args) -> {
                    if (!context.config().mcp().exportEnabled()) {
                        throw new McpToolException("EXPORT_DISABLED",
                                "MCP export is disabled in settings", Map.of());
                    }
                    WorkspaceSession session = ClassResolver.session(context, args);
                    Workspace workspace = session.workspace();
                    String formatValue = McpArgs.optionalString(args, "format", "DIR")
                            .toUpperCase(Locale.ROOT);
                    if (!formatValue.equals("DIR") && !formatValue.equals("ZIP")) {
                        throw new McpToolException("INVALID_ARGUMENT",
                                "format must be DIR or ZIP", Map.of("format", formatValue));
                    }
                    boolean zip = formatValue.equals("ZIP");
                    Path output = ExportPathGuard.validate(
                            McpArgs.requireString(args, "outputPath"), zip,
                            McpArgs.booleanValue(args, "overwriteOutput", false));
                    String engineValue = McpArgs.optionalString(args, "engine", "");
                    DecompilerTypeEnum engine = engineValue.isBlank()
                            ? context.config().decompiler().defaultEngine()
                            : parseEngine(engineValue);
                    ExportConfig.ConflictPolicy conflict = parseConflict(
                            McpArgs.optionalString(args, "conflictPolicy", "SKIP"));
                    boolean exportResources = McpArgs.booleanValue(args, "exportResources", true);
                    boolean includeComments = McpArgs.booleanValue(args, "includeComments", false);
                    String scope = McpArgs.optionalString(args, "scope", "all")
                            .toLowerCase(Locale.ROOT);
                    if (!List.of("all", "package", "class", "classes").contains(scope)) {
                        throw new McpToolException("INVALID_ARGUMENT",
                                "scope must be all, package, class or classes",
                                Map.of("scope", scope));
                    }
                    String target = McpArgs.optionalString(args, "target", "");
                    List<String> targets = McpArgs.stringList(args, "targets");
                    List<FileTreeNode> nodes = selectNodes(workspace, scope, target, targets);
                    if (nodes.isEmpty()) {
                        throw new McpToolException("INVALID_ARGUMENT",
                                "No exportable entries matched the scope", Map.of("scope", scope));
                    }
                    Map<String, String> options = McpEngineOptions.merge(
                            context.config(), engine, McpArgs.stringMap(args, "options"));
                    CommentScope commentScope = includeComments
                            ? CommentScope.of(workspace, DecompilerOptions.hash(options)) : null;
                    ExportConfig config = new ExportConfig(output, engine,
                            zip ? ExportConfig.Format.ZIP : ExportConfig.Format.DIR,
                            conflict, exportResources, options);
                    McpJob job = context.jobs().submit("EXPORT", session.id(), running -> {
                        long start = System.currentTimeMillis();
                        try {
                            WorkspaceClassBytecodeProvider provider =
                                    new WorkspaceClassBytecodeProvider(workspace);
                            ExportResult result = ExportService.exportAll(nodes, config, provider,
                                    commentScope,
                                    (path, percent) -> running.updateProgress(percent, 100, path),
                                    () -> running.isCancelled() || workspace.isClosed(),
                                    new WorkspaceExportCache(workspace, context.decompileCache()));
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("outputPath", output.toString());
                            data.put("format", zip ? "ZIP" : "DIR");
                            data.put("engine", engine.name());
                            data.put("totalFiles", result.totalFiles());
                            data.put("successCount", result.successCount());
                            data.put("failedCount", result.failedCount());
                            data.put("errors", result.errors().stream().limit(100).toList());
                            data.put("elapsedMs", System.currentTimeMillis() - start);
                            running.updateProgress(result.totalFiles(), result.totalFiles(),
                                    "export finished");
                            running.complete(data);
                        } catch (Exception e) {
                            throw new RuntimeException(e.getMessage() == null
                                    ? e.getClass().getSimpleName() : e.getMessage(), e);
                        }
                    });
                    return McpToolResult.ok(Map.of("jobId", job.id(), "state", job.state().name()));
                });
    }

    // 按导出范围选择文件树节点
    private static List<FileTreeNode> selectNodes(Workspace workspace, String scope,
                                                  String target, List<String> targets) {
        List<FileTreeNode> all = new ArrayList<>();
        collect(workspace.getTreeRoot(), all);
        if ("class".equals(scope)) {
            ClassResolver.ResolvedClass resolved = ClassResolver.resolve(workspace, target);
            return List.of(resolved.node());
        }
        if ("classes".equals(scope)) {
            if (targets.isEmpty()) {
                throw new McpToolException("INVALID_ARGUMENT",
                        "targets is required for classes scope", Map.of());
            }
            List<FileTreeNode> selected = new ArrayList<>();
            java.util.Set<String> paths = new java.util.LinkedHashSet<>();
            for (String item : targets) {
                ClassResolver.ResolvedClass resolved = ClassResolver.resolve(workspace, item);
                if (paths.add(resolved.fullPath())) {
                    selected.add(resolved.node());
                }
            }
            return selected;
        }
        if ("package".equals(scope)) {
            String prefix = target.replace('\\', '/').replace('.', '/');
            if (prefix.isBlank()) {
                throw new McpToolException("INVALID_ARGUMENT",
                        "target is required for package scope", Map.of());
            }
            String boundary = prefix.endsWith("/") ? prefix : prefix + "/";
            List<FileTreeNode> selected = new ArrayList<>();
            for (FileTreeNode node : all) {
                String path = node.getFullPath().replace('\\', '/');
                if (path.startsWith(boundary) || path.equals(prefix)) {
                    selected.add(node);
                }
            }
            return selected;
        }
        return all;
    }

    private static void collect(FileTreeModel model, List<FileTreeNode> result) {
        if (model == null) {
            return;
        }
        if (model.getValue() != null) {
            result.add(model.getValue());
        }
        for (FileTreeModel child : model.getChildren()) {
            collect(child, result);
        }
    }

    private static DecompilerTypeEnum parseEngine(String value) {
        try {
            return DecompilerTypeEnum.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new McpToolException("INVALID_ARGUMENT",
                    "Unknown engine: " + value, Map.of("engine", value));
        }
    }

    private static ExportConfig.ConflictPolicy parseConflict(String value) {
        try {
            return ExportConfig.ConflictPolicy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new McpToolException("INVALID_ARGUMENT",
                    "Unknown conflictPolicy: " + value, Map.of("conflictPolicy", value));
        }
    }
}
