package com.bingbaihanji.fxdecomplie.ui.hex.renderer;

import com.bingbaihanji.fxdecomplie.ui.hex.HexDataProvider;
import com.bingbaihanji.fxdecomplie.ui.hex.HexViewConfig;
import com.bingbaihanji.fxdecomplie.ui.hex.model.HighlightModel;
import com.bingbaihanji.fxdecomplie.ui.hex.model.SelectionModel;
import com.bingbaihanji.fxdecomplie.ui.hex.util.HexViewMetrics;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * 在 JavaFX Canvas 上绘制十六进制编辑器网格的渲染器 
 * <p>
 * 负责绘制地址列、十六进制字节网格(支持每行字节数自定义)、ASCII 列,
 * 以及选区边框、前景/背景高亮等视觉效果 
 * </p>
 *
 * @author BingBaiHanJi
 * @see HexViewMetrics
 * @see HighlightModel
 * @see SelectionModel
 * @see HexViewConfig
 */
public class HexGridRenderer {

    // ---------- 颜色常量 ----------
    private static final Color ADDRESS_COLOR = Color.rgb(120, 120, 120);
    private static final Color HEADER_BG = Color.rgb(35, 35, 40);
    private static final Color HEADER_TEXT_COLOR = Color.rgb(180, 180, 180);
    private static final Color HEADER_RULE_COLOR = Color.rgb(55, 55, 62);
    private static final Color ASCII_SEPARATOR_COLOR = Color.rgb(80, 80, 80);
    private static final Color SELECTION_FRAME_COLOR = Color.rgb(200, 200, 200);
    private static final Color DEFAULT_TEXT_COLOR = Color.rgb(220, 220, 220);
    private static final Color GRID_BG = Color.rgb(25, 25, 28);

    // ---------- 成员变量 ----------
    private final GraphicsContext gc;          // 图形上下文
    private HexViewMetrics metrics;            // 度量信息(字体、间距、列宽等)
    private byte[] rowBuf;                     // 每行读取数据的复用缓冲区

    /**
     * 构造渲染器 
     *
     * @param gc      目标画布的 {@link GraphicsContext}
     * @param metrics 初始度量信息
     */
    public HexGridRenderer(GraphicsContext gc, HexViewMetrics metrics) {
        this.gc = gc;
        setMetrics(metrics);
    }

    /**
     * 判断两个颜色是否完全相同(RGBA 均相等) 
     *
     * @param a 颜色 a
     * @param b 颜色 b
     * @return 若完全相同返回 {@code true}
     */
    private static boolean sameBackground(Color a, Color b) {
        if (a == null || b == null) {
            return false;
        }
        return Double.compare(a.getRed(), b.getRed()) == 0
                && Double.compare(a.getGreen(), b.getGreen()) == 0
                && Double.compare(a.getBlue(), b.getBlue()) == 0
                && Double.compare(a.getOpacity(), b.getOpacity()) == 0;
    }

    /**
     * 更新度量信息(当字体、每行字节数等变化时调用) 
     *
     * @param metrics 新的度量对象
     */
    public void setMetrics(HexViewMetrics metrics) {
        this.metrics = metrics;
        this.rowBuf = new byte[metrics.getBytesPerRow()];
    }

    /**
     * 绘制十六进制网格主体(包括地址列、十六进制列、ASCII 列以及高亮/选区效果) 
     *
     * @param provider     数据提供者,用于读取字节
     * @param selection    选区模型,用于绘制选区边框
     * @param highlights   高亮模型,提供前景/背景色
     * @param config       视图配置(大小写、是否显示 ASCII 等)
     * @param scrollRow    当前滚动到的行索引(从 0 开始)
     * @param canvasWidth  画布宽度
     * @param canvasHeight 画布高度
     */
    public void draw(HexDataProvider provider, SelectionModel selection,
                     HighlightModel highlights, HexViewConfig config,
                     long scrollRow, double canvasWidth, double canvasHeight) {

        Font font = config.getFont();
        gc.setFont(font);
        gc.setTextBaseline(VPos.BASELINE);
        gc.setTextAlign(TextAlignment.LEFT);

        // 清空背景
        gc.setFill(GRID_BG);
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        int bytesPerRow = config.getBytesPerRow();
        int visibleRows = metrics.visibleRows(canvasHeight);
        long totalRows = provider.getSize() == 0 ? 0
                : (provider.getSize() + bytesPerRow - 1) / bytesPerRow;

        if (rowBuf.length != bytesPerRow) {
            rowBuf = new byte[bytesPerRow];
        }

        for (int vy = 0; vy < visibleRows; vy++) {
            long rowIdx = scrollRow + vy;
            if (rowIdx >= totalRows) {
                break;
            }

            long baseAddr = rowIdx * bytesPerRow;
            double y = metrics.getRowY(vy);
            int bytesRead = provider.read(baseAddr, rowBuf, 0, bytesPerRow);

            // --- 绘制地址列 ---
            String addrFmt = config.isUpperCaseHex()
                    ? "%0" + config.getAddressWidth() + "X"
                    : "%0" + config.getAddressWidth() + "x";
            gc.setFill(ADDRESS_COLOR);
            gc.fillText(String.format(addrFmt + ": ", baseAddr),
                    0, y + metrics.getRowHeight() - 2);

            // --- 绘制十六进制列的背景高亮 ---
            for (int col = 0; col < bytesPerRow; col++) {
                if (col >= bytesRead) {
                    continue;
                }
                long addr = baseAddr + col;
                var bgOpt = highlights.getBackgroundColor(addr);
                if (bgOpt.isPresent()) {
                    double cellX = metrics.getHexCellX(col);
                    double cellW = metrics.getHexColWidth();
                    // 如果下一个字节背景相同,合并绘制(避免缝隙)
                    if (col + 1 < bytesRead && sameBackground(bgOpt.get(),
                            highlights.getBackgroundColor(addr + 1).orElse(null))) {
                        double nextX = metrics.getHexCellX(col + 1);
                        cellW = nextX - cellX;
                    }
                    gc.setFill(bgOpt.get());
                    gc.fillRect(cellX, y, cellW, metrics.getRowHeight());
                }
            }

            // --- 绘制十六进制字节 ---
            for (int col = 0; col < bytesPerRow; col++) {
                if (col >= bytesRead) {
                    continue;
                }
                double cellX = metrics.getHexCellX(col);
                long addr = baseAddr + col;
                byte data = rowBuf[col];

                // 获取前景色
                var fgOpt = highlights.getForegroundColor(addr, data);
                Color fg = fgOpt.orElse(DEFAULT_TEXT_COLOR);

                // 选区边框
                if (selection.contains(addr)) {
                    gc.setStroke(SELECTION_FRAME_COLOR);
                    gc.setLineWidth(1.0);
                    gc.strokeRect(cellX, y, metrics.getHexColWidth(), metrics.getRowHeight());
                }

                // 十六进制文本
                String hex = config.isUpperCaseHex()
                        ? String.format("%02X", data & 0xFF)
                        : String.format("%02x", data & 0xFF);
                gc.setFill(fg);
                gc.fillText(hex, cellX + 1, y + metrics.getRowHeight() - 2);
            }

            // --- ASCII 列(可选) ---
            if (config.isShowAscii()) {
                double asciiStartX = metrics.getAsciiCellX(0) - 7;
                // 绘制分隔线
                gc.setStroke(ASCII_SEPARATOR_COLOR);
                gc.setLineWidth(0.5);
                gc.strokeLine(asciiStartX, y, asciiStartX, y + metrics.getRowHeight());

                for (int col = 0; col < bytesPerRow && col < bytesRead; col++) {
                    double ax = metrics.getAsciiCellX(col);
                    long addr = baseAddr + col;
                    byte data = rowBuf[col];
                    int c = data & 0xFF;

                    var fgOpt = highlights.getForegroundColor(addr, data);
                    Color fg = fgOpt.orElse(DEFAULT_TEXT_COLOR);

                    if (selection.contains(addr)) {
                        gc.setStroke(SELECTION_FRAME_COLOR);
                        gc.setLineWidth(0.5);
                        gc.strokeRect(ax, y, metrics.getCharWidth(), metrics.getRowHeight());
                    }

                    char ch = (c >= 32 && c < 127) ? (char) c : '.';
                    gc.setFill(fg);
                    gc.fillText(String.valueOf(ch), ax, y + metrics.getRowHeight() - 2);
                }
            }
        }
    }

    /**
     * 绘制表头(地址标签、列编号、ASCII 标签) 
     *
     * @param y      表头起始 Y 坐标(通常为 0)
     * @param config 视图配置
     */
    public void drawHeader(double y, HexViewConfig config) {
        int bytesPerRow = config.getBytesPerRow();
        Font font = config.getFont();
        gc.setFont(font);
        gc.setTextBaseline(VPos.BASELINE);
        gc.setTextAlign(TextAlignment.LEFT);

        // 表头背景
        gc.setFill(HEADER_BG);
        gc.fillRect(0, y, metrics.getTotalWidth(), metrics.getHeaderHeight());
        // 底部边框线
        gc.setStroke(HEADER_RULE_COLOR);
        gc.setLineWidth(1.0);
        gc.strokeLine(0, y + metrics.getHeaderHeight() - 0.5,
                metrics.getTotalWidth(), y + metrics.getHeaderHeight() - 0.5);

        gc.setFill(HEADER_TEXT_COLOR);
        double textY = y + (metrics.getHeaderHeight() + metrics.getTextHeight()) / 2.0 - 2.0;

        // “Address” 标签
        gc.fillText("Address", 0, textY);

        // 列编号(00, 01, ...)
        for (int col = 0; col < bytesPerRow; col++) {
            String hdr = config.isUpperCaseHex()
                    ? String.format("%02X", col)
                    : String.format("%02x", col);
            gc.fillText(hdr, metrics.getHexCellX(col) + 1, textY);
        }

        // “ASCII” 标签
        if (config.isShowAscii()) {
            gc.fillText("ASCII", metrics.getAsciiCellX(0), textY);
        }
    }
}