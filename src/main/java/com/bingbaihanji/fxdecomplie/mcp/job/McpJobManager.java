package com.bingbaihanji.fxdecomplie.mcp.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * MCP 异步任务管理器:有界并发,状态查询,取消与结果保留.
 *
 * @author bingbaihanji
 */
public final class McpJobManager {

    private static final Logger log = LoggerFactory.getLogger(McpJobManager.class);

    private static final int MAX_CONCURRENT_JOBS = 2;

    private static final int MAX_RETAINED_JOBS = 50;

    private static final Duration RETENTION = Duration.ofMinutes(30);

    private final Map<String, McpJob> jobs = new ConcurrentHashMap<>();

    private final AtomicInteger threadCount = new AtomicInteger();

    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_JOBS, task -> {
        Thread thread = new Thread(task, "mcp-job-" + threadCount.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    public McpJob submit(String type, String workspaceId, Consumer<McpJob> task) {
        cleanup();
        McpJob job = new McpJob(UUID.randomUUID().toString(), type, workspaceId);
        jobs.put(job.id(), job);
        try {
            job.setFuture(executor.submit(() -> {
                job.markRunning();
                try {
                    task.accept(job);
                    if (job.state() == McpJob.State.CANCELLED
                            || job.state() == McpJob.State.FAILED
                            || job.state() == McpJob.State.SUCCEEDED) {
                        return;
                    }
                    job.complete(job.result());
                } catch (java.util.concurrent.CancellationException e) {
                    job.cancel();
                } catch (Throwable e) {
                    if (job.isCancelled()) {
                        job.cancel();
                    } else {
                        log.error("MCP 任务失败: {} ({})", job.id(), type, e);
                        job.fail(e.getMessage() == null
                                ? e.getClass().getSimpleName() : e.getMessage());
                    }
                }
            }));
        } catch (RuntimeException e) {
            jobs.remove(job.id());
            job.fail("job executor rejected: " + e.getMessage());
            throw e;
        }
        return job;
    }

    public Optional<McpJob> find(String jobId) {
        return jobId == null ? Optional.empty() : Optional.ofNullable(jobs.get(jobId));
    }

    public List<McpJob> list() {
        List<McpJob> result = new ArrayList<>(jobs.values());
        result.sort(Comparator.comparing(McpJob::createdAt).reversed());
        return result;
    }

    public boolean cancel(String jobId) {
        McpJob job = jobs.get(jobId);
        if (job == null) {
            return false;
        }
        McpJob.State state = job.state();
        if (state == McpJob.State.SUCCEEDED || state == McpJob.State.FAILED
                || state == McpJob.State.CANCELLED) {
            return false;
        }
        job.cancel();
        return true;
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(RETENTION);
        for (McpJob job : jobs.values()) {
            if (job.state() == McpJob.State.SUCCEEDED
                    || job.state() == McpJob.State.FAILED
                    || job.state() == McpJob.State.CANCELLED) {
                if (job.updatedAt().isBefore(cutoff)) {
                    jobs.remove(job.id());
                }
            }
        }
        if (jobs.size() <= MAX_RETAINED_JOBS) {
            return;
        }
        jobs.values().stream()
                .filter(job -> job.state() == McpJob.State.SUCCEEDED
                        || job.state() == McpJob.State.FAILED
                        || job.state() == McpJob.State.CANCELLED)
                .sorted(Comparator.comparing(McpJob::updatedAt))
                .limit(jobs.size() - MAX_RETAINED_JOBS)
                .forEach(job -> jobs.remove(job.id()));
    }

    public int activeCount() {
        return (int) jobs.values().stream()
                .filter(job -> job.state() == McpJob.State.PENDING
                        || job.state() == McpJob.State.RUNNING)
                .count();
    }

    public void close() {
        executor.shutdownNow();
        jobs.values().forEach(McpJob::cancel);
        jobs.clear();
    }
}
