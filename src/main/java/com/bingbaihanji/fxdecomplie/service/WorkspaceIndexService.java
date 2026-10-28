package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 按需构建工作区完整索引打开 JAR 时不自动读取/解析全部 class,
 * 只有搜索 用法 导出等功能真正需要时才启动.
 *
 * <p>所有归档类型统一并行构建全部节点(class 与文本资源混合调度),
 * 最终只在完整索引完成后发布给工作区.支持基于内容指纹的索引缓存,
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
     * <p>先尝试从缓存加载,未命中时并行构建完整索引.</p>
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

                        // 统一并行构建全部节点（class 与文本资源混合调度）
                        WorkspaceIndex fullIndex = WorkspaceIndex.build(
                                workspace.getTreeRoot(), progress ->
                                        reportProgress(workspace, progress.processed(),
                                                progress.total(), progress.phase()));

                        if (workspace.isClosed()) {
                            log.info("工作区已关闭,丢弃索引构建结果: {}", workspace.getName());
                            workspace.failIndex(new CancellationException("工作区已关闭"));
                            return;
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
                                    () -> {
                                        if (!workspace.isClosed()) {
                                            WorkspaceIndexCache.save(fingerprint, indexToSave);
                                        }
                                    });
                        }
                    } catch (Throwable ex) {
                        long elapsed = System.currentTimeMillis() - start;
                        if (ex instanceof CancellationException) {
                            log.warn("工作区索引构建已取消: {} ({}ms)", workspace.getName(), elapsed);
                        } else {
                            log.error("工作区索引构建失败: {} ({}ms)", workspace.getName(), elapsed, ex);
                        }
                        workspace.failIndex(ex);
                        if (ex instanceof Error error) {
                            throw error;
                        }
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
