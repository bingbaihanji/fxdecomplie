package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件树构建器将扁平的类条目列表转换为带包层级的树结构
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class FileTreeBuilder {

    private static final Logger logger = LoggerFactory.getLogger(FileTreeBuilder.class);

    private FileTreeBuilder() {
        throw new AssertionError("utility class");
    }

    /**
     * 构建文件树
     *
     * @param rootName 根节点名称(如 "demo.jar")
     * @param entries  扁平条目列表
     * @return 树根节点
     */
    public static FileTreeModel build(String rootName, List<ClassDiscoverer.ClassEntry> entries) {
        FileTreeNode rootData = new FileTreeNode(rootName, "", FileTreeNode.NodeTypeEnum.PACKAGE);
        FileTreeModel root = new FileTreeModel(rootData);
        root.setExpanded(true);

        Map<String, FileTreeModel> pathMap = new HashMap<>();
        pathMap.put("", root);

        for (ClassDiscoverer.ClassEntry entry : entries) {
            FileTreeNode nodeData = new FileTreeNode(entry.name(), entry.fullPath(), entry.nodeType());
            nodeData.setSize(entry.size());
            if (entry.bytes() != null) {
                nodeData.setCachedBytes(entry.bytes());
            }
            if (entry.byteLoader() != null) {
                nodeData.setByteLoader(entry.byteLoader());
            }
            if (entry.cleanup() != null) {
                nodeData.setCleanup(entry.cleanup());
            }

            FileTreeModel parent = getOrCreateParent(root, entry.fullPath(), pathMap);
            FileTreeModel child = new FileTreeModel(nodeData);
            parent.getChildren().add(child);
        }

        sortTree(root);
        logger.debug("文件树构建完成: {} (根节点={}, 条目={})", rootName,
                countNodes(root), entries.size());
        return root;
    }

    private static int countNodes(FileTreeModel node) {
        int count = 1;
        for (FileTreeModel child : node.getChildren()) {
            count += countNodes(child);
        }
        return count;
    }

    /** 获取或创建父节点链,返回目标条目的直接父节点 */
    private static FileTreeModel getOrCreateParent(
            FileTreeModel root, String fullPath,
            Map<String, FileTreeModel> pathMap) {
        int lastSlash = fullPath.lastIndexOf('/');
        if (lastSlash < 0) {
            return root;
        }

        String parentPath = fullPath.substring(0, lastSlash);
        String[] parts = parentPath.split("/");

        FileTreeModel current = root;
        StringBuilder pathBuilder = new StringBuilder();

        for (String part : parts) {
            if (!pathBuilder.isEmpty()) {
                pathBuilder.append('/');
            }
            pathBuilder.append(part);
            String currentPath = pathBuilder.toString();

            final FileTreeModel parent = current;
            current = pathMap.computeIfAbsent(currentPath, k -> {
                FileTreeNode data = new FileTreeNode(part, k, FileTreeNode.NodeTypeEnum.PACKAGE);
                FileTreeModel node = new FileTreeModel(data);
                node.setExpanded(false);
                parent.getChildren().add(node);
                return node;
            });
        }

        return current;
    }

    /** 递归排序树节点(包在前,文件在后) */
    private static void sortTree(FileTreeModel root) {
        java.util.ArrayDeque<FileTreeModel> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            FileTreeModel node = queue.poll();
            node.getChildren().sort((a, b) -> {
                FileTreeNode aData = a.getValue();
                FileTreeNode bData = b.getValue();
                boolean aDir = aData.getNodeType() == FileTreeNode.NodeTypeEnum.PACKAGE;
                boolean bDir = bData.getNodeType() == FileTreeNode.NodeTypeEnum.PACKAGE;
                if (aDir != bDir) {
                    return aDir ? -1 : 1;
                }
                return aData.getName().compareToIgnoreCase(bData.getName());
            });
            queue.addAll(node.getChildren());
        }
    }
}
