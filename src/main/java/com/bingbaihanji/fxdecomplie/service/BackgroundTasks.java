package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.service.task.TaskExecutorService;
import com.bingbaihanji.fxdecomplie.service.task.TaskKind;
import com.bingbaihanji.fxdecomplie.service.task.ThreadPoolTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * 后台任务工具类,提供三个独立线程池以避免长任务阻塞交互操作.
 *
 * <p>内部委托给 {@link TaskExecutorService} 实例;可通过 {@link #setExecutor(TaskExecutorService)}
 * 注入自定义实现(例如测试中替换).</p>
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BackgroundTasks {

    private static final Logger log = LoggerFactory.getLogger(BackgroundTasks.class);

    /** 当前委派的执行器实例 */
    private static volatile TaskExecutorService executor;

    static {
        executor = new ThreadPoolTaskExecutor();
    }

    private BackgroundTasks() {
        throw new AssertionError("utility class");
    }

    /** 获取当前执行器实例 */
    public static TaskExecutorService getExecutor() {
        return executor;
    }

    /** 注入自定义执行器(测试或特殊场景),传 null 恢复默认 */
    public static void setExecutor(TaskExecutorService newExecutor) {
        if (newExecutor != null) {
            executor = newExecutor;
        } else {
            executor = new ThreadPoolTaskExecutor();
        }
    }

    private static TaskKind mapType(PoolType type) {
        return switch (type) {
            case INTERACTIVE -> TaskKind.INTERACTIVE;
            case IO -> TaskKind.IO;
            case EXPORT -> TaskKind.EXPORT;
        };
    }

    /** 提交到 INTERACTIVE 池(向后兼容) */
    public static Future<?> run(String name, Runnable task) {
        return executor.submit(TaskKind.INTERACTIVE, name, task);
    }

    public static Future<?> run(String name, Runnable task,
                                Consumer<RejectedExecutionException> onRejected) {
        return executor.submit(TaskKind.INTERACTIVE, name, task, onRejected);
    }

    /** 提交到指定池 */
    public static Future<?> run(PoolType type, String name, Runnable task) {
        return executor.submit(mapType(type), name, task);
    }

    public static Future<?> run(PoolType type, String name, Runnable task,
                                Consumer<RejectedExecutionException> onRejected) {
        return executor.submit(mapType(type), name, task, onRejected);
    }

    /** @param future 要取消的任务(null 安全,已完成时为 no-op) */
    public static void cancel(Future<?> future) {
        executor.cancel(future);
    }

    /** @return 指定池的待执行任务队列长度 */
    public static int getQueueLength(PoolType type) {
        return executor.snapshot(mapType(type)).queueLength();
    }

    /** @return 指定池的正在执行任务数 */
    public static int getActiveCount(PoolType type) {
        return executor.snapshot(mapType(type)).activeCount();
    }

    /** @return 所有池的总待处理任务数(队列 + 执行中) */
    public static int getTotalPendingTasks() {
        return executor.totalPending();
    }

    /** 优雅关闭所有池,最多等待 3 秒 */
    public static void shutdown() {
        executor.close();
    }

    /** 任务池类型,调用方据此将任务路由到合适的池 */
    public enum PoolType {
        /** 搜索 打开类 Ctrl+Click UI 触发的交互操作 */
        INTERACTIVE,
        /** 磁盘缓存写入 索引构建 — 磁盘/CPU 密集型批处理 */
        IO,
        /** 导出任务专用 */
        EXPORT
    }
}
