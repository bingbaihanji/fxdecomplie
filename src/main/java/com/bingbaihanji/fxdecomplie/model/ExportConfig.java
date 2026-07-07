package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * 用户选择的批量导出配置
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

    /**
     * 便捷构造函数,默认不传递额外的引擎选项
     */
    public ExportConfig(Path outputPath, DecompilerTypeEnum engine, Format format,
                        ConflictPolicy conflictPolicy, boolean exportResources) {
        this(outputPath, engine, format, conflictPolicy, exportResources, Map.of());
    }

    /** 导出文件格式 */
    public enum Format {
        /** 导出为目录结构 */
        DIR,
        /** 导出为 ZIP 压缩包 */
        ZIP
    }

    /** 文件名冲突处理策略 */
    public enum ConflictPolicy {
        /** 跳过已存在的文件 */
        SKIP,
        /** 覆盖已存在的文件 */
        OVERWRITE,
        /** 自动重命名以避免冲突 */
        RENAME
    }
}
