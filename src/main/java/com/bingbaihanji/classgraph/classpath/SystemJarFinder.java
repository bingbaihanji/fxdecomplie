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

import com.bingbaihanji.classgraph.utils.FastPathResolver;
import com.bingbaihanji.classgraph.utils.FileUtils;
import com.bingbaihanji.classgraph.utils.JarUtils;
import com.bingbaihanji.classgraph.utils.VersionFinder;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** 用于查找 rt.jar 以及 JRE "lib/" 或 "ext/" 目录下 JAR 文件的类 */
public final class SystemJarFinder {
    /** 在 JRE 中找到的所有 "rt.jar" 文件的路径 */
    private static final Set<String> RT_JARS = new LinkedHashSet<>();

    /** 第一个找到的 "rt.jar" 的路径 */
    private static final String RT_JAR;

    /** 在 JRE 中找到的所有 "lib/" 或 "ext/" JAR 文件的路径 */
    private static final Set<String> JRE_LIB_OR_EXT_JARS = new LinkedHashSet<>();

    // 在 JRE 目录中查找 JAR 文件({java.home}、{java.home}/lib、{java.home}/lib/ext 等)
    static {
        String javaHome = VersionFinder.getProperty("java.home");
        if (javaHome == null || javaHome.isEmpty()) {
            javaHome = System.getenv("JAVA_HOME");
        }
        if (javaHome != null && !javaHome.isEmpty()) {
            final File javaHomeFile = new File(javaHome);
            addJREPath(javaHomeFile);
            if ("jre".equals(javaHomeFile.getName())) {
                // 当 java.home 是 JRE 路径时，尝试将 "{java.home}/.." 添加为 JDK 根路径
                final File jreParent = javaHomeFile.getParentFile();
                addJREPath(jreParent);
                addJREPath(new File(jreParent, "lib"));
                addJREPath(new File(jreParent, "lib/ext"));
            } else {
                // 当 java.home 不是 JRE 路径时，尝试将 "{java.home}/jre" 添加为 JRE 根路径
                addJREPath(new File(javaHomeFile, "jre"));
            }
            addJREPath(new File(javaHomeFile, "lib"));
            addJREPath(new File(javaHomeFile, "lib/ext"));
            addJREPath(new File(javaHomeFile, "jre/lib"));
            addJREPath(new File(javaHomeFile, "jre/lib/ext"));
            addJREPath(new File(javaHomeFile, "packages"));
            addJREPath(new File(javaHomeFile, "packages/lib"));
            addJREPath(new File(javaHomeFile, "packages/lib/ext"));
        }
        final String javaExtDirs = VersionFinder.getProperty("java.ext.dirs");
        if (javaExtDirs != null && !javaExtDirs.isEmpty()) {
            for (final String javaExtDir : JarUtils.smartPathSplit(javaExtDirs, /* scanSpec = */ null)) {
                if (!javaExtDir.isEmpty()) {
                    addJREPath(new File(javaExtDir));
                }
            }
        }

        // 系统扩展路径 -- 参见：https://docs.oracle.com/javase/tutorial/ext/basics/install.html
        switch (VersionFinder.OS) {
            case Linux:
            case Unix:
            case BSD:
            case Unknown:
                addJREPath(new File("/usr/java/packages"));
                addJREPath(new File("/usr/java/packages/lib"));
                addJREPath(new File("/usr/java/packages/lib/ext"));
                break;
            case MacOSX:
                addJREPath(new File("/System/Library/Java"));
                addJREPath(new File("/System/Library/Java/Libraries"));
                addJREPath(new File("/System/Library/Java/Extensions"));
                break;
            case Windows:
                final String systemRoot = File.separatorChar == '\\' ? System.getenv("SystemRoot") : null;
                if (systemRoot != null) {
                    addJREPath(new File(systemRoot, "Sun\\Java"));
                    addJREPath(new File(systemRoot, "Sun\\Java\\lib"));
                    addJREPath(new File(systemRoot, "Sun\\Java\\lib\\ext"));
                    addJREPath(new File(systemRoot, "Oracle\\Java"));
                    addJREPath(new File(systemRoot, "Oracle\\Java\\lib"));
                    addJREPath(new File(systemRoot, "Oracle\\Java\\lib\\ext"));
                }
                break;
            case Solaris:
                // Solaris 路径：
                addJREPath(new File("/usr/jdk/packages"));
                addJREPath(new File("/usr/jdk/packages/lib"));
                addJREPath(new File("/usr/jdk/packages/lib/ext"));
                break;
            default:
                break;
        }

        RT_JAR = RT_JARS.isEmpty() ? null : FastPathResolver.resolve(RT_JARS.iterator().next());
    }

    /**
     * 构造函数
     */
    private SystemJarFinder() {
        // 不可构造
    }

    /**
     * 添加并搜索 JRE 路径
     *
     * @param dir
     *            JRE 目录
     * @return 如果目录可读则返回 true
     */
    private static boolean addJREPath(final File dir) {
        if (dir != null && !dir.getPath().isEmpty() && FileUtils.canReadAndIsDir(dir)) {
            final File[] dirFiles = dir.listFiles();
            if (dirFiles != null) {
                for (final File file : dirFiles) {
                    final String filePath = file.getPath();
                    if (filePath.endsWith(".jar")) {
                        final String jarPathResolved = FastPathResolver.resolve(FileUtils.currDirPath(), filePath);
                        if (jarPathResolved.endsWith("/rt.jar")) {
                            RT_JARS.add(jarPathResolved);
                        } else {
                            JRE_LIB_OR_EXT_JARS.add(jarPathResolved);
                        }
                        try {
                            final File canonicalFile = file.getCanonicalFile();
                            final String canonicalFilePath = canonicalFile.getPath();
                            if (!canonicalFilePath.equals(filePath)) {
                                final String canonicalJarPathResolved = FastPathResolver
                                        .resolve(FileUtils.currDirPath(), filePath);
                                JRE_LIB_OR_EXT_JARS.add(canonicalJarPathResolved);
                            }
                        } catch (IOException | SecurityException e) {
                            // 忽略
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 获取 JRE "rt.jar" 路径
     *
     * @return rt.jar 的路径(在 JDK 7 或 8 中)，如果未找到则返回 null(例如在 JDK 9+ 中)
     */
    public static String getJreRtJarPath() {
        // 仅包含第一个 rt.jar -- 如果 JDK 和 JRE 中各有一份副本，则无需扫描两者
        return RT_JAR;
    }

    /**
     * 获取 JRE "lib/" 和 "ext/" JAR 路径
     *
     * @return 在 JRE/JDK "lib/" 或 "ext/" 目录中找到的所有 JAR 文件的路径
     */
    public static Set<String> getJreLibOrExtJars() {
        return JRE_LIB_OR_EXT_JARS;
    }
}
