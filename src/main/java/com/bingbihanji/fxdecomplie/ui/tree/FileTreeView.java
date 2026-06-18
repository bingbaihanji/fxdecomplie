package com.bingbihanji.fxdecomplie.ui.tree;

import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/**
 * 文件树视图组件，展示反编译项目的包和类层级结构。
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
        // Cell factory set by WorkspaceTabManager.installTreeContextMenu (includes context menu wiring)
        setCellFactory(tv -> new FileTreeCell());
    }
}
