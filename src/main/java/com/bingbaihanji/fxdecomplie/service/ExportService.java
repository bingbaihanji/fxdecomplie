package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.Decompiler;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.model.ExportResult;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import javafx.scene.control.TreeItem;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 导出服务。支持单文件保存、批量导出到目录和 ZIP 归档。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class ExportService {

    private ExportService() {
        throw new AssertionError("utility class");
    }

    /**
     * 导出当前源码到磁盘文件。
     *
     * @param code       源码内容
     * @param outputPath 输出路径
     * @throws IOException 写入文件失败时抛出
     */
    public static void exportCurrentCode(String code, Path outputPath) throws IOException {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(outputPath, "outputPath");
        Files.writeString(outputPath, code);
    }

    /**
     * Export all supported workspace files according to user selected options.
     *
     * @param root       workspace tree root
     * @param config     export configuration
     * @param index      workspace index (pre-built)
     * @param onProgress progress callback receiving current path and percent
     * @return export summary
     * @throws IOException when output container cannot be created
     */
    public static ExportResult exportAll(TreeItem<FileTreeNode> root, ExportConfig config,
                                         WorkspaceIndex index,
                                         BiConsumer<String, Integer> onProgress)
            throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(index, "index");

        // ---- Step 1: tree walk — collect all exportable class/resource items ----
        List<TreeItem<FileTreeNode>> items = collectExportableItems(root, config.exportResources());
        ExportState state = new ExportState(items.size(), onProgress);
        Decompiler decompiler = DecompilerFactory.getDecompiler(config.engine());
        DecompilerContext context = DecompilerContext.fromWorkspaceIndex(index, config.engineOptions());

        // ---- Step 2: decompile & write — dispatch to ZIP or directory path ----
        if (config.format() == ExportConfig.Format.ZIP) {
            exportAllToZip(items, decompiler, context, config, state);
        } else {
            exportAllToDir(items, decompiler, context, config, state);
        }

        return new ExportResult(state.totalFiles, state.successCount, state.errors);
    }

    /**
     * 导出工作区所有 .class 文件为 .java 到目录。
     *
     * @param root      文件树根节点
     * @param engine    反编译引擎
     * @param outputDir 输出目录
     * @throws IOException 写入文件失败时抛出
     */
    public static void exportAllToDir(TreeItem<FileTreeNode> root, DecompilerTypeEnum engine,
                                      Path outputDir) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(outputDir, "outputDir");
        ExportConfig config = new ExportConfig(outputDir, engine, ExportConfig.Format.DIR,
                ExportConfig.ConflictPolicy.OVERWRITE, false);
        exportAll(root, config, WorkspaceIndex.build(root), null);
    }

    /**
     * 导出所有类到目录（带进度回调）。
     * @param onProgress 进度回调 0-100
     */
    public static void exportAllToDir(TreeItem<FileTreeNode> root, DecompilerTypeEnum engine,
                                      Path outputDir, IntConsumer onProgress)
            throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(outputDir, "outputDir");
        ExportConfig config = new ExportConfig(outputDir, engine, ExportConfig.Format.DIR,
                ExportConfig.ConflictPolicy.OVERWRITE, false);
        exportAll(root, config, WorkspaceIndex.build(root), (path, pct) -> {
            if (onProgress != null) {
                onProgress.accept(pct);
            }
        });
    }

    /**
     * 导出工作区所有 .class 文件为 ZIP。
     *
     * @param root    文件树根节点
     * @param engine  反编译引擎
     * @param zipPath ZIP 输出路径
     * @throws IOException 写入 ZIP 失败时抛出
     */
    public static void exportAllToZip(TreeItem<FileTreeNode> root, DecompilerTypeEnum engine,
                                      Path zipPath) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(zipPath, "zipPath");
        ExportConfig config = new ExportConfig(zipPath, engine, ExportConfig.Format.ZIP,
                ExportConfig.ConflictPolicy.OVERWRITE, false);
        exportAll(root, config, WorkspaceIndex.build(root), null);
    }

    /** 解析类节点的字节码（优先节点缓存，其次全局缓存） */
    private static byte[] resolveClassBytes(FileTreeNode data) {
        byte[] bytes = data.getCachedBytes();
        return bytes;
    }

    public static java.util.Map<String, String> engineOptions(AppConfig appConfig,
                                                              DecompilerTypeEnum engine) {
        if (appConfig == null || appConfig.decompiler().engineOptions() == null || engine == null) {
            return java.util.Map.of();
        }
        var options = appConfig.decompiler().engineOptions().get(engine.name());
        return options == null ? java.util.Map.of() : java.util.Map.copyOf(options);
    }

    private static List<TreeItem<FileTreeNode>> collectExportableItems(TreeItem<FileTreeNode> root,
                                                                       boolean exportResources) {
        List<TreeItem<FileTreeNode>> items = new ArrayList<>();
        collectExportableItems(root, exportResources, items);
        return items;
    }

    private static void collectExportableItems(TreeItem<FileTreeNode> item, boolean exportResources,
                                               List<TreeItem<FileTreeNode>> items) {
        FileTreeNode data = item.getValue();
        if (data != null && shouldExport(data, exportResources)) {
            items.add(item);
        }
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            collectExportableItems(child, exportResources, items);
        }
    }

    private static boolean shouldExport(FileTreeNode data, boolean exportResources) {
        return data.isClassFile()
                || (exportResources && (data.getNodeType() == FileTreeNode.NodeTypeEnum.RESOURCE
                || data.getNodeType() == FileTreeNode.NodeTypeEnum.JAVA_FILE));
    }

    private static void exportAllToDir(List<TreeItem<FileTreeNode>> items, Decompiler decompiler,
                                       DecompilerContext context,
                                       ExportConfig config, ExportState state)
            throws IOException {
        Path outputDir = config.outputPath().toAbsolutePath().normalize();
        Files.createDirectories(outputDir);
        for (TreeItem<FileTreeNode> item : items) {
            if (Thread.currentThread().isInterrupted()) {
                state.errors.add("Export canceled");
                return;
            }
            FileTreeNode data = item.getValue();
            try {
                // ---- Decompile: class -> .java source, resource -> raw bytes ----
                ExportContent content = buildExportContent(data, decompiler, context);
                // ---- Path validate: ensure output stays inside the target directory ----
                Path target = resolveSafeOutputPath(outputDir, content.relativePath());
                // ---- Conflict resolve: OVERWRITE / SKIP / RENAME ----
                target = applyDirConflictPolicy(target, config.conflictPolicy());
                if (target != null) {
                    // ---- Write: create parent directories and write content ----
                    Files.createDirectories(target.getParent());
                    Files.write(target, content.bytes());
                    state.successCount++;
                }
            } catch (Exception e) {
                state.errors.add(data.getFullPath() + ": " + e.getMessage());
            } finally {
                state.advance(data.getFullPath());
            }
        }
    }

    private static void exportAllToZip(List<TreeItem<FileTreeNode>> items, Decompiler decompiler,
                                       DecompilerContext context,
                                       ExportConfig config, ExportState state)
            throws IOException {
        Path zipPath = config.outputPath().toAbsolutePath().normalize();
        if (zipPath.getParent() != null) {
            Files.createDirectories(zipPath.getParent());
        }
        Set<String> writtenEntries = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
            for (TreeItem<FileTreeNode> item : items) {
                if (Thread.currentThread().isInterrupted()) {
                    state.errors.add("Export canceled");
                    try {
                        zos.closeEntry();
                    } catch (IOException ignored) {
                    }
                    return;
                }
                FileTreeNode data = item.getValue();
                try {
                    ExportContent content = buildExportContent(data, decompiler, context);
                    String entryName = sanitizeZipEntryName(content.relativePath());
                    entryName = applyZipConflictPolicy(entryName, config.conflictPolicy(), writtenEntries);
                    if (entryName != null) {
                        writtenEntries.add(entryName);
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.write(content.bytes());
                        zos.closeEntry();
                        state.successCount++;
                    }
                } catch (Exception e) {
                    state.errors.add(data.getFullPath() + ": " + e.getMessage());
                } finally {
                    state.advance(data.getFullPath());
                }
            }
        }
    }

    private static ExportContent buildExportContent(FileTreeNode data, Decompiler decompiler,
                                                    DecompilerContext context) {
        if (data.isClassFile()) {
            byte[] bytes = resolveClassBytes(data);
            if (bytes == null) {
                throw new IllegalStateException(
                        "class bytes not found for " + data.getFullPath()
                                + " — open the class first or re-index the workspace");
            }
            String source = decompiler.decompile(data.getFullPath(), bytes, context);
            return new ExportContent(data.getFullPath().replace(".class", ".java"),
                    source.getBytes(StandardCharsets.UTF_8));
        }

        byte[] bytes = data.getCachedBytes();
        if (bytes == null) {
            throw new IllegalStateException("resource bytes not found");
        }
        return new ExportContent(data.getFullPath(), bytes);
    }

    private static Path resolveSafeOutputPath(Path outputDir, String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        Path relative = Path.of(normalized).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("unsafe output path");
        }

        Path targetRoot = outputDir.toAbsolutePath().normalize();
        Path target = targetRoot.resolve(relative).normalize();
        if (!target.startsWith(targetRoot)) {
            throw new IllegalArgumentException("unsafe output path: resolves outside target directory");
        }

        try {
            Files.createDirectories(targetRoot);
            Path realRoot = targetRoot.toRealPath();
            Path parent = target.getParent();
            if (parent != null) {
                ensureSafeDirectoryTree(targetRoot, realRoot, targetRoot.relativize(parent));
                Path realParent = parent.toRealPath();
                Path realTarget = realParent.resolve(target.getFileName()).normalize();
                if (!realTarget.startsWith(realRoot)) {
                    throw new IllegalArgumentException("unsafe output path: resolves outside target directory");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot resolve output path: " + target, e);
        }
        return target;
    }

    private static void ensureSafeDirectoryTree(Path targetRoot, Path realRoot,
                                                Path relativeParent) throws IOException {
        Path current = targetRoot;
        for (Path part : relativeParent) {
            current = current.resolve(part).normalize();
            if (!current.startsWith(targetRoot)) {
                throw new IllegalArgumentException("unsafe output path");
            }
            if (Files.exists(current, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                Path realCurrent = current.toRealPath();
                if (!realCurrent.startsWith(realRoot)) {
                    throw new IllegalArgumentException("unsafe output path: resolves outside target directory");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private static String sanitizeZipEntryName(String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException("unsafe ZIP entry path");
        }
        return path.toString().replace('\\', '/');
    }

    private static String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("empty output path");
        }
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new IllegalArgumentException("unsafe output path");
        }
        return normalized;
    }

    private static Path applyDirConflictPolicy(Path target, ExportConfig.ConflictPolicy policy)
            throws IOException {
        if (!Files.exists(target) || policy == ExportConfig.ConflictPolicy.OVERWRITE) {
            return target;
        }
        if (policy == ExportConfig.ConflictPolicy.SKIP) {
            return null;
        }
        return nextAvailablePath(target);
    }

    private static String applyZipConflictPolicy(String entryName, ExportConfig.ConflictPolicy policy,
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
        for (int i = 1; i < 10_000; i++) {
            Path candidate = parent.resolve(baseName + "-" + i + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("unable to find available output path: " + target);
    }

    private static String nextAvailableEntry(String entryName, Set<String> writtenEntries) {
        int slash = entryName.lastIndexOf('/');
        String dir = slash >= 0 ? entryName.substring(0, slash + 1) : "";
        String fileName = slash >= 0 ? entryName.substring(slash + 1) : entryName;
        String baseName = baseName(fileName);
        String extension = extension(fileName);
        for (int i = 1; i < 10_000; i++) {
            String candidate = dir + baseName + "-" + i + extension;
            if (!writtenEntries.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("unable to find available ZIP entry: " + entryName);
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot) : "";
    }

    private record ExportContent(String relativePath, byte[] bytes) {
    }

    private static final class ExportState {
        private final int totalFiles;
        private final BiConsumer<String, Integer> onProgress;
        private final List<String> errors = new ArrayList<>();
        private int completed;
        private int successCount;

        private ExportState(int totalFiles, BiConsumer<String, Integer> onProgress) {
            this.totalFiles = totalFiles;
            this.onProgress = onProgress;
        }

        private void advance(String currentPath) {
            completed++;
            if (onProgress != null) {
                int percent = totalFiles == 0 ? 100 : completed * 100 / totalFiles;
                onProgress.accept(currentPath, percent);
            }
        }
    }
}
