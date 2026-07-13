package com.bingbaihanji.fxdecomplie.ui.hex;

public class ByteArrayProvider implements HexDataProvider {
    private final byte[] data;

    public ByteArrayProvider(byte[] data) {
        this.data = data.clone();
    }

    @Override
    public int read(long address, byte[] dst, int offset, int length) {
        if (address < 0 || address >= data.length) {
            return 0;
        }
        int available = (int) Math.min(length, data.length - address);
        System.arraycopy(data, (int) address, dst, offset, available);
        return available;
    }

    @Override
    public long getSize() {
        return data.length;
    }
}
