package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.nio.file.Path;
import java.util.Map;
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
        boolean exportResources,
        Map<String, String> engineOptions
) {

    public ExportConfig {
        Objects.requireNonNull(outputPath, "outputPath");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        engineOptions = engineOptions == null ? Map.of() : Map.copyOf(engineOptions);
    }

    public ExportConfig(Path outputPath, DecompilerTypeEnum engine, Format format,
                        ConflictPolicy conflictPolicy, boolean exportResources) {
        this(outputPath, engine, format, conflictPolicy, exportResources, Map.of());
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
