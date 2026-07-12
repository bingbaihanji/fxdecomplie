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

    @Test
    void evictsWhenExceedingByteLimit() {
        DecompileCache cache = new DecompileCache();
        // ~25MB 的字符串（50MB 上限 → 三个触发淘汰）
        StringBuilder sb = new StringBuilder(12_500_000);
        for (int i = 0; i < 12_500_000; i++) {
            sb.append('x');
        }
        String bigValue = sb.toString();

        cache.put("ws", "com/A", DecompilerTypeEnum.VINEFLOWER, "h", bigValue);
        assertNotNull(cache.get("ws", "com/A", DecompilerTypeEnum.VINEFLOWER, "h"));

        cache.put("ws", "com/B", DecompilerTypeEnum.VINEFLOWER, "h", bigValue);
        assertNotNull(cache.get("ws", "com/B", DecompilerTypeEnum.VINEFLOWER, "h"));

        // 第三个大值应该触发淘汰
        cache.put("ws", "com/C", DecompilerTypeEnum.VINEFLOWER, "h", bigValue);
        assertNotNull(cache.get("ws", "com/C", DecompilerTypeEnum.VINEFLOWER, "h"));

        int present = 0;
        if (cache.get("ws", "com/A", DecompilerTypeEnum.VINEFLOWER, "h") != null) present++;
        if (cache.get("ws", "com/B", DecompilerTypeEnum.VINEFLOWER, "h") != null) present++;
        assertTrue(present <= 1, "expected at most 1 of A/B to survive eviction, got: " + present);
    }

    @Test
    void clearEmptiesEverything() {
        DecompileCache cache = new DecompileCache();
        cache.put("ws1", "com/A", DecompilerTypeEnum.VINEFLOWER, "h", "x");
        cache.put("ws2", "com/B", DecompilerTypeEnum.CFR, "h", "y");
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void invalidateRemovesAllEngineVariants() {
        DecompileCache cache = new DecompileCache();
        cache.put("ws", "com/X", DecompilerTypeEnum.VINEFLOWER, "h", "a");
        cache.put("ws", "com/X", DecompilerTypeEnum.CFR, "h", "b");
        cache.invalidate("ws", "com/X");
        assertNull(cache.get("ws", "com/X", DecompilerTypeEnum.VINEFLOWER, "h"));
        assertNull(cache.get("ws", "com/X", DecompilerTypeEnum.CFR, "h"));
    }
}
