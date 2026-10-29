package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.DecompileCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L2 (内存) + L3 (磁盘) 两级缓存协调.
 *
 * <p>L3 写入是异步、尽力而为的: 提交被拒绝仅记录日志,不影响 L2 中已写入的结果.
 * 失效时先递增失效纪元,使已排队但尚未执行的旧写入成为空操作,避免其复活已失效条目.</p>
 *
 * <p>{@link #invalidate(CodeCacheKey, CodeCacheScope)} 会同步执行 L3 磁盘 IO,
 * 必须从后台线程调用,禁止在 JavaFX Application Thread 上调用.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public class DecompileCacheService implements CodeCacheStore {

    private static final Logger log = LoggerFactory.getLogger(DecompileCacheService.class);

    private final DecompileCache l2;

    private final CodeCacheStore l3;

    private final Executor l3Executor;

    /** 失效纪元 — 递增后,早于该纪元提交的 L3 写入不再落盘 */
    private final AtomicLong invalidationEpoch = new AtomicLong();

    public DecompileCacheService(DecompileCache l2, CodeCacheStore l3) {
        this(l2, l3, task -> BackgroundTasks.run(
                BackgroundTasks.PoolType.IO, "cache-l3-write", task));
    }

    public DecompileCacheService(DecompileCache l2, CodeCacheStore l3, Executor l3Executor) {
        this.l2 = Objects.requireNonNull(l2, "l2");
        this.l3 = Objects.requireNonNull(l3, "l3");
        this.l3Executor = Objects.requireNonNull(l3Executor, "l3Executor");
    }

    @Override
    public Optional<String> load(CodeCacheKey key) {
        // 1. L2
        Optional<String> result = l2.load(key);
        if (result.isPresent()) {
            return result;
        }
        // 2. L3
        result = l3.load(key);
        // 3. 回填 L2
        result.ifPresent(source -> l2.save(key, source));
        return result;
    }

    @Override
    public void save(CodeCacheKey key, String source) {
        // L2 同步写入
        l2.save(key, source);
        // L3 异步写入: 若提交后发生失效,则丢弃本次旧写入
        long epoch = invalidationEpoch.get();
        try {
            l3Executor.execute(() -> {
                if (invalidationEpoch.get() == epoch) {
                    l3.save(key, source);
                }
            });
        } catch (RejectedExecutionException e) {
            log.debug("L3 缓存写入任务被拒绝: {}", key, e);
        }
    }

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
        // 先递增纪元,使已排队的旧写入失效
        invalidationEpoch.incrementAndGet();
        l2.invalidate(key, scope);
        l3.invalidate(key, scope);
    }
}
