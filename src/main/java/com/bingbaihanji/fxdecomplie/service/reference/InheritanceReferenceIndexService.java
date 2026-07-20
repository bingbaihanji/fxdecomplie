package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.core.classgraph.ScanResult;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public final class InheritanceReferenceIndexService {

    private static final Logger log = LoggerFactory.getLogger(InheritanceReferenceIndexService.class);
    private static final WeakHashMap<Workspace, IndexState> STATES = new WeakHashMap<>();

    private InheritanceReferenceIndexService() {
        throw new AssertionError("utility class");
    }

    public static synchronized InheritanceReferenceIndex getOrStart(Workspace workspace) {
        IndexState state = STATES.computeIfAbsent(workspace, w -> new IndexState());
        if (!state.started) {
            state.started = true;
            log.info("开始构建继承引用索引: {}", workspace.getName());
            long start = System.currentTimeMillis();
            Future<?> task = BackgroundTasks.run(BackgroundTasks.PoolType.IO,
                    "RefIndex-" + workspace.getName(), () -> {
                        try {
                            ScanResult scanResult = ClassGraphWorkspaceAdapter.scan(workspace);
                            InheritanceReferenceIndex index = new InheritanceReferenceIndex(
                                    scanResult, buildPathMap(scanResult));
                            long elapsed = System.currentTimeMillis() - start;
                            log.info("继承引用索引构建完成: {} ({} classes, {}ms)",
                                    workspace.getName(),
                                    scanResult.getAllClasses().size(), elapsed);
                            state.future.complete(index);
                        } catch (Exception ex) {
                            long elapsed = System.currentTimeMillis() - start;
                            log.error("继承引用索引构建失败: {} ({}ms)",
                                    workspace.getName(), elapsed, ex);
                            state.future.completeExceptionally(ex);
                        }
                    });
            state.task = task;
        }
        return state.future.isDone() && !state.future.isCompletedExceptionally()
                ? state.future.getNow(null) : null;
    }

    private static Map<String, String> buildPathMap(ScanResult scanResult) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (var ci : scanResult.getAllClasses()) {
            if (ci.getFullPath() != null) {
                map.put(ci.getName(), ci.getFullPath());
            }
        }
        return map;
    }

    public static synchronized InheritanceReferenceIndex getIfReady(Workspace workspace) {
        IndexState state = STATES.get(workspace);
        if (state == null) {
            return null;
        }
        return state.future.isDone() && !state.future.isCompletedExceptionally()
                ? state.future.getNow(null) : null;
    }

    public static synchronized CompletableFuture<InheritanceReferenceIndex> getFuture(Workspace workspace) {
        IndexState state = STATES.get(workspace);
        return state == null ? null : state.future;
    }

    public static synchronized void invalidate(Workspace workspace) {
        IndexState state = STATES.remove(workspace);
        if (state != null && state.task != null) {
            state.task.cancel(true);
        }
    }

    private static final class IndexState {
        volatile boolean started;
        volatile Future<?> task;
        final CompletableFuture<InheritanceReferenceIndex> future = new CompletableFuture<>();
    }
}
