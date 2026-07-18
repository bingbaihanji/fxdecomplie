package com.bingbaihanji.fxdecomplie.ui.hex.tooltip;

import com.bingbaihanji.fxdecomplie.ui.hex.HexDataProvider;
import com.bingbaihanji.fxdecomplie.ui.hex.HexViewController;
import com.bingbaihanji.fxdecomplie.ui.hex.analyzer.DataAnalyzer;
import com.bingbaihanji.fxdecomplie.ui.hex.model.PatternModel;
import com.bingbaihanji.fxdecomplie.ui.hex.util.HexFonts;
import com.bingbaihanji.fxdecomplie.ui.hex.util.HexViewMetrics;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 在 {@link javafx.scene.canvas.Canvas} 上渲染悬停工具提示,用于展示指定地址处的详细字节分析信息 
 * <p>
 * 该渲染器为静态工具类,通过 {@link #draw} 方法直接在给定的图形上下文中绘制一个信息框,
 * 内容包括地址、原始字节值、所在区域路径以及各数据分析器生成的标签-值对 
 * </p>
 * <p>
 * 工具提示样式为深色半透明背景、青色边框,内部使用等宽字体,并自动调整位置以避免超出画布边界 
 * </p>
 *
 * @author BingBaiHanJi
 * @see HexViewController
 * @see DataAnalyzer
 * @see PatternModel
 */
public final class HexTooltipRenderer {

    /** 内边距(像素) */
    private static final double PAD = 6;

    /** 每行文字的高度(像素) */
    private static final double LINE_H = 16;

    /** 工具提示背景色(半透明深色) */
    private static final Color BG = Color.rgb(0x1E, 0x1E, 0x22, 0xF2 / 255.0);

    /** 边框颜色(青色,带透明度) */
    private static final Color BORDER = Color.rgb(0x00, 0x88, 0x88, 0.9);

    /** 标签文本颜色(灰色) */
    private static final Color LABEL_COLOR = Color.rgb(0xA0, 0xA0, 0xA0);

    /** 工具提示中使用的等宽字体(Fira Code Light,12pt) */
    private static final Font MONO = HexFonts.firaCodeLight(12);

    /** 用于测量文本宽度的共享 {@link Text} 节点,避免重复创建对象 */
    private static final Text MEASURE = new Text();

    /**
     * 私有构造方法,防止实例化 
     */
    private HexTooltipRenderer() {
    }

    /**
     * 在给定的 {@link GraphicsContext} 上绘制悬停工具提示 
     * <p>
     * 方法会从 {@code provider} 中读取从 {@code address} 开始的至多 16 个字节,
     * 结合 {@code patternModel} 的区域信息和 {@link HexViewController} 中的分析器结果,
     * 构建多行文本,测量尺寸后自动调整绘制位置,确保提示框完全可见 
     * </p>
     *
     * @param gc            图形上下文,用于绘制
     * @param mouseX        鼠标在画布上的 X 坐标(用于定位提示框)
     * @param mouseY        鼠标在画布上的 Y 坐标
     * @param address       当前悬停的起始地址
     * @param provider      数据提供者,用于读取原始字节
     * @param patternModel  模式模型,提供区域路径和描述信息
     * @param metrics       度量信息(本方法未使用,保留以供扩展)
     * @param canvasWidth   画布宽度(用于边界检查)
     * @param canvasHeight  画布高度(用于边界检查)
     * @return 绘制的提示框总高度(像素),便于调用方调整布局
     */
    public static double draw(GraphicsContext gc, double mouseX, double mouseY,
                              long address, HexDataProvider provider, PatternModel patternModel,
                              HexViewMetrics metrics,
                              double canvasWidth, double canvasHeight) {

        long size = provider.getSize();
        if (address >= size) {
            return 0;
        }

        // 读取从 address 开始的至多 16 个字节(或直到数据末尾)
        int bufLen = (int) Math.min(16, size - address);
        byte[] buf = new byte[bufLen];
        provider.read(address, buf, 0, bufLen);

        HexViewController ctrl = HexViewController.getInstance();

        // 构建工具提示的每一行
        var lines = new ArrayList<TooltipLine>();

        // 第一行：地址信息
        lines.add(new TooltipLine(
                String.format("Addr: 0x%08X  (+0x%X)", address, address),
                Color.rgb(0x88, 0xC0, 0xFF)));

        // 第二行：原始字节值(十进制、二进制、ASCII 字符)
        int rawByte = buf[0] & 0xFF;
        char ascii = (rawByte >= 32 && rawByte < 127) ? (char) rawByte : '·';
        lines.add(new TooltipLine(
                String.format("Raw:  %02X  dec=%d  bin=%s  '%c'", rawByte, rawByte,
                        String.format("%8s", Integer.toBinaryString(rawByte)).replace(' ', '0'), ascii),
                Color.rgb(0xCC, 0xCC, 0xCC)));

        // 如果当前地址在模式模型中有区域信息,则添加区域路径和描述
        String path = patternModel.getPathAt(address);
        if (path != null) {
            lines.add(new TooltipLine("---", LABEL_COLOR));
            lines.add(new TooltipLine("Region: " + path, Color.rgb(0xFF, 0xD0, 0x70)));
            for (PatternModel.Region r : patternModel.getRegionsAt(address)) {
                if (r.description() != null && !r.description().isEmpty()) {
                    lines.add(new TooltipLine("  " + r.description(), LABEL_COLOR));
                }
            }
        }

        // 添加数据分析器的结果
        lines.add(new TooltipLine("---", LABEL_COLOR));
        List<DataAnalyzer.Result> results = ctrl.analyze(address, buf, 0, bufLen);
        if (results.isEmpty()) {
            lines.add(new TooltipLine("(no analyzers)", LABEL_COLOR));
        } else {
            for (var r : results) {
                lines.add(new TooltipLine(
                        String.format("%-14s %s", r.label() + ":", r.value()), r.color()));
            }
        }

        // 测量所有行的最大宽度(使用共享的 MEASURE 节点)
        MEASURE.setFont(MONO);
        double maxW = 0;
        for (TooltipLine line : lines) {
            MEASURE.setText(line.text);
            double w = MEASURE.getLayoutBounds().getWidth();
            if (w > maxW) {
                maxW = w;
            }
        }
        double boxW = maxW + PAD * 2;
        double boxH = lines.size() * LINE_H + PAD * 2;

        // 计算提示框位置,优先显示在鼠标右下方,若超出边界则调整
        double bx = mouseX + 16;
        double by = mouseY;
        if (bx + boxW > canvasWidth) {
            bx = mouseX - boxW - 16;
        }
        if (by + boxH > canvasHeight) {
            by = canvasHeight - boxH - 4;
        }
        if (bx < 0) {
            bx = 4;
        }
        if (by < 0) {
            by = 4;
        }

        // 绘制提示框(背景圆角矩形 + 边框)
        gc.save();
        gc.setFill(BG);
        gc.fillRoundRect(bx, by, boxW, boxH, 6, 6);
        gc.setStroke(BORDER);
        gc.setLineWidth(1);
        gc.strokeRoundRect(bx, by, boxW, boxH, 6, 6);

        // 逐行绘制文本
        gc.setFont(MONO);
        gc.setTextBaseline(VPos.TOP);
        gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        double ty = by + PAD;
        for (TooltipLine line : lines) {
            gc.setFill(line.color);
            gc.fillText(line.text, bx + PAD, ty);
            ty += LINE_H;
        }
        gc.restore();

        return boxH;
    }

    /**
     * 工具提示中的一行文本及其颜色 
     *
     * @param text  要显示的文本内容
     * @param color 该行文本的颜色
     */
    private record TooltipLine(String text, Color color) {
    }
}