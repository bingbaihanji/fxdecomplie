package com.bingbaihanji.fxdecomplie.ui.hex.renderer;

import com.bingbaihanji.fxdecomplie.ui.hex.HexDataProvider;
import com.bingbaihanji.fxdecomplie.ui.hex.HexViewConfig;
import com.bingbaihanji.fxdecomplie.ui.hex.util.HexViewMetrics;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * 在滚动条槽区域绘制 MiniMap(彩色概览条)的渲染器 
 * <p>
 * MiniMap 以像素行表示数据行的颜色摘要,每个像素行对应若干数据行,
 * 颜色反映该行中非零字节的平均值,帮助用户快速定位数据分布和热点区域 
 * 此外还会绘制当前视口指示框,显示可见范围在全局数据中的位置 
 * </p>
 *
 * @author BingBaiHanJi
 * @see HexDataProvider
 * @see HexViewConfig
 * @see HexViewMetrics
 */
public class MiniMapRenderer {

    /**
     * 按字节值分级的颜色映射表 
     * <ul>
     *   <li>索引 0：全零行(深灰)</li>
     *   <li>索引 1~5：低值到高值渐变</li>
     *   <li>索引 6：极高值(洋红色)</li>
     * </ul>
     */
    private static final Color[] VALUE_COLORS = {
            Color.rgb(40, 40, 45),    // 0x00-heavy
            Color.rgb(60, 60, 120),   // low values
            Color.rgb(80, 120, 60),   // mid-low
            Color.rgb(120, 100, 50),  // mid
            Color.rgb(180, 100, 40),  // mid-high
            Color.rgb(200, 60, 60),   // high
            Color.rgb(220, 50, 180),  // very high
    };

    /** 目标画布的图形上下文 */
    private final GraphicsContext gc;

    /**
     * 构造 MiniMap 渲染器 
     *
     * @param gc 目标画布的 {@link GraphicsContext}
     */
    public MiniMapRenderer(GraphicsContext gc) {
        this.gc = gc;
    }

    /**
     * 在指定区域绘制 MiniMap 
     * <p>
     * 绘制过程包括：
     * <ol>
     *   <li>为每个像素行计算对应数据行的平均颜色并填充</li>
     *   <li>在 MiniMap 上叠加半透明的视口指示矩形,表示当前可视范围</li>
     * </ol>
     * </p>
     *
     * @param x          MiniMap 区域的左边缘 X 坐标
     * @param y          MiniMap 区域的顶部 Y 坐标
     * @param width      MiniMap 区域的宽度(像素)
     * @param height     MiniMap 区域的高度(像素)
     * @param provider   数据提供者,用于读取字节数据
     * @param metrics    布局度量信息(用于计算可见行数)
     * @param config     视图配置(每行字节数等)
     * @param scrollRow  当前滚动到的行索引(从 0 开始)
     * @param totalRows  数据总行数
     */
    public void draw(double x, double y, double width, double height,
                     HexDataProvider provider, HexViewMetrics metrics,
                     HexViewConfig config, long scrollRow, long totalRows) {

        if (totalRows == 0 || height <= 0) {
            return;
        }

        int bytesPerRow = config.getBytesPerRow();
        byte[] rowBuf = new byte[bytesPerRow];

        double rowsPerPixel = (double) totalRows / height;
        int rowsPerSample = Math.max(1, (int) Math.ceil(rowsPerPixel));

        // --- 绘制视口指示框 ---
        int visibleRows = metrics.visibleRows(height);
        double viewportTop = (double) scrollRow / totalRows * height;
        double viewportHeight = (double) visibleRows / totalRows * height;
        gc.setFill(Color.rgb(255, 255, 255, 0.15));
        gc.fillRect(x, y + viewportTop, width, Math.max(2, viewportHeight));

        // --- 逐像素行绘制颜色条 ---
        for (int pixelRow = 0; pixelRow < (int) height; pixelRow++) {
            long fileRow = (long) pixelRow * rowsPerSample;
            if (fileRow >= totalRows) {
                break;
            }

            long addr = fileRow * bytesPerRow;
            int n = provider.read(addr, rowBuf, 0, bytesPerRow);

            if (n > 0) {
                Color rowColor = computeRowColor(rowBuf, n);
                gc.setFill(rowColor);
                gc.fillRect(x, y + pixelRow, width, 1);
            }
        }
    }

    /**
     * 计算一行字节的摘要颜色 
     * <p>
     * 算法：对非零字节求平均值,然后将平均值映射到 {@link #VALUE_COLORS} 颜色表,
     * 若全为零则返回颜色表第一个(深灰) 
     * </p>
     *
     * @param buf 包含行数据的字节数组
     * @param len 有效数据长度(≤ buf.length)
     * @return 该行对应的颜色
     */
    private Color computeRowColor(byte[] buf, int len) {
        long sum = 0;
        int count = 0;
        for (int i = 0; i < len; i++) {
            int v = buf[i] & 0xFF;
            if (v != 0) {
                sum += v;
                count++;
            }
        }
        if (count == 0) {
            return VALUE_COLORS[0];
        }
        int avg = (int) (sum / count);
        int idx = Math.min(VALUE_COLORS.length - 1, avg * VALUE_COLORS.length / 256);
        return VALUE_COLORS[idx];
    }
}