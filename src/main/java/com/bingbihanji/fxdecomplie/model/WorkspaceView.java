package com.bingbihanji.fxdecomplie.model;

import com.bingbihanji.fxdecomplie.ui.tree.FileTreeView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * 工作区视图，关联 workspace、文件树、代码标签面板和工作区标签页。
 *
 * @param workspace     工作区
 * @param treeView      文件树视图
 * @param codeTabPane   代码标签页面板
 * @param workspaceTab  工作区标签页
 */
public record WorkspaceView(
        Workspace workspace,
        FileTreeView treeView,
        TabPane codeTabPane,
        Tab workspaceTab
) {
}
