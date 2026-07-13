package com.bingbaihanji.fxdecomplie.ui.hex.model;

import javafx.scene.paint.Color;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class HighlightModel {

    @FunctionalInterface
    public interface HighlightCallback { Optional<Color> apply(long address, byte data); }

    @FunctionalInterface
    public interface PatternColorProvider { Color getColorAt(long address); }

    private final List<HighlightCallback> foregroundCallbacks = new CopyOnWriteArrayList<>();
    private final List<HighlightCallback> backgroundCallbacks = new CopyOnWriteArrayList<>();
    private final List<HighlightRegion> foregroundRegions = new CopyOnWriteArrayList<>();
    private final List<HighlightRegion> backgroundRegions = new CopyOnWriteArrayList<>();
    private volatile PatternColorProvider patternProvider;
    private volatile boolean grayOutZero = true;

    public void addForegroundCallback(HighlightCallback cb) { foregroundCallbacks.add(cb); }
    public void removeForegroundCallback(HighlightCallback cb) { foregroundCallbacks.remove(cb); }
    public void addForegroundRegion(long addr, long size, Color color) { foregroundRegions.add(new HighlightRegion(addr, size, color)); }

    public Optional<Color> getForegroundColor(long address, byte data) {
        for (var cb : foregroundCallbacks) { var c = cb.apply(address, data); if (c.isPresent()) return c; }
        for (var r : foregroundRegions) { if (r.contains(address)) return Optional.of(r.color); }
        if (grayOutZero && data == 0x00) return Optional.of(Color.gray(0.5));
        return Optional.empty();
    }

    public void addBackgroundCallback(HighlightCallback cb) { backgroundCallbacks.add(cb); }
    public void removeBackgroundCallback(HighlightCallback cb) { backgroundCallbacks.remove(cb); }
    public void addBackgroundRegion(long addr, long size, Color color) { backgroundRegions.add(new HighlightRegion(addr, size, color)); }
    public void setPatternProvider(PatternColorProvider p) { this.patternProvider = p; }

    public Optional<Color> getBackgroundColor(long address) {
        Color result = null;
        PatternColorProvider pp = patternProvider;
        if (pp != null) { Color pc = pp.getColorAt(address); if (pc != null) result = pc; }
        for (var r : backgroundRegions) { if (r.contains(address)) result = result == null ? r.color : blend(result, r.color); }
        for (var cb : backgroundCallbacks) { var c = cb.apply(address, (byte) 0); if (c.isPresent()) result = result == null ? c.get() : blend(result, c.get()); }
        return Optional.ofNullable(result);
    }

    public boolean isGrayOutZero() { return grayOutZero; }
    public void setGrayOutZero(boolean v) { this.grayOutZero = v; }
    public void clearAll() { foregroundCallbacks.clear(); backgroundCallbacks.clear(); foregroundRegions.clear(); backgroundRegions.clear(); }

    private static Color blend(Color below, Color above) {
        double a = above.getOpacity();
        double invA = 1.0 - a;
        return new Color(above.getRed() * a + below.getRed() * invA, above.getGreen() * a + below.getGreen() * invA,
                above.getBlue() * a + below.getBlue() * invA, Math.min(1.0, below.getOpacity() + a));
    }

    public record HighlightRegion(long address, long size, Color color) {
        public boolean contains(long addr) { return addr >= address && addr < address + size; }
    }
}
