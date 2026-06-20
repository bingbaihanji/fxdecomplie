package com.bingbaihanji.fxdecomplie.ui.menu;

import java.util.List;

/**
 * 文件相关菜单动作
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface FileActions {
    /** 打开 JAR/ZIP/Class 文件 */
    void openFile();

    /** 打开目录 */
    void openDirectory();

    /** 打开项目文件 */
    void openProject();

    /** 保存项目文件 */
    void saveProject();

    /** 保存当前文件 */
    void saveCurrentFile();

    /** 导出所有文件 */
    void exportAllFiles();

    /** 关闭当前工作区 */
    void closeCurrentWorkspace();

    /** 关闭其他工作区 */
    void closeOtherWorkspaces();

    /** 打开最近文件 */
    void openRecentFile(String path);

    /** 获取最近文件列表 */
    List<String> getRecentFiles();

    /** 清除最近文件列表并持久化 */
    void clearRecentFiles();

    /** 新窗口打开当前文件 */
    void openNewWindow();

    /** 退出应用 */
    void exit();
}
