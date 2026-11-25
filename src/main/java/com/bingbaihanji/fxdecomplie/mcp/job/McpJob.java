package com.bingbaihanji.fxdecomplie.mcp.job;

import java.time.Instant;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一个 MCP 异步任务的状态与结果。
 *
 * @author bingbaihanji
 */
public final class McpJob {

    public enum State {
        PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED
    }

    private final String id;

    private final String type;

    private final String workspaceId;

    private final Instant createdAt = Instant.now();

    private final AtomicBoolean cancelled = new AtomicBoolean();

    private volatile State state = State.PENDING;

    private volatile String message = "";

    private volatile int processed;

    private volatile int total;

    private volatile Object result;

    private volatile Instant updatedAt = Instant.now();

    private volatile Future<?> future;

    public McpJob(String id, String type, String workspaceId) {
        this.id = id;
        this.type = type;
        this.workspaceId = workspaceId;
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public State state() {
        return state;
    }

    public String message() {
        return message;
    }

    public int processed() {
        return processed;
    }

    public int total() {
        return total;
    }

    public Object result() {
        return result;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isCancelled() {
        return cancelled.get() || Thread.currentThread().isInterrupted();
    }

    public double progress() {
        return total <= 0 ? 0.0 : Math.min(1.0, processed / (double) total);
    }

    public void setFuture(Future<?> future) {
        this.future = future;
    }

    public void markRunning() {
        if (state == State.PENDING) {
            state = State.RUNNING;
        }
        updatedAt = Instant.now();
    }

    public void updateProgress(int processed, int total, String message) {
        this.processed = Math.max(0, processed);
        this.total = Math.max(0, total);
        if (message != null) {
            this.message = message;
        }
        updatedAt = Instant.now();
    }

    public void complete(Object result) {
        this.result = result;
        if (state != State.CANCELLED) {
            state = State.SUCCEEDED;
        }
        updatedAt = Instant.now();
    }

    public void fail(String message) {
        this.message = message == null ? "job failed" : message;
        state = State.FAILED;
        updatedAt = Instant.now();
    }

    public void cancel() {
        cancelled.set(true);
        State current = state;
        if (current == State.PENDING || current == State.RUNNING) {
            state = State.CANCELLED;
        }
        Future<?> currentFuture = future;
        if (currentFuture != null) {
            currentFuture.cancel(true);
        }
        updatedAt = Instant.now();
    }
}
