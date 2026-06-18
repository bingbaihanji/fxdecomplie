package com.bingbihanji.fxdecomplie.model;

/**
 * Path node for a resource file in the navigation chain.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class ResourcePathNode extends AbstractPathNode<FileTreeNode> {
    public ResourcePathNode(PathNode<?> parent, FileTreeNode node) {
        super(parent, node, FileTreeNode.class);
    }
}
