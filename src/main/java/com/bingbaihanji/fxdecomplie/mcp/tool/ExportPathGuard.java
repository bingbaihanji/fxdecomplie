package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.config.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * MCP 导出路径安全校验.
 *
 * <p>允许任意绝对路径,但拒绝文件系统根目录,应用配置/日志/缓存目录,
 * 以及默认不允许覆盖已有输出.</p>
 *
 * @author bingbaihanji
 */
public final class ExportPathGuard {

    private ExportPathGuard() {
        throw new AssertionError("utility class");
    }

    public static Path validate(String rawPath, boolean zip, boolean overwrite) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new McpToolException("INVALID_ARGUMENT", "outputPath is required", Map.of());
        }
        Path path;
        try {
            path = Path.of(rawPath);
        } catch (RuntimeException e) {
            throw new McpToolException("EXPORT_PATH_UNSAFE", "Invalid output path",
                    Map.of("outputPath", rawPath));
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!path.isAbsolute()) {
            throw new McpToolException("EXPORT_PATH_UNSAFE", "outputPath must be absolute",
                    Map.of("outputPath", rawPath));
        }
        if (normalized.getParent() == null) {
            throw new McpToolException("EXPORT_PATH_UNSAFE", "Refusing to export to filesystem root",
                    Map.of("outputPath", normalized.toString()));
        }
        Path appDir = AppConfig.appDir().toAbsolutePath().normalize();
        for (String protectedName : new String[]{"config", "logs", "cache"}) {
            if (normalized.startsWith(appDir.resolve(protectedName))) {
                throw new McpToolException("EXPORT_PATH_UNSAFE",
                        "Refusing to export into application " + protectedName + " directory",
                        Map.of("outputPath", normalized.toString()));
            }
        }
        if (zip) {
            if (Files.exists(normalized) && !overwrite) {
                throw new McpToolException("EXPORT_OUTPUT_EXISTS",
                        "ZIP output already exists; pass overwriteOutput=true to replace it",
                        Map.of("outputPath", normalized.toString()));
            }
            if (Files.exists(normalized) && Files.isDirectory(normalized)) {
                throw new McpToolException("EXPORT_PATH_UNSAFE",
                        "ZIP output path is a directory",
                        Map.of("outputPath", normalized.toString()));
            }
        } else {
            if (Files.exists(normalized) && !Files.isDirectory(normalized)) {
                throw new McpToolException("EXPORT_PATH_UNSAFE",
                        "Directory output path is a regular file",
                        Map.of("outputPath", normalized.toString()));
            }
            if (Files.isDirectory(normalized) && !overwrite && !isEmptyDirectory(normalized)) {
                throw new McpToolException("EXPORT_OUTPUT_EXISTS",
                        "Directory output is not empty; pass overwriteOutput=true to continue",
                        Map.of("outputPath", normalized.toString()));
            }
        }
        return normalized;
    }

    private static boolean isEmptyDirectory(Path directory) {
        try (var stream = Files.list(directory)) {
            return stream.findAny().isEmpty();
        } catch (IOException e) {
            throw new McpToolException("EXPORT_PATH_UNSAFE",
                    "Unable to inspect output directory", Map.of());
        }
    }
}
