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
 * Renders the hex editor grid onto a JavaFX Canvas.
 */
public class HexGridRenderer {

    private static final Color ADDRESS_COLOR = Color.rgb(120, 120, 120);
    private static final Color HEADER_BG = Color.rgb(35, 35, 40);
    private static final Color HEADER_TEXT_COLOR = Color.rgb(180, 180, 180);
    private static final Color HEADER_RULE_COLOR = Color.rgb(55, 55, 62);
    private static final Color ASCII_SEPARATOR_COLOR = Color.rgb(80, 80, 80);
    private static final Color SELECTION_FRAME_COLOR = Color.rgb(200, 200, 200);
    private static final Color DEFAULT_TEXT_COLOR = Color.rgb(220, 220, 220);
    private static final Color GRID_BG = Color.rgb(25, 25, 28);
    private final GraphicsContext gc;
    private HexViewMetrics metrics;
    private byte[] rowBuf; // reused buffer

    public HexGridRenderer(GraphicsContext gc, HexViewMetrics metrics) {
        this.gc = gc;
        setMetrics(metrics);
    }

    private static boolean sameBackground(Color a, Color b) {
        if (a == null || b == null) {
            return false;
        }
        return Double.compare(a.getRed(), b.getRed()) == 0
                && Double.compare(a.getGreen(), b.getGreen()) == 0
                && Double.compare(a.getBlue(), b.getBlue()) == 0
                && Double.compare(a.getOpacity(), b.getOpacity()) == 0;
    }

    /** Call when metrics change (font, bytesPerRow, etc.) */
    public void setMetrics(HexViewMetrics metrics) {
        this.metrics = metrics;
        this.rowBuf = new byte[metrics.getBytesPerRow()];
    }

    public void draw(HexDataProvider provider, SelectionModel selection,
                     HighlightModel highlights, HexViewConfig config,
                     long scrollRow, double canvasWidth, double canvasHeight) {

        Font font = config.getFont();
        gc.setFont(font);
        gc.setTextBaseline(VPos.BASELINE);
        gc.setTextAlign(TextAlignment.LEFT);

        // Clear
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

            // --- Address ---
            String addrFmt = config.isUpperCaseHex() ? "%0" + config.getAddressWidth() + "X" : "%0" + config.getAddressWidth() + "x";
            gc.setFill(ADDRESS_COLOR);
            gc.fillText(String.format(addrFmt + ": ", baseAddr), 0, y + metrics.getRowHeight() - 2);

            // --- Hex background regions ---
            for (int col = 0; col < bytesPerRow; col++) {
                if (col >= bytesRead) {
                    continue;
                }

                long addr = baseAddr + col;
                var bgOpt = highlights.getBackgroundColor(addr);
                if (bgOpt.isPresent()) {
                    double cellX = metrics.getHexCellX(col);
                    double cellW = metrics.getHexColWidth();
                    if (col + 1 < bytesRead && sameBackground(bgOpt.get(),
                            highlights.getBackgroundColor(addr + 1).orElse(null))) {
                        double nextX = metrics.getHexCellX(col + 1);
                        cellW = nextX - cellX;
                    }
                    gc.setFill(bgOpt.get());
                    gc.fillRect(cellX, y, cellW, metrics.getRowHeight());
                }
            }

            // --- Hex columns ---
            for (int col = 0; col < bytesPerRow; col++) {
                if (col >= bytesRead) {
                    continue;
                }
                double cellX = metrics.getHexCellX(col);
                long addr = baseAddr + col;
                byte data = rowBuf[col];

                var fgOpt = highlights.getForegroundColor(addr, data);
                Color fg = fgOpt.orElse(DEFAULT_TEXT_COLOR);

                if (selection.contains(addr)) {
                    gc.setStroke(SELECTION_FRAME_COLOR);
                    gc.setLineWidth(1.0);
                    gc.strokeRect(cellX, y, metrics.getHexColWidth(), metrics.getRowHeight());
                }

                String hex = config.isUpperCaseHex()
                        ? String.format("%02X", data & 0xFF)
                        : String.format("%02x", data & 0xFF);
                gc.setFill(fg);
                gc.fillText(hex, cellX + 1, y + metrics.getRowHeight() - 2);
            }

            // --- ASCII column ---
            if (config.isShowAscii()) {
                double asciiStartX = metrics.getAsciiCellX(0) - 7;
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

    public void drawHeader(double y, HexViewConfig config) {
        int bytesPerRow = config.getBytesPerRow();
        Font font = config.getFont();
        gc.setFont(font);
        gc.setTextBaseline(VPos.BASELINE);
        gc.setTextAlign(TextAlignment.LEFT);

        gc.setFill(HEADER_BG);
        gc.fillRect(0, y, metrics.getTotalWidth(), metrics.getHeaderHeight());
        gc.setStroke(HEADER_RULE_COLOR);
        gc.setLineWidth(1.0);
        gc.strokeLine(0, y + metrics.getHeaderHeight() - 0.5,
                metrics.getTotalWidth(), y + metrics.getHeaderHeight() - 0.5);

        gc.setFill(HEADER_TEXT_COLOR);
        double textY = y + (metrics.getHeaderHeight() + metrics.getTextHeight()) / 2.0 - 2.0;
        gc.fillText("Address", 0, textY);

        for (int col = 0; col < bytesPerRow; col++) {
            String hdr = config.isUpperCaseHex()
                    ? String.format("%02X", col)
                    : String.format("%02x", col);
            gc.fillText(hdr, metrics.getHexCellX(col) + 1, textY);
        }
        if (config.isShowAscii()) {
            gc.fillText("ASCII", metrics.getAsciiCellX(0), textY);
        }
    }
}
