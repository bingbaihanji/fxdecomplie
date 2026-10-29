package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheScope;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DecompileCacheTest {

    private static CodeCacheKey key(String workspace, String internalName) {
        return new CodeCacheKey(workspace, "fp", internalName,
                DecompilerTypeEnum.CFR, "default", 1);
    }

    @Test
    void separatesSameClassAcrossWorkspaces() {
        DecompileCache cache = new DecompileCache();
        cache.save(key("workspace-a", "com/example/App"), "source-a");
        cache.save(key("workspace-b", "com/example/App"), "source-b");

        assertEquals("source-a", cache.load(key("workspace-a", "com/example/App")).orElse(null));
        assertEquals("source-b", cache.load(key("workspace-b", "com/example/App")).orElse(null));
    }

    @Test
    void invalidatesOnlyTargetWorkspaceClass() {
        DecompileCache cache = new DecompileCache();
        cache.save(key("workspace-a", "com/example/App"), "source-a");
        cache.save(key("workspace-b", "com/example/App"), "source-b");

        cache.invalidate(key("workspace-a", "com/example/App"), CodeCacheScope.CLASS);

        assertNull(cache.load(key("workspace-a", "com/example/App")).orElse(null));
        assertEquals("source-b", cache.load(key("workspace-b", "com/example/App")).orElse(null));
    }

    @Test
    void invalidatesWorkspaceScope() {
        DecompileCache cache = new DecompileCache();
        cache.save(key("workspace-a", "com/example/App"), "source-a");
        cache.save(key("workspace-a", "com/example/Other"), "source-other");
        cache.save(key("workspace-b", "com/example/App"), "source-b");

        cache.invalidate(key("workspace-a", "com/example/App"), CodeCacheScope.WORKSPACE);

        assertNull(cache.load(key("workspace-a", "com/example/App")).orElse(null));
        assertNull(cache.load(key("workspace-a", "com/example/Other")).orElse(null));
        assertEquals("source-b", cache.load(key("workspace-b", "com/example/App")).orElse(null));
    }

    @Test
    void evictsByByteSize() {
        DecompileCache cache = new DecompileCache(20);
        cache.save(key("w", "A"), "12345678");   // 16 bytes
        cache.save(key("w", "B"), "abcdefgh");   // 16 bytes -> evicts A
        assertNull(cache.load(key("w", "A")).orElse(null));
        assertEquals("abcdefgh", cache.load(key("w", "B")).orElse(null));
    }

    @Test
    void concurrentSaveAndInvalidateNoCME() throws InterruptedException {
        DecompileCache cache = new DecompileCache();
        AtomicBoolean failed = new AtomicBoolean(false);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(2);

        Thread writer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 5_000; i++) {
                    cache.save(key("w" + (i % 10), "com/example/C" + i), "source-" + i);
                }
            } catch (Exception e) {
                failed.set(true);
            } finally {
                stopLatch.countDown();
            }
        });
        Thread invalidator = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 5_000; i++) {
                    cache.invalidate(key("w" + (i % 10), "com/example/C" + i),
                            CodeCacheScope.CLASS);
                }
            } catch (Exception e) {
                failed.set(true);
            } finally {
                stopLatch.countDown();
            }
        });
        writer.start();
        invalidator.start();
        startLatch.countDown();
        assertTrue(stopLatch.await(30, TimeUnit.SECONDS));
        assertFalse(failed.get());
    }
}
