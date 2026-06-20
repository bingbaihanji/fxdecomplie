package com.bingbaihanji.fxdecomplie.model;

/**
 * 导航链中的类文件路径节点
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class ClassPathNode extends AbstractPathNode<FileTreeNode> {
    public ClassPathNode(PathNode<?> parent, FileTreeNode node) {
        super(parent, node, FileTreeNode.class);
    }
}
