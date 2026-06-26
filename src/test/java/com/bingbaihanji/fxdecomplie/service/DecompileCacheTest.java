package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DecompileCacheTest {

    @Test
    void separatesSameClassAcrossWorkspaces() {
        DecompileCache cache = new DecompileCache();

        cache.put("workspace-a", "com/example/App", DecompilerTypeEnum.CFR,
                "default", "source-a");
        cache.put("workspace-b", "com/example/App", DecompilerTypeEnum.CFR,
                "default", "source-b");

        assertEquals("source-a", cache.get("workspace-a", "com/example/App",
                DecompilerTypeEnum.CFR, "default"));
        assertEquals("source-b", cache.get("workspace-b", "com/example/App",
                DecompilerTypeEnum.CFR, "default"));
    }

    @Test
    void invalidatesOnlyTargetWorkspaceClass() {
        DecompileCache cache = new DecompileCache();
        cache.put("workspace-a", "com/example/App", DecompilerTypeEnum.CFR,
                "default", "source-a");
        cache.put("workspace-b", "com/example/App", DecompilerTypeEnum.CFR,
                "default", "source-b");

        cache.invalidate("workspace-a", "com/example/App");

        assertNull(cache.get("workspace-a", "com/example/App",
                DecompilerTypeEnum.CFR, "default"));
        assertEquals("source-b", cache.get("workspace-b", "com/example/App",
                DecompilerTypeEnum.CFR, "default"));
    }

    @Test
    void concurrentPutAndInvalidateNoCME() throws InterruptedException {
        DecompileCache cache = new DecompileCache();
        AtomicBoolean failed = new AtomicBoolean(false);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(2);

        Thread writer = new Thread(() -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                for (int i = 0; i < 5_000; i++) {
                    cache.put("ws", "com/example/A", DecompilerTypeEnum.CFR,
                            "hash", "source-" + i);
                }
            } catch (Exception e) {
                failed.set(true);
            } finally {
                stopLatch.countDown();
            }
        }, "cache-writer");

        Thread invalidator = new Thread(() -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                for (int i = 0; i < 5_000; i++) {
                    cache.invalidate("ws", "com/example/A");
                }
            } catch (Exception e) {
                failed.set(true);
            } finally {
                stopLatch.countDown();
            }
        }, "cache-invalidator");

        writer.start();
        invalidator.start();
        startLatch.countDown();
        assertTrue(stopLatch.await(30, TimeUnit.SECONDS), "测试应在 30 秒内完成");
        assertFalse(failed.get(), "并发 put + invalidate 不应抛异常");
    }
}
