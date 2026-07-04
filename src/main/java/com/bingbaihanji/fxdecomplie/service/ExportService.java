package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.*;
import com.bingbaihanji.fxdecomplie.util.ClassNameUtil;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 导出服务支持单文件保存、批量导出到目录和 ZIP 归档
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private ExportService() {
        throw new AssertionError("utility class");
    }

    /**
     * 导出当前源码到磁盘文件
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
     * 根据用户选择的选项导出所有支持的工作区文件
     *
     * @param root       工作区树根节点
     * @param config     导出配置
     * @param index      工作区索引(预构建)
     * @param onProgress 进度回调,接收当前路径和百分比
     * @return 导出摘要
     * @throws IOException 无法创建输出容器时抛出
     */
    public static ExportResult exportAll(TreeItem<FileTreeNode> root, ExportConfig config,
                                         WorkspaceIndex index,
                                         BiConsumer<String, Integer> onProgress)
            throws IOException {
        List<FileTreeNode> nodes = collectExportableNodes(root, config.exportResources());
        return exportAll(nodes, config, index, null, onProgress, null);
    }

    public static ExportResult exportAll(TreeItem<FileTreeNode> root, ExportConfig config,
                                         WorkspaceIndex index, CommentScope commentScope,
                                         BiConsumer<String, Integer> onProgress)
            throws IOException {
        List<FileTreeNode> nodes = collectExportableNodes(root, config.exportResources());
        return exportAll(nodes, config, index, commentScope, onProgress, null);
    }

    /**
     * @param canceled 用户取消标志 supplier，非 null 时每轮迭代检查
     */
    public static ExportResult exportAll(List<FileTreeNode> nodes, ExportConfig config,
                                         WorkspaceIndex index, CommentScope commentScope,
                                         BiConsumer<String, Integer> onProgress,
                                         BooleanSupplier canceled)
            throws IOException {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(index, "index");

        // ---- 步骤 1: 过滤可导出的类/资源条目（调用方已在 FX 线程提取为普通 List）----
        List<FileTreeNode> items = new ArrayList<>();
        for (FileTreeNode node : nodes) {
            if (shouldExport(node, config.exportResources())) {
                items.add(node);
            }
        }
        log.info("导出开始: {} 个文件, format={}, engine={}, output={}",
                items.size(), config.format(), config.engine(), config.outputPath());
        long exportStart = System.currentTimeMillis();
        ExportState state = new ExportState(items.size(), onProgress);
        // ---- 步骤 2: 反编译并写入 — 分发到 ZIP 或目录路径 ----
        if (config.format() == ExportConfig.Format.ZIP) {
            exportAllToZip(items, index, config, commentScope, state, canceled);
        } else {
            exportAllToDir(items, index, config, commentScope, state, canceled);
        }

        long elapsed = System.currentTimeMillis() - exportStart;
        log.info("导出完成: {}/{} 成功, {} 错误 ({}ms)",
                state.successCount, state.totalFiles, state.errors.size(), elapsed);
        return new ExportResult(state.totalFiles, state.successCount, state.errors);
    }

    /**
     * 导出工作区所有 .class 文件为 .java 到目录
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
        List<FileTreeNode> nodes = collectExportableNodes(root, config.exportResources());
        exportAll(nodes, config, WorkspaceIndex.build(root), null, null, null);
    }

    /**
     * 导出所有类到目录(带进度回调)
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
     * 导出工作区所有 .class 文件为 ZIP
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
        List<FileTreeNode> nodes = collectExportableNodes(root, config.exportResources());
        exportAll(nodes, config, WorkspaceIndex.build(root), null, null, null);
    }

    /** 解析类节点的字节码(优先节点懒加载来源,其次工作区索引上下文) */
    private static byte[] resolveClassBytes(FileTreeNode data, DecompilerContext context)
            throws IOException {
        byte[] bytes = data.resolveBytes();
        if (bytes != null) {
            return bytes;
        }
        String fullPath = data.getFullPath();
        String internalName = ClassNameUtil.normalizeInternalName(fullPath);
        return context == null ? null : context.getClassBytes(internalName);
    }

    /**
     * 从 TreeItem 树中提取可导出节点的快照（必须在 FX 线程调用）。
     * 仅供遗留 API 和测试使用；新代码应在 FX 线程提取 FileTreeNode 列表后直接调用 exportAll。
     */
    @Deprecated
    private static List<FileTreeNode> collectExportableNodes(TreeItem<FileTreeNode> root,
                                                             boolean exportResources) {
        List<FileTreeNode> nodes = new ArrayList<>();
        collectExportableNodes(root, exportResources, nodes);
        return nodes;
    }

    private static void collectExportableNodes(TreeItem<FileTreeNode> item, boolean exportResources,
                                               List<FileTreeNode> nodes) {
        FileTreeNode data = item.getValue();
        if (data != null && shouldExport(data, exportResources)) {
            nodes.add(data);
        }
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            collectExportableNodes(child, exportResources, nodes);
        }
    }

    private static boolean shouldExport(FileTreeNode data, boolean exportResources) {
        return data.isClassFile()
                || (exportResources && (data.getNodeType() == FileTreeNode.NodeTypeEnum.RESOURCE
                || data.getNodeType() == FileTreeNode.NodeTypeEnum.JAVA_FILE));
    }

    private static void exportAllToDir(List<FileTreeNode> items, WorkspaceIndex index,
                                       ExportConfig config, CommentScope commentScope, ExportState state,
                                       BooleanSupplier canceled)
            throws IOException {
        Path outputDir = config.outputPath().toAbsolutePath().normalize();
        Files.createDirectories(outputDir);
        DecompilerContext context = DecompilerContext.fromWorkspaceIndex(index, config.engineOptions());
        for (FileTreeNode data : items) {
            if (canceled != null && canceled.getAsBoolean()) {
                state.errors.add("导出已取消");
                return;
            }
            try {
                // ---- 反编译: class → .java 源码, 资源 → 原始字节 ----
                ExportContent content = buildExportContent(data, context, config, commentScope, canceled);
                // ---- 路径验证: 确保输出保持在目标目录内 ----
                Path target = resolveSafeOutputPath(outputDir, content.relativePath());
                // ---- 冲突解决: 覆盖 / 跳过 / 重命名 ----
                target = applyDirConflictPolicy(target, config.conflictPolicy());
                if (target != null) {
                    // ---- 写入: 创建父目录并写入内容 ----
                    Files.createDirectories(target.getParent());
                    try {
                        Files.write(target, content.bytes());
                    } catch (Exception e) {
                        try {
                            Files.deleteIfExists(target);
                        } catch (IOException ignored) {
                            log.debug("清理失败写入残留文件失败: {}", target, ignored);
                        }
                        throw e;
                    }
                    state.successCount++;
                }
            } catch (Exception e) {
                state.errors.add(data.getFullPath() + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            } finally {
                state.advance(data.getFullPath());
            }
        }
    }

    private static void exportAllToZip(List<FileTreeNode> items, WorkspaceIndex index,
                                       ExportConfig config, CommentScope commentScope, ExportState state,
                                       BooleanSupplier canceled)
            throws IOException {
        Path zipPath = config.outputPath().toAbsolutePath().normalize();
        if (zipPath.getParent() != null) {
            Files.createDirectories(zipPath.getParent());
        }
        Set<String> writtenEntries = new HashSet<>();
        DecompilerContext context = DecompilerContext.fromWorkspaceIndex(index, config.engineOptions());
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
            boolean entryOpen = false;
            for (FileTreeNode data : items) {
                if (canceled != null && canceled.getAsBoolean()) {
                    state.errors.add("导出已取消");
                    if (entryOpen) {
                        try {
                            zos.closeEntry();
                        } catch (IOException e) {
                            log.debug("取消导出时关闭 ZIP 条目失败", e);
                        }
                    }
                    return;
                }
                try {
                    ExportContent content = buildExportContent(data, context, config, commentScope, canceled);
                    String entryName = sanitizeZipEntryName(content.relativePath());
                    entryName = applyZipConflictPolicy(entryName, config.conflictPolicy(), writtenEntries);
                    if (entryName != null) {
                        writtenEntries.add(entryName);
                        zos.putNextEntry(new ZipEntry(entryName));
                        entryOpen = true;
                        try {
                            zos.write(content.bytes());
                            state.successCount++;
                        } catch (IOException e) {
                            // 写入失败时关闭条目并终止整个 ZIP 导出，
                            // 避免 closeEntry 写入损坏的 CRC 导致整个归档不可用
                            try {
                                zos.closeEntry();
                            } catch (IOException ignored) {
                            }
                            entryOpen = false;
                            throw e;
                        } finally {
                            if (entryOpen) {
                                zos.closeEntry();
                                entryOpen = false;
                            }
                        }
                    }
                } catch (Exception e) {
                    state.errors.add(data.getFullPath() + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                    // ZIP 写入失败后整个归档已不可靠，终止导出
                    if (e instanceof IOException) {
                        break;
                    }
                } finally {
                    state.advance(data.getFullPath());
                }
            }
        }
    }

    private static ExportContent buildExportContent(FileTreeNode data, DecompilerContext context,
                                                    ExportConfig config, CommentScope commentScope,
                                                    BooleanSupplier canceled)
            throws IOException {
        if (data.isClassFile()) {
            byte[] bytes = resolveClassBytes(data, context);
            if (bytes == null) {
                throw new IllegalStateException(
                        "未找到类字节码: " + data.getFullPath());
            }
            // 使用外部取消标志而非 Thread.isInterrupted()，避免
            // 单个中断信号级联导致所有后续文件反编译失败
            BooleanSupplier active = canceled == null
                    ? () -> !Thread.currentThread().isInterrupted()
                    : () -> !canceled.getAsBoolean();
            String source = DecompilerRunner.decompileWithTimeout(
                    data.getFullPath(), bytes, config.engine(), context, active);
            if (DecompilerRunner.isFailureOutput(source)) {
                throw new IllegalStateException(firstLine(source));
            }
            String fullPath = data.getFullPath();
            String internalName = ClassNameUtil.normalizeInternalName(fullPath);
            String workspaceHash = commentScope == null ? "" : commentScope.workspaceHash();
            source = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .applyRenames(source, workspaceHash, internalName);
            if (commentScope != null && commentScope.enabled()) {
                source = CommentExportDecorator.applyForClass(source, data.getFullPath(), commentScope);
            }
            String javaPath = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .renamedJavaPath(internalName, workspaceHash);
            return new ExportContent(javaPath,
                    source.getBytes(StandardCharsets.UTF_8));
        }

        byte[] bytes = data.resolveBytes();
        if (bytes == null) {
            throw new IllegalStateException("资源字节码未找到");
        }
        return new ExportContent(data.getFullPath(), bytes);
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "反编译失败";
        }
        int end = text.indexOf('\n');
        return end >= 0 ? text.substring(0, end).trim() : text.trim();
    }

    private static Path resolveSafeOutputPath(Path outputDir, String relativePath) {
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
                ensureSafeDirectoryTree(targetRoot, realRoot, targetRoot.relativize(parent));
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

    private static String sanitizeZipEntryName(String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException("不安全的 ZIP 条目路径");
        }
        return path.toString().replace('\\', '/');
    }

    private static String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("输出路径为空");
        }
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new IllegalArgumentException("不安全的输出路径");
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
        throw new IOException("无法找到可用的输出路径: " + target);
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
        throw new IllegalStateException("无法找到可用的 ZIP 条目: " + entryName);
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
