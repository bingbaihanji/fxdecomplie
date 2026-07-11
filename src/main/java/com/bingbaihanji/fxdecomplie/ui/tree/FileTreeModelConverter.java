package com.bingbaihanji.fxdecomplie.ui.tree;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeItem;

/** 将 model 层的 FileTreeModel POJO 转换为 JavaFX TreeItem（UI 边界） */
public final class FileTreeModelConverter {

    private FileTreeModelConverter() {
        throw new AssertionError("utility class");
    }

    public static TreeItem<FileTreeNode> toTreeItem(FileTreeModel model) {
        if (model == null) {
            return null;
        }
        TreeItem<FileTreeNode> item = new TreeItem<>(model.getValue());
        item.setExpanded(model.isExpanded());
        for (FileTreeModel child : model.getChildren()) {
            item.getChildren().add(toTreeItem(child));
        }
        return item;
    }
}
