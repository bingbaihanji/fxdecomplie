package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 目录导出写入器:逐项写文件,冲突策略由文件系统状态决定.
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
final class DirExportSink implements ExportSink {

    private final Path outputDir;

    private final ExportConfig.ConflictPolicy conflictPolicy;

    DirExportSink(Path outputPath, ExportConfig.ConflictPolicy conflictPolicy) throws IOException {
        this.outputDir = outputPath.toAbsolutePath().normalize();
        this.conflictPolicy = conflictPolicy;
        Files.createDirectories(outputDir);
    }

    @Override
    public boolean write(FileTreeNode item, ExportService.ExportContent content) throws IOException {
        Path target = ExportWriter.resolveSafeOutputPath(outputDir, content.relativePath());
        target = ExportConflictResolver.applyDirConflictPolicy(target, conflictPolicy);
        if (target == null) {
            return false;
        }
        Files.createDirectories(target.getParent());
        try {
            Files.write(target, content.bytes());
        } catch (IOException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
            throw e;
        }
        return true;
    }

    @Override
    public void close() {
        // 目录写入无需关闭资源
    }
}
