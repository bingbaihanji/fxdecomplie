package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.util.I18nUtil;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 管理五种代码内容视图（Code/Smali/Bytecode/Simple/HEX）的懒加载、切换和销毁
 *
 * <p>底部使用 HBox + ToggleButton ToggleGroup 而非嵌套 TabPane,
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
    /** HEX 十六进制视图索引 */
    public static final int TAB_HEX = 4;
    /** 标签总数 */
    private static final int TAB_COUNT = 5;
    private static final Logger log = LoggerFactory.getLogger(CodeContentDeck.class);
    private final AbstractCodeContentPanel[] panels;
    private final ToggleButton[] buttons;
    private final StackPane contentArea;
    private final HBox bottomBar;
    private final ToggleGroup toggleGroup;
    private final AtomicInteger cancelGen = new AtomicInteger();
    private final AtomicInteger activeIndex = new AtomicInteger(TAB_CODE);
    /** 类文件字节码,Smali/Bytecode 按需读取 */
    private final byte[] classBytes;
    /** 防止 setSelected→action→selectTab 死循环 */
    private boolean suppressAction;
    /** 源码内容提供回调,Simple 面板按需读取 */
    private volatile String sourceCode;
    /** 编辑器主题,用于 HEX 面板配色 */
    private volatile VsCodeThemeLoader.ThemeData theme;
    /** 字体族 */
    private volatile String fontFamily = "Consolas";
    /** 字号 */
    private volatile int fontSize = 14;
    /** 是否显示行号 */
    private volatile boolean lineNumbersEnabled = true;

    /** 使用默认字体参数构造 */
    public CodeContentDeck(String sourceCode, byte[] classBytes) {
        this(sourceCode, classBytes, null);
    }

    /** 使用默认字体参数构造,可传入外部创建的源码面板 */
    public CodeContentDeck(String sourceCode, byte[] classBytes, SourceContentPanel sourcePanel) {
        this(sourceCode, classBytes, sourcePanel, "Consolas", 14, true);
    }

    /**
     * 完整构造器,指定字体族、字号和行号开关
     *
     * @param sourceCode          反编译源码内容
     * @param classBytes          类文件字节码
     * @param sourcePanel         外部创建的源码面板（可为 null,由内部懒创建）
     * @param fontFamily          字体族名称
     * @param fontSize            字号
     * @param lineNumbersEnabled  是否显示行号
     */
    public CodeContentDeck(String sourceCode, byte[] classBytes, SourceContentPanel sourcePanel,
                           String fontFamily, int fontSize, boolean lineNumbersEnabled) {
        this.sourceCode = sourceCode;
        this.classBytes = classBytes == null ? null : classBytes.clone();
        this.fontFamily = fontFamily;
        this.fontSize = fontSize;
        this.lineNumbersEnabled = lineNumbersEnabled;
        this.panels = new AbstractCodeContentPanel[TAB_COUNT];
        this.panels[TAB_CODE] = sourcePanel;
        this.buttons = new ToggleButton[TAB_COUNT];
        this.toggleGroup = new ToggleGroup();
        this.contentArea = new StackPane();
        this.bottomBar = buildBottomBar();
        contentArea.getStyleClass().add("code-content-area");
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        getChildren().setAll(contentArea, bottomBar);

        // 默认显示 Code 面板
        selectTab(TAB_CODE);
    }

    /** 从任意面板中提取 CodeArea 编辑器引用（用于统一操作字体、行号等） */
    private static jfx.incubator.scene.control.richtext.CodeArea getCodeArea(AbstractCodeContentPanel panel) {
        return switch (panel) {
            case SourceContentPanel p -> p.getCodeArea();
            case SmaliContentPanel p -> p.getCodeArea();
            case BytecodeContentPanel p -> p.getCodeArea();
            case SimpleContentPanel p -> p.getCodeArea();
            default -> null;
        };
    }

    /** 构建底部标签栏 */
    private HBox buildBottomBar() {
        HBox bar = new HBox(2);
        bar.getStyleClass().add("code-deck-bar");
        bar.setMinHeight(30);

        addToggle(bar, 0, "tab.code");
        addToggle(bar, 1, "tab.smali");
        addToggle(bar, 2, "tab.bytecode");
        addToggle(bar, 3, "tab.simple");
        addToggle(bar, 4, "tab.hex");

        // 右侧弹簧
        Node spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().add(spacer);

        return bar;
    }

    /** 向底部栏添加一个切换按钮,使用国际化 key 作为按钮文本 */
    private void addToggle(HBox bar, int index, String i18nKey) {
        ToggleButton btn = new ToggleButton(I18nUtil.getString(i18nKey));
        btn.getStyleClass().add("code-deck-toggle");
        btn.setToggleGroup(toggleGroup);
        btn.setMinWidth(68);
        btn.setMaxHeight(28);
        btn.setOnAction(e -> {
            if (suppressAction) {
                return;
            }
            if (btn.isSelected()) {
                selectTab(index);
            }
        });
        buttons[index] = btn;
        bar.getChildren().add(btn);
    }

    /**
     * 切换到指定索引的标签页,首次切换时触发懒加载
     */
    private void selectTab(int index) {
        if (index < 0 || index >= TAB_COUNT) {
            return;
        }
        cancelGen.incrementAndGet(); // 取消旧任务
        activeIndex.set(index);

        if (panels[index] == null) {
            panels[index] = createPanel(index);
        }
        AbstractCodeContentPanel panel = panels[index];
        contentArea.getChildren().setAll(panel);

        // Code 面板由外部 setSourcePanel 填充,不在此触发异步加载
        if (index != TAB_CODE && !panel.isLoaded() && !panel.isError()) {
            Object token = new Object();
            panel.loadAsync(token);
        }

        suppressAction = true;
        buttons[index].setSelected(true);
        suppressAction = false;
    }

    /** 按索引创建对应面板,注入字体参数供 createContent() 使用 */
    private AbstractCodeContentPanel createPanel(int index) {
        AbstractCodeContentPanel panel = switch (index) {
            case TAB_CODE -> new SourceContentPanel(sourceCode);
            case TAB_SMALI -> new SmaliContentPanel(classBytes);
            case TAB_BYTECODE -> new BytecodeContentPanel(classBytes);
            case TAB_SIMPLE -> new SimpleContentPanel(sourceCode);
            case TAB_HEX -> new HexContentPanel(classBytes, theme);
            default -> throw new IllegalArgumentException("未知面板索引: " + index);
        };
        panel.setFontSettings(fontFamily, fontSize, lineNumbersEnabled);
        return panel;
    }

    /** 更新所有已加载面板的字体族和字号设置 */
    public void applyFontSettings(int newFontSize, String newFontFamily) {
        this.fontSize = newFontSize;
        this.fontFamily = newFontFamily != null && !newFontFamily.isBlank() ? newFontFamily : "Consolas";
        javafx.scene.text.Font font = loadFont();
        for (AbstractCodeContentPanel panel : panels) {
            if (panel != null) {
                panel.fontSize = this.fontSize;
                panel.fontFamily = this.fontFamily;
                var area = getCodeArea(panel);
                if (area != null) {
                    area.setFont(font);
                }
            }
        }
    }

    /** 切换所有面板的行号显示状态 */
    public void setLineNumbersEnabled(boolean enabled) {
        this.lineNumbersEnabled = enabled;
        for (AbstractCodeContentPanel panel : panels) {
            if (panel != null) {
                panel.lineNumbersEnabled = enabled;
                var area = getCodeArea(panel);
                if (area != null) {
                    LineNumberGutter.setEnabled(area, enabled);
                }
            }
        }
    }

    /** 加载字体,优先级：内置 FiraCode → 指定字体族 → Consolas 回退 */
    private javafx.scene.text.Font loadFont() {
        try {
            java.net.URL url = getClass().getResource("/ttf/FiraCode-Light.ttf");
            if (url != null) {
                return javafx.scene.text.Font.loadFont(url.toExternalForm(), fontSize);
            }
        } catch (Exception ignored) {
            log.debug("加载自定义字体失败,回退到系统字体", ignored);
        }
        if (fontFamily != null && !fontFamily.isBlank()) {
            return javafx.scene.text.Font.font(fontFamily, fontSize);
        }
        return javafx.scene.text.Font.font("Consolas", fontSize);
    }

    /** @return 当前激活的面板索引 */
    public int getActiveIndex() {
        return activeIndex.get();
    }

    /**
     * 替换源码面板并同步源码缓存
     *
     * @param newSource 新源码
     * @param panel     已按当前主题和导航元数据创建好的源码面板
     */
    public void replaceSourcePanel(String newSource, SourceContentPanel panel) {
        if (panel == null) {
            return;
        }
        AbstractCodeContentPanel oldSource = panels[TAB_CODE];
        if (oldSource != null && oldSource != panel) {
            oldSource.dispose();
        }
        this.sourceCode = newSource;
        panels[TAB_CODE] = panel;
        if (activeIndex.get() == TAB_CODE) {
            contentArea.getChildren().setAll(panel);
        }
        resetSimplePanel();
    }

    /** @return 源码面板中的 CodeArea（可能为 null） */
    public SourceContentPanel getSourcePanel() {
        return (SourceContentPanel) panels[TAB_CODE];
    }

    /**
     * 用外部配置的 SourceContentPanel 替换 Code 面板
     *
     * @param panel 外部创建的 SourceContentPanel（带完整 theme/font 配置）
     */
    public void setSourcePanel(SourceContentPanel panel) {
        panels[TAB_CODE] = panel;
        if (activeIndex.get() == TAB_CODE) {
            contentArea.getChildren().setAll(panel);
        }
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
        resetSimplePanel();
    }

    /** 更新 Code 面板显示文本（如注释装饰后）,不改变 Simple 面板所基于的原始源码缓存 */
    public void updateDisplayedSource(String displaySource) {
        if (panels[TAB_CODE] instanceof SourceContentPanel sp) {
            sp.setDisplayedSourceCode(displaySource);
        }
    }

    /** 销毁并重置 Simple 面板,下次切换时用新源码重建 */
    private void resetSimplePanel() {
        boolean simpleActive = activeIndex.get() == TAB_SIMPLE;
        if (panels[TAB_SIMPLE] != null) {
            panels[TAB_SIMPLE].dispose();
            panels[TAB_SIMPLE] = null; // 下次切换时用新源码重建
        }
        if (simpleActive) {
            selectTab(TAB_SIMPLE);
        }
    }

    /** @return 当前取消分代号,用于外部判断任务是否过期 */
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

    /** @return 指定索引的底部切换按钮 */
    public ToggleButton getButton(int index) {
        return buttons[index];
    }

    /** 程序化选中指定标签 */
    public void setSelected(int index) {
        selectTab(index);
    }

    /** 设置编辑器主题（用于 HEX 面板配色） */
    public void setTheme(VsCodeThemeLoader.ThemeData newTheme) {
        this.theme = newTheme;
        if (panels[TAB_HEX] instanceof HexContentPanel hex) {
            hex.reapplyTheme(newTheme);
        }
    }

    /** 释放所有面板资源 */
    public void dispose() {
        cancelGen.incrementAndGet();
        for (AbstractCodeContentPanel panel : panels) {
            if (panel != null) {
                panel.dispose();
            }
        }
    }
}
