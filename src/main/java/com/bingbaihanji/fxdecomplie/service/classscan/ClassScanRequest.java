package com.bingbaihanji.fxdecomplie.service.classscan;

import com.bingbaihanji.fxdecomplie.model.Workspace;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 扫描请求 — 不可变的扫描输入参数。
 *
 * @author bingbaihanji
 */
public record ClassScanRequest(
    Workspace workspace,
    Predicate<String> classFilter,
    ClassScanOptions options
) {
    public ClassScanRequest {
        Objects.requireNonNull(workspace, "workspace");
        if (options == null) {
            options = ClassScanOptions.DEFAULT;
        }
    }

    /** 便捷构造：扫描全部类，使用默认选项 */
    public static ClassScanRequest of(Workspace workspace) {
        return new ClassScanRequest(workspace, null, ClassScanOptions.DEFAULT);
    }

    /** 便捷构造：按过滤条件扫描 */
    public static ClassScanRequest withFilter(Workspace workspace,
                                               Predicate<String> classFilter) {
        return new ClassScanRequest(workspace, classFilter, ClassScanOptions.DEFAULT);
    }
}
