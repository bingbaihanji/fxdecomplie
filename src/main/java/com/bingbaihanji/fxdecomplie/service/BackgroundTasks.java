package com.bingbaihanji.fxdecomplie.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 后台任务工具类，提供三个独立线程池以避免长任务阻塞交互操作
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BackgroundTasks {

    private static final Logger log = LoggerFactory.getLogger(BackgroundTasks.class);
    private static final int CORES = Runtime.getRuntime().availableProcessors();
    private static final int INTERACTIVE_THREADS = Math.clamp(CORES, 4, 8);
    private static final ThreadPoolExecutor INTERACTIVE_POOL = createPool(
            INTERACTIVE_THREADS, 200, "bg-int");
    private static final int IO_THREADS = 2;
    private static final int EXPORT_THREADS = Math.clamp(CORES, 2, 4);
    private static final ThreadPoolExecutor EXPORT_POOL = createPool(
            EXPORT_THREADS, 50, "bg-exp");
    private static final ThreadPoolExecutor IO_POOL = createPool(
            IO_THREADS, Integer.MAX_VALUE, "bg-io");

    private BackgroundTasks() {
        throw new AssertionError("utility class");
    }

    private static ThreadPoolExecutor createPool(int threads, int queueSize, String prefix) {
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

    private static ThreadPoolExecutor poolFor(PoolType type) {
        return switch (type) {
            case INTERACTIVE -> INTERACTIVE_POOL;
            case IO -> IO_POOL;
            case EXPORT -> EXPORT_POOL;
        };
    }

    /** 提交到 INTERACTIVE 池（向后兼容） */
    public static Future<?> run(String name, Runnable task) {
        return run(PoolType.INTERACTIVE, name, task, null);
    }

    public static Future<?> run(String name, Runnable task,
                                Consumer<RejectedExecutionException> onRejected) {
        return run(PoolType.INTERACTIVE, name, task, onRejected);
    }

    /** 提交到指定池 */
    public static Future<?> run(PoolType type, String name, Runnable task) {
        return run(type, name, task, null);
    }

    public static Future<?> run(PoolType type, String name, Runnable task,
                                Consumer<RejectedExecutionException> onRejected) {
        ThreadPoolExecutor pool = poolFor(type);
        try {
            log.debug("提交后台任务[{}]: {} (队列: {})", type, name, pool.getQueue().size());
            return pool.submit(() -> {
                String previousName = Thread.currentThread().getName();
                Thread.currentThread().setName(name);
                try {
                    if (Thread.currentThread().isInterrupted()) {
                        log.debug("后台任务在启动前被取消: {}", name);
                        return;
                    }
                    task.run();
                } catch (Exception e) {
                    log.error("后台任务异常: {}", name, e);
                    throw e;
                } finally {
                    Thread.currentThread().setName(previousName);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("后台任务被拒绝(队列满)[{}]: {}", type, name);
            if (onRejected != null) {
                try {
                    onRejected.accept(e);
                } catch (RuntimeException callbackError) {
                    log.debug("后台任务拒绝回调失败: {}", name, callbackError);
                }
            }
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    /** @param future 要取消的任务(null 安全,已完成时为 no-op) */
    public static void cancel(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    /** @return 指定池的待执行任务队列长度 */
    public static int getQueueLength(PoolType type) {
        return poolFor(type).getQueue().size();
    }

    /** @return 指定池的正在执行任务数 */
    public static int getActiveCount(PoolType type) {
        return poolFor(type).getActiveCount();
    }

    /** @return 所有池的总待处理任务数(队列 + 执行中) */
    public static int getTotalPendingTasks() {
        int total = 0;
        for (ThreadPoolExecutor pool : new ThreadPoolExecutor[]{INTERACTIVE_POOL, IO_POOL, EXPORT_POOL}) {
            total += pool.getQueue().size() + pool.getActiveCount();
        }
        return total;
    }

    /** 优雅关闭所有池,最多等待 3 秒 */
    public static void shutdown() {
        for (ThreadPoolExecutor pool : new ThreadPoolExecutor[]{INTERACTIVE_POOL, IO_POOL, EXPORT_POOL}) {
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

    /** 任务池类型，调用方据此将任务路由到合适的池 */
    public enum PoolType {
        /** 搜索 打开类 Ctrl+Click UI 触发的交互操作 */
        INTERACTIVE,
        /** 磁盘缓存写入 索引构建 — 磁盘/CPU 密集型批处理 */
        IO,
        /** 导出任务专用 */
        EXPORT
    }
}
