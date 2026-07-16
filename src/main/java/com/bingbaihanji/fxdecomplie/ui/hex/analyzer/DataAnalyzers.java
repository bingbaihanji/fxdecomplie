package com.bingbaihanji.fxdecomplie.ui.hex.analyzer;

import com.bingbaihanji.fxdecomplie.ui.hex.HexViewController;
import javafx.scene.paint.Color;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Built-in DataAnalyzer implementations registered by default.
 */
public final class DataAnalyzers {

    // ===== Shared colors =====
    private static final Color C_HEX = Color.rgb(0x88, 0xC0, 0xFF);
    private static final Color C_DEC = Color.rgb(0xC0, 0xE0, 0xC0);
    private static final Color C_FLOAT = Color.rgb(0xFF, 0xC0, 0x80);
    private static final Color C_STR = Color.rgb(0xFF, 0xD0, 0x70);
    private static final Color C_TIME = Color.rgb(0x80, 0xC0, 0xFF);
    private static final Color C_BIN = Color.rgb(0xA0, 0xA0, 0xA0);
    private static final Color C_GUID = Color.rgb(0xC0, 0x80, 0xFF);

    private DataAnalyzers() {
    }

    public static void registerDefaults(HexViewController ctrl) {
        // Hex (raw byte)
        ctrl.registerAnalyzer(new SimpleAnalyzer("Hex (raw)", C_HEX,
                (addr, data, off, len) -> {
                    if (off >= len) {
                        return List.of();
                    }
                    return List.of(r("Raw Byte",
                            String.format("0x%02X  (dec %d)", data[off] & 0xFF, data[off] & 0xFF), C_HEX));
                }));

        // Decimal unsigned
        ctrl.registerAnalyzer(new SimpleAnalyzer("Decimal", C_DEC,
                (addr, data, off, len) -> {
                    if (off >= len) {
                        return List.of();
                    }
                    return List.of(r("UInt8", Integer.toString(data[off] & 0xFF), C_DEC));
                }));

        // Binary
        ctrl.registerAnalyzer(new SimpleAnalyzer("Binary", C_BIN,
                (addr, data, off, len) -> {
                    if (off >= len) {
                        return List.of();
                    }
                    String bin = String.format("%8s",
                            Integer.toBinaryString(data[off] & 0xFF)).replace(' ', '0');
                    return List.of(r("Binary", bin, C_BIN));
                }));

        // ASCII
        ctrl.registerAnalyzer(new SimpleAnalyzer("ASCII", C_STR,
                (addr, data, off, len) -> {
                    if (off >= len) {
                        return List.of();
                    }
                    int c = data[off] & 0xFF;
                    String s = (c >= 32 && c < 127) ? "'" + (char) c + "'" : "·";
                    return List.of(r("ASCII", s, C_STR));
                }));

        // Int8 signed
        ctrl.registerAnalyzer(new MultiAnalyzer("Int8", 1, (addr, buf) -> {
            byte v = buf.duplicate().get();
            return List.of(r("Int8 (signed)", Byte.toString(v), C_DEC));
        }));

        // Int16 LE/BE
        ctrl.registerAnalyzer(new MultiAnalyzer("Int16", 2, (addr, buf) -> {
            short le = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN).getShort();
            short be = buf.duplicate().order(ByteOrder.BIG_ENDIAN).getShort();
            return List.of(
                    r("Int16 LE", String.format("%d (0x%04X)", le, le & 0xFFFF), C_DEC),
                    r("Int16 BE", String.format("%d (0x%04X)", be, be & 0xFFFF), C_DEC),
                    r("UInt16 LE", Integer.toString(le & 0xFFFF), C_DEC),
                    r("UInt16 BE", Integer.toString(be & 0xFFFF), C_DEC));
        }));

        // Int32 LE/BE
        ctrl.registerAnalyzer(new MultiAnalyzer("Int32", 4, (addr, buf) -> {
            int le = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt();
            int be = buf.duplicate().order(ByteOrder.BIG_ENDIAN).getInt();
            return List.of(
                    r("Int32 LE", String.format("%d (0x%08X)", le, le), C_DEC),
                    r("Int32 BE", String.format("%d (0x%08X)", be, be), C_DEC),
                    r("UInt32 LE", Long.toString(le & 0xFFFF_FFFFL), C_DEC),
                    r("UInt32 BE", Long.toString(be & 0xFFFF_FFFFL), C_DEC));
        }));

        // Int64 LE/BE
        ctrl.registerAnalyzer(new MultiAnalyzer("Int64", 8, (addr, buf) -> {
            long le = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN).getLong();
            long be = buf.duplicate().order(ByteOrder.BIG_ENDIAN).getLong();
            return List.of(
                    r("Int64 LE", String.format("%d (0x%016X)", le, le), C_DEC),
                    r("Int64 BE", String.format("%d (0x%016X)", be, be), C_DEC));
        }));

        // Float32 LE/BE
        ctrl.registerAnalyzer(new MultiAnalyzer("Float32", 4, (addr, buf) -> {
            float le = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN).getFloat();
            float be = buf.duplicate().order(ByteOrder.BIG_ENDIAN).getFloat();
            return List.of(
                    r("Float LE", String.format("%.6g", le), C_FLOAT),
                    r("Float BE", String.format("%.6g", be), C_FLOAT));
        }));

        // Float64
        ctrl.registerAnalyzer(new MultiAnalyzer("Float64", 8, (addr, buf) -> {
            double le = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN).getDouble();
            double be = buf.duplicate().order(ByteOrder.BIG_ENDIAN).getDouble();
            return List.of(
                    r("Double LE", String.format("%.12g", le), C_FLOAT),
                    r("Double BE", String.format("%.12g", be), C_FLOAT));
        }));

        // Unix timestamp (32-bit LE)
        ctrl.registerAnalyzer(new MultiAnalyzer("Timestamp", 4, (addr, buf) -> {
            long ts = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFF_FFFFL;
            String timeStr;
            try {
                timeStr = Instant.ofEpochSecond(ts).toString();
            } catch (Exception e) {
                timeStr = "out of range";
            }
            return List.of(r("Unix time LE", timeStr, C_TIME));
        }));

        // RGBA
        ctrl.registerAnalyzer(new MultiAnalyzer("RGBA", 4, (addr, buf) -> {
            var b = buf.duplicate();
            int ri = b.get() & 0xFF, gi = b.get() & 0xFF;
            int bi = b.get() & 0xFF, ai = b.get() & 0xFF;
            return List.of(new DataAnalyzer.Result("RGBA",
                    String.format("#%02X%02X%02X%02X  r=%d g=%d b=%d a=%d",
                            ri, gi, bi, ai, ri, gi, bi, ai),
                    Color.rgb(ri, gi, bi)));
        }));

        // GUID (16 bytes)
        ctrl.registerAnalyzer(new MultiAnalyzer("GUID", 16, (addr, buf) -> {
            var b = buf.duplicate().order(ByteOrder.BIG_ENDIAN);
            UUID uuid = new UUID(b.getLong(), b.getLong());
            return List.of(r("GUID", uuid.toString(), C_GUID));
        }));
    }

    // ===== helpers =====

    private static DataAnalyzer.Result r(String label, String value, Color c) {
        return new DataAnalyzer.Result(label, value, c);
    }

    // ===== internal classes =====

    @FunctionalInterface
    private interface SimpleFn {
        List<DataAnalyzer.Result> apply(long addr, byte[] data, int offset, int length);
    }

    @FunctionalInterface
    private interface MultiFn {
        List<DataAnalyzer.Result> apply(long addr, ByteBuffer buf);
    }

    private record SimpleAnalyzer(String name, Color colorHint, SimpleFn fn)
            implements DataAnalyzer {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Result> analyze(long addr, byte[] data, int off, int len) {
            return fn.apply(addr, data, off, len);
        }
    }

    /** Analyzer that reads N bytes starting at offset, passes ByteBuffer to lambda */
    private record MultiAnalyzer(String name, int byteCount, MultiFn fn)
            implements DataAnalyzer {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Result> analyze(long addr, byte[] data, int off, int len) {
            if (off + byteCount > len) {
                return List.of(new Result(getName() + " (need " + byteCount + " bytes)",
                        "(insufficient data for multi-byte read)", Color.GRAY));
            }
            ByteBuffer buf = ByteBuffer.wrap(data, off, byteCount);
            return fn.apply(addr, buf);
        }
    }
}
