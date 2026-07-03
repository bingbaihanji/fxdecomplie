package com.bingbaihanji.fxdecomplie.ui.window;

import com.bingbaihanji.util.I18nUtil;
import com.bingbaihanji.windows.jfx.WindowToolkit;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * 应用标题栏工具类,创建自定义窗口标题栏
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class AppHeaderBar {

    private static final Logger logger = LoggerFactory.getLogger(AppHeaderBar.class);

    /** 标题栏高度 */
    private static final double TITLE_BAR_HEIGHT = 38.0;
    private static final double TITLE_BUTTON_SIZE = 38.0;
    private static final double TITLE_ICON_SIZE = 16.0;
    private static final String TOP_ON_ICON = "/icon/top-on.png";
    private static final String TOP_OFF_ICON = "/icon/top-off.png";

    private AppHeaderBar() {
        throw new AssertionError("utility class");
    }

    /**
     * 创建自定义标题栏
     *
     * @param stage  主窗口 Stage
     * @param title  窗口标题
     * @return HeaderBar 实例
     */
    public static HeaderBar create(Stage stage, String title) {
        return create(stage, title, null);
    }

    /** 创建带菜单栏的标题栏 */
    public static HeaderBar create(Stage stage, String title, Node menuBar) {
        HBox brand = createBrand(title);
        Node center = menuBar != null ? createCenterArea(menuBar) : createTitle(title);

        HeaderBar headerBar = new HeaderBar();
        headerBar.getStyleClass().add("app-header-bar");
        headerBar.setLeading(brand);
        headerBar.setCenter(center);
        headerBar.setTrailing(createWindowTools(stage));
        headerBar.setLeadingSystemPadding(true);
        headerBar.setTrailingSystemPadding(true);
        headerBar.setMinHeight(TITLE_BAR_HEIGHT);
        headerBar.setPrefHeight(TITLE_BAR_HEIGHT);

        HeaderBar.setAlignment(brand, Pos.CENTER_LEFT);
        HeaderBar.setAlignment(center, Pos.CENTER_LEFT);
        HeaderBar.setAlignment(headerBar.getTrailing(), Pos.CENTER_RIGHT);
        HeaderBar.setDragType(headerBar, HeaderDragType.DRAGGABLE_SUBTREE);
        HeaderBar.setDragType(brand, HeaderDragType.DRAGGABLE);
        HeaderBar.setDragType(center, HeaderDragType.DRAGGABLE_SUBTREE);
        HeaderBar.setPrefButtonHeight(stage, TITLE_BAR_HEIGHT);
        headerBar.setOpacity(0.618);
        return headerBar;
    }

    /** 创建居中区域(菜单栏 + 拖拽区域) */
    private static Node createCenterArea(Node menuBar) {
        menuBar.getStyleClass().add("header-menu-bar");
        HeaderBar.setDragType(menuBar, HeaderDragType.NONE);

        Region dragSpace = new Region();
        dragSpace.getStyleClass().add("header-drag-space");
        HBox.setHgrow(dragSpace, Priority.ALWAYS);
        HeaderBar.setDragType(dragSpace, HeaderDragType.DRAGGABLE);

        HBox center = new HBox(menuBar, dragSpace);
        center.getStyleClass().add("header-center");
        center.setAlignment(Pos.CENTER_LEFT);
        return center;
    }

    /** 创建窗口控制扩展区域(位于系统最小化按钮左侧) */
    private static Node createWindowTools(Stage stage) {
        ToggleButton alwaysOnTopButton = createAlwaysOnTopButton(stage);

        HBox tools = new HBox(alwaysOnTopButton);
        tools.getStyleClass().add("header-window-tools");
        tools.setAlignment(Pos.CENTER_RIGHT);
        HeaderBar.setDragType(tools, HeaderDragType.NONE);
        return tools;
    }

    /** 创建置顶/取消置顶按钮 */
    private static ToggleButton createAlwaysOnTopButton(Stage stage) {
        Image onIcon = loadIcon(TOP_ON_ICON);
        Image offIcon = loadIcon(TOP_OFF_ICON);
        ImageView iconView = new ImageView();
        iconView.setFitWidth(TITLE_ICON_SIZE);
        iconView.setFitHeight(TITLE_ICON_SIZE);
        iconView.setPreserveRatio(true);

        Tooltip tooltip = new Tooltip();
        ToggleButton button = new ToggleButton();
        button.getStyleClass().addAll("header-icon-toggle", "header-always-on-top-toggle");
        button.setFocusTraversable(false);
        button.setMinSize(TITLE_BUTTON_SIZE, TITLE_BAR_HEIGHT);
        button.setPrefSize(TITLE_BUTTON_SIZE, TITLE_BAR_HEIGHT);
        button.setMaxSize(TITLE_BUTTON_SIZE, TITLE_BAR_HEIGHT);
        button.setGraphic(iconView);
        button.setTooltip(tooltip);
        HeaderBar.setDragType(button, HeaderDragType.NONE);

        Runnable update = () -> updateAlwaysOnTopButton(button, iconView, tooltip, onIcon, offIcon,
                stage.isAlwaysOnTop());
        button.setSelected(stage.isAlwaysOnTop());
        update.run();

        button.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (stage.isAlwaysOnTop() != selected) {
                WindowToolkit.setAlwaysOnTop(stage, selected);
            }
            update.run();
        });
        stage.alwaysOnTopProperty().addListener((obs, oldValue, selected) -> {
            if (button.isSelected() != selected) {
                button.setSelected(selected);
            }
            update.run();
        });
        I18nUtil.addLocaleChangeListener(update);
        return button;
    }

    private static void updateAlwaysOnTopButton(ToggleButton button, ImageView iconView,
                                                Tooltip tooltip, Image onIcon, Image offIcon,
                                                boolean alwaysOnTop) {
        iconView.setImage(alwaysOnTop ? onIcon : offIcon);
        String text = I18nUtil.getString(alwaysOnTop
                ? "header.alwaysOnTop.disable"
                : "header.alwaysOnTop.enable");
        tooltip.setText(text);
        button.setAccessibleText(text);
        button.setSelected(alwaysOnTop);
    }

    private static Image loadIcon(String path) {
        URL url = AppHeaderBar.class.getResource(path);
        if (url == null) {
            return null;
        }
        return new Image(url.toExternalForm(), TITLE_ICON_SIZE, TITLE_ICON_SIZE, true, true);
    }

    /** 创建标题标签 */
    private static Label createTitle(String title) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("app-title");
        HeaderBar.setDragType(titleLabel, HeaderDragType.DRAGGABLE);
        return titleLabel;
    }

    /** 创建品牌图标区域(标题栏左侧 logo) */
    private static HBox createBrand(String title) {
        ImageView logoView = logoIcon();
        HBox brand = new HBox(logoView);
        brand.getStyleClass().add("app-brand");
        brand.setAlignment(Pos.CENTER_LEFT);
        return brand;
    }

    private static ImageView logoIcon() {
        ImageView iv = new ImageView();
        iv.setFitWidth(20);
        iv.setFitHeight(20);
        iv.setPreserveRatio(true);
        Image logo = com.bingbaihanji.fxdecomplie.ui.IconHelper.getLogoImage();
        if (logo != null) {
            iv.setImage(logo);
        }
        iv.getStyleClass().add("app-icon");
        return iv;
    }
}
