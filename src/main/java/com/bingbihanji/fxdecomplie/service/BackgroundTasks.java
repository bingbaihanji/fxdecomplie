package com.bingbihanji.fxdecomplie.service;

import java.util.concurrent.*;

/**
 * 后台任务工具类，在守护线程上运行任务。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BackgroundTasks {

    /** Daemon thread pool (0-core, 8-max) for background file loading and decompilation */
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            0, 8,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });

    private BackgroundTasks() {
        throw new AssertionError("utility class");
    }

    /** @return a Future that can be cancelled via {@link #cancel(Future)} */
    public static Future<?> run(String name, Runnable task) {
        return EXECUTOR.submit(() -> {
            Thread.currentThread().setName(name);
            // 清除线程池复用残留的中断标志，避免反编译器抛出 InterruptedException
            Thread.interrupted();
            task.run();
        });
    }

    /** @param future the task to cancel (null-safe, no-op if already done) */
    public static void cancel(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }
}
