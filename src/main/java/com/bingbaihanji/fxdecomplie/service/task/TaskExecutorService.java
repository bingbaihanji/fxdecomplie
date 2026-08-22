package com.bingbaihanji.fxdecomplie.service.task;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * 任务执行器抽象 — 将后台任务提交从静态工具解耦为可替换实例.
 *
 * <p>实现类负责管理多个线程池,按 {@link TaskKind} 路由任务.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public interface TaskExecutorService extends AutoCloseable {

    /**
     * 提交任务到对应类型的线程池.
     *
     * @param kind 任务类型
     * @param name 任务名称(用于日志和线程命名)
     * @param task 要执行的任务
     * @return 可取消的 Future
     */
    Future<?> submit(TaskKind kind, String name, Runnable task);

    /**
     * 提交任务,队列满时调用拒绝回调而非抛异常.
     *
     * @param kind       任务类型
     * @param name       任务名称
     * @param task       要执行的任务
     * @param onRejected 队列满时回调(可为 null)
     * @return Future
     */
    Future<?> submit(TaskKind kind, String name, Runnable task,
                     Consumer<RejectedExecutionException> onRejected);

    /** 取消任务(null 安全和已完成安全) */
    void cancel(Future<?> future);

    /** 获取指定类型池的快照 */
    TaskSnapshot snapshot(TaskKind kind);

    /** 所有池的待处理任务总数(队列 + 执行中) */
    int totalPending();

    /** 关闭所有线程池,等待进行中任务完成 */
    @Override
    void close();
}
