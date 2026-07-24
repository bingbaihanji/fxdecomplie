package com.bingbaihanji.classgraph.util;

import com.bingbaihanji.classgraph.util.VersionFinder.OperatingSystem;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 以比 Java URL/URI 解析器更快(且比 Path 快得多)的方式，
 * 针对基础路径解析相对路径和 URL/URI，同时力求跨平台兼容，
 * 并特别希望对 Windows 路径的各种奇怪形式具有良好的鲁棒性
 */
public final class FastPathResolver {
    /** 匹配 URL 中的百分号编码字符 */
    private static final Pattern percentMatcher = Pattern.compile("(%[0-9a-fA-F][0-9a-fA-F])+");

    /** 匹配后跟一个或两个斜杠的自定义 URL */
    private static final Pattern schemeOneOrTwoSlashMatcher = Pattern.compile("^[a-zA-Z+\\-.]+:/{1,2}");

    /**
     * 构造方法
     */
    private FastPathResolver() {
        // 不可构造
    }

    /**
     * 将反斜杠转换为正斜杠，可选择性移除尾部路径分隔符
     *
     * @param path
     *            路径
     * @param startIdx
     *            起始索引
     * @param endIdx
     *            结束索引
     * @param stripFinalSeparator
     *            如果为 true，则移除尾部分隔符
     * @param buf
     *            缓冲区
     */
    private static void translateSeparator(final String path, final int startIdx, final int endIdx,
                                           final boolean stripFinalSeparator, final StringBuilder buf) {
        for (int i = startIdx; i < endIdx; i++) {
            final char c = path.charAt(i);
            if (c == '\\' || c == '/') {
                // 必要时移除尾部分隔符
                if (i < endIdx - 1 || !stripFinalSeparator) {
                    // 移除重复的分隔符
                    final char prevChar = buf.length() == 0 ? '\0' : buf.charAt(buf.length() - 1);
                    if (prevChar != '/') {
                        buf.append('/');
                    }
                }
            } else {
                buf.append(c);
            }
        }
    }

    /**
     * 十六进制字符转整数
     *
     * @param c
     *            字符
     * @return 字符对应的整数值
     */
    private static int hexCharToInt(final char c) {
        return (c >= '0' && c <= '9') ? (c - '0') //
                : (c >= 'a' && c <= 'f') ? (c - 'a' + 10) //
                : (c - 'A' + 10);
    }

    /**
     * 反转义连续的百分号编码，例如 "%20%43%20" -> " + "
     *
     * @param path
     *            路径
     * @param startIdx
     *            起始索引
     * @param endIdx
     *            结束索引
     * @param buf
     *            缓冲区
     */
    private static void unescapePercentEncoding(final String path, final int startIdx, final int endIdx,
                                                final StringBuilder buf) {
        if (endIdx - startIdx == 3 && path.charAt(startIdx + 1) == '2' && path.charAt(startIdx + 2) == '0') {
            // "%20" 的快速路径
            buf.append(' ');
        } else {
            final byte[] bytes = new byte[(endIdx - startIdx) / 3];
            for (int i = startIdx, j = 0; i < endIdx; i += 3, j++) {
                final char c1 = path.charAt(i + 1);
                final char c2 = path.charAt(i + 2);
                final int digit1 = hexCharToInt(c1);
                final int digit2 = hexCharToInt(c2);
                bytes[j] = (byte) ((digit1 << 4) | digit2);
            }
            // 解码 UTF-8 字节
            String str = new String(bytes, StandardCharsets.UTF_8);
            // 将正斜杠 / 反斜杠转回百分号编码
            str = str.replace("/", "%2F").replace("\\", "%5C");
            buf.append(str);
        }
    }

    /**
     * 解析百分号编码，例如 "%20" -&gt; " "；将 '/' 或 '\\' 转换为 SEP；
     * 如果存在则移除尾部分隔符字符
     *
     * @param path
     *            要规范化的路径
     * @param isFileOrJarURL
     *            如果这是一个 "file:" 或 "jar:" URL 则为 true
     * @return 规范化后的路径
     */
    public static String normalizePath(final String path, final boolean isFileOrJarURL) {
        final boolean hasPercent = path.indexOf('%') >= 0;
        if (!hasPercent && path.indexOf('\\') < 0 && !path.endsWith("/")) {
            return path;
        } else {
            final int len = path.length();
            final StringBuilder buf = new StringBuilder();
            // 仅对 "file:" 和 "jar:" URL 进行百分号解码(issue 255)
            if (hasPercent && isFileOrJarURL) {
                // 对路径段执行百分号解码
                int prevEndMatchIdx = 0;
                final Matcher matcher = percentMatcher.matcher(path);
                while (matcher.find()) {
                    final int startMatchIdx = matcher.start();
                    final int endMatchIdx = matcher.end();
                    translateSeparator(path, prevEndMatchIdx, startMatchIdx, /* stripFinalSeparator = */ false,
                            buf);
                    unescapePercentEncoding(path, startMatchIdx, endMatchIdx, buf);
                    prevEndMatchIdx = endMatchIdx;
                }
                translateSeparator(path, prevEndMatchIdx, len, /* stripFinalSeparator = */ true, buf);
            } else {
                // 快速路径 -- 无 '%'，或 "http(s)://" 或 "jrt:" URL，或非 "file:" / 非 "jar:" URL
                translateSeparator(path, 0, len, /* stripFinalSeparator = */ true, buf);
                return buf.toString();
            }
            return buf.toString();
        }
    }

    /**
     * 从文件名 URI 中剥离任何 "jar:" 前缀，并将其转换为文件路径，
     * 处理可能混用文件系统和 URI 约定的情况；将相对路径相对于 resolveBasePath 进行解析
     *
     * @param resolveBasePath
     *            基础路径
     * @param relativePath
     *            相对于基础路径进行解析的路径
     * @return 解析后的路径
     */
    public static String resolve(final String resolveBasePath, final String relativePath) {
        // 参见: http://stackoverflow.com/a/17870390/3950982
        // https://weblogs.java.net/blog/kohsuke/archive/2007/04/how_to_convert.html

        if (relativePath == null || relativePath.isEmpty()) {
            return resolveBasePath == null ? "" : resolveBasePath;
        }

        String prefix = "";
        boolean isAbsolutePath = false;
        boolean isFileOrJarURL = false;
        int startIdx = 0;
        boolean matchedPrefix;
        do {
            matchedPrefix = false;
            if (relativePath.regionMatches(true, startIdx, "jar:", 0, 4)) {
                // "jar:" 前缀可以剥离
                matchedPrefix = true;
                startIdx = 4;
                isFileOrJarURL = true;
            } else if (relativePath.regionMatches(true, startIdx, "http://", 0, 7)) {
                // 检测 http://
                matchedPrefix = true;
                startIdx += 7;
                // 将协议名强制转为小写
                prefix += "http://";
                // 将协议后的部分视为绝对路径，这样域名不会被当作相对于当前目录的目录
                isAbsolutePath = true;
                // 不反转义百分号编码等
            } else if (relativePath.regionMatches(true, startIdx, "https://", 0, 8)) {
                // 检测 https://
                matchedPrefix = true;
                startIdx += 8;
                prefix += "https://";
                isAbsolutePath = true;
            } else if (relativePath.regionMatches(true, startIdx, "jrt:", 0, 5)) {
                // 检测 jrt:
                matchedPrefix = true;
                startIdx += 4;
                prefix += "jrt:";
                isAbsolutePath = true;
            } else if (relativePath.regionMatches(true, startIdx, "file:", 0, 5)) {
                // 从相对路径中剥离 "file:" 前缀
                matchedPrefix = true;
                startIdx += 5;
                isFileOrJarURL = true;
            } else {
                // 保留自定义 URL 方案中的斜杠数量(#420)
                final String relPath = startIdx == 0 ? relativePath : relativePath.substring(startIdx);
                final Matcher matcher = schemeOneOrTwoSlashMatcher.matcher(relPath);
                if (matcher.find()) {
                    matchedPrefix = true;
                    final String match = matcher.group();
                    startIdx += match.length();
                    prefix += match;
                    // 将协议后的部分视为绝对路径，这样 URL 的其余部分不会被当作相对于当前目录的目录
                    isAbsolutePath = true;
                }
            }
        } while (matchedPrefix);

        // 将以驱动器标识开头的 Windows 路径视为绝对路径
        if (VersionFinder.OS == OperatingSystem.Windows) {
            if (relativePath.startsWith("//", startIdx) || relativePath.startsWith("\\\\", startIdx)) {
                // Windows UNC 路径
                startIdx += 2;
                prefix += "//";
                isAbsolutePath = true;
            } else if (relativePath.length() - startIdx > 2 && Character.isLetter(relativePath.charAt(startIdx))
                    && relativePath.charAt(startIdx + 1) == ':') {
                // 类似 "C:/xyz" 的路径
                isAbsolutePath = true;
            } else if (relativePath.length() - startIdx > 3
                    && (relativePath.charAt(startIdx) == '/' || relativePath.charAt(startIdx) == '\\')
                    && Character.isLetter(relativePath.charAt(startIdx + 1))
                    && relativePath.charAt(startIdx + 2) == ':') {
                // 类似 "/C:/xyz" 的路径
                isAbsolutePath = true;
                startIdx++;
            }
        }
        // 以分隔符开头的路径的通用处理
        if (relativePath.length() - startIdx > 1
                && (relativePath.charAt(startIdx) == '/' || relativePath.charAt(startIdx) == '\\')) {
            isAbsolutePath = true;
        }

        // 规范化路径，然后添加任何 UNC 或 URL 前缀
        String pathStr = normalizePath(startIdx == 0 ? relativePath : relativePath.substring(startIdx),
                isFileOrJarURL);
        if (!"/".equals(pathStr)) {
            // 移除 URL 末尾的任何 "!/"
            if (pathStr.endsWith("/")) {
                pathStr = pathStr.substring(0, pathStr.length() - 1);
            }
            if (pathStr.endsWith("!")) {
                pathStr = pathStr.substring(0, pathStr.length() - 1);
            }
            if (pathStr.endsWith("/")) {
                pathStr = pathStr.substring(0, pathStr.length() - 1);
            }
            if (pathStr.isEmpty()) {
                pathStr = "/";
            }
        }

        // 清理路径(解析 ".." 段、合并 "//" 双重分隔符等)
        String pathResolved;
        if (isAbsolutePath || resolveBasePath == null || resolveBasePath.isEmpty()) {
            // 没有基础路径可供解析，或者路径是绝对路径或 http(s):// URL(忽略基础路径)
            pathResolved = FileUtils.sanitizeEntryPath(pathStr, /* removeInitialSlash = */ false,
                    /* removeFinalSlash = */ true);
        } else {
            // 路径是相对路径 -- 相对于基础路径进行解析
            pathResolved = FileUtils.sanitizeEntryPath(
                    resolveBasePath + (resolveBasePath.endsWith("/") ? "" : "/") + pathStr,
                    /* removeInitialSlash = */ false, /* removeFinalSlash = */ true);
        }

        // 将任何前缀加回，例如 "https://"
        return prefix.isEmpty() ? pathResolved : prefix + pathResolved;
    }

    /**
     * 从文件名 URI 中剥离任何 "jar:" 前缀，并将其转换为文件路径，
     * 处理可能混用文件系统和 URI 约定的情况如果 relativePathStr 是 "http(s):" 路径则返回 null
     *
     * @param pathStr
     *            要解析的路径
     * @return 解析后的路径
     */
    public static String resolve(final String pathStr) {
        return resolve(null, pathStr);
    }
}
