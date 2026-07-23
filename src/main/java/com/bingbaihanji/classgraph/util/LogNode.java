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

import com.bingbaihanji.classgraph.classpath.SystemJarFinder;
import com.bingbaihanji.classgraph.core.ClassGraph;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 树形结构的线程安全日志，允许您以任意顺序添加日志条目，
 * 并让输出保持合理的顺序还可以通过为日志条目指定排序键来使顺序变为确定性的
 */
public final class LogNode {
    /** 日志记录器 */
    private static final Logger log = Logger.getLogger(ClassGraph.class.getName());
    /** 日期/时间格式化器(非线程安全) */
    private static final SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ",
            Locale.US);
    /** 已用时间格式化器 */
    private static final DecimalFormat nanoFormatter = new DecimalFormat("0.000000");
    /** 此日志条目的排序键后缀，用于使排序键唯一 */
    private static AtomicInteger sortKeyUniqueSuffix = new AtomicInteger(0);
    /** 如果为 true，日志条目在添加到 LogNode 树的同时也会实时输出 */
    private static boolean logInRealtime;

    // 缓解 log4j2 漏洞(CVE-2021-44228)，防止 log4j 被添加到类路径中作为日志记录器
    // https://blog.cloudflare.com/inside-the-log4j2-vulnerability-cve-2021-44228/
    static {
        System.getProperties().setProperty("log4j2.formatMsgNoLookups", "true");
    }

    /**
     * 日志节点创建时的时间戳(相对于某个任意的系统时间点)
     */
    private final long timeStampNano = System.nanoTime();
    /** 日志节点创建时的时间戳，以纪元毫秒计 */
    private final long timeStampMillis = System.currentTimeMillis();
    /** 日志消息 */
    private final String msg;
    /** 此日志节点的子节点 */
    private final Map<String, LogNode> children = new ConcurrentSkipListMap<>();
    /** 用于日志条目确定性排序的排序键前缀 */
    private final String sortKeyPrefix;
    /** 堆栈跟踪，如果此日志条目是由异常引起的 */
    private String stackTrace;
    /** 从此日志条目创建到调用 addElapsedTime() 之间的时间间隔 */
    private long elapsedTimeNanos;
    /** 父 LogNode */
    private LogNode parent;

    /**
     * 创建一个非顶级日志节点还可以通过为日志条目指定排序键来使顺序变为确定性的
     *
     * @param sortKey
     *            排序键
     * @param msg
     *            日志消息
     * @param elapsedTimeNanos
     *            以纳秒计的已用时间
     * @param exception
     *            抛出的异常
     */
    private LogNode(final String sortKey, final String msg, final long elapsedTimeNanos,
                    final Throwable exception) {
        this.sortKeyPrefix = sortKey;
        this.msg = msg;
        this.elapsedTimeNanos = elapsedTimeNanos;
        if (exception != null) {
            final StringWriter writer = new StringWriter();
            exception.printStackTrace(new PrintWriter(writer));
            stackTrace = writer.toString();
        } else {
            stackTrace = null;
        }
        if (logInRealtime) {
            log.info(toString());
        }
    }

    /** 创建一个顶级日志节点 */
    public LogNode() {
        this("", "", /* elapsedTimeNanos = */ -1L, /* exception = */ null);
        this.log("ClassGraph version " + VersionFinder.getVersion());
        logJavaInfo();
    }

    /**
     * 如果 logInRealtime 为 true，日志条目在添加到 LogNode 树的同时也会实时输出
     * 这有助于调试日志信息永远不显示的情况，例如死锁，
     * 或者需要显示直到断点处的日志信息的情况
     *
     * @param logInRealtime
     *            是否实时记录日志
     */
    public static void logInRealtime(final boolean logInRealtime) {
        LogNode.logInRealtime = logInRealtime;
    }

    /**
     * 记录 Java 版本和找到的 JRE 路径
     */
    private void logJavaInfo() {
        log("Operating system: " + VersionFinder.getProperty("os.name") + " "
                + VersionFinder.getProperty("os.version") + " " + VersionFinder.getProperty("os.arch"));
        log("Java version: " + VersionFinder.getProperty("java.version") + " / "
                + VersionFinder.getProperty("java.runtime.version") + " ("
                + VersionFinder.getProperty("java.vendor") + ")");
        log("Java home: " + VersionFinder.getProperty("java.home"));
        final String jreRtJarPath = SystemJarFinder.getJreRtJarPath();
        if (jreRtJarPath != null) {
            log("JRE rt.jar:").log(jreRtJarPath);
        }
    }

    /**
     * 向日志输出追加一行，根据树结构缩进此日志条目
     *
     * @param timeStampStr
     *            时间戳字符串
     * @param indentLevel
     *            缩进级别
     * @param line
     *            要记录的行
     * @param buf
     *            缓冲区
     */
    private void appendLine(final String timeStampStr, final int indentLevel, final String line,
                            final StringBuilder buf) {
        buf.append(timeStampStr);
        buf.append('\t');
        buf.append(ClassGraph.class.getSimpleName());
        buf.append('\t');
        final int numDashes = 2 * (indentLevel - 1);
        for (int i = 0; i < numDashes; i++) {
            buf.append('-');
        }
        if (numDashes > 0) {
            buf.append(' ');
        }
        buf.append(line);
        buf.append('\n');
    }

    /**
     * 递归构建日志输出
     *
     * @param indentLevel
     *            缩进级别
     * @param buf
     *            缓冲区
     */
    private void toString(final int indentLevel, final StringBuilder buf) {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeStampMillis);
        final String timeStampStr;
        synchronized (dateTimeFormatter) {
            timeStampStr = dateTimeFormatter.format(cal.getTime());
        }

        if (msg != null && !msg.isEmpty()) {
            appendLine(timeStampStr, indentLevel,
                    elapsedTimeNanos > 0L
                            ? msg + " (took " + nanoFormatter.format(elapsedTimeNanos * 1e-9) + " sec)" //
                            : msg,
                    buf);
        }
        if (stackTrace != null && !stackTrace.isEmpty()) {
            final String[] parts = stackTrace.split("\n");
            for (final String part : parts) {
                appendLine(timeStampStr, indentLevel, part, buf);
            }
        }

        for (final Entry<String, LogNode> ent : children.entrySet()) {
            final LogNode child = ent.getValue();
            child.toString(indentLevel + 1, buf);
        }
    }

    /**
     * 构建日志输出请在顶级日志节点上调用此方法
     *
     * @return 字符串
     */
    @Override
    public String toString() {
        // DateTimeFormatter 不是线程安全的
        synchronized (dateTimeFormatter) {
            final StringBuilder buf = new StringBuilder();
            toString(0, buf);
            return buf.toString();
        }
    }

    /**
     * 当与给定日志条目对应的工作完成后调用此方法，
     * 如果要在日志条目后显示耗时
     */
    public void addElapsedTime() {
        elapsedTimeNanos = System.nanoTime() - timeStampNano;
    }

    /**
     * 添加子日志节点
     *
     * @param sortKey
     *            排序键
     * @param msg
     *            日志消息
     * @param elapsedTimeNanos
     *            以纳秒计的已用时间
     * @param exception
     *            抛出的异常
     * @return 日志节点
     */
    private LogNode addChild(final String sortKey, final String msg, final long elapsedTimeNanos,
                             final Throwable exception) {
        final String newSortKey = sortKeyPrefix + "\t" + (sortKey == null ? "" : sortKey) + "\t"
                + String.format("%09d", sortKeyUniqueSuffix.getAndIncrement());
        final LogNode newChild = new LogNode(newSortKey, msg, elapsedTimeNanos, exception);
        newChild.parent = this;
        // 使排序键唯一，以便键被重用时不覆盖日志条目；每个新日志条目的唯一后缀递增，
        // 以便按时间顺序打破平局
        children.put(newSortKey, newChild);
        return newChild;
    }

    /**
     * 为消息添加子日志节点
     *
     * @param sortKey
     *            排序键
     * @param msg
     *            日志消息
     * @param elapsedTimeNanos
     *            以纳秒计的已用时间
     * @return 日志节点
     */
    private LogNode addChild(final String sortKey, final String msg, final long elapsedTimeNanos) {
        return addChild(sortKey, msg, elapsedTimeNanos, null);
    }

    /**
     * 为异常添加子日志节点
     *
     * @param exception
     *            抛出的异常
     * @return 日志节点
     */
    private LogNode addChild(final Throwable exception) {
        return addChild("", "", -1L, exception);
    }

    /**
     * 添加带有排序键的日志条目以实现确定性排序
     *
     * @param sortKey
     *            日志条目的排序键
     * @param msg
     *            消息
     * @param elapsedTimeNanos
     *            已用时间
     * @param e
     *            抛出的 {@link Throwable}
     * @return 子日志节点，可用于添加子条目
     */
    public LogNode log(final String sortKey, final String msg, final long elapsedTimeNanos, final Throwable e) {
        return addChild(sortKey, msg, elapsedTimeNanos).addChild(e);
    }

    /**
     * 添加带有排序键的日志条目以实现确定性排序
     *
     * @param sortKey
     *            日志条目的排序键
     * @param msg
     *            消息
     * @param elapsedTimeNanos
     *            已用时间
     * @return 子日志节点，可用于添加子条目
     */
    public LogNode log(final String sortKey, final String msg, final long elapsedTimeNanos) {
        return addChild(sortKey, msg, elapsedTimeNanos);
    }

    /**
     * 添加带有排序键的日志条目以实现确定性排序
     *
     * @param sortKey
     *            日志条目的排序键
     * @param msg
     *            消息
     * @param e
     *            抛出的 {@link Throwable}
     * @return 子日志节点，可用于添加子条目
     */
    public LogNode log(final String sortKey, final String msg, final Throwable e) {
        return addChild(sortKey, msg, -1L).addChild(e);
    }

    /**
     * 添加带有排序键的日志条目以实现确定性排序
     *
     * @param sortKey
     *            日志条目的排序键
     * @param msg
     *            消息
     * @return 子日志节点，可用于添加子条目
     */
    public LogNode log(final String sortKey, final String msg) {
        return addChild(sortKey, msg, -1L);
    }

    /**
     * 添加日志条目
     *
     * @param msg
     *            消息
     * @param elapsedTimeNanos
     *            已用时间
     * @param e
     *            抛出的 {@link Throwable}
     * @return 子日志节点，可用于添加子条目
     */
    public LogNode log(final String msg, final long elapsedTimeNanos, final Throwable e) {
        return addChild("", msg, elapsedTimeNanos).addChild(e);
    }

    /**
     * 添加日志条目
     *
     * @param msg
     *            消息
     * @param elapsedTimeNanos
     *            已用时间
     * @return 子日志节点，可用于添加子条目
     */
    public LogNode log(final String msg, final long elapsedTimeNanos) {
        return addChild("", msg, elapsedTimeNanos);
    }

    /**
     * 添加日志条目
     *
     * @param msg
     *            消息
     * @param e
     *            抛出的 {@link Throwable}
     * @return 子日志节点，可用于添加子条目
     */
    public LogNode log(final String msg, final Throwable e) {
        return addChild("", msg, -1L).addChild(e);
    }

    /**
     * 添加日志条目
     *
     * @param msg
     *            消息
     * @return 子日志节点，可用于添加子条目
     */
    public LogNode log(final String msg) {
        return addChild("", msg, -1L);
    }

    /**
     * 添加一系列日志条目返回最后一个创建的 LogNode
     *
     * @param msgs
     *            消息
     * @return 最后创建的日志节点，可用于添加子条目
     */
    public LogNode log(final Collection<String> msgs) {
        LogNode last = null;
        for (final String m : msgs) {
            last = log(m);
        }
        return last;
    }

    /**
     * 添加日志条目
     *
     * @param e
     *            抛出的 {@link Throwable}
     * @return 子日志节点，可用于添加子条目
     */
    public LogNode log(final Throwable e) {
        return log("Exception thrown").addChild(e);
    }

    /**
     * 将日志刷新到 stderr，并清除日志内容仅在顶级日志节点上调用此方法，
     * 且当线程没有对内部日志节点的引用访问权限时调用，
     * 这样它们就不能在树内部添加更多日志条目，否则日志条目可能会丢失
     */
    public void flush() {
        if (parent != null) {
            throw new IllegalArgumentException("Only flush the toplevel LogNode");
        }
        if (!children.isEmpty()) {
            final String logOutput = this.toString();
            this.children.clear();
            log.info(logOutput);
        }
    }
}
