package com.bingbihanji.fxdecomplie.ui.tree;

import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;

/**
 * 文件树单元格渲染器，根据节点类型显示不同的图标和样式。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class FileTreeCell extends TreeCell<FileTreeNode> {

    /** 节点图标标签 */
    private final Label icon = new Label();

    @Override
    protected void updateItem(FileTreeNode item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            icon.getStyleClass().clear();
        } else {
            String iconText = switch (item.getNodeType()) {
                case PACKAGE -> "PKG";
                case CLASS_FILE -> "CLS";
                case JAVA_FILE -> "JAVA";
                case RESOURCE -> "RES";
                case BINARY -> "BIN";
            };
            String iconStyle = switch (item.getNodeType()) {
                case PACKAGE -> "file-tree-icon-package";
                case CLASS_FILE -> "file-tree-icon-class";
                case JAVA_FILE -> "file-tree-icon-java";
                case RESOURCE -> "file-tree-icon-resource";
                case BINARY -> "file-tree-icon-binary";
            };
            icon.setText(iconText);
            icon.getStyleClass().setAll("file-tree-icon", iconStyle);
            setText(item.getName());
            setGraphic(icon);
        }
    }
}
