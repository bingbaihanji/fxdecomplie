package com.bingbaihanji.fxdecomplie.service.cache;

import com.bingbaihanji.fxdecomplie.service.decompile.DecompileCacheService;

import java.util.Objects;

/**
 * 应用缓存装配入口;Phase 2/3 将扩展索引缓存、搜索快照与统计/清理.
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
public final class CacheManager implements AutoCloseable {

    private final DecompileCacheService decompileCache;

    public CacheManager(DecompileCacheService decompileCache) {
        this.decompileCache = Objects.requireNonNull(decompileCache, "decompileCache");
    }

    /** 反编译 L2/L3 统一入口 */
    public DecompileCacheService decompileCache() {
        return decompileCache;
    }

    @Override
    public void close() {
        // Phase 2 在此清理 L2/快照
    }
}
