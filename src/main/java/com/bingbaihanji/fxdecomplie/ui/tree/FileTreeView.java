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

    private static final double FIXED_CELL_SIZE = 24.0;

    /** 创建文件树视图 */
    public FileTreeView(TreeItem<FileTreeNode> root) {
        super(root);
        setShowRoot(true);
        getStyleClass().add("file-tree-view");
        setFixedCellSize(FIXED_CELL_SIZE);
        setCellFactory(tv -> new FileTreeCell());
    }

    /**
     * 刷新所有 TreeCell 的显示文本
     * 延迟到下一 pulse(此时 cells 已在 scene graph 中),然后调用每个
     * FileTreeCell.refreshDisplay() 触发 updateItem 获取最新显示名
     * 若 lookUp 未找到 cells,回退到 setRoot 重建方案
     */
    public void refreshVisibleCells() {
        javafx.application.Platform.runLater(() -> {
            boolean any = false;
            for (var node : lookupAll(".tree-cell")) {
                if (node instanceof FileTreeCell cell) {
                    cell.refreshDisplay();
                    any = true;
                }
            }
            if (!any) {
                // cells 不在 scene graph 中,使用 setRoot 强制重建
                TreeItem<FileTreeNode> r = getRoot();
                if (r != null) {
                    setRoot(null);
                    setRoot(r);
                }
            }
        });
    }
}
