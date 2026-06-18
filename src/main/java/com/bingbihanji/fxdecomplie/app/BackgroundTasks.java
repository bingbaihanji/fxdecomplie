package com.bingbihanji.fxdecomplie.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 后台任务工具类，在守护线程上运行任务。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BackgroundTasks {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
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
            task.run();
        });
    }

    public static void cancel(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }
}
