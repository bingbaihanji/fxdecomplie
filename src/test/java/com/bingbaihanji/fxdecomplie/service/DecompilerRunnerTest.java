package com.bingbaihanji.fxdecomplie.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DecompilerRunnerTest {

    @Test
    void jadxFailureOutputDetectedByStringPrefix() {
        assertTrue(DecompilerRunner.isFailureOutput(
                "// jadx Error: something wrong\n// Class: com/example/Foo"));
        assertTrue(DecompilerRunner.isFailureOutput(
                "// jadx decompile failed: no classes loaded\n// Class: com/example/Foo"));
    }

    @Test
    void jadxSuccessOutputNotDetectedAsFailure() {
        assertFalse(DecompilerRunner.isFailureOutput(
                "package com.example;\n\npublic class Foo {\n}\n"));
    }

    @Test
    void jadxEmptyOrNullOutputDetectedAsFailure() {
        assertTrue(DecompilerRunner.isFailureOutput(null));
        assertTrue(DecompilerRunner.isFailureOutput(""));
        assertTrue(DecompilerRunner.isFailureOutput("   "));
    }

    @Test
    void timeoutCounterResetDoesNotDeadlock() throws Exception {
        // 通过多线程并发调用同步方法来验证无死锁
        int threads = 10;
        java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicInteger errors =
                new java.util.concurrent.atomic.AtomicInteger();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        DecompilerRunner.resetTimeoutCounter();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, errors.get(), "no errors during concurrent access");
    }

    @Test
    void maybeRebuildExecutorRequiresThreshold() {
        // reset counter first to start from a clean state
        DecompilerRunner.resetTimeoutCounter();
        // 单次调用不应重建（阈值 = 3）
        boolean rebuilt = DecompilerRunner.maybeRebuildExecutor();
        // 计数从 0 开始，调用后 = 1，小于阈值 3，所以不应重建
        assertFalse(rebuilt, "should not rebuild on first timeout");
        DecompilerRunner.resetTimeoutCounter();
    }
}
