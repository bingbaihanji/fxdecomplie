package com.bingbaihanji.fxdecomplie.service.classscan;

import java.nio.file.Path;
import java.util.List;

/**
 * 扫描选项 — 控制扫描行为和返回的信息粒度。
 *
 * @author bingbaihanji
 */
public record ClassScanOptions(
    boolean enableClassInfo,
    boolean enableFieldInfo,
    boolean enableMethodInfo,
    boolean enableAnnotationInfo,
    List<Path> extraClasspath,
    int maxThreads
) {
    /** 默认选项：启用所有信息类型 */
    public static final ClassScanOptions DEFAULT = new ClassScanOptions(
        true, true, true, true, List.of(), 0
    );

    /** 仅类级别信息（不含字段和方法细节） */
    public static final ClassScanOptions CLASS_LEVEL_ONLY = new ClassScanOptions(
        true, false, false, true, List.of(), 0
    );

    public ClassScanOptions {
        if (extraClasspath == null) extraClasspath = List.of();
        if (maxThreads < 0) maxThreads = 0;
    }
}
