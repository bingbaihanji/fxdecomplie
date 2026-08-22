package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.DecompileCache;
import com.bingbaihanji.fxdecomplie.service.DiskCodeCache;

import java.util.Optional;

/**
 * L2 (内存) + L3 (磁盘) 两级缓存协调:
 * <ol>
 *   <li>先查 L2 → 命中返回</li>
 *   <li>L2 未命中查 L3 → 命中后回填 L2</li>
 *   <li>均未命中返回 empty</li>
 *   <li>写入时先写 L2,L3 异步写(不阻塞调用方)</li>
 * </ol>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public class DecompileCacheService implements CodeCacheStore {

    private final DecompileCache l2;

    private final DiskCodeCache l3;

    public DecompileCacheService(DecompileCache l2, DiskCodeCache l3) {
        this.l2 = l2;
        this.l3 = l3;
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
        BackgroundTasks.run(BackgroundTasks.PoolType.IO,
                "cache-l3-write", () -> l3.save(key, source));
    }

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
        l2.invalidate(key, scope);
        l3.invalidate(key, scope);
    }
}
