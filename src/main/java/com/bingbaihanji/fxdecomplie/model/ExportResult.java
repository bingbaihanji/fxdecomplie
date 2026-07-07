package com.bingbaihanji.fxdecomplie.model;

import java.util.List;
import java.util.Objects;

/**
 * 批量导出的结果摘要
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public record ExportResult(int totalFiles, int successCount, List<String> errors) {

    public ExportResult {
        Objects.requireNonNull(errors, "errors");
        errors = List.copyOf(errors);
    }

    /** @return 导出过程中是否发生了错误 */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /** @return 导出失败的文件数量 */
    public int failedCount() {
        return errors.size();
    }
}
