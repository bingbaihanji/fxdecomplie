package com.bingbaihanji.fxdecomplie.ui.hex.analyzer;

import javafx.scene.paint.Color;

import java.util.List;

public interface DataAnalyzer {
    String getName();

    List<Result> analyze(long address, byte[] data, int offset, int length);

    record Result(String label, String value, Color color) {
    }
}
