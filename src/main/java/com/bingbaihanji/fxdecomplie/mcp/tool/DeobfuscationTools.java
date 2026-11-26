package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.mcp.job.McpJob;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.rename.AutoDeobfuscator;
import com.bingbaihanji.fxdecomplie.rename.RenameEntry;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.service.WorkspaceIndexService;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 反混淆建议扫描(只读,不应用重命名).
 *
 * @author bingbaihanji
 */
public final class DeobfuscationTools {

    private static final int MAX_SUGGESTIONS = 5000;

    private DeobfuscationTools() {
        throw new AssertionError("utility class");
    }

    public static List<McpToolDefinition> definitions() {
        return List.of(startScan());
    }

    private static McpToolDefinition startScan() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "includeMembers", Map.of("type", "boolean")));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "start_deobfuscation_scan",
                "Start an asynchronous read-only scan for obfuscated class/member names. Suggestions are not applied.",
                schema,
                (context, args) -> {
                    WorkspaceSession session = ClassResolver.session(context, args);
                    Workspace workspace = session.workspace();
                    boolean includeMembers = McpArgs.booleanValue(args, "includeMembers", true);
                    McpJob job = context.jobs().submit("DEOBFUSCATION_SCAN", session.id(), running -> {
                        running.updateProgress(0, 1, "scanning names");
                        WorkspaceIndex index = workspace.getIndex();
                        if (includeMembers && index == WorkspaceIndex.EMPTY) {
                            WorkspaceIndexService.ensureIndexingStarted(workspace);
                            try {
                                index = workspace.getIndexFuture().get(30, TimeUnit.SECONDS);
                            } catch (Exception ignored) {
                                index = WorkspaceIndex.EMPTY;
                            }
                        }
                        List<FileTreeNode> nodes = ClassResolver.classNodes(workspace);
                        WorkspaceIndex scanIndex = includeMembers ? index : WorkspaceIndex.EMPTY;
                        List<RenameEntry> suggestions = AutoDeobfuscator.scan(nodes, scanIndex);
                        int total = suggestions.size();
                        List<Map<String, Object>> items = new ArrayList<>();
                        for (RenameEntry entry : suggestions.subList(0,
                                Math.min(MAX_SUGGESTIONS, total))) {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("type", entry.type());
                            item.put("className", entry.className());
                            item.put("oldName", entry.oldName());
                            item.put("newName", entry.newName());
                            item.put("descriptor", entry.desc());
                            items.add(item);
                        }
                        String workspaceHash = CommentScope.workspaceHash(workspace);
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("items", items);
                        data.put("total", total);
                        data.put("truncated", total > MAX_SUGGESTIONS);
                        data.put("existingMappings", RenameService.loadAll(workspaceHash).size());
                        data.put("indexReady", workspace.isIndexReady());
                        data.put("applied", false);
                        running.updateProgress(1, 1, "scan finished");
                        running.complete(data);
                    });
                    return McpToolResult.ok(Map.of("jobId", job.id(), "state", job.state().name()));
                });
    }
}
