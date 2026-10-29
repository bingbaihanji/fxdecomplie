package com.bingbaihanji.fxdecomplie.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.Executor;

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
    void resetRestartsThrottleWindow() {
        DiskQuotaGuard guard = new DiskQuotaGuard(root, 10, Runnable::run,
                CacheLimits.QUOTA_CHECK_INTERVAL_NANOS, 1, System::nanoTime);
        guard.reset();
        guard.onWrite();
        assertEquals(0, guard.stats("t", 0, 0).entries());
    }
}
