package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.ClassBytecodeProvider;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.model.ExportResult;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * 导出服务支持单文件保存 批量导出到目录和 ZIP 归档
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
    public static ExportResult exportAll(FileTreeModel root, ExportConfig config,
                                         WorkspaceIndex index,
                                         BiConsumer<String, Integer> onProgress)
            throws IOException {
        List<FileTreeNode> nodes = collectExportableNodes(root, config.exportResources());
        return exportAll(nodes, config, index, null, onProgress, null);
    }

    /**
     * 根据用户选择的选项导出所有支持的工作区文件(带注释范围支持)
     *
     * @param root        工作区树根节点
     * @param config      导出配置
     * @param index       工作区索引(预构建)
     * @param commentScope 注释导出范围,null 表示不导出注释
     * @param onProgress  进度回调,接收当前路径和百分比
     * @return 导出摘要
     * @throws IOException 无法创建输出容器时抛出
     */
    public static ExportResult exportAll(FileTreeModel root, ExportConfig config,
                                         WorkspaceIndex index, CommentScope commentScope,
                                         BiConsumer<String, Integer> onProgress)
            throws IOException {
        List<FileTreeNode> nodes = collectExportableNodes(root, config.exportResources());
        return exportAll(nodes, config, index, commentScope, onProgress, null);
    }

    /**
     * 执行完整导出流水线(使用懒加载字节码提供器,不依赖完整索引)
     *
     * <p>provider 会被最多 {@link DecompilerRunner#decompilerThreads()} 个并行反编译工作线程共享,
     * 实现必须线程安全;ExportService 不负责关闭 provider,其生命周期由调用方管理.</p>
     *
     * <p>反编译按原始顺序在有界窗口内提交,完成一项立即写盘,内存占用与导出总量无关.</p>
     *
     * @param nodes         可导出的文件节点列表(须在 FX 线程提取)
     * @param config        导出配置(格式 引擎 路径 冲突策略等)
     * @param provider      依赖类字节码提供器
     * @param commentScope  注释导出范围,null 表示不导出注释
     * @param onProgress    进度回调,接收当前路径和百分比
     * @param canceled      用户取消标志 supplier,非 null 时每轮迭代检查
     * @return 导出结果摘要(总数 成功数 错误列表)
     * @throws IOException 输出目录或 ZIP 创建失败时抛出
     */
    public static ExportResult exportAll(List<FileTreeNode> nodes, ExportConfig config,
                                         ClassBytecodeProvider provider, CommentScope commentScope,
                                         BiConsumer<String, Integer> onProgress,
                                         BooleanSupplier canceled) throws IOException {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(provider, "provider");

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
        ExportPipeline.run(items, config, provider, commentScope, state, canceled);
        long elapsed = System.currentTimeMillis() - exportStart;
        log.info("导出完成: {}/{} 成功, {} 错误 ({}ms)",
                state.successCount, state.totalFiles, state.errors.size(), elapsed);
        return new ExportResult(state.totalFiles, state.successCount, state.errors);
    }

    /**
     * 执行完整的导出流水线(兼容旧调用:通过工作区索引解析依赖)
     *
     * @param nodes         可导出的文件节点列表(须在 FX 线程提取)
     * @param config        导出配置(格式 引擎 路径 冲突策略等)
     * @param index         工作区索引,用于字节码解析上下文
     * @param commentScope  注释导出范围,null 表示不导出注释
     * @param onProgress    进度回调,接收当前路径和百分比
     * @param canceled      用户取消标志 supplier,非 null 时每轮迭代检查
     * @return 导出结果摘要(总数 成功数 错误列表)
     * @throws IOException 输出目录或 ZIP 创建失败时抛出
     */
    public static ExportResult exportAll(List<FileTreeNode> nodes, ExportConfig config,
                                         WorkspaceIndex index, CommentScope commentScope,
                                         BiConsumer<String, Integer> onProgress,
                                         BooleanSupplier canceled)
            throws IOException {
        Objects.requireNonNull(index, "index");
        return exportAll(nodes, config, index::getClassBytes, commentScope, onProgress, canceled);
    }

    /**
     * 导出工作区所有 .class 文件为 .java 到目录
     *
     * @param root      文件树根节点
     * @param engine    反编译引擎
     * @param outputDir 输出目录
     * @throws IOException 写入文件失败时抛出
     */
    public static void exportAllToDir(FileTreeModel root, DecompilerTypeEnum engine,
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
    public static void exportAllToDir(FileTreeModel root, DecompilerTypeEnum engine,
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
    public static void exportAllToZip(FileTreeModel root, DecompilerTypeEnum engine,
                                      Path zipPath) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(zipPath, "zipPath");
        ExportConfig config = new ExportConfig(zipPath, engine, ExportConfig.Format.ZIP,
                ExportConfig.ConflictPolicy.OVERWRITE, false);
        List<FileTreeNode> nodes = collectExportableNodes(root, config.exportResources());
        exportAll(nodes, config, WorkspaceIndex.build(root), null, null, null);
    }

    /**
     * 从 TreeItem 树中提取可导出节点的快照(必须在 FX 线程调用)
     * 仅供遗留 API 和测试使用 新代码应在 FX 线程提取 FileTreeNode 列表后直接调用 exportAll
     */
    @Deprecated
    private static List<FileTreeNode> collectExportableNodes(FileTreeModel root,
                                                             boolean exportResources) {
        return ExportPlanner.collectExportableNodes(root, exportResources);
    }

    private static boolean shouldExport(FileTreeNode data, boolean exportResources) {
        return ExportPlanner.shouldExport(data, exportResources);
    }

    /** 导出内容载体:包含相对路径和要写入的字节数据 */
    record ExportContent(String relativePath, byte[] bytes) {
    }

    /** 导出进度状态跟踪器:记录总数 完成数 成功数和错误列表 */
    static final class ExportState {

        final int totalFiles;

        final BiConsumer<String, Integer> onProgress;

        final List<String> errors = new ArrayList<>();

        int completed;

        int successCount;

        ExportState(int totalFiles, BiConsumer<String, Integer> onProgress) {
            this.totalFiles = totalFiles;
            this.onProgress = onProgress;
        }

        void advance(String currentPath) {
            completed++;
            if (onProgress != null) {
                int percent = totalFiles == 0 ? 100 : completed * 100 / totalFiles;
                onProgress.accept(currentPath, percent);
            }
        }
    }
}
