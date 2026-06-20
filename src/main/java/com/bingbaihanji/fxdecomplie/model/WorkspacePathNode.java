package com.bingbaihanji.fxdecomplie.model;

/**
 * 工作区路径节点导航路径链的根节点
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class WorkspacePathNode extends AbstractPathNode<Workspace> {
    public WorkspacePathNode(Workspace workspace) {
        super(null, workspace, Workspace.class);
    }
}
