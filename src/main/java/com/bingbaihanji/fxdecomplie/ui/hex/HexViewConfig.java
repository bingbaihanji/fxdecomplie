package com.bingbaihanji.fxdecomplie.ui.hex;

import com.bingbaihanji.fxdecomplie.ui.hex.util.HexFonts;
import javafx.beans.property.*;
import javafx.scene.text.Font;

public class HexViewConfig {
    private final IntegerProperty bytesPerRow = new SimpleIntegerProperty(16);
    private final BooleanProperty upperCaseHex = new SimpleBooleanProperty(true);
    private final BooleanProperty grayOutZero = new SimpleBooleanProperty(true);
    private final BooleanProperty showAscii = new SimpleBooleanProperty(true);
    private final BooleanProperty showMiniMap = new SimpleBooleanProperty(false);
    private final IntegerProperty miniMapWidth = new SimpleIntegerProperty(5);
    private final ObjectProperty<Font> font = new SimpleObjectProperty<>(HexFonts.defaultHexFont());
    private final IntegerProperty addressWidth = new SimpleIntegerProperty(8);
    private final Runnable onChanged;

    public HexViewConfig(Runnable onChanged) {
        this.onChanged = onChanged;
        bytesPerRow.addListener((obs, o, n) -> fire());
        upperCaseHex.addListener((obs, o, n) -> fire());
        grayOutZero.addListener((obs, o, n) -> fire());
        showAscii.addListener((obs, o, n) -> fire());
        showMiniMap.addListener((obs, o, n) -> fire());
        miniMapWidth.addListener((obs, o, n) -> fire());
        font.addListener((obs, o, n) -> fire());
        addressWidth.addListener((obs, o, n) -> fire());
    }

    private void fire() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    public int getBytesPerRow() { return bytesPerRow.get(); }
    public void setBytesPerRow(int v) { bytesPerRow.set(Math.max(1, Math.min(v, 128))); }
    public IntegerProperty bytesPerRowProperty() { return bytesPerRow; }

    public boolean isUpperCaseHex() { return upperCaseHex.get(); }
    public void setUpperCaseHex(boolean v) { upperCaseHex.set(v); }
    public BooleanProperty upperCaseHexProperty() { return upperCaseHex; }

    public boolean isGrayOutZero() { return grayOutZero.get(); }
    public void setGrayOutZero(boolean v) { grayOutZero.set(v); }
    public BooleanProperty grayOutZeroProperty() { return grayOutZero; }

    public boolean isShowAscii() { return showAscii.get(); }
    public void setShowAscii(boolean v) { showAscii.set(v); }
    public BooleanProperty showAsciiProperty() { return showAscii; }

    public boolean isShowMiniMap() { return showMiniMap.get(); }
    public void setShowMiniMap(boolean v) { showMiniMap.set(v); }
    public BooleanProperty showMiniMapProperty() { return showMiniMap; }

    public int getMiniMapWidth() { return miniMapWidth.get(); }
    public void setMiniMapWidth(int v) { miniMapWidth.set(Math.max(1, Math.min(v, 25))); }
    public IntegerProperty miniMapWidthProperty() { return miniMapWidth; }

    public Font getFont() { return font.get(); }
    public void setFont(Font v) { if (v != null) { font.set(v); } }
    public ObjectProperty<Font> fontProperty() { return font; }

    public int getAddressWidth() { return addressWidth.get(); }
    public void setAddressWidth(int v) { addressWidth.set(v); }
    public IntegerProperty addressWidthProperty() { return addressWidth; }
}
