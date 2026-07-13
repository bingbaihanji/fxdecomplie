package com.bingbaihanji.fxdecomplie.ui.hex.format;

@FunctionalInterface
public interface CopyFormatter {
    String format(byte[] data, long offset);
}
