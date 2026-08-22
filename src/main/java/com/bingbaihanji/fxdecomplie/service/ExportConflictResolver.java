package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.ExportConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 导出冲突解决器 — 处理文件/条目重名时的冲突策略.
 *
 * <p>从 {@link ExportService} 中提取,支持三种策略:
 * <ul>
 *   <li>{@code OVERWRITE} — 直接覆盖</li>
 *   <li>{@code SKIP} — 跳过已存在的文件</li>
 *   <li>{@code RENAME} — 自动重命名(追加 -1, -2 ... -9999)</li>
 * </ul>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
final class ExportConflictResolver {

    private static final int MAX_RENAME_ATTEMPTS = 10_000;

    private ExportConflictResolver() {
        throw new AssertionError("utility class");
    }

    /** 目录输出冲突处理 */
    static Path applyDirConflictPolicy(Path target,
                                       ExportConfig.ConflictPolicy policy)
            throws IOException {
        if (!Files.exists(target) || policy == ExportConfig.ConflictPolicy.OVERWRITE) {
            return target;
        }
        if (policy == ExportConfig.ConflictPolicy.SKIP) {
            return null;
        }
        return nextAvailablePath(target);
    }

    /** ZIP 条目冲突处理 */
    static String applyZipConflictPolicy(String entryName,
                                         ExportConfig.ConflictPolicy policy,
                                         Set<String> writtenEntries) {
        if (!writtenEntries.contains(entryName)) {
            return entryName;
        }
        if (policy == ExportConfig.ConflictPolicy.SKIP) {
            return null;
        }
        return nextAvailableEntry(entryName, writtenEntries);
    }

    private static Path nextAvailablePath(Path target) throws IOException {
        Path parent = target.getParent();
        String fileName = target.getFileName().toString();
        String baseName = baseName(fileName);
        String extension = extension(fileName);
        for (int i = 1; i < MAX_RENAME_ATTEMPTS; i++) {
            Path candidate = parent.resolve(baseName + "-" + i + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("无法找到可用的输出路径: " + target);
    }

    private static String nextAvailableEntry(String entryName,
                                             Set<String> writtenEntries) {
        int slash = entryName.lastIndexOf('/');
        String dir = slash >= 0 ? entryName.substring(0, slash + 1) : "";
        String fileName = slash >= 0 ? entryName.substring(slash + 1) : entryName;
        String baseName = baseName(fileName);
        String extension = extension(fileName);
        for (int i = 1; i < MAX_RENAME_ATTEMPTS; i++) {
            String candidate = dir + baseName + "-" + i + extension;
            if (!writtenEntries.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法找到可用的 ZIP 条目: " + entryName);
    }

    // ─── 文件名工具 ───

    static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot) : "";
    }
}
