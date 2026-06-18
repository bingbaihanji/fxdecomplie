package com.bingbaihanji.fxdecomplie.model;

/**
 * 文件树节点数据模型。表示文件树中的单个节点，可以是包、类文件、Java源文件等。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class FileTreeNode {

    /** 节点显示名称 */
    private final String name;
    /** 完整内部路径（如 "com/example/Main.class"） */
    private final String fullPath;
    /** 节点类型 */
    private final NodeTypeEnum nodeType;
    /** 缓存的字节码，仅 class 文件有效 */
    private byte[] cachedBytes;

    /**
     * 构造文件树节点。
     *
     * @param name     节点显示名称
     * @param fullPath 完整内部路径
     * @param nodeType 节点类型
     */
    public FileTreeNode(String name, String fullPath, NodeTypeEnum nodeType) {
        this.name = name;
        this.fullPath = fullPath;
        this.nodeType = nodeType;
    }

    /** @return 节点显示名称 */
    public String getName() {
        return name;
    }

    /** @return 完整内部路径 */
    public String getFullPath() {
        return fullPath;
    }

    /** @return 节点类型 */
    public NodeTypeEnum getNodeType() {
        return nodeType;
    }

    /** @return 缓存的字节码，可能为 null */
    public byte[] getCachedBytes() {
        return cachedBytes;
    }

    /** @param cachedBytes 缓存的字节码 */
    public void setCachedBytes(byte[] cachedBytes) {
        this.cachedBytes = cachedBytes;
    }

    /** @return 是否为 class 文件 */
    public boolean isClassFile() {
        return nodeType == NodeTypeEnum.CLASS_FILE;
    }

    /** @return 是否为可查看的文本文件（资源文件或 Java 源文件） */
    public boolean isTextFile() {
        return nodeType == NodeTypeEnum.RESOURCE || nodeType == NodeTypeEnum.JAVA_FILE;
    }

    @Override
    public String toString() {
        return "FileTreeNode{name='" + name + "', type=" + nodeType + ", path='" + fullPath + "'}";
    }

    public enum NodeTypeEnum {
        /** 包节点（中间层级） */
        PACKAGE,
        /** .class 文件 */
        CLASS_FILE,
        /** .java 源文件 */
        JAVA_FILE,
        /** 其他资源文件 */
        RESOURCE,
        /** 二进制文件 */
        BINARY
    }
}
