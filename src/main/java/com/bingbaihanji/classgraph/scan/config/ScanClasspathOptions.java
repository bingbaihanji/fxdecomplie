package com.bingbaihanji.classgraph.scan.config;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 类路径扫描选项 — 控制扫描哪些类路径源。
 *
 * <p>不可变，通过 {@link ScanOptionsBuilder} 构建。</p>
 */
public final class ScanClasspathOptions {

    private final boolean scanJars;
    private final boolean scanNestedJars;
    private final boolean scanDirs;
    private final boolean scanModules;
    private final boolean enableSystemJarsAndModules;
    private final Set<String> allowedURLSchemes;
    private final List<Object> overrideClasspath;
    private final int maxBufferedJarRAMSize;
    private final boolean enableMemoryMapping;
    private final boolean enableMultiReleaseVersions;
    private final boolean removeTemporaryFilesAfterScan;
    private final boolean ignoreParentClassLoaders;

    ScanClasspathOptions(boolean scanJars, boolean scanNestedJars, boolean scanDirs,
                         boolean scanModules, boolean enableSystemJarsAndModules,
                         Set<String> allowedURLSchemes, List<Object> overrideClasspath,
                         int maxBufferedJarRAMSize, boolean enableMemoryMapping,
                         boolean enableMultiReleaseVersions, boolean removeTemporaryFilesAfterScan,
                         boolean ignoreParentClassLoaders) {
        this.scanJars = scanJars;
        this.scanNestedJars = scanNestedJars;
        this.scanDirs = scanDirs;
        this.scanModules = scanModules;
        this.enableSystemJarsAndModules = enableSystemJarsAndModules;
        this.allowedURLSchemes = allowedURLSchemes == null
            ? Set.of() : Collections.unmodifiableSet(allowedURLSchemes);
        this.overrideClasspath = overrideClasspath == null
            ? List.of() : Collections.unmodifiableList(overrideClasspath);
        this.maxBufferedJarRAMSize = maxBufferedJarRAMSize;
        this.enableMemoryMapping = enableMemoryMapping;
        this.enableMultiReleaseVersions = enableMultiReleaseVersions;
        this.removeTemporaryFilesAfterScan = removeTemporaryFilesAfterScan;
        this.ignoreParentClassLoaders = ignoreParentClassLoaders;
    }

    public static final ScanClasspathOptions DEFAULT = new ScanClasspathOptions(
        true, true, true, true, false, null, null,
        64 * 1024 * 1024, false, false, false, false
    );

    public boolean scanJars() { return scanJars; }
    public boolean scanNestedJars() { return scanNestedJars; }
    public boolean scanDirs() { return scanDirs; }
    public boolean scanModules() { return scanModules; }
    public boolean enableSystemJarsAndModules() { return enableSystemJarsAndModules; }
    public Set<String> allowedURLSchemes() { return allowedURLSchemes; }
    public List<Object> overrideClasspath() { return overrideClasspath; }
    public int maxBufferedJarRAMSize() { return maxBufferedJarRAMSize; }
    public boolean enableMemoryMapping() { return enableMemoryMapping; }
    public boolean enableMultiReleaseVersions() { return enableMultiReleaseVersions; }
    public boolean removeTemporaryFilesAfterScan() { return removeTemporaryFilesAfterScan; }
    public boolean ignoreParentClassLoaders() { return ignoreParentClassLoaders; }
}
