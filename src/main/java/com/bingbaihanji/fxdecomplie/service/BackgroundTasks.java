package com.bingbaihanji.fxdecomplie.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * 后台任务工具类,在守护线程上运行任务
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BackgroundTasks {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundTasks.class);

    private static final int POOL_SIZE = Math.clamp(Runtime.getRuntime().availableProcessors(), 4, 8);
    private static final int MAX_QUEUE_SIZE = 100;

    /** 保底并发避免索引/搜索/导出任务阻塞交互任务；有界队列避免无限堆积 */
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            POOL_SIZE, POOL_SIZE,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(MAX_QUEUE_SIZE),
            r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });

    static {
        EXECUTOR.allowCoreThreadTimeOut(true);
    }

    private BackgroundTasks() {
        throw new AssertionError("utility class");
    }

    /** @return 可通过 {@link #cancel(Future)} 取消的 Future */
    public static Future<?> run(String name, Runnable task) {
        try {
            return EXECUTOR.submit(() -> {
                Thread.currentThread().setName(name);
                // 清除线程池复用残留的中断标志,避免反编译器抛出 InterruptedException
                Thread.interrupted();
                task.run();
            });
        } catch (RejectedExecutionException e) {
            logger.warn("后台任务被拒绝: {}", name, e);
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

    /** 优雅关闭执行器,最多等待 3 秒让任务完成 */
    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(3, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
