package com.bingbihanji.fxdecomplie.model;

/**
 * Path node for a class file in the navigation chain.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class ClassPathNode extends AbstractPathNode<FileTreeNode> {
    public ClassPathNode(PathNode<?> parent, FileTreeNode node) {
        super(parent, node, FileTreeNode.class);
    }
}
