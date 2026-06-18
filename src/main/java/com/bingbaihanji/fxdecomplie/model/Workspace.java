package com.bingbaihanji.fxdecomplie.model;

import javafx.scene.control.TreeItem;

import java.io.File;
import java.util.Objects;

/**
 * 工作区数据模型。表示一个已打开的JAR/ZIP/目录，包含名称、源文件、树根节点和是否为归档文件。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class Workspace {

    /** 工作区显示名称（如 demo.jar） */
    private final String name;
    /** 源文件或目录 */
    private final File sourceFile;
    /** 文件树根节点 */
    private final TreeItem<FileTreeNode> treeRoot;
    /** 是否为归档文件（JAR/ZIP） */
    private final boolean isArchive;
    /** 工作区索引，用于全局搜索、字节码搜索和后续分析 */
    private volatile WorkspaceIndex index;

    /**
     * 构造工作区。
     *
     * @param name       显示名称
     * @param sourceFile 源文件
     * @param treeRoot   文件树根节点
     * @param isArchive  是否为归档文件
     */
    public Workspace(String name, File sourceFile, TreeItem<FileTreeNode> treeRoot, boolean isArchive) {
        this(name, sourceFile, treeRoot, isArchive, WorkspaceIndex.build(treeRoot));
    }

    public Workspace(String name, File sourceFile, TreeItem<FileTreeNode> treeRoot,
                     boolean isArchive, WorkspaceIndex index) {
        this.name = name;
        this.sourceFile = sourceFile;
        this.treeRoot = treeRoot;
        this.isArchive = isArchive;
        this.index = index == null ? WorkspaceIndex.EMPTY : index;
    }

    /**
     * 获取完整索引；如果异步索引还未完成，则在当前线程上构建一次。
     *
     * @return 可用于搜索、导出和导航分析的完整工作区索引
     */
    public WorkspaceIndex getOrBuildIndex() {
        WorkspaceIndex current = index;
        if (current != WorkspaceIndex.EMPTY) {
            return current;
        }
        synchronized (this) {
            if (index == WorkspaceIndex.EMPTY) {
                index = WorkspaceIndex.build(treeRoot);
            }
            return index;
        }
    }

    /** @return 显示名称 */
    public String getName() {
        return name;
    }

    /** @return 源文件 */
    public File getSourceFile() {
        return sourceFile;
    }

    /** @return 文件树根节点 */
    public TreeItem<FileTreeNode> getTreeRoot() {
        return treeRoot;
    }

    /** @return 是否为归档文件 */
    public boolean isArchive() {
        return isArchive;
    }

    /** @return 工作区索引 */
    public WorkspaceIndex getIndex() {
        return index;
    }

    /** 更新工作区索引（用于异步构建完成后替换） */
    public void setIndex(WorkspaceIndex index) {
        this.index = index == null ? WorkspaceIndex.EMPTY : index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Workspace other)) return false;
        return Objects.equals(sourceFile, other.sourceFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFile);
    }

    @Override
    public String toString() {
        return "Workspace{name='" + name + "', source=" + sourceFile + "}";
    }
}
