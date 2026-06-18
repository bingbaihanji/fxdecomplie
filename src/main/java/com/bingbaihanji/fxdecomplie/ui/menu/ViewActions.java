package com.bingbaihanji.fxdecomplie.ui.menu;

/**
 * View-related menu actions.
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface ViewActions {
    /** 放大 */
    void zoomIn();

    /** 缩小 */
    void zoomOut();

    /** 重置缩放 */
    void resetZoom();

    /** 切换行号 */
    void toggleLineNumbers();

    /** 折叠文件树 */
    void collapseTree();

    /** 显示大纲工具窗口 */
    void showOutline();

    /** 显示继承工具窗口 */
    void showInheritance();

    /** 隐藏底部工具窗口 */
    void hideBottomTools();
}
