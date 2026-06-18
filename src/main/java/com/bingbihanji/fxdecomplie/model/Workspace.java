package com.bingbihanji.fxdecomplie.model;

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

    /**
     * 构造工作区。
     *
     * @param name       显示名称
     * @param sourceFile 源文件
     * @param treeRoot   文件树根节点
     * @param isArchive  是否为归档文件
     */
    public Workspace(String name, File sourceFile, TreeItem<FileTreeNode> treeRoot, boolean isArchive) {
        this.name = name;
        this.sourceFile = sourceFile;
        this.treeRoot = treeRoot;
        this.isArchive = isArchive;
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
