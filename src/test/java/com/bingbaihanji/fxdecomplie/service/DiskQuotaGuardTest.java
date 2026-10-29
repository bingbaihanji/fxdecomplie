package com.bingbaihanji.fxdecomplie.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class DiskQuotaGuardTest {

    @TempDir
    Path root;

    private static Path file(Path root, String name, int bytes, long mtime) throws Exception {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x".repeat(bytes));
        Files.setLastModifiedTime(file, FileTime.fromMillis(mtime));
        return file;
    }

    @Test
    void evictsOldestFilesToTargetRatio() throws Exception {
        Path oldest = file(root, "a.java", 10, 1_000);
        Path middle = file(root, "b.java", 10, 2_000);
        Path newest = file(root, "c.java", 10, 3_000);
        DiskQuotaGuard guard = new DiskQuotaGuard(root, 25, Runnable::run,
                0, 1, System::nanoTime);

        guard.evictIfNeeded();

        assertFalse(Files.exists(oldest));
        assertTrue(Files.exists(newest));
        assertTrue(guard.stats("t", 0, 0).bytes() <= (long) (25 * 0.7));
        assertTrue(guard.stats("t", 0, 0).entries() >= 1);
    }

    @Test
    void onWriteIsThrottledByWriteThreshold() throws Exception {
        file(root, "a.java", 10, 1_000);
        int[] runs = {0};
        Executor executor = task -> {
            runs[0]++;
            task.run();
        };
        DiskQuotaGuard guard = new DiskQuotaGuard(root, 100, executor,
                CacheLimits.QUOTA_CHECK_INTERVAL_NANOS, 2, System::nanoTime);

        guard.onWrite();
        assertEquals(0, runs[0]);
        guard.onWrite();
        assertEquals(1, runs[0]);
        guard.onWrite();
        assertEquals(1, runs[0]);
    }

    @Test
    void onWriteTriggersByTimeInterval() {
        long[] now = {0};
        LongSupplier clock = () -> now[0];
        AtomicInteger runs = new AtomicInteger();
        DiskQuotaGuard guard = new DiskQuotaGuard(root, 100, task -> {
            runs.incrementAndGet();
            task.run();
        }, CacheLimits.QUOTA_CHECK_INTERVAL_NANOS, 100, clock);

        guard.onWrite();
        assertEquals(0, runs.get());
        now[0] = CacheLimits.QUOTA_CHECK_INTERVAL_NANOS - 1;
        guard.onWrite();
        assertEquals(0, runs.get(), "未达到检查间隔时不应触发");
        now[0] = CacheLimits.QUOTA_CHECK_INTERVAL_NANOS;
        guard.onWrite();
        assertEquals(1, runs.get(), "达到检查间隔后应由时间条件触发");
    }

    @Test
    void resetRestartsThrottleWindow() {
        long[] now = {0};
        LongSupplier clock = () -> now[0];
        AtomicInteger runs = new AtomicInteger();
        DiskQuotaGuard guard = new DiskQuotaGuard(root, 100, task -> {
            runs.incrementAndGet();
            task.run();
        }, CacheLimits.QUOTA_CHECK_INTERVAL_NANOS, 2, clock);

        guard.onWrite();
        assertEquals(0, runs.get());
        guard.reset();
        // 未重置时本次写入计数会达到 2 并立即触发检查；重置后应从 1 重新开始
        guard.onWrite();
        assertEquals(0, runs.get(), "reset 后写入计数应重新开始");
        now[0] = CacheLimits.QUOTA_CHECK_INTERVAL_NANOS;
        guard.onWrite();
        assertEquals(1, runs.get());
    }

    @Test
    void concurrentWritesRunOnlyOneCheck() throws Exception {
        file(root, "a.java", 10, 1_000);
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Executor executor = task -> {
            runs.incrementAndGet();
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.run();
        };
        DiskQuotaGuard guard = new DiskQuotaGuard(root, 1_000, executor,
                CacheLimits.QUOTA_CHECK_INTERVAL_NANOS, 1, System::nanoTime);

        Thread first = new Thread(guard::onWrite, "quota-first");
        first.setDaemon(true);
        try {
            first.start();
            assertTrue(started.await(5, TimeUnit.SECONDS), "首次检查未启动");
            for (int i = 0; i < 5; i++) {
                guard.onWrite();
            }
            assertEquals(1, runs.get(), "单飞期间不应启动新的检查");
        } finally {
            // 断言失败时也必须释放并回收线程，避免 surefire 挂起
            release.countDown();
            first.join(5_000);
        }
        assertFalse(first.isAlive(), "首次检查未结束");

        guard.onWrite();
        assertEquals(2, runs.get(), "首次检查结束后应可再次调度");
    }

    @Test
    void rejectedCheckRecoversForNextWrite() {
        AtomicInteger attempts = new AtomicInteger();
        Executor executor = task -> {
            if (attempts.incrementAndGet() == 1) {
                throw new RejectedExecutionException("队列已满");
            }
            task.run();
        };
        DiskQuotaGuard guard = new DiskQuotaGuard(root, 100, executor,
                CacheLimits.QUOTA_CHECK_INTERVAL_NANOS, 1, System::nanoTime);

        assertDoesNotThrow(guard::onWrite);
        assertEquals(1, attempts.get());

        guard.onWrite();
        assertEquals(2, attempts.get(), "拒绝后应恢复单飞状态并允许再次调度");
    }

    @Test
    void traversalToleratesLinksAndDoesNotDoubleCount() throws Exception {
        Path realDir = root.resolve("real");
        Files.createDirectories(realDir);
        Files.writeString(realDir.resolve("a.java"), "x".repeat(12));
        boolean linksCreated = true;
        try {
            Files.createSymbolicLink(root.resolve("dir-link"), realDir);
            Files.createSymbolicLink(root.resolve("file-link.java"),
                    realDir.resolve("a.java"));
            Files.createSymbolicLink(root.resolve("dangling.java"),
                    root.resolve("missing.java"));
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            linksCreated = false;
        }
        Assumptions.assumeTrue(linksCreated, "当前环境不支持符号链接");
        DiskQuotaGuard guard = new DiskQuotaGuard(root, 1_000, Runnable::run,
                CacheLimits.QUOTA_CHECK_INTERVAL_NANOS, 1, System::nanoTime);

        assertDoesNotThrow(guard::evictIfNeeded);
        CacheStats stats = assertDoesNotThrow(() -> guard.stats("t", 0, 0));
        assertEquals(1, stats.entries());
        assertEquals(12L, stats.bytes());
    }

    @Test
    void nestedJunctionIsSkippedAndNotEvicted() throws Exception {
        Assumptions.assumeTrue(
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"),
                "仅 Windows 支持 junction");
        Path outside = Files.createTempDirectory("fxdecomplie-quota-junction-");
        Path junction = root.resolve("junction");
        try {
            Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J",
                    junction.toString(), outside.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean created = process.waitFor(10, TimeUnit.SECONDS)
                    && process.exitValue() == 0;
            if (!created) {
                process.destroyForcibly();
            }
            Assumptions.assumeTrue(created, "无法创建 junction");

            Path outsideFile = outside.resolve("outside.java");
            Files.writeString(outsideFile, "x".repeat(50));
            Path inside = file(root, "inside.java", 5, 1_000);
            DiskQuotaGuard guard = new DiskQuotaGuard(root, 1, Runnable::run,
                    CacheLimits.QUOTA_CHECK_INTERVAL_NANOS, 1, System::nanoTime);

            CacheStats stats = guard.stats("t", 0, 0);
            assertEquals(1, stats.entries(), "junction 目标不应被统计");
            assertEquals(5L, stats.bytes());

            guard.evictIfNeeded();
            assertTrue(Files.exists(outsideFile), "junction 外的目标文件不能被删除");
            assertFalse(Files.exists(inside), "根内文件应被淘汰");
        } finally {
            try {
                Files.deleteIfExists(junction);
            } catch (IOException ignored) {
            }
            deleteRecursively(outside);
        }
    }

    @Test
    void rootDirectorySymlinkIsTraversed() throws Exception {
        Path realRoot = root.resolve("real-root");
        Files.createDirectories(realRoot);
        Files.writeString(realRoot.resolve("a.java"), "x".repeat(10));
        Path linkRoot = root.resolve("link-root");
        boolean linked = true;
        try {
            Files.createSymbolicLink(linkRoot, realRoot);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            linked = false;
        }
        Assumptions.assumeTrue(linked, "当前环境不支持符号链接");

        DiskQuotaGuard guard = new DiskQuotaGuard(linkRoot, 1_000, Runnable::run,
                CacheLimits.QUOTA_CHECK_INTERVAL_NANOS, 1, System::nanoTime);

        CacheStats stats = guard.stats("t", 0, 0);
        assertEquals(1, stats.entries(), "根目录符号链接应解析到真实目录后遍历");
        assertEquals(10L, stats.bytes());
    }

    @Test
    void statsCountsNestedRegularFiles() throws Exception {
        file(root, "a.java", 10, 1_000);
        file(root, "nested/b.java", 20, 2_000);
        file(root, "nested/deep/c.java", 30, 3_000);
        DiskQuotaGuard guard = new DiskQuotaGuard(root, 1_000, Runnable::run,
                CacheLimits.QUOTA_CHECK_INTERVAL_NANOS, 1, System::nanoTime);

        CacheStats stats = guard.stats("decompile", 7, 3);

        assertEquals("decompile", stats.name());
        assertEquals(3, stats.entries());
        assertEquals(60L, stats.bytes());
        assertEquals(7, stats.hitCount());
        assertEquals(3, stats.missCount());
    }

    /** 递归删除测试临时目录，失败只忽略（不影响断言结论） */
    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }
}
