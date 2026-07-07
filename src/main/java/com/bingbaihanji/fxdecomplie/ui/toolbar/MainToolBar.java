package com.bingbaihanji.fxdecomplie.ui.toolbar;

import com.bingbaihanji.fxdecomplie.ui.menu.EditActions;
import com.bingbaihanji.fxdecomplie.ui.menu.FileActions;
import com.bingbaihanji.util.I18nUtil;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Objects;

/**
 * 主工具栏组件,位于菜单栏下方,提供常用操作快捷按钮
 *
 * <p>按钮：打开文件 / 重新加载 / 导出当前文件 / 定位文件 / 搜索</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class MainToolBar extends ToolBar {

    private static final Logger log = LoggerFactory.getLogger(MainToolBar.class);

    /** 图标基础路径 */
    private static final String ICON_BASE = "/icon/tool/";
    /** 按钮图标尺寸 */
    private static final int ICON_SIZE = 16;

    private final FileActions fileActions;
    private final EditActions editActions;

    /** 打开文件按钮 */
    private final Button openBtn;
    /** 重新反编译按钮 */
    private final Button reloadBtn;
    /** 保存当前文件按钮 */
    private final Button saveBtn;
    /** 在文件树中定位按钮 */
    private final Button localizerBtn;
    /** 搜索按钮 */
    private final Button searchBtn;

    /** 当前是否有打开的工作区 */
    private boolean hasWorkspace;
    /** 当前是否有代码标签打开 */
    private boolean hasCodeTab;

    /**
     * 创建主工具栏
     *
     * @param fileActions 文件操作回调
     * @param editActions 编辑操作回调
     */
    public MainToolBar(FileActions fileActions, EditActions editActions) {
        this.fileActions = Objects.requireNonNull(fileActions, "fileActions");
        this.editActions = Objects.requireNonNull(editActions, "editActions");

        openBtn = createButton("open.png", "toolbar.open", fileActions::openFile);
        reloadBtn = createButton("ref.png", "toolbar.reload", fileActions::refreshCurrentTab);
        saveBtn = createButton("save.png", "toolbar.save", fileActions::saveCurrentFile);
        localizerBtn = createButton("localizer.png", "toolbar.localizer", fileActions::locateCurrentFileInTree);
        searchBtn = createButton("search.png", "toolbar.search", editActions::openSearch);

        getStyleClass().add("main-toolbar");
        getItems().addAll(openBtn, reloadBtn, saveBtn, localizerBtn, searchBtn);
        refreshState(false, false);
    }

    /**
     * 根据当前 workspace 和 codeTab 状态更新按钮可用性
     *
     * @param hasWorkspace 是否有打开的工作区
     * @param hasCodeTab   是否有当前代码编辑器标签
     */
    public void refreshState(boolean hasWorkspace, boolean hasCodeTab) {
        this.hasWorkspace = hasWorkspace;
        this.hasCodeTab = hasCodeTab;
        openBtn.setDisable(false);
        reloadBtn.setDisable(!hasCodeTab);
        saveBtn.setDisable(!hasCodeTab);
        localizerBtn.setDisable(!hasCodeTab);
        searchBtn.setDisable(!hasWorkspace);
    }

    /** 创建带图标的工具栏按钮 */
    private Button createButton(String iconName, String tooltipKey, Runnable action) {
        Button btn = new Button();
        btn.getStyleClass().add("toolbar-button");
        btn.setTooltip(new Tooltip(I18nUtil.getString(tooltipKey)));
        loadIcon(btn, iconName);
        btn.setOnAction(e -> {
            btn.setDisable(true);
            try {
                action.run();
            } finally {
                refreshState(MainToolBar.this.hasWorkspace, MainToolBar.this.hasCodeTab);
            }
        });
        return btn;
    }

    /** 从 classpath 加载按钮图标 */
    private void loadIcon(Button btn, String iconName) {
        try (InputStream is = getClass().getResourceAsStream(ICON_BASE + iconName)) {
            if (is != null) {
                Image img = new Image(is, ICON_SIZE, ICON_SIZE, true, true);
                btn.setGraphic(new ImageView(img));
            }
        } catch (Exception ignored) {
            log.debug("加载工具栏按钮图标失败: {}", iconName, ignored);
        }
    }
}
