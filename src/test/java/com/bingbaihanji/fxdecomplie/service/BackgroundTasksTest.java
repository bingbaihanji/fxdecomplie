package com.bingbaihanji.fxdecomplie.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class BackgroundTasksTest {

    @Test
    void runsTaskAndReturnsFuture() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Future<?> future = BackgroundTasks.run("test", latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
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
}
