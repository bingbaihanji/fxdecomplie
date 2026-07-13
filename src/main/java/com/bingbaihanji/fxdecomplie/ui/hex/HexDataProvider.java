package com.bingbaihanji.fxdecomplie.ui.hex;

public interface HexDataProvider {
    int read(long address, byte[] dst, int offset, int length);

    long getSize();

    default int read(long address, byte[] dst, int length) {
        return read(address, dst, 0, length);
    }
}
