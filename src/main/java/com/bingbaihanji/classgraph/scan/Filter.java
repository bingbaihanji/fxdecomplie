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
package com.bingbaihanji.classgraph.scan;

import com.bingbaihanji.classgraph.metadata.*;
import com.bingbaihanji.classgraph.type.*;
import com.bingbaihanji.classgraph.resource.*;
import com.bingbaihanji.classgraph.classpath.*;
import com.bingbaihanji.classgraph.util.*;
import com.bingbaihanji.classgraph.reflect.*;
import com.bingbaihanji.classgraph.bytecode.*;

import com.bingbaihanji.classgraph.util.CollectionUtils;
import com.bingbaihanji.classgraph.util.FastPathResolver;
import com.bingbaihanji.classgraph.util.FileUtils;
import com.bingbaihanji.classgraph.util.JarUtils;

import java.util.*;
import java.util.regex.Pattern;

/** 存储接受或拒绝条件的类 */
public abstract class Filter {
    /** 已接受的项(全字符串匹配) */
    protected Set<String> accept;
    /** 已拒绝的项(全字符串匹配) */
    protected Set<String> reject;
    /** 已接受的项(前缀匹配)，以集合形式存储 */
    protected Set<String> acceptPrefixesSet;
    /** 已接受的项(前缀匹配)，以排序列表形式存储 */
    protected List<String> acceptPrefixes;
    /** 已拒绝的项(前缀匹配) */
    protected List<String> rejectPrefixes;
    /** 接受的通配符字符串(序列化为 JSON，用于日志记录) */
    protected Set<String> acceptGlobs;
    /** 拒绝的通配符字符串(序列化为 JSON，用于日志记录) */
    protected Set<String> rejectGlobs;
    /** 接受的正则表达式模式(不序列化为 JSON) */
    protected transient List<Pattern> acceptPatterns;
    /** 拒绝的正则表达式模式(不序列化为 JSON) */
    protected transient List<Pattern> rejectPatterns;
    /** 分隔符字符 */
    protected char separatorChar;

    /** 反序列化构造函数 */
    public Filter() {
    }

    /**
     * 反序列化用构造函数
     *
     * @param separatorChar
     *            分隔符字符
     */
    public Filter(final char separatorChar) {
        this.separatorChar = separatorChar;
    }

    /**
     * 移除开头和结尾的 '/' 字符(如果存在)
     *
     * @param path
     *            要规范化的路径
     * @return 规范化后的路径
     */
    public static String normalizePath(final String path) {
        String pathResolved = FastPathResolver.resolve(path);
        while (pathResolved.startsWith("/")) {
            pathResolved = pathResolved.substring(1);
        }
        return pathResolved;
    }

    /**
     * 移除开头和结尾的 '.' 字符(如果存在)
     *
     * @param packageOrClassName
     *            包名或类名
     * @return 规范化后的包名或类名
     */
    public static String normalizePackageOrClassName(final String packageOrClassName) {
        return normalizePath(packageOrClassName.replace('.', '/')).replace('/', '.');
    }

    /**
     * 将路径转换为包名
     *
     * @param path
     *            路径
     * @return 包名
     */
    public static String pathToPackageName(final String path) {
        return path.replace('/', '.');
    }

    /**
     * 将包名转换为路径
     *
     * @param packageName
     *            包名
     * @return 路径
     */
    public static String packageNameToPath(final String packageName) {
        return packageName.replace('.', '/');
    }

    /**
     * 将类名转换为类文件路径
     *
     * @param className
     *            类名
     * @return 类文件路径(包含 ".class" 后缀)
     */
    public static String classNameToClassfilePath(final String className) {
        return JarUtils.classNameToClassfilePath(className);
    }

    /**
     * 将带有 '*' 通配符的规范转换为正则表达式
     *
     * @param glob
     *            通配符字符串
     * @param simpleGlob
     *            如果为 true，处理简单通配符："*" 匹配零个或多个字符(将 "." 替换为 "\\."，将 "*"
     *            替换为 ".*"，然后编译为正则表达式)如果为 false，处理文件系统风格的通配符："**"
     *            匹配零个或多个字符，"*" 匹配除 "/" 之外的零个或多个字符，"?" 匹配
     *            一个字符(将 "." 替换为 "\\."，将 "**" 替换为 ".*"，将 "*" 替换为 "[^/]*"，将 "?" 替换为 "."，然后
     *            编译为正则表达式)
     * @return 从通配符字符串创建的 Pattern
     */
    public static Pattern globToPattern(final String glob, final boolean simpleGlob) {
        // TODO: 下次 API 变更时，使所有通配符行为在接受/拒绝条件与资源过滤之间保持一致
        // (即对路径的接受/拒绝条件强制 simpleGlob == false，但包/类需要不同处理，
        // 因为 ** 应跨任意深度的包而不是任意段数的路径工作)
        return Pattern.compile("^" //
                + (simpleGlob //
                ? glob.replace(".", "\\.") //
                .replace("*", ".*") //
                : glob.replace(".", "\\.") //
                .replace("*", "[^/]*") //
                .replace("[^/]*[^/]*", ".*") //
                .replace('?', '.') //
        ) //
                + "$");
    }

    /**
     * 检查字符串是否匹配提供的列表中的某个模式
     *
     * @param str
     *            要测试的字符串
     * @param patterns
     *            模式列表
     * @return 如果匹配成功则返回 true
     */
    private static boolean matchesPatternList(final String str, final List<Pattern> patterns) {
        if (patterns != null) {
            for (final Pattern pattern : patterns) {
                if (pattern.matcher(str).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 对列表进行引号格式化
     *
     * @param coll
     *            集合
     * @param buf
     *            字符串缓冲区
     */
    private static void quoteList(final Collection<String> coll, final StringBuilder buf) {
        buf.append('[');
        boolean first = true;
        for (final String item : coll) {
            if (first) {
                first = false;
            } else {
                buf.append(", ");
            }
            buf.append('"');
            for (int i = 0; i < item.length(); i++) {
                final char c = item.charAt(i);
                if (c == '"') {
                    buf.append("\\\"");
                } else {
                    buf.append(c);
                }
            }
            buf.append('"');
        }
        buf.append(']');
    }

    /**
     * 添加到接受列表
     *
     * @param str
     *            要接受的字符串
     */
    public abstract void addToAccept(final String str);

    /**
     * 添加到拒绝列表
     *
     * @param str
     *            要拒绝的字符串
     */
    public abstract void addToReject(final String str);

    /**
     * 检查字符串是否被接受且未被拒绝
     *
     * @param str
     *            要测试的字符串
     * @return 如果字符串被接受且未被拒绝则返回 true
     */
    public abstract boolean isAcceptedAndNotRejected(final String str);

    /**
     * 检查字符串是否被接受
     *
     * @param str
     *            要测试的字符串
     * @return 如果字符串被接受则返回 true
     */
    public abstract boolean isAccepted(final String str);

    /**
     * 检查字符串是否为某个已接受字符串的前缀
     *
     * @param str
     *            要测试的字符串
     * @return 如果字符串是某个已接受字符串的前缀则返回 true
     */
    public abstract boolean acceptHasPrefix(final String str);

    /**
     * 检查字符串是否被拒绝
     *
     * @param str
     *            要测试的字符串
     * @return 如果字符串被拒绝则返回 true
     */
    public abstract boolean isRejected(final String str);

    /**
     * 检查接受列表是否为空
     *
     * @return 如果没有添加任何接受条件则返回 true
     */
    public boolean acceptIsEmpty() {
        return accept == null && acceptPrefixes == null && acceptGlobs == null;
    }

    /**
     * 检查拒绝列表是否为空
     *
     * @return 如果没有添加任何拒绝条件则返回 true
     */
    public boolean rejectIsEmpty() {
        return reject == null && rejectPrefixes == null && rejectGlobs == null;
    }

    /**
     * 检查接受和拒绝列表是否均为空
     *
     * @return 如果没有添加任何接受或拒绝条件则返回 true
     */
    public boolean acceptAndRejectAreEmpty() {
        return acceptIsEmpty() && rejectIsEmpty();
    }

    /**
     * 检查字符串是否被明确接受且未被拒绝
     *
     * @param str
     *            要测试的字符串
     * @return 如果请求的字符串被<i>明确</i>接受且未被拒绝则返回 true，即如果接受列表为空或字符串被拒绝则不会返回 true
     */
    public boolean isSpecificallyAcceptedAndNotRejected(final String str) {
        return !acceptIsEmpty() && isAcceptedAndNotRejected(str);
    }

    /**
     * 检查字符串是否被明确接受
     *
     * @param str
     *            要测试的字符串
     * @return 如果请求的字符串被<i>明确</i>接受则返回 true，即如果接受列表为空则不会返回 true
     */
    public boolean isSpecificallyAccepted(final String str) {
        return !acceptIsEmpty() && isAccepted(str);
    }

    /** 需要对前缀进行排序以确保正确的接受/拒绝评估(参见 Issue #167) */
    void sortPrefixes() {
        if (acceptPrefixesSet != null) {
            acceptPrefixes = new ArrayList<>(acceptPrefixesSet);
        }
        if (acceptPrefixes != null) {
            CollectionUtils.sortIfNotEmpty(acceptPrefixes);
        }
        if (rejectPrefixes != null) {
            CollectionUtils.sortIfNotEmpty(rejectPrefixes);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        if (accept != null) {
            buf.append("accept: ");
            quoteList(accept, buf);
        }
        if (acceptPrefixes != null) {
            if (buf.length() > 0) {
                buf.append("; ");
            }
            buf.append("acceptPrefixes: ");
            quoteList(acceptPrefixes, buf);
        }
        if (acceptGlobs != null) {
            if (buf.length() > 0) {
                buf.append("; ");
            }
            buf.append("acceptGlobs: ");
            quoteList(acceptGlobs, buf);
        }
        if (reject != null) {
            if (buf.length() > 0) {
                buf.append("; ");
            }
            buf.append("reject: ");
            quoteList(reject, buf);
        }
        if (rejectPrefixes != null) {
            if (buf.length() > 0) {
                buf.append("; ");
            }
            buf.append("rejectPrefixes: ");
            quoteList(rejectPrefixes, buf);
        }
        if (rejectGlobs != null) {
            if (buf.length() > 0) {
                buf.append("; ");
            }
            buf.append("rejectGlobs: ");
            quoteList(rejectGlobs, buf);
        }
        return buf.toString();
    }

    /** 用于前缀字符串的接受/拒绝 */
    public static class FilterPrefix extends Filter {
        /** 反序列化构造函数 */
        public FilterPrefix() {
            super();
        }

        /**
         * 实例化一个用于前缀字符串的接受/拒绝
         *
         * @param separatorChar
         *            分隔符字符
         */
        public FilterPrefix(final char separatorChar) {
            super(separatorChar);
        }

        /**
         * 添加到接受列表
         *
         * @param str
         *            要接受的字符串
         */
        @Override
        public void addToAccept(final String str) {
            if (str.contains("*")) {
                throw new IllegalArgumentException("Cannot use a glob wildcard here: " + str);
            }
            if (this.acceptPrefixesSet == null) {
                this.acceptPrefixesSet = new HashSet<>();
            }
            this.acceptPrefixesSet.add(str);
        }

        /**
         * 添加到拒绝列表
         *
         * @param str
         *            要拒绝的字符串
         */
        @Override
        public void addToReject(final String str) {
            if (str.contains("*")) {
                throw new IllegalArgumentException("Cannot use a glob wildcard here: " + str);
            }
            if (this.rejectPrefixes == null) {
                this.rejectPrefixes = new ArrayList<>();
            }
            this.rejectPrefixes.add(str);
        }

        /**
         * 检查请求的字符串是否具有已接受/未拒绝的前缀
         *
         * @param str
         *            要测试的字符串
         * @return 如果字符串被接受且未被拒绝则返回 true
         */
        @Override
        public boolean isAcceptedAndNotRejected(final String str) {
            boolean isAccepted = acceptPrefixes == null;
            if (!isAccepted) {
                for (final String prefix : acceptPrefixes) {
                    if (str.startsWith(prefix)) {
                        isAccepted = true;
                        break;
                    }
                }
            }
            if (!isAccepted) {
                return false;
            }
            if (rejectPrefixes != null) {
                for (final String prefix : rejectPrefixes) {
                    if (str.startsWith(prefix)) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * 检查请求的字符串是否具有已接受的前缀
         *
         * @param str
         *            要测试的字符串
         * @return 如果字符串被接受则返回 true
         */
        @Override
        public boolean isAccepted(final String str) {
            boolean isAccepted = acceptPrefixes == null;
            if (!isAccepted) {
                for (final String prefix : acceptPrefixes) {
                    if (str.startsWith(prefix)) {
                        isAccepted = true;
                        break;
                    }
                }
            }
            return isAccepted;
        }

        /**
         * 前缀的前缀无效 -- 抛出 {@link IllegalArgumentException}
         *
         * @param str
         *            要测试的字符串
         * @return (不返回，抛出异常)
         * @throws IllegalArgumentException
         *             始终抛出
         */
        @Override
        public boolean acceptHasPrefix(final String str) {
            throw new IllegalArgumentException("Can only find prefixes of whole strings");
        }

        /**
         * 检查请求的字符串是否具有被拒绝的前缀
         *
         * @param str
         *            要测试的字符串
         * @return 如果字符串具有被拒绝的前缀则返回 true
         */
        @Override
        public boolean isRejected(final String str) {
            if (rejectPrefixes != null) {
                for (final String prefix : rejectPrefixes) {
                    if (str.startsWith(prefix)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /** 用于全字符串匹配的接受/拒绝 */
    public static class FilterWholeString extends Filter {
        /** 反序列化构造函数 */
        public FilterWholeString() {
            super();
        }

        /**
         * 实例化一个用于全字符串匹配的接受/拒绝
         *
         * @param separatorChar
         *            分隔符字符
         */
        public FilterWholeString(final char separatorChar) {
            super(separatorChar);
        }

        /**
         * 添加到接受列表
         *
         * @param str
         *            要接受的字符串
         */
        @Override
        public void addToAccept(final String str) {
            if (str.contains("*")) {
                if (this.acceptGlobs == null) {
                    this.acceptGlobs = new HashSet<>();
                    this.acceptPatterns = new ArrayList<>();
                }
                this.acceptGlobs.add(str);
                this.acceptPatterns.add(globToPattern(str, /* simpleGlob = */ true));
            } else {
                if (this.accept == null) {
                    this.accept = new HashSet<>();
                }
                this.accept.add(str);
            }

            // 对于不执行前缀匹配的 FilterWholeString(不同于 FilterPrefix)，
            // 使用 acceptPrefixes 存储已接受路径的所有父级前缀，以便
            // acceptHasPrefix() 能够在非常大的接受列表上高效运行(#338)，
            // 特别是当接受列表的大小远大于最大路径深度时
            if (this.acceptPrefixesSet == null) {
                this.acceptPrefixesSet = new HashSet<>();
                acceptPrefixesSet.add("");
                acceptPrefixesSet.add("/");
            }
            final String separator = Character.toString(separatorChar);
            String prefix = str;
            if (prefix.contains("*")) {
                // 在第一个 '*' 处停止前缀搜索 -- 这意味着如果路径中有多个 '*'，前缀匹配将中断
                prefix = prefix.substring(0, prefix.indexOf('*'));
                // /path/to/wildcard*.jar -> /path/to
                // /path/to/*.jar -> /path/to
                final int sepIdx = prefix.lastIndexOf(separatorChar);
                if (sepIdx < 0) {
                    prefix = "";
                } else {
                    prefix = prefix.substring(0, prefix.lastIndexOf(separatorChar));
                }
            }
            // 去掉末尾的分隔符
            while (prefix.endsWith(separator)) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            // 将 str 本身添加为前缀(这仅会匹配父目录)
            for (; !prefix.isEmpty(); prefix = FileUtils.getParentDirPath(prefix, separatorChar)) {
                acceptPrefixesSet.add(prefix + separatorChar);
            }
        }

        /**
         * 添加到拒绝列表
         *
         * @param str
         *            要拒绝的字符串
         */
        @Override
        public void addToReject(final String str) {
            if (str.contains("*")) {
                if (this.rejectGlobs == null) {
                    this.rejectGlobs = new HashSet<>();
                    this.rejectPatterns = new ArrayList<>();
                }
                this.rejectGlobs.add(str);
                this.rejectPatterns.add(globToPattern(str, /* simpleGlob = */ true));
            } else {
                if (this.reject == null) {
                    this.reject = new HashSet<>();
                }
                this.reject.add(str);
            }
        }

        /**
         * 检查请求的字符串是否被接受且未被拒绝
         *
         * @param str
         *            要测试的字符串
         * @return 如果字符串被接受且未被拒绝则返回 true
         */
        @Override
        public boolean isAcceptedAndNotRejected(final String str) {
            return isAccepted(str) && !isRejected(str);
        }

        /**
         * 检查请求的字符串是否被接受
         *
         * @param str
         *            要测试的字符串
         * @return 如果字符串被接受则返回 true
         */
        @Override
        public boolean isAccepted(final String str) {
            return (accept == null && acceptPatterns == null) || (accept != null && accept.contains(str))
                    || matchesPatternList(str, acceptPatterns);
        }

        /**
         * 检查请求的字符串是否为某个已接受字符串的前缀
         *
         * @param str
         *            要测试的字符串
         * @return 如果字符串是某个已接受字符串的前缀则返回 true
         */
        @Override
        public boolean acceptHasPrefix(final String str) {
            if (acceptPrefixesSet == null) {
                return false;
            }
            return acceptPrefixesSet.contains(str);
        }

        /**
         * 检查请求的字符串是否被拒绝
         *
         * @param str
         *            要测试的字符串
         * @return 如果字符串被拒绝则返回 true
         */
        @Override
        public boolean isRejected(final String str) {
            return (reject != null && reject.contains(str)) || matchesPatternList(str, rejectPatterns);
        }
    }

    /** 用于叶子名称匹配的接受/拒绝 */
    public static class FilterLeafname extends FilterWholeString {
        /** 反序列化构造函数 */
        public FilterLeafname() {
            super();
        }

        /**
         * 实例化一个用于叶子名称匹配的接受/拒绝
         *
         * @param separatorChar
         *            分隔符字符
         */
        public FilterLeafname(final char separatorChar) {
            super(separatorChar);
        }

        /**
         * 添加到接受列表
         *
         * @param str
         *            要接受的字符串
         */
        @Override
        public void addToAccept(final String str) {
            super.addToAccept(JarUtils.leafName(str));
        }

        /**
         * 添加到拒绝列表
         *
         * @param str
         *            要拒绝的字符串
         */
        @Override
        public void addToReject(final String str) {
            super.addToReject(JarUtils.leafName(str));
        }

        /**
         * 检查请求的字符串是否被接受且未被拒绝
         *
         * @param str
         *            要测试的字符串
         * @return 如果字符串被接受且未被拒绝则返回 true
         */
        @Override
        public boolean isAcceptedAndNotRejected(final String str) {
            return super.isAcceptedAndNotRejected(JarUtils.leafName(str));
        }

        /**
         * 检查请求的字符串是否被接受
         *
         * @param str
         *            要测试的字符串
         * @return 如果字符串被接受则返回 true
         */
        @Override
        public boolean isAccepted(final String str) {
            return super.isAccepted(JarUtils.leafName(str));
        }

        /**
         * 前缀测试对 jar 叶子名称无效 -- 抛出 {@link IllegalArgumentException}
         *
         * @param str
         *            要测试的字符串
         * @return (不返回，抛出异常)
         * @throws IllegalArgumentException
         *             始终抛出
         */
        @Override
        public boolean acceptHasPrefix(final String str) {
            throw new IllegalArgumentException("Can only find prefixes of whole strings");
        }

        /**
         * 检查请求的字符串是否被拒绝
         *
         * @param str
         *            要测试的字符串
         * @return 如果字符串被拒绝则返回 true
         */
        @Override
        public boolean isRejected(final String str) {
            return super.isRejected(JarUtils.leafName(str));
        }
    }
}