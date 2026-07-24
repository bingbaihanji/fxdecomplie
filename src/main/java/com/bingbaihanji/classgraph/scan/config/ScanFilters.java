package com.bingbaihanji.classgraph.scan.config;

/**
 * 扫描过滤器配置 — 控制包/类/模块/JAR 的接受/拒绝规则。
 *
 * <p>使用 ClassGraph 现有的 {@code Filter} 类族作为底层实现。
 * 不可变，通过 {@link ScanOptionsBuilder} 构建。</p>
 */
public final class ScanFilters {

    private final boolean ignoreClassVisibility;
    private final boolean ignoreFieldVisibility;
    private final boolean ignoreMethodVisibility;
    private final boolean disableRuntimeInvisibleAnnotations;
    private final boolean extendScanningUpwardsToExternalClasses;

    ScanFilters(boolean ignoreClassVisibility, boolean ignoreFieldVisibility,
                boolean ignoreMethodVisibility, boolean disableRuntimeInvisibleAnnotations,
                boolean extendScanningUpwardsToExternalClasses) {
        this.ignoreClassVisibility = ignoreClassVisibility;
        this.ignoreFieldVisibility = ignoreFieldVisibility;
        this.ignoreMethodVisibility = ignoreMethodVisibility;
        this.disableRuntimeInvisibleAnnotations = disableRuntimeInvisibleAnnotations;
        this.extendScanningUpwardsToExternalClasses = extendScanningUpwardsToExternalClasses;
    }

    public static final ScanFilters DEFAULT = new ScanFilters(
        false, false, false, false, true
    );

    public boolean ignoreClassVisibility() { return ignoreClassVisibility; }
    public boolean ignoreFieldVisibility() { return ignoreFieldVisibility; }
    public boolean ignoreMethodVisibility() { return ignoreMethodVisibility; }
    public boolean disableRuntimeInvisibleAnnotations() { return disableRuntimeInvisibleAnnotations; }
    public boolean extendScanningUpwardsToExternalClasses() {
        return extendScanningUpwardsToExternalClasses;
    }
}
