package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 导出计划器 — 负责收集和过滤可导出的文件节点。
 *
 * <p>从 {@link ExportService} 中提取的规划层，将"决定导出什么"与"如何写入"分离。</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
final class ExportPlanner {

    private ExportPlanner() {
        throw new AssertionError("utility class");
    }

    /** 收集工作区树中所有可导出的节点 */
    static List<FileTreeNode> collectExportableNodes(FileTreeModel root,
                                                      boolean exportResources) {
        List<FileTreeNode> nodes = new ArrayList<>();
        collectExportableNodes(root, exportResources, nodes);
        return nodes;
    }

    private static void collectExportableNodes(FileTreeModel item,
                                                boolean exportResources,
                                                List<FileTreeNode> nodes) {
        FileTreeNode data = item.getValue();
        if (data != null && shouldExport(data, exportResources)) {
            nodes.add(data);
        }
        for (FileTreeModel child : item.getChildren()) {
            collectExportableNodes(child, exportResources, nodes);
        }
    }

    /** 判断节点是否应被导出：class 文件总是导出，资源文件仅在启用时导出 */
    static boolean shouldExport(FileTreeNode data, boolean exportResources) {
        return data.isClassFile()
                || (exportResources && (data.getNodeType() == FileTreeNode.NodeTypeEnum.RESOURCE
                || data.getNodeType() == FileTreeNode.NodeTypeEnum.JAVA_FILE));
    }
}
