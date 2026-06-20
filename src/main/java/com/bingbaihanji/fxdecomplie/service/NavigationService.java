package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.*;
import javafx.scene.control.TabPane;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;

/**
 * 基于路径的最小导航服务,支持线性的前进/后退历史记录
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class NavigationService {

    /** 后退导航历史(LIFO 栈) */
    private final Deque<PathNode<?>> backStack = new ArrayDeque<>();
    /** 前进导航历史(新导航时清空) */
    private final Deque<PathNode<?>> forwardStack = new ArrayDeque<>();
    /** 当前活跃的导航路径 */
    private PathNode<?> currentPath;

    /** @return 包装工作区和类文件的路径节点链,用于导航跟踪 */
    public PathNode<?> classPath(Workspace workspace, FileTreeNode node) {
        return new ClassPathNode(new WorkspacePathNode(workspace), node);
    }

    /** @return 包装工作区和资源文件的路径节点链,用于导航跟踪 */
    public PathNode<?> resourcePath(Workspace workspace, FileTreeNode node) {
        return new ResourcePathNode(new WorkspacePathNode(workspace), node);
    }

    /**
     * 导航到路径节点,记录当前在历史中的位置
     *
     * @param path          目标路径节点
     * @param workspace     当前工作区
     * @param codeTabPane   代码标签页容器
     * @param classOpener   打开类文件的回调
     * @param resourceOpener 打开资源文件的回调
     */
    public synchronized void openPath(PathNode<?> path, Workspace workspace, TabPane codeTabPane,
                         BiConsumer<FileTreeNode, TabPane> classOpener,
                         BiConsumer<FileTreeNode, TabPane> resourceOpener) {
        openPath(path, workspace, codeTabPane, classOpener, resourceOpener, true);
    }

    /**
     * 在历史中向后导航
     */
    public synchronized void goBack(Workspace workspace, TabPane codeTabPane,
                       BiConsumer<FileTreeNode, TabPane> classOpener,
                       BiConsumer<FileTreeNode, TabPane> resourceOpener) {
        if (backStack.isEmpty() || currentPath == null) {
            return;
        }
        forwardStack.push(currentPath);
        PathNode<?> target = backStack.pop();
        openPath(target, workspace, codeTabPane, classOpener, resourceOpener, false);
    }

    /**
     * 在历史中向前导航
     */
    public synchronized void goForward(Workspace workspace, TabPane codeTabPane,
                          BiConsumer<FileTreeNode, TabPane> classOpener,
                          BiConsumer<FileTreeNode, TabPane> resourceOpener) {
        if (forwardStack.isEmpty() || currentPath == null) {
            return;
        }
        backStack.push(currentPath);
        PathNode<?> target = forwardStack.pop();
        openPath(target, workspace, codeTabPane, classOpener, resourceOpener, false);
    }

    /** @return 如果后退栈中存在上一个路径则返回 true */
    public boolean canGoBack() {
        return !backStack.isEmpty();
    }

    /** @return 如果前进栈中存在下一个路径则返回 true */
    public boolean canGoForward() {
        return !forwardStack.isEmpty();
    }

    private void openPath(PathNode<?> path, Workspace workspace, TabPane codeTabPane,
                          BiConsumer<FileTreeNode, TabPane> classOpener,
                          BiConsumer<FileTreeNode, TabPane> resourceOpener,
                          boolean recordHistory) {
        // ---- 路径解析: 从路径链中提取 FileTreeNode ----
        FileTreeNode node = path.getValueOfType(FileTreeNode.class);
        if (node == null) {
            return;
        }
        // ---- 历史记录: 将当前位置推入 backStack,清空 forward ----
        if (recordHistory && currentPath != null) {
            backStack.push(currentPath);
            forwardStack.clear();
            // 限制历史记录防止无限内存增长
            while (backStack.size() > 100) {
                backStack.removeLast();
            }
        }
        currentPath = path;
        // ---- 分发: 根据节点类型路由到类打开器或资源打开器 ----
        if (node.isClassFile()) {
            classOpener.accept(node, codeTabPane);
        } else if (node.isTextFile()) {
            resourceOpener.accept(node, codeTabPane);
        }
    }
}
