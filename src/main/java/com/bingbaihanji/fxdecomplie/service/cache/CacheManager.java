package com.bingbaihanji.fxdecomplie.service.cache;

import com.bingbaihanji.fxdecomplie.service.CacheStats;
import com.bingbaihanji.fxdecomplie.service.DecompileCache;
import com.bingbaihanji.fxdecomplie.service.DiskCodeCache;
import com.bingbaihanji.fxdecomplie.service.WorkspaceIndexCache;
import com.bingbaihanji.fxdecomplie.service.decompile.DecompileCacheService;

import java.util.List;
import java.util.Objects;

/**
 * 应用缓存装配入口与分类统计/清理.
 *
 * <p>受管缓存包括 L2、L3 与工作区索引缓存；JADX 运行时临时目录不在此管理.</p>
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
public final class CacheManager implements AutoCloseable {

    private final DecompileCacheService decompileCache;
    private final DecompileCache l2;
    private final DiskCodeCache l3;

    public CacheManager(DecompileCacheService decompileCache,
                        DecompileCache l2, DiskCodeCache l3) {
        this.decompileCache = Objects.requireNonNull(decompileCache, "decompileCache");
        this.l2 = Objects.requireNonNull(l2, "l2");
        this.l3 = Objects.requireNonNull(l3, "l3");
    }

    /** 反编译 L2/L3 统一入口 */
    public DecompileCacheService decompileCache() {
        return decompileCache;
    }

    /** L2 内存缓存统计 */
    public CacheStats l2Stats() {
        return new CacheStats("decompile-l2", l2.entryCount(), l2.sizeBytes(),
                l2.hitCount(), l2.missCount());
    }

    /** L3 磁盘缓存统计 */
    public CacheStats l3Stats() {
        return l3.stats();
    }

    /** 索引缓存统计 */
    public CacheStats indexStats() {
        return WorkspaceIndexCache.stats();
    }

    /** 所有受管缓存的统计快照 */
    public List<CacheStats> stats() {
        return List.of(l2Stats(), l3Stats(), indexStats());
    }

    /** 分类清理；磁盘清理为同步 IO，调用方应在后台线程执行 */
    public void clear(CacheKind kind) {
        switch (kind) {
            case DECOMPILE_L2 -> decompileCache.clearL2();
            case DECOMPILE_L3 -> decompileCache.clearL3();
            case INDEX -> WorkspaceIndexCache.clearAll();
            case ALL_MANAGED -> {
                decompileCache.clearAllLevels();
                WorkspaceIndexCache.clearAll();
            }
        }
    }

    /** 关闭时只清理内存，不删除磁盘缓存 */
    @Override
    public void close() {
        decompileCache.clearL2();
    }
}
