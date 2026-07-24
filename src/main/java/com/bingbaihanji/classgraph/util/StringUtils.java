package com.bingbaihanji.classgraph.util;

/**
 * 字符串工具类
 */
public final class StringUtils {
    /**
     * 构造方法
     */
    private StringUtils() {
        // 不可构造
    }

    /**
     * 读取 Java 类文件规范中定义的"改良 UTF8"格式，
     * 可选择将 '/' 替换为 '.'，并可选择移除前缀 "L" 和后缀 ";"
     *
     * @param arr
     *            要从中读取字符串的数组
     * @param startOffset
     *            字符串在数组中的起始偏移量
     * @param numBytes
     *            字符串 UTF8 编码的字节数
     * @param replaceSlashWithDot
     *            如果为 true，将 '/' 替换为 '.'
     * @param stripLSemicolon
     *            如果为 true，剥离字符串末尾的 ';' 字符
     * @return 字符串
     * @throws IllegalArgumentException
     *             如果无法解析字符串
     */
    public static String readString(final byte[] arr, final int startOffset, final int numBytes,
                                    final boolean replaceSlashWithDot, final boolean stripLSemicolon) throws IllegalArgumentException {
        if (startOffset < 0L || numBytes < 0 || startOffset + numBytes > arr.length) {
            throw new IllegalArgumentException("offset or numBytes out of range");
        }
        final char[] chars = new char[numBytes];
        int byteIdx = 0;
        int charIdx = 0;
        for (; byteIdx < numBytes; byteIdx++) {
            final int c = arr[startOffset + byteIdx] & 0xff;
            if (c > 127) {
                break;
            }
            chars[charIdx++] = (char) (replaceSlashWithDot && c == '/' ? '.' : c);
        }
        while (byteIdx < numBytes) {
            final int c = arr[startOffset + byteIdx] & 0xff;
            switch (c >> 4) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7: {
                    byteIdx++;
                    chars[charIdx++] = (char) (replaceSlashWithDot && c == '/' ? '.' : c);
                    break;
                }
                case 12:
                case 13: {
                    byteIdx += 2;
                    if (byteIdx > numBytes) {
                        throw new IllegalArgumentException("Bad modified UTF8");
                    }
                    final int c2 = arr[startOffset + byteIdx - 1];
                    if ((c2 & 0xc0) != 0x80) {
                        throw new IllegalArgumentException("Bad modified UTF8");
                    }
                    final int c3 = ((c & 0x1f) << 6) | (c2 & 0x3f);
                    chars[charIdx++] = (char) (replaceSlashWithDot && c3 == '/' ? '.' : c3);
                    break;
                }
                case 14: {
                    byteIdx += 3;
                    if (byteIdx > numBytes) {
                        throw new IllegalArgumentException("Bad modified UTF8");
                    }
                    final int c2 = arr[startOffset + byteIdx - 2];
                    final int c3 = arr[startOffset + byteIdx - 1];
                    if ((c2 & 0xc0) != 0x80 || (c3 & 0xc0) != 0x80) {
                        throw new IllegalArgumentException("Bad modified UTF8");
                    }
                    final int c4 = ((c & 0x0f) << 12) | ((c2 & 0x3f) << 6) | (c3 & 0x3f);
                    chars[charIdx++] = (char) (replaceSlashWithDot && c4 == '/' ? '.' : c4);
                    break;
                }
                default:
                    throw new IllegalArgumentException("Bad modified UTF8");
            }
        }
        if (charIdx == numBytes && !stripLSemicolon) {
            return new String(chars);
        } else {
            if (stripLSemicolon) {
                if (charIdx < 2 || chars[0] != 'L' || chars[charIdx - 1] != ';') {
                    throw new IllegalArgumentException("Expected string to start with 'L' and end with ';', got \""
                            + new String(chars) + "\"");
                }
                return new String(chars, 1, charIdx - 2);
            } else {
                return new String(chars, 0, charIdx);
            }
        }
    }

    /**
     * Java 8 中 String.join() 的替代实现
     *
     * @param buf
     *            要追加到的缓冲区
     * @param addAtBeginning
     *            要在字符串开头添加的标记
     * @param sep
     *            分隔符字符串
     * @param addAtEnd
     *            要在字符串末尾添加的标记
     * @param iterable
     *            要连接的 {@link Iterable}
     */
    public static void join(final StringBuilder buf, final String addAtBeginning, final String sep,
                            final String addAtEnd, final Iterable<?> iterable) {
        if (!addAtBeginning.isEmpty()) {
            buf.append(addAtBeginning);
        }
        boolean first = true;
        for (final Object item : iterable) {
            if (first) {
                first = false;
            } else {
                buf.append(sep);
            }
            buf.append(item == null ? "null" : item.toString());
        }
        if (!addAtEnd.isEmpty()) {
            buf.append(addAtEnd);
        }
    }

    /**
     * Java 8 中 String.join() 的替代实现
     *
     * @param sep
     *            分隔符字符串
     * @param iterable
     *            要连接的 {@link Iterable}
     * @return 连接后元素的字符串表示
     */
    public static String join(final String sep, final Iterable<?> iterable) {
        final StringBuilder buf = new StringBuilder();
        join(buf, "", sep, "", iterable);
        return buf.toString();
    }

    /**
     * Java 8 中 String.join() 的替代实现
     *
     * @param sep
     *            分隔符字符串
     * @param items
     *            要连接的项目
     * @return 连接后项目的字符串表示
     */
    public static String join(final String sep, final Object... items) {
        final StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (final Object item : items) {
            if (first) {
                first = false;
            } else {
                buf.append(sep);
            }
            buf.append(item == null ? "null" : item.toString());
        }
        return buf.toString();
    }

}
