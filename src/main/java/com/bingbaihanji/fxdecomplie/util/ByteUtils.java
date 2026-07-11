package com.bingbaihanji.fxdecomplie.util;

/**
 * 字节大小格式化工具类
 *
 * @author bingbaihanji
 * @date 2025-12-08
 */
public final class ByteUtils {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};

    private static final int UNIT = 1024;
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private ByteUtils() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 格式化字节大小为易读字符串
     * @param byteSize 字节数
     * @return 格式化后的字符串,如 "1.23 MB"
     */
    public static String formatByteSize(long byteSize) {

        if (byteSize < 0) {
            return String.valueOf(byteSize);
        }

        double size = (double) byteSize;
        int unitIndex = 0;

        while (unitIndex < UNITS.length - 1 && Math.floor(size / UNIT) > 0) {
            size /= UNIT;
            unitIndex++;
        }

        return format(size, UNITS[unitIndex]);
    }

    /**
     * 字节数组转十六进制字符串
     * @param bytes 字节数组,不能为 null
     * @return 十六进制字符串(小写)
     */
    public static String bytesToHex(byte[] bytes) {
        java.util.Objects.requireNonNull(bytes, "bytes 不能为 null");
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * 十六进制字符串转字节数组
     * @param hex 十六进制字符串,不能为 null,长度必须为偶数
     * @return 字节数组
     * @throws IllegalArgumentException 如果 hex 为空或长度非偶数
     */
    public static byte[] hexToBytes(String hex) {
        java.util.Objects.requireNonNull(hex, "hex 不能为 null");
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("十六进制字符串长度必须为偶数: " + hex.length());
        }
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }

    /**
     * 字节数组转 int(大端序,4 字节)
     * @param bytes 字节数组,长度至少 4
     * @return int 值
     * @throws IllegalArgumentException 如果 bytes 长度不足 4
     */
    public static int toInt(byte[] bytes) {
        java.util.Objects.requireNonNull(bytes, "bytes 不能为 null");
        if (bytes.length < 4) {
            throw new IllegalArgumentException("字节数组长度不足 4: " + bytes.length);
        }
        return (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    /**
     * 字节数组转 long(大端序,8 字节)
     * @param bytes 字节数组,长度至少 8
     * @return long 值
     * @throws IllegalArgumentException 如果 bytes 长度不足 8
     */
    public static long toLong(byte[] bytes) {
        java.util.Objects.requireNonNull(bytes, "bytes 不能为 null");
        if (bytes.length < 8) {
            throw new IllegalArgumentException("字节数组长度不足 8: " + bytes.length);
        }
        return (long) (bytes[0] & 0xFF) << 56 | (long) (bytes[1] & 0xFF) << 48 | (long) (bytes[2] & 0xFF) << 40
                | (long) (bytes[3] & 0xFF) << 32 | (long) (bytes[4] & 0xFF) << 24 | (long) (bytes[5] & 0xFF) << 16
                | (long) (bytes[6] & 0xFF) << 8 | (long) (bytes[7] & 0xFF);
    }

    private static String format(double size, String type) {
        int precision = 0;

        if (size * 100 % 10 > 0) {
            precision = 2;
        } else if (size * 10 % 10 > 0) {
            precision = 1;
        }

        String formatStr = "%." + precision + "f";
        return String.format(formatStr, size) + " " + type;
    }

}
