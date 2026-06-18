package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.*;
import javafx.scene.control.TabPane;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;

/**
 * Minimal path-based navigation service with linear back/forward history.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class NavigationService {

    /** Backward navigation history (LIFO stack) */
    private final Deque<PathNode<?>> backStack = new ArrayDeque<>();
    /** Forward navigation history (cleared on new navigation) */
    private final Deque<PathNode<?>> forwardStack = new ArrayDeque<>();
    /** Currently active navigation path */
    private PathNode<?> currentPath;

    /** @return a path node chain wrapping a workspace and class file for navigation tracking */
    public PathNode<?> classPath(Workspace workspace, FileTreeNode node) {
        return new ClassPathNode(new WorkspacePathNode(workspace), node);
    }

    /** @return a path node chain wrapping a workspace and resource file for navigation tracking */
    public PathNode<?> resourcePath(Workspace workspace, FileTreeNode node) {
        return new ResourcePathNode(new WorkspacePathNode(workspace), node);
    }

    /**
     * Navigate to a path node, recording the current position in history.
     *
     * @param path         target path node
     * @param workspace    current workspace
     * @param codeTabPane  code tab container
     * @param classOpener  callback to open a class file
     * @param resourceOpener callback to open a resource file
     */
    public void openPath(PathNode<?> path, Workspace workspace, TabPane codeTabPane,
                         BiConsumer<FileTreeNode, TabPane> classOpener,
                         BiConsumer<FileTreeNode, TabPane> resourceOpener) {
        openPath(path, workspace, codeTabPane, classOpener, resourceOpener, true);
    }

    /**
     * Navigate backward in history.
     */
    public void goBack(Workspace workspace, TabPane codeTabPane,
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
     * Navigate forward in history.
     */
    public void goForward(Workspace workspace, TabPane codeTabPane,
                          BiConsumer<FileTreeNode, TabPane> classOpener,
                          BiConsumer<FileTreeNode, TabPane> resourceOpener) {
        if (forwardStack.isEmpty() || currentPath == null) {
            return;
        }
        backStack.push(currentPath);
        PathNode<?> target = forwardStack.pop();
        openPath(target, workspace, codeTabPane, classOpener, resourceOpener, false);
    }

    /** @return true if there is a previous path in the back stack */
    public boolean canGoBack() {
        return !backStack.isEmpty();
    }

    /** @return true if there is a next path in the forward stack */
    public boolean canGoForward() {
        return !forwardStack.isEmpty();
    }

    private void openPath(PathNode<?> path, Workspace workspace, TabPane codeTabPane,
                          BiConsumer<FileTreeNode, TabPane> classOpener,
                          BiConsumer<FileTreeNode, TabPane> resourceOpener,
                          boolean recordHistory) {
        // ---- Path resolution: extract FileTreeNode from the path chain ----
        FileTreeNode node = path.getValueOfType(FileTreeNode.class);
        if (node == null) {
            return;
        }
        // ---- History recording: push current position to backStack, clear forward ----
        if (recordHistory && currentPath != null) {
            backStack.push(currentPath);
            forwardStack.clear();
            // Limit history to prevent unbounded memory growth
            while (backStack.size() > 100) {
                backStack.removeLast();
            }
        }
        currentPath = path;
        // ---- Dispatch: route to class opener or resource opener based on node type ----
        if (node.isClassFile()) {
            classOpener.accept(node, codeTabPane);
        } else if (node.isTextFile()) {
            resourceOpener.accept(node, codeTabPane);
        }
    }
}
