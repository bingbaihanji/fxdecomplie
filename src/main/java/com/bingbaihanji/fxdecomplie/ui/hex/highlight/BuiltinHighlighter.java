package com.bingbaihanji.fxdecomplie.ui.hex.highlight;

import com.bingbaihanji.fxdecomplie.ui.hex.HexDataProvider;
import com.bingbaihanji.fxdecomplie.ui.hex.model.PatternModel;

public interface BuiltinHighlighter {
    String getName();
    boolean matches(HexDataProvider provider);
    void highlight(HexDataProvider provider, PatternModel model);
}
