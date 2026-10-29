package com.bingbaihanji.fxdecomplie.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * 磁盘缓存配额守卫：写入节流（60 秒或 256 次）、单飞、后台 mtime LRU 淘汰到 70%.
 *
 * <p>遍历不跟随符号链接与联接点；后台执行器拒绝任务时不影响调用方.</p>
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
        this.root = Objects.requireNonNull(root, "root");
        this.quotaBytes = Math.max(0, quotaBytes);
        this.executor = Objects.requireNonNull(executor, "executor");
        this.checkIntervalNanos = checkIntervalNanos;
        this.writeThreshold = Math.max(1, writeThreshold);
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.lastCheckNanos = this.nanoClock.getAsLong();
    }

    /**
     * 构造使用后台线程池的配额守卫；池拒绝任务时抛出 {@link RejectedExecutionException}，
     * 由 {@link #onWrite()} 内部捕获并恢复单飞状态.
     */
    public static DiskQuotaGuard background(Path root, long quotaBytes,
                                            BackgroundTasks.PoolType type, String taskName) {
        return new DiskQuotaGuard(root, quotaBytes, task -> {
            AtomicBoolean rejected = new AtomicBoolean();
            BackgroundTasks.run(type, taskName, task, e -> rejected.set(true));
            if (rejected.get()) {
                throw new RejectedExecutionException(taskName);
            }
        });
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
                } catch (RuntimeException e) {
                    log.debug("磁盘配额后台检查失败: {}", root, e);
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
        List<FileInfo> files = scanFiles();
        long totalSize = 0;
        for (FileInfo file : files) {
            totalSize += file.size();
        }
        if (totalSize <= quotaBytes) {
            return;
        }
        long target = (long) (quotaBytes * CacheLimits.EVICTION_TARGET_RATIO);
        List<FileInfo> ordered = files.stream()
                .sorted(Comparator.comparingLong(FileInfo::mtime))
                .toList();
        int deleted = 0;
        for (FileInfo file : ordered) {
            if (totalSize <= target) {
                break;
            }
            try {
                // 已被并发删除的文件不计入删除数，也不扣减总量（宁可多删，不对已消失文件再减）
                if (Files.deleteIfExists(file.path())) {
                    totalSize -= file.size();
                    deleted++;
                }
            } catch (IOException ignored) {
            }
        }
        log.info("缓存配额清理完成: root={}, 删除 {} 个文件, 剩余 {} MB",
                root, deleted, totalSize / (1024 * 1024));
    }

    public CacheStats stats(String name, long hitCount, long missCount) {
        long entries = 0;
        long bytes = 0;
        for (FileInfo file : scanFiles()) {
            entries++;
            bytes += file.size();
        }
        return new CacheStats(name, entries, bytes, hitCount, missCount);
    }

    /** 重置写入计数与检查时间戳；不取消进行中的检查，也不改变单飞状态 */
    public void reset() {
        writes.set(0);
        lastCheckNanos = nanoClock.getAsLong();
    }

    /**
     * 单次遍历收集普通文件快照：不跟随符号链接/联接点，绝不向调用方抛异常.
     *
     * <p>Windows 联接点在无跟随属性下报告为 other；根目录本身允许是合法的重定位联接点，
     * 其余链接目录直接跳过子树.</p>
     */
    private List<FileInfo> scanFiles() {
        List<FileInfo> files = new ArrayList<>();
        if (!Files.exists(root)) {
            return files;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && (attrs.isOther() || attrs.isSymbolicLink())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        files.add(new FileInfo(file, attrs.size(),
                                attrs.lastModifiedTime().toMillis()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.debug("遍历缓存路径失败: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            log.debug("缓存配额遍历失败: {}", root, e);
        }
        return files;
    }

    /** 遍历得到的单个普通文件快照；淘汰排序使用快照值，不再逐文件读取属性 */
    private record FileInfo(Path path, long size, long mtime) {
    }
}
