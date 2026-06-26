package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;

import java.util.concurrent.Future;

/**
 * 按需构建工作区完整索引打开 JAR 时不自动读取/解析全部 class,
 * 只有搜索、用法、导出等功能真正需要时才启动
 */
public final class WorkspaceIndexService {

    private WorkspaceIndexService() {
        throw new AssertionError("utility class");
    }

    public static void ensureIndexingStarted(Workspace workspace) {
        if (workspace == null || workspace.isIndexReady()) {
            return;
        }
        if (!workspace.markIndexBuildStarted()) {
            return;
        }
        Future<?> task = BackgroundTasks.run("Index-" + workspace.getName(), () -> {
            int previousPriority = Thread.currentThread().getPriority();
            try {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                WorkspaceIndex fullIndex = WorkspaceIndex.build(workspace.getTreeRoot());
                workspace.setIndex(fullIndex);
            } catch (Exception ex) {
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
