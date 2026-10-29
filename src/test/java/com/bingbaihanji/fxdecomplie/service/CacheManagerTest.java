package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.cache.CacheKind;
import com.bingbaihanji.fxdecomplie.service.cache.CacheManager;
import com.bingbaihanji.fxdecomplie.service.cache.SourceSnapshotCache;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.DecompileCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheManagerTest {

    @TempDir
    Path tempDir;

    private DecompileCache l2;
    private DiskCodeCache l3;
    private CacheManager manager;

    @BeforeEach
    void setUp() {
        WorkspaceIndexCache.setCacheDirectoryForTesting(tempDir.resolve("index"));
        l2 = new DecompileCache();
        l3 = new DiskCodeCache(tempDir.resolve("decompile"), CacheLimits.L3_BYTES);
        manager = new CacheManager(new DecompileCacheService(l2, l3, Runnable::run), l2, l3,
                new SourceSnapshotCache());
    }

    @AfterEach
    void tearDown() {
        WorkspaceIndexCache.setCacheDirectoryForTesting(null);
        WorkspaceIndexCache.setQuotaForTesting(CacheLimits.INDEX_BYTES);
    }

    private static CodeCacheKey key() {
        return new CodeCacheKey("w", "fp", "com/example/A",
                DecompilerTypeEnum.CFR, "default", 1);
    }

    @Test
    void statsIncludeL2L3AndIndex() {
        manager.decompileCache().save(key(), "source");
        assertEquals(1, manager.l2Stats().entries());
        assertTrue(manager.l2Stats().bytes() > 0);
        assertTrue(manager.l3Stats().bytes() > 0);
        assertEquals(4, manager.stats().size());
    }

    @Test
    void clearsEachKindAndAllManaged() {
        manager.decompileCache().save(key(), "source");
        manager.clear(CacheKind.DECOMPILE_L2);
        assertNull(l2.load(key()).orElse(null));
        assertTrue(manager.l3Stats().bytes() > 0);

        manager.clear(CacheKind.DECOMPILE_L3);
        assertEquals(0, manager.l3Stats().bytes());

        manager.clear(CacheKind.INDEX);
        assertEquals(0, manager.indexStats().bytes());

        manager.decompileCache().save(key(), "source");
        manager.clear(CacheKind.ALL_MANAGED);
        assertNull(l2.load(key()).orElse(null));
        assertEquals(0, manager.l3Stats().bytes());
    }

    @Test
    void closeClearsMemoryOnly() {
        manager.decompileCache().save(key(), "source");
        manager.close();
        assertNull(l2.load(key()).orElse(null));
        assertTrue(manager.l3Stats().bytes() > 0);
    }
}
