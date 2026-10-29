package com.bingbaihanji.fxdecomplie.service.cache;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CacheKeysTest {

    @TempDir
    Path tempDir;

    private Workspace workspace(String contentStamp) {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        return new Workspace("demo", tempDir.resolve("demo.jar").toFile(), root,
                true, WorkspaceIndex.EMPTY, contentStamp);
    }

    @Test
    void sameInputsProduceSameKey() {
        Workspace ws = workspace("stamp-1");
        byte[] bytes = {1, 2, 3};
        CodeCacheKey a = CacheKeys.forClass(ws, bytes, "com/example/Foo",
                DecompilerTypeEnum.CFR, Map.of());
        CodeCacheKey b = CacheKeys.forClass(ws, bytes, "com/example/Foo",
                DecompilerTypeEnum.CFR, Map.of());
        assertEquals(a, b);
        assertEquals(a.compositeKey(), b.compositeKey());
    }

    @Test
    void classContentChangesKey() {
        Workspace ws = workspace("stamp-1");
        CodeCacheKey a = CacheKeys.forClass(ws, new byte[]{1}, "com/example/Foo",
                DecompilerTypeEnum.CFR, Map.of());
        CodeCacheKey b = CacheKeys.forClass(ws, new byte[]{2}, "com/example/Foo",
                DecompilerTypeEnum.CFR, Map.of());
        assertNotEquals(a.classFingerprint(), b.classFingerprint());
        assertNotEquals(a, b);
    }

    @Test
    void workspaceEngineOptionsAndVersionChangeKey() {
        Workspace ws = workspace("stamp-1");
        byte[] bytes = {1, 2, 3};
        CodeCacheKey base = CacheKeys.forClass(ws, bytes, "com/example/Foo",
                DecompilerTypeEnum.CFR, Map.of());

        assertNotEquals(base, CacheKeys.forClass(workspace("stamp-2"), bytes,
                "com/example/Foo", DecompilerTypeEnum.CFR, Map.of()));
        assertNotEquals(base, CacheKeys.forClass(ws, bytes, "com/example/Foo",
                DecompilerTypeEnum.VINEFLOWER, Map.of()));
        assertNotEquals(base, CacheKeys.forClass(ws, bytes, "com/example/Foo",
                DecompilerTypeEnum.CFR, Map.of("a", "1")));
        assertNotEquals(base, new CodeCacheKey(base.workspaceId(), base.classFingerprint(),
                base.internalName(), base.engine(), base.optionsHash(), base.keyVersion() + 1));
    }

    @Test
    void workspaceIdUsesAbsolutePathAndContentStamp() {
        Workspace ws = workspace("stamp-1");
        String id = CacheKeys.workspaceId(ws);
        assertEquals(ws.getSourceFile().getAbsolutePath() + "|stamp-1", id);
    }
}
