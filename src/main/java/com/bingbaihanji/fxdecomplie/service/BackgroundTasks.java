package com.bingbaihanji.fxdecomplie.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 后台任务工具类,在守护线程上运行任务
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BackgroundTasks {

    private static final Logger log = LoggerFactory.getLogger(BackgroundTasks.class);

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

    /**
     * 提交后台任务。
     *
     * <p>任务启动时不清除线程中断标志，保留取消信号让任务内部
     * 通过 {@code Thread.currentThread().isInterrupted()} 自行检测。
     * 仅读取并重置残留中断标志（来自线程池复用的旧任务），
     * 若当前已有中断信号则跳过任务执行。</p>
     *
     * @param name 任务名称（设置为线程名，便于调试）
     * @param task 要执行的任务
     * @return 可通过 {@link #cancel(Future)} 取消的 Future
     */
    public static Future<?> run(String name, Runnable task) {
        return run(name, task, null);
    }

    public static Future<?> run(String name, Runnable task,
                                Consumer<RejectedExecutionException> onRejected) {
        try {
            log.debug("提交后台任务: {} (队列: {}/{})", name,
                    EXECUTOR.getQueue().size(), MAX_QUEUE_SIZE);
            return EXECUTOR.submit(() -> {
                Thread.currentThread().setName(name);
                // 检查线程是否已被中断（任务提交后、执行前被取消）
                if (Thread.currentThread().isInterrupted()) {
                    log.debug("后台任务在启动前被取消: {}", name);
                    return;
                }
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("后台任务异常: {}", name, e);
                    throw e;
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("后台任务被拒绝(队列满): {} (队列: {}/{})", name,
                    EXECUTOR.getQueue().size(), MAX_QUEUE_SIZE);
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
