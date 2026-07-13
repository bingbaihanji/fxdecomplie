package com.bingbaihanji.fxdecomplie.ui.hex.util;

import javafx.geometry.Bounds;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class HexViewMetrics {
    private static final double CELL_PADDING = 2.0;
    private static final double ADDRESS_GAP = 8.0;
    private static final double GROUP_GAP = 8.0;
    private static final double ASCII_GAP = 14.0;
    private static final double HEADER_VERTICAL_PADDING = 5.0;
    private final Font font;
    private final double charWidth;
    private final double textHeight;
    private final double rowHeight;
    private final int bytesPerRow;
    private final int addressWidth;
    private final double addressColWidth;
    private final double hexColWidth;
    private final double asciiColWidth;
    private final double totalWidth;
    private final double groupGapWidth;
    private final double headerHeight;

    public HexViewMetrics(Font font, int bytesPerRow, int addressWidth) {
        this.font = font;
        this.bytesPerRow = bytesPerRow;
        this.addressWidth = addressWidth;
        Text measureText = new Text("0");
        measureText.setFont(font);
        Bounds bounds = measureText.getLayoutBounds();
        this.charWidth = bounds.getWidth();
        this.textHeight = bounds.getHeight();
        this.rowHeight = Math.ceil(textHeight + 4.0);
        this.headerHeight = Math.ceil(rowHeight + HEADER_VERTICAL_PADDING * 2.0);
        this.addressColWidth = (addressWidth + 2) * charWidth + ADDRESS_GAP;
        this.hexColWidth = 2 * charWidth + CELL_PADDING;
        this.groupGapWidth = GROUP_GAP;
        this.asciiColWidth = bytesPerRow * charWidth;
        int groupGaps = (bytesPerRow - 1) / 8;
        this.totalWidth = addressColWidth + bytesPerRow * hexColWidth + groupGaps * groupGapWidth + ASCII_GAP + asciiColWidth;
    }

    public double getCharWidth() { return charWidth; }
    public double getTextHeight() { return textHeight; }
    public double getRowHeight() { return rowHeight; }
    public int getBytesPerRow() { return bytesPerRow; }
    public double getAddressColWidth() { return addressColWidth; }
    public double getHexColWidth() { return hexColWidth; }
    public double getGroupGapWidth() { return groupGapWidth; }
    public double getHeaderHeight() { return headerHeight; }
    public double getAsciiColWidth() { return asciiColWidth; }
    public double getTotalWidth() { return totalWidth; }
    public Font getFont() { return font; }

    public double getHexCellX(int col) {
        double x = addressColWidth;
        x += col * hexColWidth;
        x += (col / 8) * groupGapWidth;
        return x;
    }

    public double getAsciiCellX(int col) {
        int groupGaps = (bytesPerRow - 1) / 8;
        return addressColWidth + bytesPerRow * hexColWidth + groupGaps * groupGapWidth + ASCII_GAP + col * charWidth;
    }

    public double getRowY(int visibleRow) { return visibleRow * rowHeight; }

    public int pixelToHexColumn(double x) {
        double relX = x - addressColWidth;
        if (relX < 0) return -1;
        int col = 0;
        double curX = 0;
        while (col < bytesPerRow) {
            if (col > 0 && col % 8 == 0) {
                if (relX >= curX && relX < curX + groupGapWidth) return -1;
                curX += groupGapWidth;
            }
            if (relX >= curX && relX < curX + hexColWidth) return col;
            curX += hexColWidth;
            col++;
        }
        return -1;
    }

    public int pixelToAsciiColumn(double x) {
        double asciiStart = getAsciiCellX(0);
        double asciiEnd = asciiStart + asciiColWidth;
        if (x < asciiStart || x >= asciiEnd) return -1;
        return (int) ((x - asciiStart) / charWidth);
    }

    public int pixelToRow(double y) { return (int) (y / rowHeight); }
    public int visibleRows(double viewportHeight) { return Math.max(1, (int) Math.ceil(viewportHeight / rowHeight)); }
}
