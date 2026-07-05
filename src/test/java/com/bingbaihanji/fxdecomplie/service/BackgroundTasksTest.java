package com.bingbaihanji.fxdecomplie.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundTasksTest {

    @Test
    void runsTaskAndReturnsFuture() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Future<?> future = BackgroundTasks.run("test", latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        future.get(1, TimeUnit.SECONDS);
        assertTrue(future.isDone());
    }

    @Test
    void cancelInterruptsRunningTask() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Future<?> future = BackgroundTasks.run("test-cancel", () -> {
            started.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        BackgroundTasks.cancel(future);
        // Give it a moment to propagate
        Thread.sleep(200);
        // Task may complete normally after catching InterruptedException,
        // so isCancelled() may be false, but isDone() should be true
        assertTrue(future.isCancelled() || future.isDone());
    }

    @Test
    void cancelNullFutureIsSafe() {
        assertDoesNotThrow(() -> BackgroundTasks.cancel(null));
    }

    @Test
    void longTaskDoesNotBlockFollowingTask() throws Exception {
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch secondRan = new CountDownLatch(1);

        Future<?> blocker = BackgroundTasks.run("index-blocker", () -> {
            blockerStarted.countDown();
            try {
                releaseBlocker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));

        Future<?> second = BackgroundTasks.run("decompile-after-index", secondRan::countDown);

        assertTrue(secondRan.await(1, TimeUnit.SECONDS),
                "a long index task must not queue all later decompile tasks");
        releaseBlocker.countDown();
        second.get(1, TimeUnit.SECONDS);
        blocker.get(1, TimeUnit.SECONDS);
    }
}
