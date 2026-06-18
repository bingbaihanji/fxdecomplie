package com.bingbihanji.fxdecomplie.ui.menu;

import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbihanji.fxdecomplie.utils.I18nUtil;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;

/**
 * 主菜单栏组件，提供文件、编辑、视图、引擎、主题和帮助菜单。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class MainMenuBar extends MenuBar {

    /**
     * @param actions       菜单动作回调
     * @param currentEngine 当前选中的反编译引擎
     */
    public MainMenuBar(Actions actions, DecompilerTypeEnum currentEngine) {
        getStyleClass().add("main-menu-bar");
        getMenus().addAll(
                createFileMenu(actions),
                createEditMenu(actions),
                createViewMenu(actions),
                createEngineMenu(actions, currentEngine),
                createThemeMenu(),
                createWindowMenu(actions),
                createHelpMenu()
        );
    }

    /** 创建文件菜单 */
    private Menu createFileMenu(Actions actions) {
        Menu fileMenu = new Menu("文件(F)");

        MenuItem openFile = item("打开文件...", "Shortcut+O", actions::openFile);
        MenuItem openDir = item("打开目录...", "Shortcut+Shift+O", actions::openDirectory);
        MenuItem closeCurrent = item("关闭当前", "Shortcut+W", actions::closeCurrentWorkspace);
        MenuItem closeOthers = item("关闭其他", null, actions::closeOtherWorkspaces);
        MenuItem saveFile = item("保存当前文件", "Shortcut+S", actions::saveCurrentFile);
        MenuItem exportAll = item("全部导出...", "Shortcut+Shift+E", actions::exportAllFiles);
        MenuItem exit = item("退出", null, actions::exit);

        fileMenu.getItems().addAll(
                openFile, openDir, new SeparatorMenuItem(),
                closeCurrent, closeOthers, new SeparatorMenuItem(),
                saveFile, exportAll
        );

        Menu recentMenu = new Menu("最近打开");
        recentMenu.setOnShowing(e -> {
            recentMenu.getItems().clear();
            java.util.List<String> recents = actions.getRecentFiles();
            if (recents.isEmpty()) {
                javafx.scene.control.MenuItem none = new javafx.scene.control.MenuItem("(无)");
                none.setDisable(true);
                recentMenu.getItems().add(none);
            } else {
                for (String path : recents) {
                    String display = path;
                    int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                    if (sep > 0) display = path.substring(sep + 1) + "  —  " + path;
                    javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(display);
                    item.setOnAction(ev -> actions.openRecentFile(path));
                    recentMenu.getItems().add(item);
                }
                recentMenu.getItems().addAll(new SeparatorMenuItem(), clearRecentItem(actions));
            }
        });
        fileMenu.getItems().addAll(recentMenu, new SeparatorMenuItem(), exit);
        return fileMenu;
    }

    /** 创建编辑菜单 */
    private Menu createEditMenu(Actions actions) {
        Menu editMenu = new Menu("编辑(E)");
        MenuItem copy = item("复制", "Shortcut+C", actions::copySelection);
        MenuItem selectAll = item("全选", "Shortcut+A", actions::selectAll);
        MenuItem quickOpen = item("快速打开类...", "Shortcut+N", actions::quickOpenClass);
        MenuItem search = item("全文搜索...", "Shortcut+Shift+F", actions::openSearch);
        MenuItem settings = item(I18nUtil.getString("menu.edit.settings"), null, actions::openSettings);
        editMenu.getItems().addAll(copy, selectAll, new SeparatorMenuItem(), quickOpen, search,
                new SeparatorMenuItem(), settings);
        return editMenu;
    }

    /** 创建视图菜单 */
    private Menu createViewMenu(Actions actions) {
        Menu viewMenu = new Menu("视图(V)");
        MenuItem zoomIn = item("放大", "Shortcut+PLUS", actions::zoomIn);
        MenuItem zoomOut = item("缩小", "Shortcut+MINUS", actions::zoomOut);
        MenuItem zoomReset = item("重置缩放", "Shortcut+0", actions::resetZoom);
        MenuItem toggleLineNumbers = item("切换行号显示", null, actions::toggleLineNumbers);
        MenuItem collapseTree = item("文件树折叠", null, actions::collapseTree);
        MenuItem showOutline = item("大纲", null, actions::showOutline);
        MenuItem showInheritance = item("继承", null, actions::showInheritance);
        MenuItem hideBottomTools = item("隐藏底部工具窗口", null, actions::hideBottomTools);
        viewMenu.getItems().addAll(
                zoomIn, zoomOut, zoomReset,
                new SeparatorMenuItem(), toggleLineNumbers, collapseTree,
                new SeparatorMenuItem(), showOutline, showInheritance, hideBottomTools
        );
        return viewMenu;
    }

    /** 创建引擎选择菜单 */
    private Menu createEngineMenu(Actions actions, DecompilerTypeEnum currentEngine) {
        Menu engineMenu = new Menu("引擎(G)");
        ToggleGroup engineGroup = new ToggleGroup();

        RadioMenuItem procyonItem = engineItem("Procyon", DecompilerTypeEnum.PROCYON, currentEngine, engineGroup, actions);
        RadioMenuItem cfrItem = engineItem("CFR", DecompilerTypeEnum.CFR, currentEngine, engineGroup, actions);
        RadioMenuItem vineflowerItem = engineItem("Vineflower", DecompilerTypeEnum.VINEFLOWER, currentEngine, engineGroup, actions);
        RadioMenuItem jdItem = engineItem("JD-Core", DecompilerTypeEnum.JD, currentEngine, engineGroup, actions);

        engineMenu.getItems().addAll(procyonItem, cfrItem, vineflowerItem, jdItem);
        return engineMenu;
    }

    /** 创建主题菜单 */
    private Menu createThemeMenu() {
        Menu themeMenu = new Menu("主题(T)");
        RadioMenuItem darkPlus = new RadioMenuItem("Dark+");
        darkPlus.setSelected(true);
        darkPlus.setDisable(true);
        themeMenu.getItems().add(darkPlus);
        return themeMenu;
    }

    /** 创建帮助菜单 */
    private Menu createHelpMenu() {
        Menu helpMenu = new Menu("帮助(H)");
        MenuItem about = new MenuItem("关于");
        about.setDisable(true);
        helpMenu.getItems().add(about);
        return helpMenu;
    }

    /** 创建窗口菜单 */
    private Menu createWindowMenu(Actions actions) {
        Menu windowMenu = new Menu(I18nUtil.getString("menu.window"));
        MenuItem newWindow = item(I18nUtil.getString("menu.window.newWindow"), null, actions::openNewWindow);
        windowMenu.getItems().add(newWindow);
        return windowMenu;
    }

    /** 创建带选中状态的引擎菜单项 */
    private RadioMenuItem engineItem(String text, DecompilerTypeEnum type, DecompilerTypeEnum currentEngine,
                                     ToggleGroup group, Actions actions) {
        RadioMenuItem item = new RadioMenuItem(text);
        item.setToggleGroup(group);
        item.setSelected(currentEngine == type);
        item.setOnAction(event -> actions.selectEngine(type));
        return item;
    }

    /** 创建带快捷键的菜单项 */
    private MenuItem item(String text, String accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyCombination.keyCombination(accelerator));
        }
        item.setOnAction(event -> action.run());
        return item;
    }

    /** 创建清除最近历史菜单项 */
    private javafx.scene.control.MenuItem clearRecentItem(Actions actions) {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem("清除历史");
        item.setOnAction(e -> {
            actions.getRecentFiles().clear();
        });
        return item;
    }

    public interface Actions {
        /** 打开 JAR/ZIP/Class 文件 */
        void openFile();

        /** 打开目录 */
        void openDirectory();

        /** 关闭当前工作区 */
        void closeCurrentWorkspace();

        /** 关闭其他工作区 */
        void closeOtherWorkspaces();

        /** 保存当前文件 */
        void saveCurrentFile();

        /** 导出所有文件 */
        void exportAllFiles();

        /** 退出应用 */
        void exit();

        /** 复制选中文本 */
        void copySelection();

        /** 全选 */
        void selectAll();

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

        /** 切换反编译引擎 */
        void selectEngine(DecompilerTypeEnum engine);

        /** 打开搜索对话框 (Ctrl+Shift+F) */
        void openSearch();

        /** 快速打开类 (Ctrl+N) */
        void quickOpenClass();

        /** 打开最近文件 */
        void openRecentFile(String path);

        /** 获取最近文件列表 */
        java.util.List<String> getRecentFiles();

        /** 打开设置对话框 */
        void openSettings();

        /** 新窗口打开当前文件 */
        void openNewWindow();
    }
}
