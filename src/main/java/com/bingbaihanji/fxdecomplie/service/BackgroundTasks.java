package com.bingbaihanji.fxdecomplie.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * 后台任务工具类，在守护线程上运行任务。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BackgroundTasks {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundTasks.class);

    private static final int CORE_POOL_SIZE = Math.max(2,
            Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    private static final int MAX_POOL_SIZE = Math.max(8, CORE_POOL_SIZE);
    private static final int MAX_QUEUE_SIZE = 100;

    /** 保底并发避免索引任务阻塞反编译；有界队列避免无限堆积。 */
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAX_POOL_SIZE,
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

    /** @return a Future that can be cancelled via {@link #cancel(Future)} */
    public static Future<?> run(String name, Runnable task) {
        try {
            return EXECUTOR.submit(() -> {
                Thread.currentThread().setName(name);
                // 清除线程池复用残留的中断标志，避免反编译器抛出 InterruptedException
                Thread.interrupted();
                task.run();
            });
        } catch (RejectedExecutionException e) {
            logger.warn("Background task rejected: {}", name, e);
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    /** @param future the task to cancel (null-safe, no-op if already done) */
    public static void cancel(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    /** Shut down the executor gracefully, waiting up to 3 seconds for tasks to finish. */
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
