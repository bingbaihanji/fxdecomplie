package com.bingbaihanji.classgraph.scan.config;

/**
 * 不可变扫描配置 — 聚合所有扫描选项。
 *
 * <p>通过 {@link ScanOptionsBuilder} 构建。构建后不可修改。
 * 设计用于替代 {@code ScanConfig} 的可变公共字段。</p>
 *
 * <p>示例用法：
 * <pre>{@code
 * ScanOptions options = new ScanOptionsBuilder()
 *     .metadata(m -> m.withAll())
 *     .classpath(c -> c.scanJars(true))
 *     .build();
 * }</pre>
 */
public final class ScanOptions {

    private final ScanMetadataOptions metadata;
    private final ScanClasspathOptions classpath;
    private final ScanRuntimeOptions runtime;
    private final ScanFilters filters;
    private final int maxThreads;

    ScanOptions(ScanMetadataOptions metadata, ScanClasspathOptions classpath,
                ScanRuntimeOptions runtime, ScanFilters filters, int maxThreads) {
        this.metadata = metadata != null ? metadata : ScanMetadataOptions.DEFAULT;
        this.classpath = classpath != null ? classpath : ScanClasspathOptions.DEFAULT;
        this.runtime = runtime != null ? runtime : ScanRuntimeOptions.DEFAULT;
        this.filters = filters != null ? filters : ScanFilters.DEFAULT;
        this.maxThreads = Math.max(0, maxThreads);
    }

    /** 全部使用默认值的扫描选项 */
    public static final ScanOptions DEFAULT = new ScanOptions(
        ScanMetadataOptions.DEFAULT,
        ScanClasspathOptions.DEFAULT,
        ScanRuntimeOptions.DEFAULT,
        ScanFilters.DEFAULT,
        0
    );

    /** 启用所有元数据收集（字段、方法、注解）的扫描选项 */
    public static final ScanOptions ALL_METADATA = new ScanOptions(
        ScanMetadataOptions.ALL,
        ScanClasspathOptions.DEFAULT,
        ScanRuntimeOptions.DEFAULT,
        ScanFilters.DEFAULT,
        0
    );

    // ─── 访问器 ───

    public ScanMetadataOptions metadata() { return metadata; }
    public ScanClasspathOptions classpath() { return classpath; }
    public ScanRuntimeOptions runtime() { return runtime; }
    public ScanFilters filters() { return filters; }
    public int maxThreads() { return maxThreads; }

    // ─── 便捷委托方法 ───

    public boolean enableClassInfo() { return metadata.enableClassInfo(); }
    public boolean enableFieldInfo() { return metadata.enableFieldInfo(); }
    public boolean enableMethodInfo() { return metadata.enableMethodInfo(); }
    public boolean enableAnnotationInfo() { return metadata.enableAnnotationInfo(); }
    public boolean enableExternalClasses() { return metadata.enableExternalClasses(); }
    public boolean enableInterClassDependencies() { return metadata.enableInterClassDependencies(); }
    public boolean scanJars() { return classpath.scanJars(); }
    public boolean scanDirs() { return classpath.scanDirs(); }
    public boolean scanModules() { return classpath.scanModules(); }
    public boolean enableSystemJarsAndModules() { return classpath.enableSystemJarsAndModules(); }

    /** 返回一个新的 Builder，预填充此 ScanOptions 的值 */
    public ScanOptionsBuilder toBuilder() {
        return new ScanOptionsBuilder()
            .metadata(metadata)
            .classpath(classpath)
            .runtime(runtime)
            .filters(filters)
            .maxThreads(maxThreads);
    }

    @Override
    public String toString() {
        return "ScanOptions{metadata=" + metadata + ", classpath=" + classpath
            + ", runtime=" + runtime + ", filters=" + filters
            + ", maxThreads=" + maxThreads + '}';
    }
}
