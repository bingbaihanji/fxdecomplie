package com.bingbaihanji.classgraph.scan.config;

/**
 * 元数据提取选项 — 控制扫描期间收集哪些类元数据信息。
 *
 * <p>不可变，通过 {@link ScanOptionsBuilder} 构建。</p>
 */
public final class ScanMetadataOptions {

    private final boolean enableClassInfo;
    private final boolean enableFieldInfo;
    private final boolean enableMethodInfo;
    private final boolean enableAnnotationInfo;
    private final boolean enableStaticFinalFieldConstantInitializerValues;
    private final boolean enableInterClassDependencies;
    private final boolean enableExternalClasses;

    ScanMetadataOptions(boolean enableClassInfo, boolean enableFieldInfo,
                        boolean enableMethodInfo, boolean enableAnnotationInfo,
                        boolean enableStaticFinalFieldConstantInitializerValues,
                        boolean enableInterClassDependencies,
                        boolean enableExternalClasses) {
        this.enableClassInfo = enableClassInfo;
        this.enableFieldInfo = enableFieldInfo;
        this.enableMethodInfo = enableMethodInfo;
        this.enableAnnotationInfo = enableAnnotationInfo;
        this.enableStaticFinalFieldConstantInitializerValues =
            enableStaticFinalFieldConstantInitializerValues;
        this.enableInterClassDependencies = enableInterClassDependencies;
        this.enableExternalClasses = enableExternalClasses;
    }

    /** 默认值：仅类级别信息 */
    public static final ScanMetadataOptions DEFAULT = new ScanMetadataOptions(
        false, false, false, false, false, false, false
    );

    /** 全部启用 */
    public static final ScanMetadataOptions ALL = new ScanMetadataOptions(
        true, true, true, true, true, true, true
    );

    public boolean enableClassInfo() { return enableClassInfo; }
    public boolean enableFieldInfo() { return enableFieldInfo; }
    public boolean enableMethodInfo() { return enableMethodInfo; }
    public boolean enableAnnotationInfo() { return enableAnnotationInfo; }
    public boolean enableStaticFinalFieldConstantInitializerValues() {
        return enableStaticFinalFieldConstantInitializerValues;
    }
    public boolean enableInterClassDependencies() { return enableInterClassDependencies; }
    public boolean enableExternalClasses() { return enableExternalClasses; }
}
