package com.bingbaihanji.fxdecomplie.service.cache;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SourceSnapshotCacheTest {

    @TempDir
    Path tempDir;

    private Workspace workspace(String stamp) {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        return new Workspace("demo", tempDir.resolve("demo-" + stamp + ".jar").toFile(),
                root, true, WorkspaceIndex.EMPTY, stamp);
    }

    private static Map<String, String> source(String text) {
        return Map.of("com/example/A.class", text);
    }

    @Test
    void storesAndReturnsImmutableSnapshot() {
        SourceSnapshotCache cache = new SourceSnapshotCache();
        Workspace ws = workspace("1");
        long generation = cache.generation(ws);

        assertTrue(cache.putIfCurrent(ws, DecompilerTypeEnum.CFR, Map.of(),
                generation, source("class A {}")));

        Map<String, String> snapshot = cache.get(ws, DecompilerTypeEnum.CFR, Map.of());
        assertEquals("class A {}", snapshot.get("com/example/A.class"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put("x", "y"));
    }

    @Test
    void rejectsStaleWriteAfterInvalidation() {
        SourceSnapshotCache cache = new SourceSnapshotCache();
        Workspace ws = workspace("1");
        long generation = cache.generation(ws);
        cache.putIfCurrent(ws, DecompilerTypeEnum.CFR, Map.of(), generation,
                source("old"));

        cache.invalidateWorkspace(ws);

        assertFalse(cache.putIfCurrent(ws, DecompilerTypeEnum.CFR, Map.of(), generation,
                source("stale")));
        assertNull(cache.get(ws, DecompilerTypeEnum.CFR, Map.of()));
        assertNotEquals(generation, cache.generation(ws));
    }

    @Test
    void invalidatesOnlyTargetWorkspace() {
        SourceSnapshotCache cache = new SourceSnapshotCache();
        Workspace ws1 = workspace("1");
        Workspace ws2 = workspace("2");
        cache.putIfCurrent(ws1, DecompilerTypeEnum.CFR, Map.of(), cache.generation(ws1),
                source("one"));
        cache.putIfCurrent(ws2, DecompilerTypeEnum.CFR, Map.of(), cache.generation(ws2),
                source("two"));

        cache.invalidateWorkspace(ws1);

        assertNull(cache.get(ws1, DecompilerTypeEnum.CFR, Map.of()));
        assertEquals("two", cache.get(ws2, DecompilerTypeEnum.CFR, Map.of())
                .get("com/example/A.class"));
    }

    @Test
    void invalidateAllRejectsInFlightWrites() {
        SourceSnapshotCache cache = new SourceSnapshotCache();
        Workspace ws = workspace("1");
        long generation = cache.generation(ws);
        cache.putIfCurrent(ws, DecompilerTypeEnum.CFR, Map.of(), generation,
                source("old"));

        cache.invalidateAll();

        assertNull(cache.get(ws, DecompilerTypeEnum.CFR, Map.of()));
        assertFalse(cache.putIfCurrent(ws, DecompilerTypeEnum.CFR, Map.of(), generation,
                source("stale")));
    }

    @Test
    void evictsByWeightedBudget() {
        SourceSnapshotCache cache = new SourceSnapshotCache(200);
        Workspace ws1 = workspace("1");
        Workspace ws2 = workspace("2");
        Map<String, String> first = new LinkedHashMap<>();
        Map<String, String> second = new LinkedHashMap<>();
        for (int i = 0; i < 2; i++) {
            first.put("k" + i, "x".repeat(40));
            second.put("k" + i, "y".repeat(40));
        }
        cache.putIfCurrent(ws1, DecompilerTypeEnum.CFR, Map.of(), cache.generation(ws1), first);
        cache.putIfCurrent(ws2, DecompilerTypeEnum.CFR, Map.of(), cache.generation(ws2), second);

        assertNull(cache.get(ws1, DecompilerTypeEnum.CFR, Map.of()));
        assertNotNull(cache.get(ws2, DecompilerTypeEnum.CFR, Map.of()));
        assertEquals(1, cache.stats().entries());
    }
}
