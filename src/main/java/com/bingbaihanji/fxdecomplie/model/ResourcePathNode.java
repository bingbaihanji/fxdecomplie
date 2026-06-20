package com.bingbaihanji.fxdecomplie.model;

/**
 * 导航链中的资源文件路径节点
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class ResourcePathNode extends AbstractPathNode<FileTreeNode> {
    public ResourcePathNode(PathNode<?> parent, FileTreeNode node) {
        super(parent, node, FileTreeNode.class);
    }
}
