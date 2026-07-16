package com.bingbaihanji.fxdecomplie.ui.hex.format;

public final class BuiltinFormatters {
    public static final CopyFormatter HEX_SPACED = (data, offset) -> {
        var sb = new StringBuilder(data.length * 3);
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        return sb.toString();
    };
    public static final CopyFormatter HEX_PLAIN = (data, offset) -> {
        var sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    };
    public static final CopyFormatter C_ARRAY = (data, offset) -> {
        var sb = new StringBuilder(data.length * 6 + 2);
        sb.append('{');
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("0x%02X", data[i] & 0xFF));
        }
        sb.append('}');
        return sb.toString();
    };
    public static final CopyFormatter JAVA_ARRAY = (data, offset) -> {
        var sb = new StringBuilder(data.length * 10 + 12);
        sb.append("new byte[] {");
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            int v = data[i] & 0xFF;
            if (v > 0x7F) {
                sb.append("(byte)");
            }
            sb.append(String.format("0x%02X", v));
        }
        sb.append('}');
        return sb.toString();
    };
    public static final CopyFormatter RUST_ARRAY = (data, offset) -> {
        var sb = new StringBuilder(data.length * 6 + 2);
        sb.append('[');
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("0x%02X", data[i] & 0xFF));
        }
        sb.append(']');
        return sb.toString();
    };
    public static final CopyFormatter PYTHON_BYTES = (data, offset) -> {
        var sb = new StringBuilder(data.length * 5 + 3);
        sb.append("b'");
        for (byte b : data) {
            sb.append(String.format("\\x%02x", b & 0xFF));
        }
        sb.append('\'');
        return sb.toString();
    };
    public static final CopyFormatter JS_ARRAY = (data, offset) -> {
        var sb = new StringBuilder(data.length * 6 + 2);
        sb.append('[');
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("0x%02X", data[i] & 0xFF));
        }
        sb.append(']');
        return sb.toString();
    };
    public static final CopyFormatter ASCII_ART = (data, offset) -> {
        var sb = new StringBuilder();
        int bytesPerRow = 16;
        for (int row = 0; row < data.length; row += bytesPerRow) {
            sb.append(String.format("%08X  ", offset + row));
            int end = Math.min(row + bytesPerRow, data.length);
            for (int i = row; i < end; i++) {
                sb.append(String.format("%02X ", data[i] & 0xFF));
            }
            sb.repeat("   ", Math.max(0, row + bytesPerRow - end));
            sb.append(' ');
            for (int i = row; i < end; i++) {
                int c = data[i] & 0xFF;
                sb.append(c >= 32 && c < 127 ? (char) c : '.');
            }
            sb.append('\n');
        }
        return sb.toString();
    };

    private BuiltinFormatters() {
    }
}
