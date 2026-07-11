package com.bingbaihanji.fxdecomplie.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 纯 POJO 文件树节点，替代 javafx TreeItem 用于 model/service 层，
 * 使这两层不再依赖 JavaFX。UI 层通过转换器将其构建为 TreeItem。
 */
public final class FileTreeModel {
    private final FileTreeNode value;
    private final List<FileTreeModel> children = new ArrayList<>();
    private boolean expanded;

    public FileTreeModel(FileTreeNode value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public FileTreeNode getValue() { return value; }
    public List<FileTreeModel> getChildren() { return children; }
    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    @Override
    public String toString() {
        return "FileTreeModel{value=" + value + ", children=" + children.size() + "}";
    }
}
