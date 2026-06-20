package com.bingbaihanji.fxdecomplie.ui.code;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.SideDecorator;

import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * CodeArea 左侧行号栏工具类
 * <p>
 * JavaFX 25 incubator RichText 的内置行号由 {@code LineNumberDecorator} 生成,
 * 在当前暗色主题下不容易通过外部 CSS 稳定覆盖颜色因此这里使用自定义
 * {@link SideDecorator} 创建行号节点,并直接给 {@link Label} 设置灰色前景色
 */
public final class LineNumberGutter {

    /** 暗色主题下的行号颜色 */
    private static final Color LINE_NUMBER_COLOR = Color.web("#6f7a82");
    /** 行号 Label 的内联样式,用于覆盖控件默认白色文本 */
    private static final String LINE_NUMBER_STYLE = String.join("",
            "-fx-background-color: #252526;",
            "-fx-text-fill: #6f7a82;",
            "-fx-padding: 0 8 0 8;"
    );

    private LineNumberGutter() {
        throw new AssertionError("utility class");
    }

    /**
     * 设置 CodeArea 是否显示行号
     * <p>
     * 先关闭 CodeArea 内置行号,避免 JavaFX 默认白色行号与自定义行号同时出现；
     * 然后通过 leftDecorator 安装或移除暗色主题行号栏
     *
     * @param area    目标代码编辑区
     * @param enabled 是否显示行号
     */
    public static void setEnabled(CodeArea area, boolean enabled) {
        if (area.isLineNumbersEnabled()) {
            area.setLineNumbersEnabled(false);
        }
        area.setLeftDecorator(enabled ? new DarkLineNumberDecorator(area) : null);
    }

    private static final class DarkLineNumberDecorator implements SideDecorator {
        /** 绑定目标 CodeArea,用于让行号字体跟随编辑器字体 */
        private final CodeArea area;
        /** 行号格式化器,保持和 JavaFX 内置行号类似的分组显示 */
        private final DecimalFormat format = new DecimalFormat("#,##0");

        private DarkLineNumberDecorator(CodeArea area) {
            this.area = area;
        }

        @Override
        public double getPrefWidth(double viewWidth) {
            // 返回 0 表示宽度由 measurement node 动态测量
            return 0;
        }

        @Override
        public Node getMeasurementNode(int index) {
            // 用一组较宽的数字预估当前视图所需宽度,避免滚动时行号栏抖动
            String value = format.format(index + 300);
            char[] digits = new char[value.length()];
            Arrays.fill(digits, '8');
            return createNode(new String(digits));
        }

        @Override
        public Node getNode(int index) {
            // SideDecorator 的 index 从 0 开始,界面行号从 1 开始显示
            return createNode(format.format(index + 1));
        }

        /** 创建单个行号 Label,并强制应用暗色主题样式 */
        private Label createNode(String text) {
            Label label = new Label(text);
            label.getStyleClass().addAll("line-number-decorator", "app-line-number-decorator");
            label.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            label.setMinHeight(1);
            label.setPrefHeight(1);
            label.setAlignment(Pos.CENTER_RIGHT);
            label.setOpacity(1.0);
            label.setTextFill(LINE_NUMBER_COLOR);
            label.setStyle(LINE_NUMBER_STYLE);
            // 行号字体跟随 CodeArea,和正文保持一致的字号/字体族
            label.fontProperty().bind(area.fontProperty());
            return label;
        }
    }
}
