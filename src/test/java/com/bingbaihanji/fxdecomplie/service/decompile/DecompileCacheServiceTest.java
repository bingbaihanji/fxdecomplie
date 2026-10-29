package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.DecompileCache;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DecompileCacheServiceTest {

    private static CodeCacheKey key(String workspace) {
        return new CodeCacheKey(workspace, "fp", "com/example/A",
                DecompilerTypeEnum.CFR, "default", 1);
    }

    /** 同步记录型 L3 */
    static final class MapStore implements CodeCacheStore {
        final Map<CodeCacheKey, String> data = new HashMap<>();
        final List<Map.Entry<CodeCacheKey, CodeCacheScope>> invalidations = new ArrayList<>();
        int loads;

        @Override
        public Optional<String> load(CodeCacheKey key) {
            loads++;
            return Optional.ofNullable(data.get(key));
        }

        @Override
        public void save(CodeCacheKey key, String source) {
            data.put(key, source);
        }

        @Override
        public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
            invalidations.add(Map.entry(key, scope));
            data.remove(key);
        }

        @Override
        public void clearAll() {
            data.clear();
        }
    }

    /** 延迟执行器: 暂存任务,便于验证异步写入与失效的时序 */
    static final class DeferredExecutor implements Executor {
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
        }
    }

    /** 阻塞型 L3: 写入开始后暂停,用于验证失效与在途写入的互斥 */
    static final class BlockingStore implements CodeCacheStore {
        final Map<CodeCacheKey, String> data = new HashMap<>();
        final CountDownLatch writeStarted = new CountDownLatch(1);
        final CountDownLatch releaseWrite = new CountDownLatch(1);

        @Override
        public Optional<String> load(CodeCacheKey key) {
            return Optional.ofNullable(data.get(key));
        }

        @Override
        public void save(CodeCacheKey key, String source) {
            writeStarted.countDown();
            try {
                if (!releaseWrite.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("写入未被释放");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("写入被中断", e);
            }
            data.put(key, source);
        }

        @Override
        public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
            data.remove(key);
        }
    }

    @Test
    void l2HitSkipsL3() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        DecompileCacheService service = new DecompileCacheService(l2, l3, Runnable::run);
        l2.save(key("w"), "l2-source");

        assertEquals("l2-source", service.load(key("w")).orElse(null));
        assertEquals(0, l3.loads);
    }

    @Test
    void l3HitRefillsL2() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        l3.data.put(key("w"), "l3-source");
        DecompileCacheService service = new DecompileCacheService(l2, l3, Runnable::run);

        assertEquals("l3-source", service.load(key("w")).orElse(null));
        assertEquals("l3-source", l2.load(key("w")).orElse(null));
    }

    @Test
    void saveWritesL2ImmediatelyAndL3ThroughExecutor() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        DecompileCacheService service = new DecompileCacheService(l2, l3, Runnable::run);

        service.save(key("w"), "source");

        assertEquals("source", l2.load(key("w")).orElse(null));
        assertEquals("source", l3.data.get(key("w")));
    }

    @Test
    void invalidateHitsBothLevels() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        DecompileCacheService service = new DecompileCacheService(l2, l3, Runnable::run);
        service.save(key("w"), "source");

        service.invalidate(key("w"), CodeCacheScope.CLASS);

        assertNull(l2.load(key("w")).orElse(null));
        assertNull(l3.data.get(key("w")));
        assertEquals(1, l3.invalidations.size());
        assertEquals(key("w"), l3.invalidations.get(0).getKey());
        assertEquals(CodeCacheScope.CLASS, l3.invalidations.get(0).getValue());
    }

    @Test
    void invalidateBeforeQueuedWritePreventsResurrection() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        DeferredExecutor executor = new DeferredExecutor();
        DecompileCacheService service = new DecompileCacheService(l2, l3, executor);

        service.save(key("w"), "stale");
        service.invalidate(key("w"), CodeCacheScope.CLASS);
        executor.runAll();

        assertNull(l3.data.get(key("w")));
        assertNull(l2.load(key("w")).orElse(null));
    }

    @Test
    void saveAfterInvalidateStillWritesFreshValue() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        DeferredExecutor executor = new DeferredExecutor();
        DecompileCacheService service = new DecompileCacheService(l2, l3, executor);

        service.invalidate(key("w"), CodeCacheScope.CLASS);
        service.save(key("w"), "fresh");
        executor.runAll();

        assertEquals("fresh", l3.data.get(key("w")));
        assertEquals("fresh", l2.load(key("w")).orElse(null));
    }

    @Test
    void rejectedL3WriteDoesNotThrowAndKeepsL2() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        DecompileCacheService service = new DecompileCacheService(l2, l3, task -> {
            throw new RejectedExecutionException("rejected");
        });

        assertDoesNotThrow(() -> service.save(key("w"), "source"));
        assertEquals("source", l2.load(key("w")).orElse(null));
        assertNull(l3.data.get(key("w")));
    }

    @Test
    void inFlightWriteCannotLandAfterInvalidate() throws Exception {
        DecompileCache l2 = new DecompileCache();
        BlockingStore store = new BlockingStore();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            DecompileCacheService service = new DecompileCacheService(l2, store, pool);

            service.save(key("w"), "old");
            assertTrue(store.writeStarted.await(5, TimeUnit.SECONDS));

            CountDownLatch invalidationDone = new CountDownLatch(1);
            Thread invalidator = new Thread(() -> {
                try {
                    service.invalidate(key("w"), CodeCacheScope.CLASS);
                } finally {
                    invalidationDone.countDown();
                }
            });
            invalidator.start();

            // 失效必须等待在途写入持有的读锁,不能被提前完成
            assertFalse(invalidationDone.await(200, TimeUnit.MILLISECONDS));

            store.releaseWrite.countDown();
            assertTrue(invalidationDone.await(5, TimeUnit.SECONDS));
            // 写入先落盘,随后失效将其删除,不能出现“失效后陈旧写回”
            assertNull(store.data.get(key("w")));
        } finally {
            store.releaseWrite.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void l2IsWrittenBeforeL3IsSubmitted() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        DecompileCacheService service = new DecompileCacheService(l2, l3, task -> {
            assertEquals("source", l2.load(key("w")).orElse(null));
            task.run();
        });

        service.save(key("w"), "source");

        assertEquals("source", l3.data.get(key("w")));
    }

    static final class BlockingLoadStore implements CodeCacheStore {
        final Map<CodeCacheKey, String> data = new HashMap<>();
        final CountDownLatch loadStarted = new CountDownLatch(1);
        final CountDownLatch releaseLoad = new CountDownLatch(1);

        @Override
        public Optional<String> load(CodeCacheKey key) {
            loadStarted.countDown();
            try {
                releaseLoad.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Optional.ofNullable(data.get(key));
        }

        @Override
        public void save(CodeCacheKey key, String source) {
            data.put(key, source);
        }

        @Override
        public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
            data.remove(key);
        }
    }

    @Test
    void invalidateWaitsForInFlightRefillAndClearsIt() throws Exception {
        DecompileCache l2 = new DecompileCache();
        BlockingLoadStore l3 = new BlockingLoadStore();
        l3.data.put(key("w"), "stale");
        DecompileCacheService service = new DecompileCacheService(l2, l3, Runnable::run);

        Thread loader = new Thread(() -> service.load(key("w")));
        loader.start();
        assertTrue(l3.loadStarted.await(5, TimeUnit.SECONDS));

        CountDownLatch invalidated = new CountDownLatch(1);
        Thread invalidator = new Thread(() -> {
            try {
                service.invalidate(key("w"), CodeCacheScope.CLASS);
            } finally {
                invalidated.countDown();
            }
        });
        invalidator.start();
        assertFalse(invalidated.await(200, TimeUnit.MILLISECONDS),
                "失效必须等待在途回填完成");

        l3.releaseLoad.countDown();
        assertTrue(invalidated.await(5, TimeUnit.SECONDS));
        loader.join(5_000);
        assertNull(l2.load(key("w")).orElse(null));
    }

    @Test
    void clearKindsClearExpectedLevels() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        DecompileCacheService service = new DecompileCacheService(l2, l3, Runnable::run);
        service.save(key("w"), "source");

        service.clearL2();
        assertNull(l2.load(key("w")).orElse(null));
        assertEquals("source", l3.data.get(key("w")));

        service.clearL3();
        assertNull(l3.data.get(key("w")));

        service.save(key("w"), "source");
        service.clearAllLevels();
        assertNull(l2.load(key("w")).orElse(null));
        assertNull(l3.data.get(key("w")));
    }
}
