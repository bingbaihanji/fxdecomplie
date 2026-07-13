package com.bingbaihanji.fxdecomplie.ui.hex;

import com.bingbaihanji.fxdecomplie.ui.hex.analyzer.DataAnalyzer;
import com.bingbaihanji.fxdecomplie.ui.hex.analyzer.DataAnalyzers;
import com.bingbaihanji.fxdecomplie.ui.hex.highlight.BuiltinHighlighter;
import com.bingbaihanji.fxdecomplie.ui.hex.model.PatternModel;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton controller for global HexView configuration and services.
 */
public final class HexViewController {

    private static final HexViewController INSTANCE = new HexViewController();

    private final List<DataAnalyzer> analyzers = new CopyOnWriteArrayList<>();
    private final List<BuiltinHighlighter> highlighters = new CopyOnWriteArrayList<>();
    private final PatternModel patternModel = new PatternModel();
    private final Color selectionColor = rgba(0x60, 0xC0, 0x80, 0x80);
    private final Color tooltipBgColor = rgba(0x1E, 0x1E, 0x22, 0xF0);
    private final Color tooltipBorderColor = Color.rgb(0x50, 0x50, 0x55, 1.0);
    private final Color magicColor = rgba(0xF4, 0x47, 0x47, 0x40);
    private final Color versionColor = rgba(0x44, 0x9F, 0xD8, 0x40);
    private final Color poolColor = rgba(0x6E, 0xC0, 0x77, 0x35);
    private final Color accessColor = rgba(0xF0, 0x90, 0x30, 0x45);
    private final Color methodColor = rgba(0xA0, 0x70, 0xD0, 0x40);
    private final Color fieldColor = rgba(0xD0, 0x90, 0xD0, 0x40);
    private final Color interfaceColor = rgba(0x40, 0xC0, 0xC0, 0x40);
    private final Color attributeColor = rgba(0xC0, 0xC0, 0x40, 0x40);
    private final Color unknownColor = rgba(0x80, 0x80, 0x80, 0x30);
    private HexView hexView;

    private HexViewController() {
        DataAnalyzers.registerDefaults(this);
    }

    private static Color rgba(int r, int g, int b, int a) {
        return Color.rgb(r, g, b, a / 255.0);
    }

    public static HexViewController getInstance() {
        return INSTANCE;
    }

    public void attach(HexView hexView) {
        this.hexView = hexView;
        hexView.getHighlights().setPatternProvider(addr -> patternModel.getColorAt(addr));
        hexView.setOnHover(this::onHover);
    }

    public HexView getHexView() {
        return hexView;
    }

    public void registerAnalyzer(DataAnalyzer analyzer) {
        analyzers.add(analyzer);
    }

    public List<DataAnalyzer> getAnalyzers() {
        return analyzers;
    }

    public List<DataAnalyzer.Result> analyze(long address, byte[] fullData, int offset, int length) {
        List<DataAnalyzer.Result> results = new ArrayList<>();
        for (DataAnalyzer a : analyzers) {
            results.addAll(a.analyze(address, fullData, offset, length));
        }
        return results;
    }

    public void registerHighlighter(BuiltinHighlighter highlighter) {
        highlighters.add(highlighter);
    }

    public List<BuiltinHighlighter> getHighlighters() {
        return highlighters;
    }

    public void applyHighlights(HexDataProvider provider) {
        patternModel.clear();
        for (BuiltinHighlighter h : highlighters) {
            if (h.matches(provider)) {
                h.highlight(provider, patternModel);
                break;
            }
        }
        if (hexView != null) {
            hexView.markDirty();
        }
    }

    public PatternModel getPatternModel() {
        return patternModel;
    }

    private void onHover(long address, int size) {
    }

    public Color getSelectionColor() {
        return selectionColor;
    }

    public Color getMagicColor() {
        return magicColor;
    }

    public Color getVersionColor() {
        return versionColor;
    }

    public Color getPoolColor() {
        return poolColor;
    }

    public Color getAccessColor() {
        return accessColor;
    }

    public Color getMethodColor() {
        return methodColor;
    }

    public Color getFieldColor() {
        return fieldColor;
    }

    public Color getInterfaceColor() {
        return interfaceColor;
    }

    public Color getAttributeColor() {
        return attributeColor;
    }

    public Color getUnknownColor() {
        return unknownColor;
    }
}
