package com.bingbaihanji.fxdecomplie.model;

import javafx.scene.control.TreeItem;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工作区数据模型表示一个已打开的JAR/ZIP/目录,包含名称、源文件、树根节点和是否为归档文件
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class Workspace implements AutoCloseable {

    /** 工作区显示名称(如 demo.jar) */
    private final String name;
    /** 源文件或目录 */
    private final File sourceFile;
    /** 文件树根节点 */
    private final TreeItem<FileTreeNode> treeRoot;
    /** 是否为归档文件(JAR/ZIP) */
    private final boolean isArchive;
    /** 完整索引是否已经被显式请求构建 */
    private final AtomicBoolean indexBuildStarted = new AtomicBoolean();
    /** 工作区级源码搜索缓存,按引擎和选项分组 */
    private final ConcurrentMap<String, Map<String, String>> sourceSearchCaches = new ConcurrentHashMap<>();
    /** 工作区索引,用于全局搜索、字节码搜索和后续分析 */
    private volatile WorkspaceIndex index;
    /** 异步索引构建结果,供 UI 等待,避免在 JavaFX 线程兜底同步构建 */
    private volatile CompletableFuture<WorkspaceIndex> indexFuture = new CompletableFuture<>();
    /** 轻量路径索引,只保存文件树节点引用,不读取 class 字节 */
    private volatile Map<String, FileTreeNode> nodesByFullPath;

    /**
     * 构造工作区
     *
     * @param name       显示名称
     * @param sourceFile 源文件
     * @param treeRoot   文件树根节点
     * @param isArchive  是否为归档文件
     */
    public Workspace(String name, File sourceFile, TreeItem<FileTreeNode> treeRoot, boolean isArchive) {
        this(name, sourceFile, treeRoot, isArchive, WorkspaceIndex.EMPTY);
    }

    public Workspace(String name, File sourceFile, TreeItem<FileTreeNode> treeRoot,
                     boolean isArchive, WorkspaceIndex index) {
        this.name = Objects.requireNonNull(name, "name");
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.treeRoot = Objects.requireNonNull(treeRoot, "treeRoot");
        this.isArchive = isArchive;
        this.index = index == null ? WorkspaceIndex.EMPTY : index;
        if (this.index != WorkspaceIndex.EMPTY) {
            indexFuture.complete(this.index);
        }
    }

    private static Map<String, FileTreeNode> buildPathIndex(TreeItem<FileTreeNode> root) {
        if (root == null) {
            return Map.of();
        }
        Map<String, FileTreeNode> result = new LinkedHashMap<>();
        ArrayDeque<TreeItem<FileTreeNode>> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TreeItem<FileTreeNode> item = queue.removeFirst();
            FileTreeNode node = item.getValue();
            if (node != null && node.getFullPath() != null && !node.getFullPath().isBlank()) {
                result.putIfAbsent(node.getFullPath().replace('\\', '/'), node);
            }
            queue.addAll(item.getChildren());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取完整索引；如果异步索引还未完成,则在当前线程上构建一次
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
                setIndex(WorkspaceIndex.build(treeRoot));
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

    /** 更新工作区索引(用于异步构建完成后替换) */
    public synchronized void setIndex(WorkspaceIndex index) {
        WorkspaceIndex next = index == null ? WorkspaceIndex.EMPTY : index;
        this.index = next;
        if (next != WorkspaceIndex.EMPTY) {
            if (indexFuture.isDone()) {
                indexFuture = CompletableFuture.completedFuture(next);
            } else {
                indexFuture.complete(next);
            }
            indexBuildStarted.set(false);
        }
    }

    public CompletableFuture<WorkspaceIndex> getIndexFuture() {
        return indexFuture;
    }

    public boolean isIndexReady() {
        return index != WorkspaceIndex.EMPTY;
    }

    public boolean isIndexBuildStarted() {
        return indexBuildStarted.get();
    }

    public synchronized boolean markIndexBuildStarted() {
        if (index != WorkspaceIndex.EMPTY || indexBuildStarted.get()) {
            return false;
        }
        if (indexFuture.isDone()) {
            indexFuture = new CompletableFuture<>();
        }
        return indexBuildStarted.compareAndSet(false, true);
    }

    /**
     * 按完整路径定位文件树节点该索引只遍历树结构,不读取文件内容,
     * 用于反编译器依赖解析和 UI 导航,避免每次查找都扫描整棵树
     */
    public FileTreeNode findNodeByPath(String fullPath) {
        if (fullPath == null || fullPath.isBlank()) {
            return null;
        }
        return pathIndex().get(fullPath.replace('\\', '/'));
    }

    private Map<String, FileTreeNode> pathIndex() {
        Map<String, FileTreeNode> current = nodesByFullPath;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (nodesByFullPath == null) {
                nodesByFullPath = buildPathIndex(treeRoot);
            }
            return nodesByFullPath;
        }
    }

    /** 标记异步索引构建失败 */
    public synchronized void failIndex(Throwable error) {
        if (!indexFuture.isDone()) {
            indexFuture.completeExceptionally(error);
        }
        indexBuildStarted.set(false);
    }

    public Map<String, String> getSourceSearchCache(String key) {
        return sourceSearchCaches.get(key);
    }

    public void putSourceSearchCache(String key, Map<String, String> cache) {
        if (key != null && cache != null) {
            sourceSearchCaches.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(cache)));
        }
    }

    @Override
    public void close() {
        if (treeRoot == null) {
            return;
        }
        sourceSearchCaches.clear();
        ArrayDeque<TreeItem<FileTreeNode>> queue = new ArrayDeque<>();
        queue.add(treeRoot);
        while (!queue.isEmpty()) {
            TreeItem<FileTreeNode> item = queue.removeFirst();
            FileTreeNode node = item.getValue();
            if (node != null) {
                node.close();
            }
            queue.addAll(item.getChildren());
        }
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
