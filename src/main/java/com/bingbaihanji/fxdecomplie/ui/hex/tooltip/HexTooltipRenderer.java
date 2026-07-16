package com.bingbaihanji.fxdecomplie.ui.hex.tooltip;

import com.bingbaihanji.fxdecomplie.ui.hex.HexDataProvider;
import com.bingbaihanji.fxdecomplie.ui.hex.HexViewController;
import com.bingbaihanji.fxdecomplie.ui.hex.model.PatternModel;
import com.bingbaihanji.fxdecomplie.ui.hex.util.HexFonts;
import com.bingbaihanji.fxdecomplie.ui.hex.util.HexViewMetrics;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.util.List;

/**
 * Renders a hover tooltip on the Canvas showing detailed byte analysis.
 */
public final class HexTooltipRenderer {

    private static final double PAD = 6;
    private static final double LINE_H = 16;
    private static final Color BG = Color.rgb(0x1E, 0x1E, 0x22, 0xF2 / 255.0);
    private static final Color BORDER = Color.rgb(0x00, 0x88, 0x88, 0.9);
    private static final Color LABEL_COLOR = Color.rgb(0xA0, 0xA0, 0xA0);
    private static final javafx.scene.text.Font MONO = HexFonts.firaCodeLight(12);
    private static final Text MEASURE = new Text();

    private HexTooltipRenderer() {
    }

    public static double draw(GraphicsContext gc, double mouseX, double mouseY,
                              long address, HexDataProvider provider, PatternModel patternModel,
                              HexViewMetrics metrics,
                              double canvasWidth, double canvasHeight) {

        long size = provider.getSize();
        if (address >= size) {
            return 0;
        }

        int bufLen = (int) Math.min(16, size - address);
        byte[] buf = new byte[bufLen];
        provider.read(address, buf, 0, bufLen);

        HexViewController ctrl = HexViewController.getInstance();
        PatternModel pm = patternModel;

        // Build lines
        var lines = new java.util.ArrayList<TooltipLine>();

        lines.add(new TooltipLine(
                String.format("Addr: 0x%08X  (+0x%X)", address, address),
                Color.rgb(0x88, 0xC0, 0xFF)));

        int rawByte = address < size ? buf[0] & 0xFF : 0;
        char ascii = (rawByte >= 32 && rawByte < 127) ? (char) rawByte : '·';
        lines.add(new TooltipLine(
                String.format("Raw:  %02X  dec=%d  bin=%s  '%c'", rawByte, rawByte,
                        String.format("%8s", Integer.toBinaryString(rawByte)).replace(' ', '0'), ascii),
                Color.rgb(0xCC, 0xCC, 0xCC)));

        String path = pm.getPathAt(address);
        if (path != null) {
            lines.add(new TooltipLine("---", LABEL_COLOR));
            lines.add(new TooltipLine("Region: " + path, Color.rgb(0xFF, 0xD0, 0x70)));
            for (PatternModel.Region r : pm.getRegionsAt(address)) {
                if (r.description() != null && !r.description().isEmpty()) {
                    lines.add(new TooltipLine("  " + r.description(), LABEL_COLOR));
                }
            }
        }

        lines.add(new TooltipLine("---", LABEL_COLOR));
        List<com.bingbaihanji.fxdecomplie.ui.hex.analyzer.DataAnalyzer.Result> results = ctrl.analyze(address, buf, 0, bufLen);
        if (results.isEmpty()) {
            lines.add(new TooltipLine("(no analyzers)", LABEL_COLOR));
        } else {
            for (var r : results) {
                lines.add(new TooltipLine(
                        String.format("%-14s %s", r.label() + ":", r.value()), r.color()));
            }
        }

        // Measure (reuse shared Text node)
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

        // Position
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

        gc.save();
        // Draw box
        gc.setFill(BG);
        gc.fillRoundRect(bx, by, boxW, boxH, 6, 6);
        gc.setStroke(BORDER);
        gc.setLineWidth(1);
        gc.strokeRoundRect(bx, by, boxW, boxH, 6, 6);

        // Draw text
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

    private record TooltipLine(String text, Color color) {
    }
}
