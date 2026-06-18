package com.bingbaihanji.fxdecomplie.ui.window;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * 应用标题栏工具类，创建自定义窗口标题栏。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class AppHeaderBar {

    /** 标题栏高度 */
    private static final double TITLE_BAR_HEIGHT = 38.0;

    private AppHeaderBar() {
        throw new AssertionError("utility class");
    }

    /**
     * 创建自定义标题栏。
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
        headerBar.setLeadingSystemPadding(true);
        headerBar.setTrailingSystemPadding(true);
        headerBar.setMinHeight(TITLE_BAR_HEIGHT);
        headerBar.setPrefHeight(TITLE_BAR_HEIGHT);

        HeaderBar.setAlignment(brand, Pos.CENTER_LEFT);
        HeaderBar.setAlignment(center, Pos.CENTER_LEFT);
        HeaderBar.setDragType(headerBar, HeaderDragType.DRAGGABLE_SUBTREE);
        HeaderBar.setDragType(brand, HeaderDragType.DRAGGABLE);
        HeaderBar.setDragType(center, HeaderDragType.DRAGGABLE_SUBTREE);
        HeaderBar.setPrefButtonHeight(stage, TITLE_BAR_HEIGHT);

        return headerBar;
    }

    /** 创建居中区域（菜单栏 + 拖拽区域） */
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

    /** 创建标题标签 */
    private static Label createTitle(String title) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("app-title");
        HeaderBar.setDragType(titleLabel, HeaderDragType.DRAGGABLE);
        return titleLabel;
    }

    /** 创建品牌图标区域 */
    private static HBox createBrand(String title) {
        Label icon = new Label("Fx");
        icon.getStyleClass().add("app-icon");

        HBox brand = new HBox(icon);
        brand.getStyleClass().add("app-brand");
        brand.setAlignment(Pos.CENTER_LEFT);
        return brand;
    }
}
