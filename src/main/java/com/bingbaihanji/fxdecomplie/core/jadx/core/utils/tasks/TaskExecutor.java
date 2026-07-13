package com.bingbaihanji.fxdecomplie.core.jadx.core.utils.tasks;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.utils.tasks.ITaskExecutor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务执行器实现，负责调度和执行分阶段的任务
 * 支持并行和顺序两种执行模式 (类似 fork-join 模式)
 * 任务按阶段顺序执行，每个阶段可以是并行或顺序执行
 */
public class TaskExecutor implements ITaskExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(TaskExecutor.class);
    /** 已注册的执行阶段列表 */
    private final List<ExecStage> stages = new ArrayList<>();
    /** 并行执行的线程数，默认值来自 JadxArgs */
    private final AtomicInteger threadsCount = new AtomicInteger(JadxArgs.DEFAULT_THREADS_COUNT);
    /** 已完成的任务计数 */
    private final AtomicInteger progress = new AtomicInteger(0);
    /** 标记执行器是否正在运行 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** 标记执行器是否正在终止 */
    private final AtomicBoolean terminating = new AtomicBoolean(false);
    /** 用于 executor 生命周期同步的锁对象 */
    private final Object executorSync = new Object();
    /** 内部执行器服务实例 */
    private @Nullable ExecutorService executor;
    /** 已注册的任务总数 */
    private int tasksCount = 0;
    /** 终止时的错误信息，用于在 awaitTermination 时抛出 */
    private @Nullable Error terminateError;

    /**
     * 等待指定执行器终止
     * <p>
     * 最长等待 10 天，超时抛出运行时异常 等待被中断时恢复当前线程的中断状态
     *
     * @param executor 待等待的执行器服务
     * @throws JadxRuntimeException 如果在超时时间内仍未终止
     */
    public static void awaitExecutorTermination(ExecutorService executor) {
        try {
            boolean complete = executor.awaitTermination(10, TimeUnit.DAYS);
            if (!complete) {
                throw new JadxRuntimeException("Executor timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 添加一个并行执行阶段该阶段内的所有任务会在多个线程上并行执行
     * 空列表将被忽略
     *
     * @param parallelTasks 需要并行执行的任务列表
     */
    @Override
    public void addParallelTasks(List<? extends Runnable> parallelTasks) {
        if (parallelTasks.isEmpty()) {
            return;
        }
        tasksCount += parallelTasks.size();
        stages.add(new ExecStage(ExecType.PARALLEL, parallelTasks));
    }

    /**
     * 添加一个顺序执行阶段该阶段内的所有任务会在单线程上按顺序依次执行
     * 空列表将被忽略
     *
     * @param seqTasks 需要顺序执行的任务列表
     */
    @Override
    public void addSequentialTasks(List<? extends Runnable> seqTasks) {
        if (seqTasks.isEmpty()) {
            return;
        }
        tasksCount += seqTasks.size();
        stages.add(new ExecStage(ExecType.SEQUENTIAL, seqTasks));
    }

    /**
     * 添加单个顺序执行任务，作为独立阶段追加到执行队列
     *
     * @param seqTask 需要顺序执行的单个任务
     */
    @Override
    public void addSequentialTask(Runnable seqTask) {
        addSequentialTasks(Collections.singletonList(seqTask));
    }

    /**
     * 获取当前并行执行的线程数
     *
     * @return 线程数
     */
    @Override
    public int getThreadsCount() {
        return threadsCount.get();
    }

    /**
     * 设置并行执行的线程数
     *
     * @param count 线程数
     */
    @Override
    public void setThreadsCount(int count) {
        threadsCount.set(count);
    }

    /**
     * 获取已注册的任务总数
     *
     * @return 任务总数
     */
    @Override
    public int getTasksCount() {
        return tasksCount;
    }

    /**
     * 获取当前已完成的任务数量 (执行进度)
     *
     * @return 已完成的任务数
     */
    @Override
    public int getProgress() {
        return progress.get();
    }

    /**
     * 启动执行器
     * <p>
     * 创建单线程调度器并在其中异步运行所有阶段若执行器已在运行则抛出异常
     *
     * @throws IllegalStateException 如果执行器已经在运行
     */
    @Override
    public void execute() {
        synchronized (executorSync) {
            if (running.get() || executor != null) {
                throw new IllegalStateException("Already executing");
            }
            executor = Executors.newFixedThreadPool(1, new com.bingbaihanji.fxdecomplie.util.concurrent.NamedThreadFactory("task-s"));
            running.set(true);
            terminating.set(false);
            progress.set(0);
            executor.execute(this::runStages);
        }
    }

    /**
     * 停止执行：重置运行标志 置终止标志并关闭内部执行器
     */
    private void stopExecution() {
        synchronized (executorSync) {
            running.set(false);
            terminating.set(true);
            if (executor != null) {
                executor.shutdown();
                executor = null;
            }
        }
    }

    /**
     * 阻塞等待执行结束
     * <p>
     * 若执行过程中记录了错误 ({@link Error})，等待结束后将其重新抛出
     */
    @Override
    public void awaitTermination() {
        ExecutorService activeExecutor = executor;
        if (activeExecutor != null && running.get()) {
            awaitExecutorTermination(activeExecutor);
        }
        Error error = terminateError;
        if (error != null) {
            throw error;
        }
    }

    /**
     * 请求终止执行设置终止标志，后续任务将不再执行
     */
    @Override
    public void terminate() {
        terminating.set(true);
    }

    /**
     * 因发生错误而终止执行：记录错误信息 置终止标志并立即关闭执行器
     * 错误将在 {@link #awaitTermination()} 时重新抛出
     *
     * @param error 触发终止的错误
     */
    @SuppressWarnings("DataFlowIssue")
    private void terminateWithError(Error error) {
        if (terminating.get()) {
            return;
        }
        terminateError = error;
        terminate();
        executor.shutdownNow();
    }

    /**
     * 判断执行器是否正在终止
     *
     * @return 正在终止返回 true
     */
    @Override
    public boolean isTerminating() {
        return terminating.get();
    }

    /**
     * 判断执行器是否正在运行
     *
     * @return 正在运行返回 true
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取内部使用的执行器服务实例
     *
     * @return 内部执行器，未启动时可能为 null
     */
    @Override
    public @Nullable ExecutorService getInternalExecutor() {
        return executor;
    }

    /**
     * 逐阶段运行所有任务
     * <p>
     * 顺序阶段 (或线程数为 1 时)在当前线程逐个执行 并行阶段创建固定大小线程池并发执行，
     * 并等待其全部完成任一阶段后若检测到终止标志则提前退出执行结束时统一停止执行器
     */
    private void runStages() {
        try {
            for (ExecStage stage : stages) {
                int threads = Math.min(stage.getTasks().size(), threadsCount.get());
                if (stage.getType() == ExecType.SEQUENTIAL || threads == 1) {
                    for (Runnable task : stage.getTasks()) {
                        wrapTask(task);
                    }
                } else {
                    ExecutorService parallelExecutor = Executors.newFixedThreadPool(
                            threads, new com.bingbaihanji.fxdecomplie.util.concurrent.NamedThreadFactory("task-p"));
                    for (Runnable task : stage.getTasks()) {
                        parallelExecutor.execute(() -> wrapTask(task));
                    }
                    parallelExecutor.shutdown();
                    awaitExecutorTermination(parallelExecutor);
                }
                if (terminating.get()) {
                    break;
                }
            }
        } finally {
            stopExecution();
        }
    }

    /**
     * 包装并执行单个任务
     * <p>
     * 若已处于终止状态则跳过 正常完成后递增进度计数 捕获 {@link Error} 触发错误终止，
     * 捕获 {@link Exception} 仅记录日志而不中断整体执行
     *
     * @param task 待执行的任务
     */
    private void wrapTask(Runnable task) {
        if (terminating.get()) {
            return;
        }
        try {
            task.run();
            progress.incrementAndGet();
        } catch (Error e) {
            terminateWithError(e);
        } catch (Exception e) {
            LOG.error("Unhandled task exception:", e);
        }
    }

    /** 执行类型枚举：并行或顺序 */
    private enum ExecType {
        PARALLEL,
        SEQUENTIAL,
    }

    /**
     * 执行阶段，封装一组任务及其执行类型
     */
    private static final class ExecStage {
        private final ExecType type;
        private final List<? extends Runnable> tasks;

        private ExecStage(ExecType type, List<? extends Runnable> tasks) {
            this.type = type;
            this.tasks = tasks;
        }

        public ExecType getType() {
            return type;
        }

        public List<? extends Runnable> getTasks() {
            return tasks;
        }
    }
}
