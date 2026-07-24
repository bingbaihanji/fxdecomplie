package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.WorkspaceIndexService;
import com.bingbaihanji.fxdecomplie.service.classscan.ClassGraphClassScanService;
import com.bingbaihanji.fxdecomplie.service.classscan.ClassScanRequest;
import com.bingbaihanji.fxdecomplie.service.classscan.ClassScanResult;
import com.bingbaihanji.fxdecomplie.service.classscan.ClassScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public final class InheritanceReferenceIndexService {

    private static final Logger log = LoggerFactory.getLogger(InheritanceReferenceIndexService.class);
    private static final WeakHashMap<Workspace, IndexState> STATES = new WeakHashMap<>();
    private static final ClassScanService classScanService = new ClassGraphClassScanService();

    private InheritanceReferenceIndexService() {
        throw new AssertionError("utility class");
    }

    public static synchronized InheritanceReferenceIndex getOrStart(Workspace workspace) {
        IndexState state = STATES.computeIfAbsent(workspace, w -> new IndexState());
        if (workspace != null && !workspace.isIndexReady()) {
            WorkspaceIndexService.ensureIndexingStarted(workspace);
        }
        if (state.future.isDone() && !state.future.isCompletedExceptionally()) {
            InheritanceReferenceIndex current = state.future.getNow(null);
            if (current != null && current.scanResult().getAllClasses().isEmpty()
                    && workspace != null && workspace.isIndexReady()) {
                invalidate(workspace);
                state = STATES.computeIfAbsent(workspace, w -> new IndexState());
            }
        }
        if (!state.started) {
            state.started = true;
            IndexState taskState = state;
            log.info("开始构建继承引用索引: {}", workspace.getName());
            long start = System.currentTimeMillis();
            Future<?> task = BackgroundTasks.run(BackgroundTasks.PoolType.IO,
                    "RefIndex-" + workspace.getName(), () -> {
                        try {
                            if (!workspace.isIndexReady()) {
                                workspace.getIndexFuture().join();
                            }
                            ClassScanResult scanResult = classScanService.scan(
                                    ClassScanRequest.of(workspace));
                            InheritanceReferenceIndex index = new InheritanceReferenceIndex(
                                    scanResult, buildPathMap(scanResult));
                            long elapsed = System.currentTimeMillis() - start;
                            log.info("继承引用索引构建完成: {} ({} classes, {}ms)",
                                    workspace.getName(),
                                    scanResult.getAllClasses().size(), elapsed);
                            taskState.future.complete(index);
                        } catch (Exception ex) {
                            long elapsed = System.currentTimeMillis() - start;
                            if (isCancellationOrInterrupt(ex)) {
                                Thread.currentThread().interrupt();
                                log.debug("继承引用索引构建被取消: {} ({}ms)",
                                        workspace.getName(), elapsed);
                                taskState.future.cancel(true);
                            } else {
                                log.error("继承引用索引构建失败: {} ({}ms)",
                                        workspace.getName(), elapsed, ex);
                                taskState.future.completeExceptionally(ex);
                            }
                        }
                    });
            state.task = task;
        }
        return state.future.isDone() && !state.future.isCompletedExceptionally()
                ? state.future.getNow(null) : null;
    }

    private static Map<String, String> buildPathMap(ClassScanResult scanResult) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (var cm : scanResult.getAllClasses()) {
            if (cm.fullPath() != null) {
                map.put(cm.name(), cm.fullPath());
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

    private static boolean isCancellationOrInterrupt(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof CancellationException || cause instanceof InterruptedException) {
                return true;
            }
            cause = cause.getCause();
        }
        return Thread.currentThread().isInterrupted();
    }

    private static final class IndexState {
        final CompletableFuture<InheritanceReferenceIndex> future = new CompletableFuture<>();
        volatile boolean started;
        volatile Future<?> task;
    }
}
