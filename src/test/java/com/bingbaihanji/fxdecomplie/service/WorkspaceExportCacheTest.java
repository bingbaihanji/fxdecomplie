package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.cache.CacheKeys;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.DecompileCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceExportCacheTest {

    @TempDir
    Path tempDir;

    private Workspace workspace() {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        return new Workspace("demo", tempDir.resolve("demo.jar").toFile(),
                root, true, WorkspaceIndex.EMPTY, "stamp");
    }

    private FileTreeNode classNode() {
        return new FileTreeNode("Foo.class", "com/example/Foo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
    }

    @Test
    void hitReturnsCachedSourceWithoutWriting() {
        Workspace workspace = workspace();
        byte[] bytes = {1, 2, 3};
        DecompileCache l2 = new DecompileCache();
        DiskCodeCache l3 = new DiskCodeCache(tempDir.resolve("decompile"), CacheLimits.L3_BYTES);
        DecompileCacheService cache = new DecompileCacheService(l2, l3, Runnable::run);
        CodeCacheKey key = CacheKeys.forClass(workspace, bytes, "com/example/Foo",
                DecompilerTypeEnum.CFR, Map.of());
        cache.save(key, "class Foo {}");

        WorkspaceExportCache exportCache = new WorkspaceExportCache(workspace, cache);

        assertEquals("class Foo {}", exportCache.load(classNode(), bytes,
                DecompilerTypeEnum.CFR, Map.of()));
    }

    @Test
    void missReturnsNullAndDoesNotWriteBack() {
        Workspace workspace = workspace();
        byte[] bytes = {4, 5, 6};
        DecompileCache l2 = new DecompileCache();
        DiskCodeCache l3 = new DiskCodeCache(tempDir.resolve("decompile"), CacheLimits.L3_BYTES);
        DecompileCacheService cache = new DecompileCacheService(l2, l3, Runnable::run);
        WorkspaceExportCache exportCache = new WorkspaceExportCache(workspace, cache);

        assertNull(exportCache.load(classNode(), bytes, DecompilerTypeEnum.CFR, Map.of()));

        CodeCacheKey key = CacheKeys.forClass(workspace, bytes, "com/example/Foo",
                DecompilerTypeEnum.CFR, Map.of());
        assertNull(cache.load(key).orElse(null));
    }

    @Test
    void noneNeverHits() {
        assertNull(ExportSourceCache.none().load(classNode(), new byte[]{1},
                DecompilerTypeEnum.CFR, Map.of()));
    }
}
