package com.bingbaihanji.fxdecomplie.model;

/**
 * 导航链中的类文件路径节点
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class ClassPathNode extends AbstractPathNode<FileTreeNode> {
    /**
     * 构造类路径节点
     *
     * @param parent 父节点(可为 null,表示根节点)
     * @param node   对应的文件树节点
     */
    public ClassPathNode(PathNode<?> parent, FileTreeNode node) {
        super(parent, node, FileTreeNode.class);
    }
}
