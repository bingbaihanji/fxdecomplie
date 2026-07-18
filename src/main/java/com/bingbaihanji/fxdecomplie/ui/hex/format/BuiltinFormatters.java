package com.bingbaihanji.fxdecomplie.ui.hex.format;

/**
 * 内置的复制格式化器({@link CopyFormatter})集合 
 * <p>
 * 该类为工具类,提供一系列预定义的格式化实现,用于将字节数据转换为不同编程语言
 * 的数组字面量、十六进制字符串、ASCII 转储等格式,方便用户复制到剪贴板后粘贴到
 * 代码、文档或分析工具中 
 * </p>
 *
 * @author BingBaiHanJi
 * @see CopyFormatter
 */
public final class BuiltinFormatters {

    /**
     * 带空格的十六进制字符串 
     * <p>
     * 例如：{@code "00 01 02 0A FF"},每两个十六进制字符之间用一个空格分隔 
     * </p>
     */
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

    /**
     * 紧凑的十六进制字符串(无分隔符) 
     * <p>
     * 例如：{@code "0001020AFF"} 
     * </p>
     */
    public static final CopyFormatter HEX_PLAIN = (data, offset) -> {
        var sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    };

    /**
     * C 语言风格的数组初始化列表 
     * <p>
     * 例如：{@code {0x00, 0x01, 0x02, 0x0A, 0xFF}} 
     * </p>
     */
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

    /**
     * Java 语言风格的字节数组初始化 
     * <p>
     * 例如：{@code new byte[] {0x00, 0x01, (byte)0xFF}} 
     * 对于大于 0x7F 的值会添加 {@code (byte)} 强制转换,以避免编译错误 
     * </p>
     */
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

    /**
     * Rust 语言风格的数组字面量 
     * <p>
     * 例如：{@code [0x00, 0x01, 0x02, 0x0A, 0xFF]} 
     * </p>
     */
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

    /**
     * Python 字节串字面量 
     * <p>
     * 例如：{@code b'\x00\x01\x02\x0a\xff'}(全部转义为十六进制) 
     * </p>
     */
    public static final CopyFormatter PYTHON_BYTES = (data, offset) -> {
        var sb = new StringBuilder(data.length * 5 + 3);
        sb.append("b'");
        for (byte b : data) {
            sb.append(String.format("\\x%02x", b & 0xFF));
        }
        sb.append('\'');
        return sb.toString();
    };

    /**
     * JavaScript 语言风格的数组字面量 
     * <p>
     * 例如：{@code [0x00, 0x01, 0x02, 0x0A, 0xFF]} 
     * </p>
     */
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

    /**
     * ASCII 转储格式,类似 hexdump 输出 
     * <p>
     * 每行 16 字节,显示地址偏移、十六进制字节(空格分隔)以及右侧的 ASCII 可打印字符,
     * 不可打印字符显示为点({@code .}) 
     * </p>
     * <p>
     * 示例输出(每行)：
     * <pre>
     * 00000000  00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F  ................
     * 00000010  41 42 43 44                                      ABCD
     * </pre>
     * </p>
     */
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

    /**
     * 私有构造方法,防止实例化(工具类) 
     */
    private BuiltinFormatters() {
    }
}