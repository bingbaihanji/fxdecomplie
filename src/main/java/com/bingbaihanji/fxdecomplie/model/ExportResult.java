package com.bingbaihanji.fxdecomplie.model;

import java.util.List;
import java.util.Objects;

/**
 * Result summary for a batch export.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public record ExportResult(int totalFiles, int successCount, List<String> errors) {

    public ExportResult {
        Objects.requireNonNull(errors, "errors");
        errors = List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public int failedCount() {
        return errors.size();
    }
}
