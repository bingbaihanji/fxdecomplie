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
package com.bingbaihanji.classgraph.util;

import com.bingbaihanji.classgraph.resource.JarReader;
import com.bingbaihanji.classgraph.scan.ScanConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jar 文件工具类
 */
public final class JarUtils {
    /**
     * 检查路径开头是否具有 URL 方案要求 URL 方案至少包含 2 个字符，
     * 以防止 Windows 驱动器标识被误认为是 URL 方案
     */
    public static final Pattern URL_SCHEME_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9+-.]+[:].*");

    /** 常量 DASH_VERSION */
    private static final Pattern DASH_VERSION = Pattern.compile("-(\\d+(\\.|$))");

    /** 常量 NON_ALPHANUM */
    private static final Pattern NON_ALPHANUM = Pattern.compile("[^A-Za-z0-9]");

    /** 常量 REPEATING_DOTS */
    private static final Pattern REPEATING_DOTS = Pattern.compile("(\\.)(\\1)+");

    /** 常量 LEADING_DOTS */
    private static final Pattern LEADING_DOTS = Pattern.compile("^\\.");

    /** 常量 TRAILING_DOTS */
    private static final Pattern TRAILING_DOTS = Pattern.compile("\\.$");

    /** 常量 DOUBLE_BACKSHLASH_WITH_COLON */
    private static final Pattern DOUBLE_BACKSHLASH_WITH_COLON = Pattern.compile("\\\\:");

    /**
     * 在路径分隔符为 ':' 的非 Windows 系统上，
     * 当以下子字符串出现在字符串开头或跟随 ':' 时，需要将其中的冒号视为非分隔符
     */
    private static final String[] UNIX_NON_PATH_SEPARATORS = { //
            "jar:", "file:", "http://", "https://", //
            // 允许在路径中转义 ':' 字符，这可能超出规范允许的范围，
            // 但这样做是合理的，因为当 File.pathSeparatorChar 为 ':' 时，
            // File.separatorChar 永远不会是 '\\'
            "\\:" //
    };

    /**
     * 对应 UNIX_NON_PATH_SEPARATORS 数组条目中冒号字符的位置
     */
    private static final int[] UNIX_NON_PATH_SEPARATOR_COLON_POSITIONS;

    static {
        UNIX_NON_PATH_SEPARATOR_COLON_POSITIONS = new int[UNIX_NON_PATH_SEPARATORS.length];
        for (int i = 0; i < UNIX_NON_PATH_SEPARATORS.length; i++) {
            UNIX_NON_PATH_SEPARATOR_COLON_POSITIONS[i] = UNIX_NON_PATH_SEPARATORS[i].indexOf(':');
            if (UNIX_NON_PATH_SEPARATOR_COLON_POSITIONS[i] < 0) {
                throw new RuntimeException("Could not find ':' in \"" + UNIX_NON_PATH_SEPARATORS[i] + "\"");
            }
        }
    }

    /**
     * 构造方法
     */
    private JarUtils() {
        // 不可构造
    }

    /**
     * 按 File.pathSeparator(Linux 上为 ':'，Windows 上为 ';')拆分路径，
     * 但同时允许使用带协议说明符的 URL，例如 "http://domain/jar1.jar:http://domain/jar2.jar"
     *
     * @param pathStr
     *            要拆分的路径
     * @param ScanConfig
     *            扫描规格
     * @return 路径元素子字符串数组
     */
    public static String[] smartPathSplit(final String pathStr, final ScanConfig ScanConfig) {
        return smartPathSplit(pathStr, File.pathSeparatorChar, ScanConfig);
    }

    /**
     * 按给定的分隔符字符拆分路径如果分隔符字符是 ':'，
     * 则同时允许使用带协议说明符的 URL，例如 "http://domain/jar1.jar:http://domain/jar2.jar"
     *
     * @param pathStr
     *            要拆分的路径
     * @param separatorChar
     *            要使用的分隔符字符
     * @param ScanConfig
     *            扫描规格
     * @return 路径元素子字符串数组
     */
    public static String[] smartPathSplit(final String pathStr, final char separatorChar, final ScanConfig ScanConfig) {
        if (pathStr == null || pathStr.isEmpty()) {
            return new String[0];
        }
        if (separatorChar != ':') {
            // Windows(使用 ';' 作为路径分隔符)或分隔符不是 ':' 时的快速路径
            final List<String> partsFiltered = new ArrayList<>();
            for (final String part : pathStr.split(String.valueOf(separatorChar))) {
                final String partFiltered = part.trim();
                if (!partFiltered.isEmpty()) {
                    partsFiltered.add(partFiltered);
                }
            }
            return partsFiltered.toArray(new String[0]);
        } else {
            // 如果分隔符字符是 ':'，不要在 URL 协议边界处拆分
            // 这将允许 HTTP(S) JAR 在 java.class.path 中使用
            // (JRE 甚至可能不支持它们，但我们不妨支持一下)
            final Set<Integer> splitPoints = new HashSet<>();
            for (int i = -1; ; ) {
                boolean foundNonPathSeparator = false;
                for (int j = 0; j < UNIX_NON_PATH_SEPARATORS.length; j++) {
                    // 跳过非路径分隔符(如 "http://")中间的 ':' 字符
                    final int startIdx = i - UNIX_NON_PATH_SEPARATOR_COLON_POSITIONS[j];
                    if (pathStr.regionMatches(true, startIdx, UNIX_NON_PATH_SEPARATORS[j], 0,
                            UNIX_NON_PATH_SEPARATORS[j].length())
                            && (startIdx == 0 || pathStr.charAt(startIdx - 1) == ':')) {
                        // 不要把 "x.jar:y.jar" 中间的 "jar:" 当作 URL 方案
                        foundNonPathSeparator = true;
                        break;
                    }
                }
                if (!foundNonPathSeparator && ScanConfig != null && ScanConfig.allowedURLSchemes != null
                        && !ScanConfig.allowedURLSchemes.isEmpty()) {
                    // 如果注册了自定义 URL 方案，也将其用作分隔符
                    for (final String scheme : ScanConfig.allowedURLSchemes) {
                        // 跳过上方更快匹配代码中已处理的方案
                        if (!"http".equals(scheme) && !"https".equals(scheme) && !"jar".equals(scheme)
                                && !"file".equals(scheme)) {
                            final int schemeLen = scheme.length();
                            final int startIdx = i - schemeLen;
                            if (pathStr.regionMatches(true, startIdx, scheme, 0, schemeLen)
                                    && (startIdx == 0 || pathStr.charAt(startIdx - 1) == ':')) {
                                foundNonPathSeparator = true;
                                break;
                            }
                        }
                    }
                }
                if (!foundNonPathSeparator) {
                    // ':' 字符是有效的路径分隔符
                    splitPoints.add(i);
                }
                // 搜索下一个 ':' 字符
                i = pathStr.indexOf(':', i + 1);
                if (i < 0) {
                    // 找到最后一个 ':' 后添加字符串结束标记
                    splitPoints.add(pathStr.length());
                    break;
                }
            }
            final List<Integer> splitPointsSorted = new ArrayList<>(splitPoints);
            CollectionUtils.sortIfNotEmpty(splitPointsSorted);
            final List<String> parts = new ArrayList<>();
            for (int i = 1; i < splitPointsSorted.size(); i++) {
                final int idx0 = splitPointsSorted.get(i - 1);
                final int idx1 = splitPointsSorted.get(i);
                // 去空格，并反转义 "\\:"
                String part = pathStr.substring(idx0 + 1, idx1).trim();
                part = DOUBLE_BACKSHLASH_WITH_COLON.matcher(part).replaceAll(":");
                // 移除空路径组件
                if (!part.isEmpty()) {
                    parts.add(part);
                }
            }
            return parts.toArray(new String[0]);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 向缓冲区追加路径元素
     *
     * @param pathElt
     *            路径元素
     * @param buf
     *            缓冲区
     */
    private static void appendPathElt(final Object pathElt, final StringBuilder buf) {
        if (buf.length() > 0) {
            buf.append(File.pathSeparatorChar);
        }
        // 转义任何异常路径分隔符，只要文件分隔符不是 '\\'(在 Windows 上，
        // 如果路径元素中有任何额外的 ';' 字符，我们无法将它们转义为 "\\;")
        final String path = File.separatorChar == '\\' ? pathElt.toString()
                : pathElt.toString().replaceAll(File.pathSeparator, "\\" + File.pathSeparator);
        buf.append(path);
    }

    /**
     * 从对象数组(例如 String、File 或 URL 类型，将调用其 toString() 方法以获取路径组件)
     * 获取一组路径元素的字符串表示，并将路径作为以标准路径分隔符字符分隔的单个字符串返回
     *
     * @param pathElts
     *            路径元素
     * @return 由路径元素组成的带分隔符的路径
     */
    public static String pathElementsToPathStr(final Object... pathElts) {
        final StringBuilder buf = new StringBuilder();
        for (final Object pathElt : pathElts) {
            appendPathElt(pathElt, buf);
        }
        return buf.toString();
    }

    /**
     * 从对象数组(例如 String、File 或 URL 类型，将调用其 toString() 方法以获取路径组件)
     * 获取一组路径元素的字符串表示，并将路径作为以标准路径分隔符字符分隔的单个字符串返回
     *
     * @param pathElts
     *            路径元素
     * @return 调用每个对象的 toString() 方法后，由路径元素组成的带分隔符的路径
     */
    public static String pathElementsToPathStr(final Iterable<?> pathElts) {
        final StringBuilder buf = new StringBuilder();
        for (final Object pathElt : pathElts) {
            appendPathElt(pathElt, buf);
        }
        return buf.toString();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 返回路径的叶子名称，首先剥离第一个 '!' 之后的所有内容(如果存在)
     *
     * @param path
     *            文件路径
     * @return 路径的叶子名称
     */
    public static String leafName(final String path) {
        final int bangIdx = path.indexOf('!');
        final int endIdx = bangIdx >= 0 ? bangIdx : path.length();
        int leafStartIdx = 1 + (File.separatorChar == '/' ? path.lastIndexOf('/', endIdx)
                : Math.max(path.lastIndexOf('/', endIdx), path.lastIndexOf(File.separatorChar, endIdx)));
        // 对于临时文件(从 JAR 中提取的 JAR)，移除临时文件名前缀 -- 参见
        // JarReader.unzipToTempFile()
        int sepIdx = path.indexOf(JarReader.TEMP_FILENAME_LEAF_SEPARATOR);
        if (sepIdx >= 0) {
            sepIdx += JarReader.TEMP_FILENAME_LEAF_SEPARATOR.length();
        }
        leafStartIdx = Math.max(leafStartIdx, sepIdx);
        leafStartIdx = Math.min(leafStartIdx, endIdx);
        return path.substring(leafStartIdx, endIdx);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 将类文件路径转换为对应的类名
     *
     * @param classfilePath
     *            类文件路径
     * @return 类名
     */
    public static String classfilePathToClassName(final String classfilePath) {
        if (!classfilePath.endsWith(".class")) {
            throw new IllegalArgumentException("ClassParser path does not end with \".class\": " + classfilePath);
        }
        return classfilePath.substring(0, classfilePath.length() - 6).replace('/', '.');
    }

    /**
     * 将类名转换为对应的类文件路径
     *
     * @param className
     *            类名
     * @return 类文件路径
     */
    public static String classNameToClassfilePath(final String className) {
        return className.replace('.', '/') + ".class";
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 根据 JAR 名称推导自动模块名称，使用 <a href=
     * "https://docs.oracle.com/javase/9/docs/api/java/lang/module/ModuleFinder.html#of-java.nio.file.Path...-">此算法</a>
     *
     * @param jarPath
     *            JAR 路径
     * @return 自动模块名称
     */
    public static String derivedAutomaticModuleName(final String jarPath) {
        // 如果 JAR 路径不以文件扩展名结尾(最可能是 ".jar")，
        // 则剥离最后一个 '!' 之后的所有内容，以便移除包根目录
        int endIdx = jarPath.length();
        final int lastPlingIdx = jarPath.lastIndexOf('!');
        if (lastPlingIdx > 0
                // 如果最后一个 '!' 之后的最后一个 '/'(如果有)之后没有 '.'
                && jarPath.lastIndexOf('.') <= Math.max(lastPlingIdx, jarPath.lastIndexOf('/'))) {
            // 则在最后一个 '!' 处截断
            endIdx = lastPlingIdx;
        }
        // 找到倒数第二个 '!'(或 -1，如果没有)
        final int secondToLastPlingIdx = endIdx == 0 ? -1 : jarPath.lastIndexOf("!", endIdx - 1);
        // 找到倒数第二个到最后一个 '!' 之间的最后一个 '/'
        final int startIdx = Math.max(secondToLastPlingIdx, jarPath.lastIndexOf('/', endIdx - 1)) + 1;
        // 找到该 '/' 之后的最后一个 '.'
        final int lastDotBeforeLastPlingIdx = jarPath.lastIndexOf('.', endIdx - 1);
        if (lastDotBeforeLastPlingIdx > startIdx) {
            // 剥离扩展名
            endIdx = lastDotBeforeLastPlingIdx;
        }

        // 移除 .jar 扩展名
        String moduleName = jarPath.substring(startIdx, endIdx);

        // 查找第一次出现的 "-[0-9]"
        final Matcher matcher = DASH_VERSION.matcher(moduleName);
        if (matcher.find()) {
            moduleName = moduleName.substring(0, matcher.start());
        }

        // 将非字母数字字符替换为点号
        moduleName = NON_ALPHANUM.matcher(moduleName).replaceAll(".");

        // 将连续的点号折叠为单个点号
        moduleName = REPEATING_DOTS.matcher(moduleName).replaceAll(".");

        // 删除前导点号
        if (moduleName.length() > 0 && moduleName.charAt(0) == '.') {
            moduleName = LEADING_DOTS.matcher(moduleName).replaceAll("");
        }

        // 删除尾部点号
        final int len = moduleName.length();
        if (len > 0 && moduleName.charAt(len - 1) == '.') {
            moduleName = TRAILING_DOTS.matcher(moduleName).replaceAll("");
        }
        return moduleName;
    }
}
