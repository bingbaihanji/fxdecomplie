package com.bingbihanji.fxdecomplie.ui.menu;

/**
 * Edit-related menu actions.
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface EditActions {
    /** 复制选中文本 */
    void copySelection();

    /** 全选 */
    void selectAll();

    /** 打开搜索对话框 (Ctrl+Shift+F) */
    void openSearch();

    /** 查找使用 */
    void openFindUsages();

    /** 快速打开类 (Ctrl+N) */
    void quickOpenClass();

    /** 打开设置对话框 */
    void openSettings();
}
