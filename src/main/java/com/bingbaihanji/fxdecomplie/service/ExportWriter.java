package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.ExportConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 导出写入器 — 负责将反编译内容写入目录或 ZIP 归档.
 *
 * <p>从 {@link ExportService} 中提取的写入层,包含路径安全校验.
 * 目录写入和 ZIP 写入共享相同的路径安全逻辑.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
final class ExportWriter {

    private ExportWriter() {
        throw new AssertionError("utility class");
    }

    /** 导出源码到单个磁盘文件 */
    static void exportCurrentCode(String code, Path outputPath) throws IOException {
        Files.writeString(outputPath, code);
    }

    /** 按导出格式创建增量写入器. */
    static ExportSink openSink(ExportConfig config) throws IOException {
        if (config.format() == ExportConfig.Format.ZIP) {
            return new ZipExportSink(config.outputPath(), config.conflictPolicy());
        }
        return new DirExportSink(config.outputPath(), config.conflictPolicy());
    }

    // ─── 路径安全 ───

    /** 将相对路径解析为绝对输出路径,防御路径穿越攻击 */
    static Path resolveSafeOutputPath(Path outputDir, String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        Path relative = Path.of(normalized).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("不安全的输出路径");
        }
        Path targetRoot = outputDir.toAbsolutePath().normalize();
        Path target = targetRoot.resolve(relative).normalize();
        if (!target.startsWith(targetRoot)) {
            throw new IllegalArgumentException("不安全的输出路径: 解析到目标目录之外");
        }
        try {
            Files.createDirectories(targetRoot);
            Path realRoot = targetRoot.toRealPath();
            Path parent = target.getParent();
            if (parent != null) {
                ensureSafeDirectoryTree(targetRoot, realRoot,
                        targetRoot.relativize(parent));
                Path realParent = parent.toRealPath();
                Path realTarget = realParent.resolve(target.getFileName()).normalize();
                if (!realTarget.startsWith(realRoot)) {
                    throw new IllegalArgumentException("不安全的输出路径: 解析到目标目录之外");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法解析输出路径: " + target, e);
        }
        return target;
    }

    private static void ensureSafeDirectoryTree(Path targetRoot, Path realRoot,
                                                Path relativeParent) throws IOException {
        Path current = targetRoot;
        for (Path part : relativeParent) {
            current = current.resolve(part).normalize();
            if (!current.startsWith(targetRoot)) {
                throw new IllegalArgumentException("不安全的输出路径");
            }
            if (Files.exists(current, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                Path realCurrent = current.toRealPath();
                if (!realCurrent.startsWith(realRoot)) {
                    throw new IllegalArgumentException("不安全的输出路径: 解析到目标目录之外");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    static String sanitizeZipEntryName(String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException("不安全的 ZIP 条目路径");
        }
        return path.toString().replace('\\', '/');
    }

    static String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("输出路径为空");
        }
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new IllegalArgumentException("不安全的输出路径");
        }
        return normalized;
    }
}
