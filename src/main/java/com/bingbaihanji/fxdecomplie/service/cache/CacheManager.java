package com.bingbaihanji.fxdecomplie.service.cache;

import com.bingbaihanji.fxdecomplie.model.Workspace;
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
 * <p>受管缓存包括 L2、L3、工作区索引与搜索快照；JADX 运行时临时目录不在此管理.</p>
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
public final class CacheManager implements AutoCloseable {

    private final DecompileCacheService decompileCache;
    private final DecompileCache l2;
    private final DiskCodeCache l3;
    private final SourceSnapshotCache snapshots;

    public CacheManager(DecompileCacheService decompileCache,
                        DecompileCache l2, DiskCodeCache l3,
                        SourceSnapshotCache snapshots) {
        this.decompileCache = Objects.requireNonNull(decompileCache, "decompileCache");
        this.l2 = Objects.requireNonNull(l2, "l2");
        this.l3 = Objects.requireNonNull(l3, "l3");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    /** 反编译 L2/L3 统一入口 */
    public DecompileCacheService decompileCache() {
        return decompileCache;
    }

    /** 全文搜索快照 */
    public SourceSnapshotCache sourceSnapshots() {
        return snapshots;
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
        return List.of(l2Stats(), l3Stats(), indexStats(), snapshots.stats());
    }

    /** 分类清理；磁盘清理为同步 IO，调用方应在后台线程执行 */
    public void clear(CacheKind kind) {
        switch (kind) {
            case DECOMPILE_L2 -> decompileCache.clearL2();
            case DECOMPILE_L3 -> decompileCache.clearL3();
            case INDEX -> WorkspaceIndexCache.clearAll();
            case SNAPSHOTS -> snapshots.invalidateAll();
            case ALL_MANAGED -> {
                decompileCache.clearAllLevels();
                WorkspaceIndexCache.clearAll();
                snapshots.invalidateAll();
            }
        }
    }

    /** 工作区关闭：释放该工作区的搜索快照 */
    public void onWorkspaceClosed(Workspace workspace) {
        snapshots.invalidateWorkspace(workspace);
    }

    /** 关闭时只清理内存，不删除磁盘缓存 */
    @Override
    public void close() {
        decompileCache.clearL2();
        snapshots.clear();
    }
}
