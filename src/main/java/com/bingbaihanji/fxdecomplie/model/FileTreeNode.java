package com.bingbaihanji.fxdecomplie.model;

import java.io.IOException;

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
    /** 缓存的文件字节，按需加载后保留，避免重复读取同一打开文件。 */
    private volatile byte[] cachedBytes;
    /** 懒加载字节来源，用于 JAR/ZIP/目录条目。 */
    private volatile ByteLoader byteLoader;
    /** 条目原始大小，未知时为 -1。 */
    private volatile long size = -1L;
    /** 可选资源清理回调，例如关闭归档句柄。 */
    private volatile Runnable cleanup;

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
    public synchronized void setCachedBytes(byte[] cachedBytes) {
        this.cachedBytes = cachedBytes;
        this.size = cachedBytes == null ? -1L : cachedBytes.length;
    }

    /** @param byteLoader 懒加载字节来源 */
    public void setByteLoader(ByteLoader byteLoader) {
        this.byteLoader = byteLoader;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setCleanup(Runnable cleanup) {
        this.cleanup = cleanup;
    }

    public void close() {
        Runnable action = cleanup;
        if (action != null) {
            cleanup = null;
            action.run();
        }
    }

    /** @return 当前节点是否存在可读取的字节来源 */
    public boolean hasByteSource() {
        return cachedBytes != null || byteLoader != null;
    }

    /**
     * 读取字节但不写入节点缓存。适合索引构建等批处理场景，避免预热阶段占用过多内存。
     */
    public byte[] readBytes() throws IOException {
        if (cachedBytes != null) {
            return cachedBytes;
        }
        return byteLoader == null ? null : byteLoader.load();
    }

    /**
     * 读取并缓存字节。适合打开单个文件、导出当前节点等用户显式操作。
     */
    public byte[] resolveBytes() throws IOException {
        byte[] current = cachedBytes;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (cachedBytes == null && byteLoader != null) {
                cachedBytes = byteLoader.load();
            }
            return cachedBytes;
        }
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

    @FunctionalInterface
    public interface ByteLoader {
        byte[] load() throws IOException;
    }
}
