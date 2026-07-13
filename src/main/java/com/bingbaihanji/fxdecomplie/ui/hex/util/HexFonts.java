package com.bingbaihanji.fxdecomplie.ui.hex.util;

import javafx.scene.text.Font;

import java.io.InputStream;

public final class HexFonts {

    public static final String FIRA_CODE_LIGHT_RESOURCE = "/ttf/FiraCode-Light.ttf";
    private static final double DEFAULT_SIZE = 13.0;

    private HexFonts() {
    }

    public static Font defaultHexFont() {
        return firaCodeLight(DEFAULT_SIZE);
    }

    public static Font firaCodeLight(double size) {
        try (InputStream in = HexFonts.class.getResourceAsStream(FIRA_CODE_LIGHT_RESOURCE)) {
            if (in != null) {
                Font font = Font.loadFont(in, size);
                if (font != null) {
                    return font;
                }
            }
        } catch (Exception ignored) {
            // Fall through to platform monospace
        }
        return Font.font("Monospaced", size);
    }
}
