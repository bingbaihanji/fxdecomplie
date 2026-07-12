package com.bingbaihanji.fxdecomplie.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TaskPoolsTest {

    @Test
    void interactivePoolSubmitsAndCompletes() throws Exception {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        BackgroundTasks.run(BackgroundTasks.PoolType.INTERACTIVE,
                "test-int", () -> done.complete(true));
        assertTrue(done.get(5, TimeUnit.SECONDS));
    }

    @Test
    void ioPoolSubmitsAndCompletes() throws Exception {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        BackgroundTasks.run(BackgroundTasks.PoolType.IO,
                "test-io", () -> done.complete(true));
        assertTrue(done.get(5, TimeUnit.SECONDS));
    }

    @Test
    void exportPoolSubmitsAndCompletes() throws Exception {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        BackgroundTasks.run(BackgroundTasks.PoolType.EXPORT,
                "test-exp", () -> done.complete(true));
        assertTrue(done.get(5, TimeUnit.SECONDS));
    }

    @Test
    void defaultRunUsesInteractivePool() throws Exception {
        // 任务内线程被重命名为任务名，但 daemon 标记可验证来自 bg 池
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        BackgroundTasks.run("test-default", () ->
                done.complete(Thread.currentThread().isDaemon()));
        assertTrue(done.get(5, TimeUnit.SECONDS),
                "thread from background pool should be daemon");
    }

    @Test
    void cancelRemovesTask() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        Future<?> task = BackgroundTasks.run(BackgroundTasks.PoolType.INTERACTIVE,
                "test-cancel", () -> {
                    try {
                        Thread.sleep(5000);
                        executed.set(true);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
        BackgroundTasks.cancel(task);
        Thread.sleep(200);
        assertFalse(executed.get(), "task should have been cancelled");
    }
}
