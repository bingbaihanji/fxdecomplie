package com.bingbaihanji.fxdecomplie.model;

/**
 * Path node for a workspace. Root of a navigation path chain.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class WorkspacePathNode extends AbstractPathNode<Workspace> {
    public WorkspacePathNode(Workspace workspace) {
        super(null, workspace, Workspace.class);
    }
}
