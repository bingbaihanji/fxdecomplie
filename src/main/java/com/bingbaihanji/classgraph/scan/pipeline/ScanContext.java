package com.bingbaihanji.classgraph.scan.pipeline;

import com.bingbaihanji.classgraph.scan.config.ScanOptions;
import com.bingbaihanji.classgraph.util.InterruptionChecker;
import com.bingbaihanji.classgraph.util.LogNode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * 扫描上下文 — 在管道各步骤间传递共享状态。
 *
 * <p>线程安全。管道执行期间由 {@link ScanPipeline} 创建和管理。
 * 支持通过类型安全的键存储和检索任意中间结果。</p>
 */
public class ScanContext {

    private final ScanOptions options;
    private final ExecutorService executor;
    private final InterruptionChecker interruptionChecker;
    private final int numParallelTasks;
    private final LogNode log;
    private final ConcurrentMap<String, Object> attributes;
    private volatile boolean cancelled;

    /**
     * 创建扫描上下文
     *
     * @param options             扫描配置
     * @param executor            线程池
     * @param interruptionChecker 中断检测器
     * @param numParallelTasks    并行任务数
     * @param log                 日志节点
     */
    public ScanContext(ScanOptions options, ExecutorService executor,
                       InterruptionChecker interruptionChecker, int numParallelTasks,
                       LogNode log) {
        this.options = options;
        this.executor = executor;
        this.interruptionChecker = interruptionChecker;
        this.numParallelTasks = numParallelTasks;
        this.log = log;
        this.attributes = new ConcurrentHashMap<>();
    }

    // ─── 访问器 ───

    public ScanOptions options() { return options; }
    public ExecutorService executor() { return executor; }
    public InterruptionChecker interruptionChecker() { return interruptionChecker; }
    public int numParallelTasks() { return numParallelTasks; }
    public LogNode log() { return log; }

    // ─── 中间结果存储 ───

    /**
     * 存储任意中间结果。
     *
     * @param key   键（建议使用 {@code StepName.result} 命名约定）
     * @param value 值
     * @param <T>   值类型
     * @return 之前与此键关联的值，或 null
     */
    @SuppressWarnings("unchecked")
    public <T> T put(String key, T value) {
        return (T) attributes.put(key, value);
    }

    /**
     * 检索中间结果。
     *
     * @param key 键
     * @param <T> 期望的类型
     * @return 值，或 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 检索中间结果，如果不存在则抛出异常。
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrThrow(String key) {
        T value = (T) attributes.get(key);
        if (value == null) {
            throw new IllegalStateException(
                "Required context attribute not found: " + key);
        }
        return value;
    }

    /** 检查是否已取消 */
    public boolean isCancelled() { return cancelled; }

    /** 取消扫描 */
    public void cancel() { this.cancelled = true; }

    /** 检查中断并抛出 */
    public void checkInterrupted() throws InterruptedException, ExecutionException {
        if (cancelled) {
            throw new InterruptedException("Scan cancelled");
        }
        if (interruptionChecker != null) {
            interruptionChecker.check();
        }
    }
}
