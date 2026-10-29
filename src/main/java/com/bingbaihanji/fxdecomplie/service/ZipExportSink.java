package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP 导出写入器:单个 ZipOutputStream 按原始顺序写入.
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
final class ZipExportSink implements ExportSink {

    private final ZipOutputStream zipStream;

    private final ExportConfig.ConflictPolicy conflictPolicy;

    private final Set<String> writtenEntries = new HashSet<>();

    ZipExportSink(Path zipPath, ExportConfig.ConflictPolicy conflictPolicy) throws IOException {
        Path normalized = zipPath.toAbsolutePath().normalize();
        if (normalized.getParent() != null) {
            Files.createDirectories(normalized.getParent());
        }
        this.zipStream = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(normalized)));
        this.conflictPolicy = conflictPolicy;
    }

    @Override
    public boolean write(FileTreeNode item, ExportService.ExportContent content) throws IOException {
        String entryName = ExportWriter.sanitizeZipEntryName(content.relativePath());
        entryName = ExportConflictResolver.applyZipConflictPolicy(
                entryName, conflictPolicy, writtenEntries);
        if (entryName == null) {
            return false;
        }
        writtenEntries.add(entryName);
        zipStream.putNextEntry(new ZipEntry(entryName));
        try {
            zipStream.write(content.bytes());
        } catch (IOException e) {
            try {
                zipStream.closeEntry();
            } catch (IOException ignored) {
            }
            throw e;
        }
        zipStream.closeEntry();
        return true;
    }

    @Override
    public void close() throws IOException {
        zipStream.close();
    }
}
