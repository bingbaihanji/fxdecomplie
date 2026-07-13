package com.bingbaihanji.fxdecomplie.ui.hex.renderer;

import com.bingbaihanji.fxdecomplie.ui.hex.HexDataProvider;
import com.bingbaihanji.fxdecomplie.ui.hex.HexViewConfig;
import com.bingbaihanji.fxdecomplie.ui.hex.util.HexViewMetrics;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Renders a MiniMap (colored overview strip) in the scrollbar gutter area.
 * Each "pixel row" represents a range of file rows, colored by data content.
 */
public class MiniMapRenderer {

    private static final Color[] VALUE_COLORS = {
            Color.rgb(40, 40, 45),    // 0x00-heavy
            Color.rgb(60, 60, 120),   // low values
            Color.rgb(80, 120, 60),   // mid-low
            Color.rgb(120, 100, 50),  // mid
            Color.rgb(180, 100, 40),  // mid-high
            Color.rgb(200, 60, 60),   // high
            Color.rgb(220, 50, 180),  // very high
    };
    private final GraphicsContext gc;

    public MiniMapRenderer(GraphicsContext gc) {
        this.gc = gc;
    }

    /**
     * Draw the minimap in a vertical strip.
     * @param x          left x of minimap area
     * @param y          top y
     * @param width      minimap width in pixels
     * @param height     minimap height in pixels
     * @param provider   data source
     * @param metrics    layout metrics
     * @param config     view config
     * @param scrollRow  current top row index
     * @param totalRows  total number of rows in file
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

        // Viewport indicator
        int visibleRows = metrics.visibleRows(height);
        double viewportTop = (double) scrollRow / totalRows * height;
        double viewportHeight = (double) visibleRows / totalRows * height;
        gc.setFill(Color.rgb(255, 255, 255, 0.15));
        gc.fillRect(x, y + viewportTop, width, Math.max(2, viewportHeight));

        // Draw per-pixel-row colors
        for (int pixelRow = 0; pixelRow < (int) height; pixelRow++) {
            long fileRow = (long) (pixelRow * rowsPerSample);
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

    /** Compute a summary color for a row of bytes (average of non-zero bytes) */
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
