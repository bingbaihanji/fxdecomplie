package com.bingbaihanji.fxdecomplie.ui.hex.analyzer;

import com.bingbaihanji.fxdecomplie.ui.hex.HexViewController;
import javafx.scene.paint.Color;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 内置数据分析器({@link DataAnalyzer})的注册工具类 
 * <p>
 * 该类为实用工具类,提供静态方法 {@link #registerDefaults} 向 {@link HexViewController}
 * 注册一系列默认的分析器,涵盖原始字节、十进制、二进制、ASCII、整数(有/无符号,大/小端)、
 * 浮点数、时间戳、RGBA 颜色以及 GUID 等常见数据类型的解析 
 * </p>
 *
 * @author BingBaiHanJi
 * @see DataAnalyzer
 * @see HexViewController
 */
public final class DataAnalyzers {

    // ---------- 颜色常量 ----------
    private static final Color C_HEX = Color.rgb(0x88, 0xC0, 0xFF);      // 十六进制
    private static final Color C_DEC = Color.rgb(0xC0, 0xE0, 0xC0);      // 十进制
    private static final Color C_FLOAT = Color.rgb(0xFF, 0xC0, 0x80);    // 浮点数
    private static final Color C_STR = Color.rgb(0xFF, 0xD0, 0x70);      // 字符串/ASCII
    private static final Color C_TIME = Color.rgb(0x80, 0xC0, 0xFF);     // 时间戳
    private static final Color C_BIN = Color.rgb(0xA0, 0xA0, 0xA0);      // 二进制
    private static final Color C_GUID = Color.rgb(0xC0, 0x80, 0xFF);     // GUID

    /**
     * 私有构造方法,防止实例化(工具类) 
     */
    private DataAnalyzers() {
    }

    /**
     * 向指定的控制器注册所有默认的内置数据分析器 
     * <p>
     * 注册的分析器包括：
     * <ul>
     *   <li><b>Hex (raw)</b> – 当前字节的十六进制表示及十进制值</li>
     *   <li><b>Decimal</b> – 无符号 8 位整数</li>
     *   <li><b>Binary</b> – 8 位二进制字符串</li>
     *   <li><b>ASCII</b> – 可打印字符或占位符</li>
     *   <li><b>Int8</b> – 有符号 8 位整数</li>
     *   <li><b>Int16</b> – 16 位整数(大/小端,有/无符号)</li>
     *   <li><b>Int32</b> – 32 位整数(大/小端,有/无符号)</li>
     *   <li><b>Int64</b> – 64 位整数(大/小端,有符号)</li>
     *   <li><b>Float32</b> – 32 位单精度浮点数(大/小端)</li>
     *   <li><b>Float64</b> – 64 位双精度浮点数(大/小端)</li>
     *   <li><b>Timestamp</b> – Unix 时间戳(32 位小端,转为 ISO 时间)</li>
     *   <li><b>RGBA</b> – 四字节颜色(R、G、B、A)并显示为 #RRGGBBAA</li>
     *   <li><b>GUID</b> – 16 字节全局唯一标识符(大端顺序)</li>
     * </ul>
     * </p>
     *
     * @param ctrl 要注册分析器的 {@link HexViewController} 实例
     */
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

    // ---------- 辅助方法 ----------

    /**
     * 快速创建 {@link DataAnalyzer.Result} 实例的便捷方法 
     *
     * @param label 标签
     * @param value 值
     * @param c     颜色
     * @return 结果对象
     */
    private static DataAnalyzer.Result r(String label, String value, Color c) {
        return new DataAnalyzer.Result(label, value, c);
    }

    // ---------- 内部类 ----------

    /**
     * 单字节或单值分析器的函数接口(用于 {@link SimpleAnalyzer}) 
     */
    @FunctionalInterface
    private interface SimpleFn {
        /**
         * 执行分析并返回结果列表 
         *
         * @param addr   当前地址
         * @param data   完整字节数组
         * @param offset 当前分析起始偏移
         * @param length 可用数据长度
         * @return 分析结果列表
         */
        List<DataAnalyzer.Result> apply(long addr, byte[] data, int offset, int length);
    }

    /**
     * 多字节分析器的函数接口(用于 {@link MultiAnalyzer}) 
     */
    @FunctionalInterface
    private interface MultiFn {
        /**
         * 执行分析并返回结果列表 
         *
         * @param addr 当前地址
         * @param buf  包含所需字节的 {@link ByteBuffer}(已定位到起始位置)
         * @return 分析结果列表
         */
        List<DataAnalyzer.Result> apply(long addr, ByteBuffer buf);
    }

    /**
     * 简单分析器实现,适用于只需处理单个偏移位置的分析(如 Hex、ASCII、Decimal 等) 
     * <p>
     * 直接使用 {@link SimpleFn} 处理从指定偏移开始的单个字节或简单数据 
     * </p>
     */
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

    /**
     * 多字节分析器实现,适用于需要读取连续 N 个字节并作为整体解析的分析(如整数、浮点数、时间戳等) 
     * <p>
     * 构造时指定所需的字节数 {@code byteCount},分析时会从数据中截取指定长度的片段,
     * 包装为 {@link ByteBuffer} 传递给 {@link MultiFn} 处理 
     * </p>
     */
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