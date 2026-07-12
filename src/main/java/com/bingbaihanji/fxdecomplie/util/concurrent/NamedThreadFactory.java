package com.bingbaihanji.fxdecomplie.util.concurrent;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 带命名前缀的简单线程工厂
 * <p>
 * 线程名格式：{@code <prefix>-<poolId>-<threadId>}
 * 配备统一的未捕获异常处理器，OOM 错误会强制中断线程
 */
public final class NamedThreadFactory implements ThreadFactory {

    private static final Logger LOG = LoggerFactory.getLogger(NamedThreadFactory.class);
    private static final AtomicInteger POOL_ID = new AtomicInteger(0);

    private final String name;
    private final AtomicInteger number = new AtomicInteger(0);
    private final int poolId;

    /**
     * 创建线程工厂
     *
     * @param name 线程名前缀
     */
    public NamedThreadFactory(String name) {
        this.name = name;
        this.poolId = POOL_ID.incrementAndGet();
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread thread = new Thread(r, name + '-' + poolId + '-' + number.incrementAndGet());
        thread.setUncaughtExceptionHandler((t, e) -> {
            if (e instanceof OutOfMemoryError) {
                t.interrupt();
                LOG.error("OutOfMemoryError in thread {}, forcing interrupt", t.getName());
            } else {
                LOG.error("Uncaught thread exception, thread: {}", t.getName(), e);
            }
        });
        return thread;
    }
}
