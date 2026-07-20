package com.bingbaihanji.fxdecomplie.ui;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.service.NavigationService;
import com.bingbaihanji.fxdecomplie.ui.code.SplitEditorPane;
import com.bingbaihanji.fxdecomplie.ui.tree.FileTreeView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * 工作区视图,关联 workspace 文件树 分屏编辑器和工作区标签页
 *
 * <p>使用 Java record 不可变数据载体,通过 {@link #codeTabPane()} 向后兼容
 * 返回主 TabPane(cell 0)</p>
 *
 * @param workspace         工作区
 * @param treeView          文件树视图
 * @param splitEditorPane   多分屏编辑器(含 1-3 个 TabPane)
 * @param workspaceTab      工作区标签页
 * @param navigationService 导航服务(前进/后退历史)
 * @author bingbaihanji
 * @date 2026-07-07
 */
public record WorkspaceView(
        Workspace workspace,
        FileTreeView treeView,
        SplitEditorPane splitEditorPane,
        Tab workspaceTab,
        NavigationService navigationService
) {
    /** 向后兼容：返回主 TabPane(cell 0) */
    public TabPane codeTabPane() {
        return splitEditorPane.primaryTabPane();
    }
}
