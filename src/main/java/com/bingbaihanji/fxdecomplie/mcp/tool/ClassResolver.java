package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceSession;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 类/工作区解析:把工具参数中的类名解析为工作区中的具体 class.
 *
 * @author bingbaihanji
 */
public final class ClassResolver {

    private ClassResolver() {
        throw new AssertionError("utility class");
    }

    public static WorkspaceSession session(McpToolContext context, Map<String, Object> args) {
        String workspaceId = McpArgs.optionalString(args, "workspaceId", "");
        if (!workspaceId.isBlank()) {
            return context.sessions().find(workspaceId)
                    .orElseThrow(() -> new McpToolException("WORKSPACE_NOT_FOUND",
                            "Workspace not found: " + workspaceId, Map.of("workspaceId", workspaceId)));
        }
        return context.sessions().active()
                .orElseThrow(() -> new McpToolException("NO_ACTIVE_WORKSPACE",
                        "No active workspace; open one first", Map.of()));
    }

    public static ResolvedClass resolve(McpToolContext context, Map<String, Object> args) {
        WorkspaceSession session = session(context, args);
        Workspace workspace = session.workspace();
        if (workspace.isClosed()) {
            throw new McpToolException("WORKSPACE_CLOSED", "Workspace is closed",
                    Map.of("workspaceId", session.id()));
        }
        String name = McpArgs.requireString(args, "class");
        return resolve(workspace, name);
    }

    public static ResolvedClass resolve(Workspace workspace, String name) {
        String normalized = name.replace('\\', '/').strip();
        FileTreeNode direct = workspace.findNodeByPath(normalized);
        if (direct == null && !normalized.endsWith(".class")) {
            direct = workspace.findNodeByPath(normalized + ".class");
        }
        if (direct != null && direct.isClassFile()) {
            return toResolved(workspace, direct);
        }

        String internal = ClassNameUtil.normalizeInternalName(normalized);
        WorkspaceIndex index = workspace.getIndex();
        if (index != WorkspaceIndex.EMPTY) {
            ClassIndexEntry entry = index.findClass(internal);
            if (entry != null) {
                FileTreeNode node = workspace.findNodeByPath(entry.fullPath());
                if (node != null) {
                    return toResolved(workspace, node);
                }
            }
        }

        List<FileTreeNode> matches = new ArrayList<>();
        String displayWorkspaceHash = CommentScope.workspaceHash(workspace);
        for (String original : RenameService.originalInternalNameCandidates(name,
                displayWorkspaceHash)) {
            FileTreeNode node = workspace.findNodeByPath(original + ".class");
            if (node != null && node.isClassFile()) {
                matches.add(node);
            }
        }
        for (FileTreeNode node : classNodes(workspace)) {
            String pathInternal = ClassNameUtil.normalizeInternalName(node.getFullPath());
            String simple = pathInternal.substring(pathInternal.lastIndexOf('/') + 1);
            if (pathInternal.equals(internal)
                    || simple.equals(name)
                    || simple.equals(internal)) {
                if (!matches.contains(node)) {
                    matches.add(node);
                }
            }
        }
        if (matches.isEmpty()) {
            throw new McpToolException("CLASS_NOT_FOUND",
                    "Class not found: " + name, Map.of("class", name));
        }
        if (matches.size() > 1) {
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (FileTreeNode node : matches.subList(0, Math.min(10, matches.size()))) {
                candidates.add(Map.of(
                        "fullPath", node.getFullPath(),
                        "internalName", ClassNameUtil.normalizeInternalName(node.getFullPath())));
            }
            throw new McpToolException("AMBIGUOUS_CLASS",
                    "Class name is ambiguous: " + name,
                    Map.of("class", name, "candidates", candidates, "matchCount", matches.size()));
        }
        return toResolved(workspace, matches.get(0));
    }

    private static ResolvedClass toResolved(Workspace workspace, FileTreeNode node) {
        String internal = ClassNameUtil.normalizeInternalName(node.getFullPath());
        String display = RenameService.displayClassName(internal, CommentScope.workspaceHash(workspace));
        return new ResolvedClass(workspace, node, node.getFullPath(), internal, display);
    }

    /** 遍历文件树收集 class 节点;不读取字节. */
    public static List<FileTreeNode> classNodes(Workspace workspace) {
        List<FileTreeNode> result = new ArrayList<>();
        collect(workspace.getTreeRoot(), result);
        return result;
    }

    private static void collect(FileTreeModel model, List<FileTreeNode> result) {
        if (model == null) {
            return;
        }
        FileTreeNode node = model.getValue();
        if (node != null && node.isClassFile()) {
            result.add(node);
        }
        for (FileTreeModel child : model.getChildren()) {
            collect(child, result);
        }
    }

    /** 类路径候选(用于错误信息与列表过滤). */
    public static List<Map<String, Object>> toItems(List<FileTreeNode> nodes, Workspace workspace) {
        List<Map<String, Object>> items = new ArrayList<>();
        String hash = CommentScope.workspaceHash(workspace);
        for (FileTreeNode node : nodes) {
            String internal = ClassNameUtil.normalizeInternalName(node.getFullPath());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fullPath", node.getFullPath());
            item.put("internalName", internal);
            item.put("displayName", RenameService.displayClassName(internal, hash));
            item.put("package", internal.lastIndexOf('/') < 0
                    ? "" : internal.substring(0, internal.lastIndexOf('/')));
            items.add(item);
        }
        return items;
    }

    public record ResolvedClass(Workspace workspace, FileTreeNode node,
                                String fullPath, String internalName, String displayName) {
    }
}
