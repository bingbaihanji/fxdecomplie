package com.bingbaihanji.fxdecomplie.controller;

import com.bingbaihanji.fxdecomplie.ui.code.CodeEditorTab;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/**
 * 编辑器动作控制器：剪贴板 缩放 行号 折叠 工具窗口等编辑区操作
 * <p>
 * 从 MainWindow 拆分而来，通过 owner 访问共享状态与协作者 (Mediator 模式)
 * 所有协作者均在调用时通过 owner 访问，以适应 tabManager/toolBar 延迟初始化
 *
 * @author bingbaihanji
 */
public final class EditorActionsController {

    private final MainWindow owner;

    public EditorActionsController(MainWindow owner) {
        this.owner = owner;
    }

    /** 复制字符串到系统剪贴板 */
    public static void copyToClipboard(String value) {
        ClipboardContent content = new ClipboardContent();
        content.putString(value == null ? "" : value);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** 复制选中文本 */
    public void copySelection() {
        CodeEditorTab codeTab = owner.tabManager().currentCodeTab();
        if (codeTab != null) {
            codeTab.getCodeArea().copy();
        }
    }

    /** 全选 */
    public void selectAll() {
        CodeEditorTab codeTab = owner.tabManager().currentCodeTab();
        if (codeTab != null) {
            codeTab.getCodeArea().selectAll();
        }
    }

    /** 放大字号 */
    public void zoomIn() {
        CodeEditorTab codeTab = owner.tabManager().currentCodeTab();
        if (codeTab != null) {
            codeTab.zoomIn();
        }
    }

    /** 缩小字号 */
    public void zoomOut() {
        CodeEditorTab codeTab = owner.tabManager().currentCodeTab();
        if (codeTab != null) {
            codeTab.zoomOut();
        }
    }

    /** 重置字号 */
    public void resetZoom() {
        CodeEditorTab codeTab = owner.tabManager().currentCodeTab();
        if (codeTab != null) {
            codeTab.resetZoom();
        }
    }

    /** 切换行号显示 */
    public void toggleLineNumbers() {
        boolean enabled = !owner.lineNumbersEnabled();
        owner.setLineNumbersEnabled(enabled);
        owner.config().decompiler().lineNumbersEnabled(enabled);
        owner.tabManager().getWorkspaceViews().values().forEach(view ->
                view.splitEditorPane().forEachTab(
                        tab -> tab.setLineNumbersEnabled(enabled)));
    }

    /** 折叠当前工作区文件树 */
    public void collapseTree() {
        owner.tabManager().collapseTreeInCurrentWorkspace();
    }

    /** 显示大纲工具窗口 */
    public void showOutline() {
        owner.tabManager().showOutlineToolWindow();
    }

    /** 显示继承工具窗口 */
    public void showInheritance() {
        owner.tabManager().showInheritanceToolWindow();
    }

    /** 显示注释工具窗口 */
    public void showComments() {
        owner.tabManager().showCommentsToolWindow();
    }

    /** 隐藏底部工具窗口 */
    public void hideBottomTools() {
        owner.tabManager().hideBottomToolWindow();
    }

    /** 根据当前工作区和代码标签页状态刷新工具栏按钮的启用/禁用状态 */
    public void refreshToolbarState() {
        if (owner.toolBar() != null) {
            boolean hasWorkspace = owner.tabManager() != null
                    && owner.tabManager().currentWorkspaceView() != null;
            boolean hasCodeTab = owner.tabManager() != null
                    && owner.tabManager().currentCodeTab() != null;
            owner.toolBar().refreshState(hasWorkspace, hasCodeTab);
        }
    }
}
