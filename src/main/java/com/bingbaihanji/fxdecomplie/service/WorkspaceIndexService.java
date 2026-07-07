package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * 按需构建工作区完整索引打开 JAR 时不自动读取/解析全部 class,
 * 只有搜索、用法、导出等功能真正需要时才启动
 *
 * @author bingbaihanji
 * @date 2026-07-07
 */
public final class WorkspaceIndexService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceIndexService.class);

    private WorkspaceIndexService() {
        throw new AssertionError("utility class");
    }

    /**
     * 确保工作区索引已启动构建（幂等操作）
     * 若索引已就绪或已在构建中则直接返回,否则以低优先级后台线程启动索引构建
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
        Future<?> task = BackgroundTasks.run("Index-" + workspace.getName(), () -> {
            int previousPriority = Thread.currentThread().getPriority();
            try {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                WorkspaceIndex fullIndex = WorkspaceIndex.build(workspace.getTreeRoot());
                long elapsed = System.currentTimeMillis() - start;
                log.info("工作区索引构建完成: {} ({} classes, {} resources, {}ms)",
                        workspace.getName(), fullIndex.classes().size(),
                        fullIndex.resources().size(), elapsed);
                workspace.setIndex(fullIndex);
            } catch (Exception ex) {
                long elapsed = System.currentTimeMillis() - start;
                log.error("工作区索引构建失败: {} ({}ms)", workspace.getName(), elapsed, ex);
                workspace.failIndex(ex);
            } finally {
                Thread.currentThread().setPriority(previousPriority);
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
}
