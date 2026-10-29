package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheScope;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DecompileCacheTest {

    private static CodeCacheKey key(String workspace, String internalName) {
        return new CodeCacheKey(workspace, "fp", internalName,
                DecompilerTypeEnum.CFR, "default", 1);
    }

    private static CodeCacheKey key(String workspace, String internalName,
                                    DecompilerTypeEnum engine, String optionsHash, int keyVersion) {
        return new CodeCacheKey(workspace, "fp", internalName, engine, optionsHash, keyVersion);
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
    void invalidatesClassAcrossEngineOptionsAndVersion() {
        DecompileCache cache = new DecompileCache();
        CodeCacheKey cfrDefaultV1 = key("w", "com/example/App");
        CodeCacheKey vineflowerDefaultV1 = key("w", "com/example/App",
                DecompilerTypeEnum.VINEFLOWER, "default", 1);
        CodeCacheKey cfrOtherV1 = key("w", "com/example/App",
                DecompilerTypeEnum.CFR, "other", 1);
        CodeCacheKey cfrDefaultV2 = key("w", "com/example/App",
                DecompilerTypeEnum.CFR, "default", 2);
        CodeCacheKey unrelated = key("w", "com/example/Other");

        cache.save(cfrDefaultV1, "cfr-default-v1");
        cache.save(vineflowerDefaultV1, "vineflower-default-v1");
        cache.save(cfrOtherV1, "cfr-other-v1");
        cache.save(cfrDefaultV2, "cfr-default-v2");
        cache.save(unrelated, "other-class");

        cache.invalidate(cfrDefaultV1, CodeCacheScope.CLASS);

        assertNull(cache.load(cfrDefaultV1).orElse(null));
        assertNull(cache.load(vineflowerDefaultV1).orElse(null));
        assertNull(cache.load(cfrOtherV1).orElse(null));
        assertNull(cache.load(cfrDefaultV2).orElse(null));
        assertEquals("other-class", cache.load(unrelated).orElse(null));
    }

    @Test
    void invalidateAllClearsEverything() {
        DecompileCache cache = new DecompileCache();
        CodeCacheKey app = key("w", "com/example/App");
        CodeCacheKey other = key("w", "com/example/Other");
        cache.save(app, "source-a");
        cache.save(other, "source-b");

        cache.invalidate(app, CodeCacheScope.ALL);

        assertEquals(0, cache.sizeBytes());
        assertNull(cache.load(app).orElse(null));
        assertNull(cache.load(other).orElse(null));
    }

    @Test
    void evictsByByteSize() {
        DecompileCache cache = new DecompileCache(20);
        cache.save(key("w", "A"), "12345678");   // 16 bytes
        cache.save(key("w", "B"), "abcdefgh");   // 16 bytes -> evicts A
        assertEquals(16, cache.sizeBytes());
        assertNull(cache.load(key("w", "A")).orElse(null));
        assertEquals("abcdefgh", cache.load(key("w", "B")).orElse(null));
    }

    @Test
    void ignoresNullOrBlankSource() {
        DecompileCache cache = new DecompileCache();
        CodeCacheKey key = key("w", "com/example/App");

        cache.save(key, null);
        cache.save(key, "");
        cache.save(key, "   ");

        assertNull(cache.load(key).orElse(null));
        assertEquals(0, cache.sizeBytes());
    }

    @Test
    void concurrentSaveAndInvalidateNoCME() throws InterruptedException {
        DecompileCache cache = new DecompileCache();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(2);

        Thread writer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 5_000; i++) {
                    cache.save(key("w" + (i % 10), "com/example/C" + i), "source-" + i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure.compareAndSet(null, e);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure.compareAndSet(null, e);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                stopLatch.countDown();
            }
        });
        writer.start();
        invalidator.start();
        startLatch.countDown();
        assertTrue(stopLatch.await(30, TimeUnit.SECONDS));
        assertNull(failure.get(), () -> "并发 save + invalidate 失败: " + failure.get());

        cache.invalidate(key("w0", "com/example/C0"), CodeCacheScope.ALL);
        assertEquals(0, cache.sizeBytes());
    }
}
