package com.bingbaihanji.fxdecomplie.ui.menu;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.util.I18nUtil;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 主菜单栏组件,提供文件、编辑、视图、引擎、主题和帮助菜单
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class MainMenuBar extends MenuBar {

    private final Map<DecompilerTypeEnum, RadioMenuItem> engineItems =
            new EnumMap<>(DecompilerTypeEnum.class);

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
                createWindowMenu(actions),
                createHelpMenu(actions)
        );
    }

    /** 创建文件菜单 */
    private Menu createFileMenu(Actions actions) {
        Menu fileMenu = new Menu(I18nUtil.getString("menu.file"));

        MenuItem openFile = item(I18nUtil.getString("menu.file.open"), "Shortcut+O", actions::openFile);
        MenuItem openDir = item(I18nUtil.getString("menu.file.openDir"), "Shortcut+Shift+O", actions::openDirectory);
        MenuItem openProject = item(I18nUtil.getString("project.open"), null, actions::openProject);
        MenuItem saveProject = item(I18nUtil.getString("project.save"), null, actions::saveProject);
        MenuItem closeCurrent = item(I18nUtil.getString("menu.file.close"), "Shortcut+W", actions::closeCurrentWorkspace);
        MenuItem closeOthers = item(I18nUtil.getString("menu.file.closeOthers"), null, actions::closeOtherWorkspaces);
        MenuItem saveFile = item(I18nUtil.getString("menu.file.save"), "Shortcut+S", actions::saveCurrentFile);
        MenuItem exportAll = item(I18nUtil.getString("menu.file.exportAll"), "Shortcut+Shift+E", actions::exportAllFiles);
        MenuItem exit = item(I18nUtil.getString("menu.file.exit"), null, actions::exit);

        fileMenu.getItems().addAll(
                openFile, openDir, openProject, saveProject, new SeparatorMenuItem(),
                closeCurrent, closeOthers, new SeparatorMenuItem(),
                saveFile, exportAll
        );

        Menu recentMenu = new Menu(I18nUtil.getString("menu.file.recent"));
        recentMenu.setOnShowing(e -> {
            recentMenu.getItems().clear();
            List<String> recents = actions.getRecentFiles();
            if (recents.isEmpty()) {
                MenuItem none = new MenuItem(
                        I18nUtil.getString("menu.file.recent.empty"));
                none.setDisable(true);
                recentMenu.getItems().add(none);
            } else {
                for (String path : recents) {
                    String display = path;
                    int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                    if (sep > 0) {
                        display = path.substring(sep + 1) + "  —  " + path;
                    }
                    MenuItem item = new MenuItem(display);
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
        Menu editMenu = new Menu(I18nUtil.getString("menu.edit"));
        MenuItem copy = item(I18nUtil.getString("menu.edit.copy"), "Shortcut+C", actions::copySelection);
        MenuItem selectAll = item(I18nUtil.getString("menu.edit.selectAll"), "Shortcut+A", actions::selectAll);
        MenuItem quickOpen = item(I18nUtil.getString("menu.edit.quickOpen"), "Shortcut+N", actions::quickOpenClass);
        MenuItem search = item(I18nUtil.getString("menu.edit.search"), "Shortcut+Shift+F", actions::openSearch);
        MenuItem findUsages = item(I18nUtil.getString("usage.title"), "Shortcut+U", actions::openFindUsages);
        MenuItem settings = item(I18nUtil.getString("menu.edit.settings"), null, actions::openSettings);
        editMenu.getItems().addAll(copy, selectAll, new SeparatorMenuItem(), quickOpen, search, findUsages,
                new SeparatorMenuItem(), settings);
        return editMenu;
    }

    /** 创建视图菜单 */
    private Menu createViewMenu(Actions actions) {
        Menu viewMenu = new Menu(I18nUtil.getString("menu.view"));
        MenuItem zoomIn = item(I18nUtil.getString("menu.view.zoomIn"), "Shortcut+PLUS", actions::zoomIn);
        MenuItem zoomOut = item(I18nUtil.getString("menu.view.zoomOut"), "Shortcut+MINUS", actions::zoomOut);
        MenuItem zoomReset = item(I18nUtil.getString("menu.view.resetZoom"), "Shortcut+0", actions::resetZoom);
        MenuItem toggleLineNumbers = item(I18nUtil.getString("menu.view.toggleLineNumbers"), null, actions::toggleLineNumbers);
        MenuItem collapseTree = item(I18nUtil.getString("menu.view.collapseTree"), null, actions::collapseTree);
        MenuItem showOutline = item(I18nUtil.getString("menu.view.outline"), null, actions::showOutline);
        MenuItem showInheritance = item(I18nUtil.getString("menu.view.inheritance"), null, actions::showInheritance);
        MenuItem hideBottomTools = item(I18nUtil.getString("menu.view.hideTools"), null, actions::hideBottomTools);
        viewMenu.getItems().addAll(
                zoomIn, zoomOut, zoomReset,
                new SeparatorMenuItem(), toggleLineNumbers, collapseTree,
                new SeparatorMenuItem(), showOutline, showInheritance, hideBottomTools
        );
        return viewMenu;
    }

    /** 创建引擎选择菜单 */
    private Menu createEngineMenu(Actions actions, DecompilerTypeEnum currentEngine) {
        Menu engineMenu = new Menu(I18nUtil.getString("menu.engine"));
        ToggleGroup engineGroup = new ToggleGroup();

        RadioMenuItem procyonItem = engineItem("Procyon", DecompilerTypeEnum.PROCYON, currentEngine, engineGroup, actions);
        RadioMenuItem cfrItem = engineItem("CFR", DecompilerTypeEnum.CFR, currentEngine, engineGroup, actions);
        RadioMenuItem vineflowerItem = engineItem("Vineflower", DecompilerTypeEnum.VINEFLOWER, currentEngine, engineGroup, actions);
        RadioMenuItem jdItem = engineItem("JD-Core", DecompilerTypeEnum.JD, currentEngine, engineGroup, actions);

        MenuItem compareAllItem = new MenuItem(I18nUtil.getString("menu.engine.compareAll"));
        compareAllItem.setOnAction(e -> actions.compareEngines());

        MenuItem deobfuscateItem = new MenuItem("Deobfuscate");
        deobfuscateItem.setOnAction(e -> actions.deobfuscate());
        MenuItem importMappingItem = new MenuItem("Import ProGuard Mapping");
        importMappingItem.setOnAction(e -> actions.importProGuardMapping());
        MenuItem exportMappingItem = new MenuItem("Export ProGuard Mapping");
        exportMappingItem.setOnAction(e -> actions.exportProGuardMapping());
        MenuItem restoreRenameSnapshotItem = new MenuItem("Restore Rename Snapshot");
        restoreRenameSnapshotItem.setOnAction(e -> actions.restoreLastRenameSnapshot());

        engineMenu.getItems().addAll(procyonItem, cfrItem, vineflowerItem, jdItem,
                new SeparatorMenuItem(), compareAllItem, deobfuscateItem,
                importMappingItem, exportMappingItem,
                restoreRenameSnapshotItem);
        return engineMenu;
    }

    /** 创建帮助菜单 */
    private Menu createHelpMenu(Actions actions) {
        Menu helpMenu = new Menu(I18nUtil.getString("menu.help"));
        MenuItem about = item(I18nUtil.getString("menu.help.about"), null, actions::about);
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
        engineItems.put(type, item);
        return item;
    }

    /**
     * 同步外部设置变更后的引擎菜单选中状态
     *
     * @param engine 要选中的反编译引擎
     */
    public void setSelectedEngine(DecompilerTypeEnum engine) {
        RadioMenuItem item = engineItems.get(engine);
        if (item != null) {
            item.setSelected(true);
        }
    }

    /**
     * 创建带可选快捷键的菜单项
     *
     * @param text        菜单项文本
     * @param accelerator 快捷键字符串（如 "Shortcut+O"）,可为 null
     * @param action      点击回调
     */
    private MenuItem item(String text, String accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyCombination.keyCombination(accelerator));
        }
        item.setOnAction(event -> action.run());
        return item;
    }

    /** 创建清除最近文件历史记录菜单项 */
    private MenuItem clearRecentItem(Actions actions) {
        MenuItem item = new MenuItem(
                I18nUtil.getString("menu.file.recent.clear"));
        item.setOnAction(e -> actions.clearRecentFiles());
        return item;
    }

    /** 综合菜单动作接口,聚合所有子菜单的动作定义 */
    public interface Actions extends FileActions, EditActions, ViewActions, EngineActions {
        /** 显示关于对话框 */
        void about();
    }
}
