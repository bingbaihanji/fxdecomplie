package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 按需构建工作区完整索引打开 JAR 时不自动读取/解析全部 class,
 * 只有搜索 用法 导出等功能真正需要时才启动.
 *
 * <p>对于 Spring Boot fatjar,采用分阶段构建策略:先解析 BOOT-INF/classes,
 * 再解析 BOOT-INF/lib.最终只在完整索引完成后发布给工作区.支持基于内容指纹的索引缓存,
 * 避免同一大包重复打开时重建整套索引.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-07
 */
public final class WorkspaceIndexService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceIndexService.class);

    private static final AtomicReference<IndexProgressSnapshot> PROGRESS = new AtomicReference<>();

    private WorkspaceIndexService() {
        throw new AssertionError("utility class");
    }

    /**
     * 确保工作区索引已启动构建(幂等操作)
     * 若索引已就绪或已在构建中则直接返回,否则以低优先级后台线程启动索引构建.
     *
     * <p>对于 Spring Boot fatjar,先尝试从缓存加载,未命中时分阶段构建:先 phase1 后 phase2.</p>
     */
    public static void ensureIndexingStarted(Workspace workspace) {
        if (workspace == null || workspace.isIndexReady()) {
            return;
        }
        if (!workspace.markIndexBuildStarted()) {
            return;
        }
        log.info("开始构建工作区索引: {}", workspace.getName());
        long start = System.currentTimeMillis();
        reportProgress(workspace, 0, 1, "start");
        Future<?> task = BackgroundTasks.run(BackgroundTasks.PoolType.IO,
                "Index-" + workspace.getName(), () -> {
                    int previousPriority = Thread.currentThread().getPriority();
                    try {
                        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

                        // 尝试从缓存加载
                        String fingerprint = WorkspaceIndexCache.computeFingerprint(
                                workspace.getSourceFile().toPath());
                        var cachedIndexData = WorkspaceIndexCache.load(fingerprint);
                        if (cachedIndexData != null && cachedIndexData.hasClasses()) {
                            log.info("索引缓存命中,跳过构建: {} ({} classes, {}ms)",
                                    workspace.getName(), cachedIndexData.classCount(),
                                    System.currentTimeMillis() - start);
                            WorkspaceIndex cachedIndex = cachedIndexData.toWorkspaceIndex(workspace);
                            workspace.setIndex(cachedIndex);
                            reportProgress(workspace, 1, 1, "done");
                            return;
                        }

                        // 判断是否需要分阶段构建(含嵌套归档的 fatjar)
                        WorkspaceIndex fullIndex;

                        if (hasNestedPhases(workspace)) {
                            // 分阶段预热:先 BOOT-INF/classes,再 BOOT-INF/lib,最终只提交完整索引
                            fullIndex = buildPhased(workspace, start);
                        } else {
                            // 普通 JAR:直接全量构建
                            fullIndex = WorkspaceIndex.build(workspace.getTreeRoot(), progress ->
                                    reportProgress(workspace, progress.processed(),
                                            progress.total(), progress.phase()));
                        }

                        long elapsed = System.currentTimeMillis() - start;
                        log.info("工作区索引构建完成: {} ({} classes, {} resources, {}ms)",
                                workspace.getName(), fullIndex.classes().size(),
                                fullIndex.resources().size(), elapsed);
                        workspace.setIndex(fullIndex);
                        reportProgress(workspace, 1, 1, "done");

                        // 异步保存缓存
                        if (fingerprint != null) {
                            final WorkspaceIndex indexToSave = fullIndex;
                            BackgroundTasks.run(BackgroundTasks.PoolType.IO,
                                    "IndexCache-" + workspace.getName(),
                                    () -> WorkspaceIndexCache.save(fingerprint, indexToSave));
                        }
                    } catch (Exception ex) {
                        long elapsed = System.currentTimeMillis() - start;
                        log.error("工作区索引构建失败: {} ({}ms)", workspace.getName(), elapsed, ex);
                        workspace.failIndex(ex);
                    } finally {
                        Thread.currentThread().setPriority(previousPriority);
                        PROGRESS.set(null);
                    }
                });
        if (task.isDone()) {
            try {
                task.get();
            } catch (Exception ex) {
                workspace.failIndex(ex.getCause() == null ? ex : ex.getCause());
            }
        }
    }

    /** 分阶段构建 Spring Boot fatjar,但只在完成后一次性提交完整索引. */
    private static WorkspaceIndex buildPhased(Workspace workspace, long start) {
        // 阶段 1:BOOT-INF/classes
        reportProgress(workspace, 0, 1, "phase1-classes");
        WorkspaceIndex phase1Index = WorkspaceIndex.buildPhase(
                workspace.getTreeRoot(), 1, progress ->
                        reportProgress(workspace, progress.processed(),
                                progress.total(), "phase1-classes"));

        long phase1Elapsed = System.currentTimeMillis() - start;
        log.info("Phase1 索引完成: {} ({} classes, {}ms)",
                workspace.getName(), phase1Index.classes().size(), phase1Elapsed);

        // 阶段 2:BOOT-INF/lib
        reportProgress(workspace, 0, 1, "phase2-lib");
        WorkspaceIndex phase2Index = WorkspaceIndex.buildPhase(
                workspace.getTreeRoot(), 2, progress ->
                        reportProgress(workspace, progress.processed(),
                                progress.total(), "phase2-lib"));

        long phase2Elapsed = System.currentTimeMillis() - start;
        log.info("Phase2 索引完成: {} ({} classes, {}ms)",
                workspace.getName(), phase2Index.classes().size(), phase2Elapsed);

        // 合并两个阶段的索引,仅作为完整索引返回
        return WorkspaceIndex.merge(phase1Index, phase2Index);
    }

    /**
     * 检查工作区是否包含嵌套归档(即 Spring Boot fatjar 结构)
     */
    private static boolean hasNestedPhases(Workspace workspace) {
        var root = workspace.getTreeRoot();
        if (root == null) {
            return false;
        }
        // 遍历根层子节点,检查是否同时存在 BOOT-INF/classes 和 BOOT-INF/lib
        boolean hasClasses = false;
        boolean hasLib = false;
        for (var child : root.getChildren()) {
            FileTreeNode node = child.getValue();
            if (node == null) {
                continue;
            }
            String name = node.getName();
            if ("BOOT-INF".equals(name) || "WEB-INF".equals(name)) {
                // 进一步检查子节点
                for (var sub : child.getChildren()) {
                    FileTreeNode subNode = sub.getValue();
                    if (subNode == null) {
                        continue;
                    }
                    if ("classes".equals(subNode.getName())) {
                        hasClasses = true;
                    } else if ("lib".equals(subNode.getName())) {
                        hasLib = true;
                    }
                }
            }
        }
        return hasClasses && hasLib;
    }

    public static IndexProgressSnapshot getProgressSnapshot() {
        return PROGRESS.get();
    }

    public static boolean isIndexing(Workspace workspace) {
        return workspace != null && workspace.isIndexBuildStarted() && !workspace.isIndexReady();
    }

    private static void reportProgress(Workspace workspace, int processed, int total, String phase) {
        if (workspace == null) {
            return;
        }
        int safeTotal = Math.max(1, total);
        int safeProcessed = Math.max(0, Math.min(processed, safeTotal));
        PROGRESS.set(new IndexProgressSnapshot(workspace.getName(), phase,
                safeProcessed, safeTotal, safeProcessed / (double) safeTotal));
    }

    public record IndexProgressSnapshot(String workspaceName, String phase,
                                        int processed, int total, double progress) {
    }
}
