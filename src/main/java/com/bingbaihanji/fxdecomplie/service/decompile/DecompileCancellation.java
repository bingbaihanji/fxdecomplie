package com.bingbaihanji.fxdecomplie.service.decompile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * 管理反编译任务的取消与过期结果丢弃.
 *
 * <p>每个活跃的反编译请求通过 requestId 注册;取消时移除并中断对应的 Future.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public final class DecompileCancellation {

    private static final Logger log = LoggerFactory.getLogger(DecompileCancellation.class);

    private final Map<String, Future<?>> activeRequests = new ConcurrentHashMap<>();

    /** 注册一个活跃请求 */
    public void register(String requestId, Future<?> future) {
        activeRequests.put(requestId, future);
    }

    /** 取消并移除指定请求 */
    public void cancel(String requestId) {
        Future<?> future = activeRequests.remove(requestId);
        if (future != null && !future.isDone()) {
            log.debug("取消反编译请求: {}", requestId);
            future.cancel(true);
        }
    }

    /** 请求完成时清理注册 */
    public void complete(String requestId) {
        activeRequests.remove(requestId);
    }

    /** 当前活跃请求数 */
    public int activeCount() {
        return activeRequests.size();
    }

    /** 取消所有活跃请求 */
    public void cancelAll() {
        log.debug("取消所有活跃反编译请求 ({} 个)", activeRequests.size());
        activeRequests.values().forEach(f -> {
            if (!f.isDone()) {
                f.cancel(true);
            }
        });
        activeRequests.clear();
    }
}
