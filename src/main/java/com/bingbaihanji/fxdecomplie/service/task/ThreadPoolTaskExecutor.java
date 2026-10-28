package com.bingbaihanji.fxdecomplie.service.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * {@link TaskExecutorService} 的默认实现 — 管理 5 个独立线程池.
 *
 * <ul>
 *   <li>INTERACTIVE: 4-8 核,队列 200 — 搜索/打开类/UI 交互</li>
 *   <li>IO: 2 核,无界队列 — 磁盘缓存写入</li>
 *   <li>INDEX: 1 核,无界队列 — 工作区索引构建(隔离,避免阻塞 IO 池)</li>
 *   <li>EXPORT: 2-4 核,队列 50 — 导出任务</li>
 *   <li>DECOMPILE: C/2 核,队列 4 — 反编译(隔离,避免阻塞短任务)</li>
 * </ul>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public final class ThreadPoolTaskExecutor implements TaskExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolTaskExecutor.class);

    private final Map<TaskKind, ThreadPoolExecutor> pools;

    public ThreadPoolTaskExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        int interactiveThreads = Math.clamp(cores, 4, 8);
        int ioThreads = 2;
        int indexThreads = 1;
        int exportThreads = Math.clamp(cores, 2, 4);
        int decompileCores = Math.max(1, cores / 2);

        pools = Map.of(
                TaskKind.INTERACTIVE, createPool(interactiveThreads, 200, "bg-int"),
                TaskKind.IO, createPool(ioThreads, Integer.MAX_VALUE, "bg-io"),
                TaskKind.INDEX, createPool(indexThreads, Integer.MAX_VALUE, "bg-index"),
                TaskKind.EXPORT, createPool(exportThreads, 50, "bg-exp"),
                TaskKind.DECOMPILE, createPool(decompileCores, 4, "bg-decomp")
        );
        log.info("任务执行器已创建: interactive={}, io={}, index={}, export={}, decompile={}",
                interactiveThreads, ioThreads, indexThreads, exportThreads, decompileCores);
    }

    private static ThreadPoolExecutor createPool(int threads, int queueSize,
                                                 String prefix) {
        ThreadPoolExecutor ex = new ThreadPoolExecutor(
                threads, threads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName(prefix + "-" + t.threadId());
                    return t;
                });
        ex.allowCoreThreadTimeOut(true);
        return ex;
    }

    @Override
    public Future<?> submit(TaskKind kind, String name, Runnable task) {
        return submit(kind, name, task, null);
    }

    @Override
    public Future<?> submit(TaskKind kind, String name, Runnable task,
                            Consumer<RejectedExecutionException> onRejected) {
        ThreadPoolExecutor pool = pools.get(kind);
        try {
            log.debug("提交任务[{}]: {} (队列: {})", kind, name, pool.getQueue().size());
            return pool.submit(() -> {
                String prev = Thread.currentThread().getName();
                Thread.currentThread().setName(name);
                try {
                    if (Thread.currentThread().isInterrupted()) {
                        log.debug("任务启动前已被取消: {}", name);
                        return;
                    }
                    task.run();
                } catch (Exception e) {
                    log.error("任务异常: {}", name, e);
                    throw e;
                } finally {
                    Thread.currentThread().setName(prev);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("任务被拒绝(队列满)[{}]: {}", kind, name);
            if (onRejected != null) {
                try {
                    onRejected.accept(e);
                } catch (RuntimeException ignored) {
                    log.debug("拒绝回调失败: {}", name, ignored);
                }
            }
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    @Override
    public void cancel(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    @Override
    public TaskSnapshot snapshot(TaskKind kind) {
        ThreadPoolExecutor pool = pools.get(kind);
        if (pool == null) {
            return TaskSnapshot.EMPTY;
        }
        return new TaskSnapshot(pool.getActiveCount(),
                pool.getQueue().size(), pool.getPoolSize());
    }

    @Override
    public int totalPending() {
        return pools.values().stream()
                .mapToInt(p -> p.getQueue().size() + p.getActiveCount())
                .sum();
    }

    @Override
    public void close() {
        log.info("关闭任务执行器");
        for (Map.Entry<TaskKind, ThreadPoolExecutor> entry : pools.entrySet()) {
            ThreadPoolExecutor pool = entry.getValue();
            pool.shutdown();
            try {
                if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
