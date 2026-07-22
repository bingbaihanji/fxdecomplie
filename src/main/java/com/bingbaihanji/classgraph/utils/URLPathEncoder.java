/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.bingbaihanji.classgraph.utils;

import com.bingbaihanji.classgraph.utils.VersionFinder.OperatingSystem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 简单的 URL 路径编码器 */
public final class URLPathEncoder {

    /** 十六进制数字 */
    private static final char[] HEXADECIMAL = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c',
            'd', 'e', 'f'};
    /** 有效的类路径 URL 方案前缀 */
    private static final String[] SCHEME_PREFIXES = {"jrt:", "file:", "jar:file:", "jar:", "http:", "https:"};
    /** ASCII 字符是否对 URL 安全 */
    private static boolean[] safe = new boolean[256];

    static {
        for (int i = 'a'; i <= 'z'; i++) {
            safe[i] = true;
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            safe[i] = true;
        }
        for (int i = '0'; i <= '9'; i++) {
            safe[i] = true;
        }
        // "safe" 规则
        safe['$'] = safe['-'] = safe['_'] = safe['.'] = safe['+'] = true;
        // "extra" 规则
        safe['!'] = safe['*'] = safe['\''] = safe['('] = safe[')'] = safe[','] = true;
        // 仅从 "fsegment" 和 "hsegment" 规则中包含 "/"(为安全起见排除 ':'、'@'、'&' 和 '=')
        safe['/'] = true;
        // 也允许 '+' 字符(#468)
        //safe['+'] = true;
    }

    /**
     * 构造方法
     */
    private URLPathEncoder() {
        // 不可构造
    }

    /** 反转义 URL 中的字符URLDecoder.decode 存在缺陷: https://bugs.openjdk.java.net/browse/JDK-8179507 */
    private static void unescapeChars(final String str, final boolean isQuery, final ByteArrayOutputStream buf) {
        if (str.isEmpty()) {
            return;
        }
        for (int chrIdx = 0, len = str.length(); chrIdx < len; chrIdx++) {
            final char c = str.charAt(chrIdx);
            if (c == '%') {
                // 解码百分号转义字符序列，例如 %5D
                if (chrIdx > len - 3) {
                    // 忽略字符串末尾被截断的百分号序列
                } else {
                    final char c1 = str.charAt(++chrIdx);
                    final int digit1 = c1 >= '0' && c1 <= '9' ? (c1 - '0')
                            : c1 >= 'a' && c1 <= 'f' ? (c1 - 'a' + 10)
                            : c1 >= 'A' && c1 <= 'F' ? (c1 - 'A' + 10) : -1;
                    final char c2 = str.charAt(++chrIdx);
                    final int digit2 = c2 >= '0' && c2 <= '9' ? (c2 - '0')
                            : c2 >= 'a' && c2 <= 'f' ? (c2 - 'a' + 10)
                            : c2 >= 'A' && c2 <= 'F' ? (c2 - 'A' + 10) : -1;
                    if (digit1 < 0 || digit2 < 0) {
                        try {
                            buf.write(str.substring(chrIdx - 2, chrIdx + 1).getBytes(StandardCharsets.UTF_8));
                        } catch (final IOException e) {
                            // 忽略
                        }
                    } else {
                        buf.write((byte) ((digit1 << 4) | digit2));
                    }
                }
            } else if (isQuery && c == '+') {
                buf.write((byte) ' ');
            } else if (c <= 0x7f) {
                buf.write((byte) c);
            } else {
                try {
                    buf.write(Character.toString(c).getBytes(StandardCharsets.UTF_8));
                } catch (final IOException e) {
                    // 忽略
                }
            }
        }
    }

    /**
     * 反转义 URL 段，并将其从 UTF-8 字节转换为 Java 字符串
     *
     * @param str
     *            字符串
     * @return 字符串
     */
    public static String decodePath(final String str) {
        final int queryIdx = str.indexOf('?');
        final String partBeforeQuery = queryIdx < 0 ? str : str.substring(0, queryIdx);
        final String partFromQuery = queryIdx < 0 ? "" : str.substring(queryIdx);
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        unescapeChars(partBeforeQuery, /* isQuery = */ false, buf);
        unescapeChars(partFromQuery, /* isQuery = */ true, buf);
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * 使用百分号编码对 URL 路径进行编码'/' 不会被编码
     *
     * @param path
     *            要编码的路径
     * @return 编码后的路径
     */
    public static String encodePath(final String path) {
        // 接受 ':' 如果它是方案前缀的一部分
        int validColonPrefixLen = 0;
        for (final String scheme : SCHEME_PREFIXES) {
            if (path.startsWith(scheme)) {
                validColonPrefixLen = scheme.length();
                break;
            }
        }
        // 同时接受 Windows 驱动器字母后的 ':'
        if (VersionFinder.OS == OperatingSystem.Windows) {
            int i = validColonPrefixLen;
            if (i < path.length() && path.startsWith("///", i)) {
                i += "///".length();
            }
            if (i < path.length() - 1 && Character.isLetter(path.charAt(i)) && path.charAt(i + 1) == ':') {
                validColonPrefixLen = i + 2;
            }
        }

        // 对路径的其余部分应用 URL 编码规则
        final byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        final StringBuilder encodedPath = new StringBuilder(pathBytes.length * 3);
        for (int i = 0; i < pathBytes.length; i++) {
            final byte pathByte = pathBytes[i];
            final int b = pathByte & 0xff;
            if (safe[b] || (b == ':' && i < validColonPrefixLen)) {
                encodedPath.append((char) b);
            } else {
                encodedPath.append('%');
                encodedPath.append(HEXADECIMAL[(b & 0xf0) >> 4]);
                encodedPath.append(HEXADECIMAL[b & 0x0f]);
            }
        }
        return encodedPath.toString();
    }

    /**
     * 规范化 URL 路径，使其可以被传入 URL 或 URI 构造函数
     *
     * @param urlPath
     *            URL 路径
     * @return URL 字符串
     */
    public static String normalizeURLPath(final String urlPath) {
        String urlPathNormalized = urlPath;
        if (!urlPathNormalized.startsWith("jrt:") && !urlPathNormalized.startsWith("http://")
                && !urlPathNormalized.startsWith("https://")) {

            // 剥离 "jar:" 和/或 "file:"，如果已存在
            if (urlPathNormalized.startsWith("jar:")) {
                urlPathNormalized = urlPathNormalized.substring(4);
            }
            if (urlPathNormalized.startsWith("file:")) {
                urlPathNormalized = urlPathNormalized.substring(4);
            }

            // 在 Windows 上，如果存在驱动器前缀，则从路径中移除(否则 ':' 后跟驱动器字母会被转义为 %3A)
            String windowsDrivePrefix = "";
            if (VersionFinder.OS == OperatingSystem.Windows) {
                if (urlPathNormalized.length() >= 2 && Character.isLetter(urlPathNormalized.charAt(0))
                        && urlPathNormalized.charAt(1) == ':') {
                    // 格式为 "C:/xyz" 的路径
                    windowsDrivePrefix = urlPathNormalized.substring(0, 2);
                    urlPathNormalized = urlPathNormalized.substring(2);
                } else if (urlPathNormalized.length() >= 3 && urlPathNormalized.charAt(0) == '/'
                        && Character.isLetter(urlPathNormalized.charAt(1)) && urlPathNormalized.charAt(2) == ':') {
                    // 格式为 "/C:/xyz" 的路径
                    windowsDrivePrefix = urlPathNormalized.substring(1, 3);
                    urlPathNormalized = urlPathNormalized.substring(3);
                }
            }

            // 包含 "!" 段的 URL 必须在 "!" 后有 "/"，以使 "jar:" URL 方案正常工作
            urlPathNormalized = urlPathNormalized.replace("/!", "!").replace("!/", "!").replace("!", "!/");

            // 在绝对路径前添加 "file:///"，在相对路径前添加 "file:"
            if (windowsDrivePrefix.isEmpty()) {
                // 没有 Windows 驱动器
                if (urlPathNormalized.startsWith("/")) {
                    // 绝对路径: file:///xyz
                    urlPathNormalized = "file://" + urlPathNormalized;
                } else {
                    // 相对路径: file:xyz
                    urlPathNormalized = "file:" + urlPathNormalized;
                }
            } else {
                // 有 Windows 驱动器，路径必须是绝对路径
                urlPathNormalized = "file:///" + windowsDrivePrefix + urlPathNormalized;
            }

            // 如果路径包含 "!" 段，在前面添加 "jar:"
            if (urlPathNormalized.contains("!") && !urlPathNormalized.startsWith("jar:")) {
                urlPathNormalized = "jar:" + urlPathNormalized;
            }
        }
        return encodePath(urlPathNormalized);
    }
}
