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
package com.bingbaihanji.classgraph.classpath;

import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.scan.ClassGraph;
import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.util.JarUtils;
import com.bingbaihanji.classgraph.util.StringUtils;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模块路径信息注意，这仅包含命令行参数中实际列出的模块系统参数 —— 特别地，这不包括传统类路径中的类路径元素
 * 或系统模块
 */
public class ModulePathInfo {
    /** 模块路径命令行开关 */
    private static final List<String> argSwitches = Arrays.asList( //
            "--module-path=", //
            "--add-modules=", //
            "--patch-module=", //
            "--add-exports=", //
            "--add-opens=", //
            "--add-reads=" //
    );
    /** 模块路径命令行开关值的分隔符 */
    private static final List<Character> argPartSeparatorChars = Arrays.asList( //
            File.pathSeparatorChar, // --module-path(路径分隔符格式)
            ',', // --add-modules(逗号分隔)
            '\0', // --patch-module(每个开关仅一个参数)
            '\0', // --add-exports(每个开关仅一个参数)
            '\0', // --add-opens(每个开关仅一个参数)
            '\0' // --add-reads(每个开关仅一个参数)
    );
    /**
     * 通过 {@code --module-path} 或 {@code -p} 开关在命令行上提供的模块路径，
     * 作为有序的模块名称集合，按它们在命令行上列出的顺序排列
     *
     * <p>
     * 注意，某些模块(例如系统模块)不会出现在此集合中，因为它们由运行时自动添加到模块系统中
     * 调用 {@link ClassGraph#getModules()} 或 {@link ScanResult#getModules()}
     * 以获取运行时所有可见的模块
     */
    public final Set<String> modulePath = new LinkedHashSet<>();
    /**
     * 通过 {@code --add-modules} 开关在命令行上添加到模块路径的模块，作为有序的模块名称集合，
     * 按它们在命令行上列出的顺序排列注意，有效的模块名称包括 {@code ALL-DEFAULT}、
     * {@code ALL-SYSTEM} 和 {@code ALL-MODULE-PATH}(详见
     * <a href="https://openjdk.java.net/jeps/261">JEP 261</a>)
     */
    public final Set<String> addModules = new LinkedHashSet<>();
    /**
     * 通过 {@code --patch-module} 开关在命令行上列出的模块补丁指令，作为有序的字符串集合，
     * 格式为 {@code <module>=<file>}，按它们在命令行上列出的顺序排列
     */
    public final Set<String> patchModules = new LinkedHashSet<>();
    /**
     * 通过 {@code --add-exports} 开关在命令行上添加的模块 {@code exports} 指令，作为有序的字符串集合，
     * 格式为 {@code <source-module>/<package>=<target-module>(,<target-module>)*}，
     * 按它们在命令行上列出的顺序排列此外，如果此 {@link ModulePathInfo} 对象是从
     * {@link ScanResult#getModulePathInfo()} 而非 {@link ClassGraph#getModulePathInfo()} 获取的，
     * 则在类路径扫描期间从清单文件中找到的任何附加 {@code Add-Exports} 条目将被追加到此列表中，
     * 格式为 {@code <source-module>/<package>=ALL-UNNAMED}
     */
    public final Set<String> addExports = new LinkedHashSet<>();
    /**
     * 通过 {@code --add-opens} 开关在命令行上添加的模块 {@code opens} 指令，作为有序的字符串集合，
     * 格式为 {@code <source-module>/<package>=<target-module>(,<target-module>)*}，
     * 按它们在命令行上列出的顺序排列此外，如果此 {@link ModulePathInfo} 对象是从
     * {@link ScanResult#getModulePathInfo()} 而非 {@link ClassGraph#getModulePathInfo()} 获取的，
     * 则在类路径扫描期间从清单文件中找到的任何附加 {@code Add-Opens} 条目将被追加到此列表中，
     * 格式为 {@code <source-module>/<package>=ALL-UNNAMED}
     */
    public final Set<String> addOpens = new LinkedHashSet<>();
    /**
     * 通过 {@code --add-reads} 开关在命令行上添加的模块 {@code reads} 指令，作为有序的字符串集合，
     * 格式为 {@code <source-module>=<target-module>}，按它们在命令行上列出的顺序排列
     */
    public final Set<String> addReads = new LinkedHashSet<>();
    /** 字段列表 */
    private final List<Set<String>> fields = Arrays.asList( //
            modulePath, //
            addModules, //
            patchModules, //
            addExports, //
            addOpens, //
            addReads //
    );
    /** 当 {@link #getRuntimeInfo()} 被调用后设置为 true */
    private final AtomicBoolean gotRuntimeInfo = new AtomicBoolean();

    /* 模块路径信息 */
    public ModulePathInfo() {
    }

    /** 从 VM 命令行参数填充模块信息 */
    public void getRuntimeInfo(final ReflectionUtils reflectionUtils) {
        // 仅在明确请求 ModulePathInfo 时才调用此反射方法，以避免在某些 JRE 上出现非法访问警告，
        // 例如 Adopt JDK 11 (#605)
        if (!gotRuntimeInfo.getAndSet(true)) {
            // 读取原始命令行参数以获取模块路径覆盖参数
            // 如果部署的运行时中不存在 java.management 模块(对于 JDK 9+)，或者运行时
            // 不包含 java.lang.management 包(例如 Android 构建系统，目前也不支持 JPMS)，
            // 则跳过尝试读取命令行参数 (#404)
            final Class<?> managementFactory = reflectionUtils
                    .classForNameOrNull("java.lang.management.ManagementFactory");
            final Object runtimeMXBean = managementFactory == null ? null
                    : reflectionUtils.invokeStaticMethod(/* throwException = */ false, managementFactory,
                    "getRuntimeMXBean");
            @SuppressWarnings("unchecked") final List<String> commandlineArguments = runtimeMXBean == null ? null
                    : (List<String>) reflectionUtils.invokeMethod(/* throwException = */ false, runtimeMXBean,
                    "getInputArguments");
            if (commandlineArguments != null) {
                for (final String arg : commandlineArguments) {
                    for (int i = 0; i < fields.size(); i++) {
                        final String argSwitch = argSwitches.get(i);
                        if (arg.startsWith(argSwitch)) {
                            final String argParam = arg.substring(argSwitch.length());
                            final Set<String> argField = fields.get(i);
                            final char sepChar = argPartSeparatorChars.get(i);
                            if (sepChar == '\0') {
                                // 每个开关仅一个参数
                                argField.add(argParam);
                            } else {
                                // 将参数值拆分为多个部分
                                argField.addAll(Arrays
                                        .asList(JarUtils.smartPathSplit(argParam, sepChar, /* ScanConfig = */ null)));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 以命令行格式返回模块路径信息
     *
     * @return 模块路径命令行字符串
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(1024);
        if (!modulePath.isEmpty()) {
            buf.append("--module-path=");
            buf.append(StringUtils.join(File.pathSeparator, modulePath));
        }
        if (!addModules.isEmpty()) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append("--add-modules=");
            buf.append(StringUtils.join(",", addModules));
        }
        for (final String patchModulesEntry : patchModules) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append("--patch-module=");
            buf.append(patchModulesEntry);
        }
        for (final String addExportsEntry : addExports) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append("--add-exports=");
            buf.append(addExportsEntry);
        }
        for (final String addOpensEntry : addOpens) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append("--add-opens=");
            buf.append(addOpensEntry);
        }
        for (final String addReadsEntry : addReads) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append("--add-reads=");
            buf.append(addReadsEntry);
        }
        return buf.toString();
    }
}