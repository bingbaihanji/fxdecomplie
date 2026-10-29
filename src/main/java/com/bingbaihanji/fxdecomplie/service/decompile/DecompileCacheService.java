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
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * L2 (内存) + L3 (磁盘) 两级缓存协调.
 *
 * <p>L3 写入是异步、尽力而为的: 提交被拒绝仅记录日志,不影响 L2 中已写入的结果.
 * 失效时先递增失效纪元,使已排队但尚未执行的旧写入成为空操作,避免其复活已失效条目.
 * 失效与在途 L3 写入通过读写锁互斥: 失效为独占操作,多个 L3 写入之间可并发.</p>
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

    /** L3 写入与失效互斥: 写入持读锁(可并发),失效持写锁(独占) */
    private final ReentrantReadWriteLock l3Lock = new ReentrantReadWriteLock();

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
        // 2. L3：回填 L2 与失效互斥，避免陈旧值在失效后重新进入 L2
        l3Lock.readLock().lock();
        try {
            long epoch = invalidationEpoch.get();
            result = l3.load(key);
            if (result.isPresent() && invalidationEpoch.get() == epoch) {
                l2.save(key, result.get());
            }
            return result;
        } finally {
            l3Lock.readLock().unlock();
        }
    }

    @Override
    public void save(CodeCacheKey key, String source) {
        l3Lock.readLock().lock();
        try {
            long epoch = invalidationEpoch.get();
            // L2 同步写入
            l2.save(key, source);
            // L3 异步写入: 若提交后发生失效,则丢弃本次旧写入
            try {
                l3Executor.execute(() -> {
                    l3Lock.readLock().lock();
                    try {
                        if (invalidationEpoch.get() == epoch) {
                            l3.save(key, source);
                        }
                    } finally {
                        l3Lock.readLock().unlock();
                    }
                });
            } catch (RejectedExecutionException e) {
                log.debug("L3 缓存写入任务被拒绝: {}", key, e);
            }
        } finally {
            l3Lock.readLock().unlock();
        }
    }

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
        l3Lock.writeLock().lock();
        try {
            // 先递增纪元,使已排队的旧写入失效
            invalidationEpoch.incrementAndGet();
            l2.invalidate(key, scope);
            l3.invalidate(key, scope);
        } finally {
            l3Lock.writeLock().unlock();
        }
    }

    /** 清空 L2；与在途 L3 写入/失效互斥 */
    public void clearL2() {
        l3Lock.writeLock().lock();
        try {
            invalidationEpoch.incrementAndGet();
            l2.clearAll();
        } finally {
            l3Lock.writeLock().unlock();
        }
    }

    /** 清空 L3；与在途 L3 写入互斥 */
    public void clearL3() {
        l3Lock.writeLock().lock();
        try {
            invalidationEpoch.incrementAndGet();
            l3.clearAll();
        } finally {
            l3Lock.writeLock().unlock();
        }
    }

    /** 同时清空 L2/L3 */
    public void clearAllLevels() {
        l3Lock.writeLock().lock();
        try {
            invalidationEpoch.incrementAndGet();
            l2.clearAll();
            l3.clearAll();
        } finally {
            l3Lock.writeLock().unlock();
        }
    }
}
