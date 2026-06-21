package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 管理四种代码内容视图（Code/Smali/Bytecode/Simple）的懒加载、切换和销毁
 *
 * <p>底部使用 HBox + ToggleButton ToggleGroup 而非嵌套 TabPane，
 * 避免与主代码标签、Split view、拖拽之间互相干扰</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class CodeContentDeck extends VBox {

    /** Code 标签索引 */
    public static final int TAB_CODE = 0;
    /** Smali/JVM指令视图索引 */
    public static final int TAB_SMALI = 1;
    /** 字节码索引 */
    public static final int TAB_BYTECODE = 2;
    /** 简化代码索引 */
    public static final int TAB_SIMPLE = 3;

    private final AbstractCodeContentPanel[] panels;
    private final ToggleButton[] buttons;
    private final StackPane contentArea;
    private final HBox bottomBar;
    private final ToggleGroup toggleGroup;
    private final AtomicInteger cancelGen = new AtomicInteger();
    private final AtomicInteger activeIndex = new AtomicInteger(TAB_CODE);

    /** 源码内容提供回调，Simple 面板按需读取 */
    private volatile String sourceCode;
    /** 类文件字节码，Smali/Bytecode 按需读取 */
    private final byte[] classBytes;

    public CodeContentDeck(String sourceCode, byte[] classBytes) {
        this.sourceCode = sourceCode;
        this.classBytes = classBytes == null ? null : classBytes.clone();
        this.panels = new AbstractCodeContentPanel[4];
        this.buttons = new ToggleButton[4];
        this.toggleGroup = new ToggleGroup();
        this.contentArea = new StackPane();
        this.bottomBar = buildBottomBar();
        contentArea.getStyleClass().add("code-content-area");
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        getChildren().setAll(contentArea, bottomBar);

        // 默认显示 Code 面板
        selectTab(TAB_CODE);
    }

    /** 构建底部标签栏 */
    private HBox buildBottomBar() {
        HBox bar = new HBox(2);
        bar.getStyleClass().add("code-deck-bar");
        bar.setMinHeight(30);

        addToggle(0, "tab.code");
        addToggle(1, "tab.smali");
        addToggle(2, "tab.bytecode");
        addToggle(3, "tab.simple");

        // 右侧弹簧
        Node spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().add(spacer);

        return bar;
    }

    private void addToggle(int index, String i18nKey) {
        ToggleButton btn = new ToggleButton(I18nUtil.getString(i18nKey));
        btn.getStyleClass().add("code-deck-toggle");
        btn.setToggleGroup(toggleGroup);
        btn.setMinWidth(60);
        btn.setMaxHeight(28);
        btn.setOnAction(e -> {
            if (btn.isSelected()) {
                selectTab(index);
            }
        });
        buttons[index] = btn;
        bottomBar.getChildren().add(btn);
    }

    /**
     * 切换到指定索引的标签页，首次切换时触发懒加载
     */
    private void selectTab(int index) {
        if (index < 0 || index >= 4) return;
        cancelGen.incrementAndGet(); // 取消旧任务
        activeIndex.set(index);

        if (panels[index] == null) {
            panels[index] = createPanel(index);
        }
        AbstractCodeContentPanel panel = panels[index];
        contentArea.getChildren().setAll(panel);

        if (!panel.isLoaded() && !panel.isError()) {
            Object token = new Object();
            panel.loadAsync(token);
        }

        buttons[index].setSelected(true);
    }

    /** 按索引创建对应面板 */
    private AbstractCodeContentPanel createPanel(int index) {
        return switch (index) {
            case TAB_CODE -> new SourceContentPanel(null); // 由外部 setSource 填充
            case TAB_SMALI -> new SmaliContentPanel(classBytes);
            case TAB_BYTECODE -> new BytecodeContentPanel(classBytes);
            case TAB_SIMPLE -> new SimpleContentPanel(sourceCode);
            default -> throw new IllegalArgumentException("未知面板索引: " + index);
        };
    }

    /** @return 当前激活的面板索引 */
    public int getActiveIndex() {
        return activeIndex.get();
    }

    /** @return 源码面板中的 CodeArea（可能为 null） */
    public SourceContentPanel getSourcePanel() {
        return (SourceContentPanel) panels[TAB_CODE];
    }

    /** @return 当前活动面板 */
    public AbstractCodeContentPanel getActivePanel() {
        int idx = activeIndex.get();
        return panels[idx];
    }

    /**
     * 更新源码内容并刷新已加载的 Code/Simple 面板
     *
     * @param newSource 新的反编译源码
     */
    public void updateSource(String newSource) {
        this.sourceCode = newSource;
        if (panels[TAB_CODE] instanceof SourceContentPanel sp) {
            sp.setSourceCode(newSource);
        }
        if (panels[TAB_SIMPLE] != null) {
            panels[TAB_SIMPLE] = null; // 下次切换时用新源码重建
        }
    }

    /** @return 当前取消分代号，用于外部判断任务是否过期 */
    public int cancelGeneration() {
        return cancelGen.get();
    }

    /** @return 内容区域 StackPane（供外部重组布局） */
    public StackPane getContentArea() {
        return contentArea;
    }

    /** @return 底部标签栏 HBox（供外部重组布局） */
    public HBox getBottomBar() {
        return bottomBar;
    }

    public ToggleButton getButton(int index) {
        return buttons[index];
    }

    /** 程序化选中指定标签 */
    public void setSelected(int index) {
        if (buttons[index] != null) {
            buttons[index].setSelected(true);
        }
    }

    /** 释放所有面板资源 */
    public void dispose() {
        cancelGen.incrementAndGet();
        for (AbstractCodeContentPanel panel : panels) {
            if (panel != null) panel.dispose();
        }
    }
}
