package com.bingbihanji.fxdecomplie.model;

import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Batch export configuration selected by the user.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public record ExportConfig(
        Path outputPath,
        DecompilerTypeEnum engine,
        Format format,
        ConflictPolicy conflictPolicy,
        boolean exportResources
) {

    public ExportConfig {
        Objects.requireNonNull(outputPath, "outputPath");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(conflictPolicy, "conflictPolicy");
    }

    public enum Format {
        DIR,
        ZIP
    }

    public enum ConflictPolicy {
        SKIP,
        OVERWRITE,
        RENAME
    }
}
