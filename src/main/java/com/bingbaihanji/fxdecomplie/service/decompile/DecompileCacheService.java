package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.DecompileCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * L2 (内存) + L3 (磁盘) 两级缓存协调.
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public class DecompileCacheService implements CodeCacheStore {

    private static final Logger log = LoggerFactory.getLogger(DecompileCacheService.class);

    private final DecompileCache l2;

    private final CodeCacheStore l3;

    private final Executor l3Executor;

    public DecompileCacheService(DecompileCache l2, CodeCacheStore l3) {
        this(l2, l3, task -> BackgroundTasks.run(
                BackgroundTasks.PoolType.IO, "cache-l3-write", task));
    }

    public DecompileCacheService(DecompileCache l2, CodeCacheStore l3, Executor l3Executor) {
        this.l2 = l2;
        this.l3 = l3;
        this.l3Executor = l3Executor;
    }

    @Override
    public Optional<String> load(CodeCacheKey key) {
        // 1. L2
        Optional<String> result = l2.load(key);
        if (result.isPresent()) {
            return result;
        }
        // 2. L3
        result = l3.load(key);
        // 3. 回填 L2
        result.ifPresent(source -> l2.save(key, source));
        return result;
    }

    @Override
    public void save(CodeCacheKey key, String source) {
        // L2 同步写入
        l2.save(key, source);
        // L3 异步写入
        try {
            l3Executor.execute(() -> l3.save(key, source));
        } catch (RuntimeException e) {
            log.debug("L3 缓存写入任务被拒绝: {}", key, e);
        }
    }

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
        l2.invalidate(key, scope);
        l3.invalidate(key, scope);
    }
}
