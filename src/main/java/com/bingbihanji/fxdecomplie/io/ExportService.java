package com.bingbihanji.fxdecomplie.io;

import com.bingbihanji.fxdecomplie.decompiler.BytecodeCache;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbihanji.fxdecomplie.decompiler.Decompiler;
import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeItem;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
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
        Decompiler decompiler = DecompilerFactory.getDecompiler(engine);
        Files.createDirectories(outputDir);
        exportTree(root, decompiler, outputDir);
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
        Decompiler decompiler = DecompilerFactory.getDecompiler(engine);
        Files.createDirectories(outputDir);
        int total = countClassFiles(root);
        int[] completed = {0};
        exportTreeWithProgress(root, decompiler, outputDir, total, completed, onProgress);
    }

    private static int countClassFiles(TreeItem<FileTreeNode> node) {
        int count = 0;
        FileTreeNode data = node.getValue();
        if (data != null && data.isClassFile()) count = 1;
        for (TreeItem<FileTreeNode> child : node.getChildren()) {
            count += countClassFiles(child);
        }
        return count;
    }

    private static void exportTreeWithProgress(TreeItem<FileTreeNode> node, Decompiler decompiler,
            Path outputDir, int total, int[] completed, IntConsumer onProgress)
            throws IOException {
        FileTreeNode data = node.getValue();
        if (data != null && data.isClassFile()) {
            byte[] bytes = resolveClassBytes(data);
            if (bytes != null) {
                String source = decompiler.decompile(data.getFullPath(), bytes);
                Path javaFile = outputDir.resolve(data.getFullPath().replace(".class", ".java"));
                Files.createDirectories(javaFile.getParent());
                Files.writeString(javaFile, source);
            }
            completed[0]++;
            if (total > 0 && onProgress != null) {
                onProgress.accept(completed[0] * 100 / total);
            }
        }
        for (TreeItem<FileTreeNode> child : node.getChildren()) {
            exportTreeWithProgress(child, decompiler, outputDir, total, completed, onProgress);
        }
    }

    /** 递归遍历树节点并导出 */
    private static void exportTree(TreeItem<FileTreeNode> node, Decompiler decompiler,
                                   Path outputDir) throws IOException {
        FileTreeNode data = node.getValue();
        if (data != null && data.isClassFile()) {
            byte[] bytes = resolveClassBytes(data);
            if (bytes != null) {
                String source = decompiler.decompile(data.getFullPath(), bytes);
                Path javaFile = outputDir.resolve(
                        data.getFullPath().replace(".class", ".java"));
                Files.createDirectories(javaFile.getParent());
                Files.writeString(javaFile, source);
            }
        }
        for (TreeItem<FileTreeNode> child : node.getChildren()) {
            exportTree(child, decompiler, outputDir);
        }
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
        Decompiler decompiler = DecompilerFactory.getDecompiler(engine);
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
            exportTreeToZip(root, decompiler, zos);
        }
    }

    /** 递归遍历树节点并写入 ZIP */
    private static void exportTreeToZip(TreeItem<FileTreeNode> node, Decompiler decompiler,
                                        ZipOutputStream zos) throws IOException {
        FileTreeNode data = node.getValue();
        if (data != null && data.isClassFile()) {
            byte[] bytes = resolveClassBytes(data);
            if (bytes != null) {
                String source = decompiler.decompile(data.getFullPath(), bytes);
                ZipEntry entry = new ZipEntry(data.getFullPath().replace(".class", ".java"));
                zos.putNextEntry(entry);
                zos.write(source.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        for (TreeItem<FileTreeNode> child : node.getChildren()) {
            exportTreeToZip(child, decompiler, zos);
        }
    }

    /** 解析类节点的字节码（优先节点缓存，其次全局缓存） */
    private static byte[] resolveClassBytes(FileTreeNode data) {
        byte[] bytes = data.getCachedBytes();
        if (bytes == null) {
            bytes = BytecodeCache.get(data.getFullPath().replace(".class", ""));
        }
        return bytes;
    }
}
