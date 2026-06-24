package com.bingbaihanji.fxdecomplie.ui.tree;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/**
 * 文件树视图组件,展示反编译项目的包和类层级结构
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class FileTreeView extends TreeView<FileTreeNode> {

    /**
     * @param root 文件树根节点
     */
    public FileTreeView(TreeItem<FileTreeNode> root) {
        super(root);
        setShowRoot(true);
        getStyleClass().add("file-tree-view");
        setCellFactory(tv -> new FileTreeCell());
        // Cell factory 可在外部由 WorkspaceTabManager.installTreeContextMenu 覆盖(含右键菜单绑定)
    }
}
