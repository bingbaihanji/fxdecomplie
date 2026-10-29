package com.bingbaihanji.fxdecomplie.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * 磁盘缓存配额守卫：写入节流（60 秒或 256 次）、单飞、后台 mtime LRU 淘汰到 70%.
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
public final class DiskQuotaGuard {

    private static final Logger log = LoggerFactory.getLogger(DiskQuotaGuard.class);

    private final Path root;
    private final long quotaBytes;
    private final Executor executor;
    private final long checkIntervalNanos;
    private final int writeThreshold;
    private final LongSupplier nanoClock;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger writes = new AtomicInteger();
    private volatile long lastCheckNanos;

    public DiskQuotaGuard(Path root, long quotaBytes, Executor executor) {
        this(root, quotaBytes, executor, CacheLimits.QUOTA_CHECK_INTERVAL_NANOS,
                CacheLimits.QUOTA_WRITE_THRESHOLD, System::nanoTime);
    }

    DiskQuotaGuard(Path root, long quotaBytes, Executor executor,
                   long checkIntervalNanos, int writeThreshold, LongSupplier nanoClock) {
        this.root = root;
        this.quotaBytes = quotaBytes;
        this.executor = executor;
        this.checkIntervalNanos = checkIntervalNanos;
        this.writeThreshold = Math.max(1, writeThreshold);
        this.nanoClock = nanoClock;
        this.lastCheckNanos = nanoClock.getAsLong();
    }

    /** 写入成功后调用；按时间或写入次数触发后台检查 */
    public void onWrite() {
        int count = writes.incrementAndGet();
        long now = nanoClock.getAsLong();
        if (count >= writeThreshold || now - lastCheckNanos >= checkIntervalNanos) {
            requestCheck();
        }
    }

    private void requestCheck() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        writes.set(0);
        lastCheckNanos = nanoClock.getAsLong();
        try {
            executor.execute(() -> {
                try {
                    evictIfNeeded();
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException e) {
            running.set(false);
            log.debug("磁盘配额检查任务被拒绝", e);
        }
    }

    /** 同步淘汰；供后台任务与测试调用 */
    void evictIfNeeded() {
        try {
            if (!Files.exists(root)) {
                return;
            }
            long totalSize = 0;
            List<Path> files;
            try (var stream = Files.walk(root)) {
                files = stream.filter(Files::isRegularFile).toList();
            }
            for (Path file : files) {
                try {
                    totalSize += Files.size(file);
                } catch (IOException ignored) {
                }
            }
            if (totalSize <= quotaBytes) {
                return;
            }
            long target = (long) (quotaBytes * CacheLimits.EVICTION_TARGET_RATIO);
            List<Path> ordered = files.stream()
                    .sorted(Comparator.comparingLong(DiskQuotaGuard::mtime))
                    .toList();
            int deleted = 0;
            for (Path file : ordered) {
                if (totalSize <= target) {
                    break;
                }
                try {
                    long size = Files.size(file);
                    Files.deleteIfExists(file);
                    totalSize -= size;
                    deleted++;
                } catch (IOException ignored) {
                }
            }
            log.info("缓存配额清理完成: root={}, 删除 {} 个文件, 剩余 {} MB",
                    root, deleted, totalSize / (1024 * 1024));
        } catch (IOException e) {
            log.debug("缓存配额检查失败: {}", root, e);
        }
    }

    public CacheStats stats(String name, long hitCount, long missCount) {
        long entries = 0;
        long bytes = 0;
        if (Files.exists(root)) {
            try (var stream = Files.walk(root)) {
                for (Path path : stream.filter(Files::isRegularFile).toList()) {
                    entries++;
                    try {
                        bytes += Files.size(path);
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return new CacheStats(name, entries, bytes, hitCount, missCount);
    }

    public void reset() {
        writes.set(0);
        lastCheckNanos = nanoClock.getAsLong();
    }

    private static long mtime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }
}
