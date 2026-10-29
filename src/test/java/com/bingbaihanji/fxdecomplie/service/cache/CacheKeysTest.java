package com.bingbaihanji.fxdecomplie.service.cache;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxProjectRenameSynchronizer;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.DecompilerOptions;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertNotEquals(a.compositeKey(), b.compositeKey());
    }

    @Test
    void workspaceEngineOptionsAndVersionChangeKey() {
        Workspace ws = workspace("stamp-1");
        byte[] bytes = {1, 2, 3};
        CodeCacheKey base = CacheKeys.forClass(ws, bytes, "com/example/Foo",
                DecompilerTypeEnum.CFR, Map.of());

        CodeCacheKey otherWorkspace = CacheKeys.forClass(workspace("stamp-2"), bytes,
                "com/example/Foo", DecompilerTypeEnum.CFR, Map.of());
        assertNotEquals(base, otherWorkspace);
        assertNotEquals(base.compositeKey(), otherWorkspace.compositeKey());

        CodeCacheKey otherEngine = CacheKeys.forClass(ws, bytes, "com/example/Foo",
                DecompilerTypeEnum.VINEFLOWER, Map.of());
        assertNotEquals(base, otherEngine);
        assertNotEquals(base.compositeKey(), otherEngine.compositeKey());

        CodeCacheKey otherOptions = CacheKeys.forClass(ws, bytes, "com/example/Foo",
                DecompilerTypeEnum.CFR, Map.of("a", "1"));
        assertNotEquals(base, otherOptions);
        assertNotEquals(base.compositeKey(), otherOptions.compositeKey());

        CodeCacheKey otherVersion = new CodeCacheKey(base.workspaceId(), base.classFingerprint(),
                base.internalName(), base.engine(), base.optionsHash(), base.keyVersion() + 1);
        assertNotEquals(base, otherVersion);
        assertNotEquals(base.compositeKey(), otherVersion.compositeKey());
    }

    @Test
    void keyPrefixesAreConsistent() {
        CodeCacheKey key = CacheKeys.forClass(workspace("stamp-1"), new byte[]{1},
                "com/example/Foo", DecompilerTypeEnum.CFR, Map.of());

        assertEquals(CacheKeys.KEY_VERSION, key.keyVersion());
        assertTrue(key.compositeKey().startsWith(key.invalidationPrefix()));
        assertTrue(key.compositeKey().startsWith(key.workspacePrefix()));
        assertTrue(key.invalidationPrefix().startsWith(key.workspacePrefix()));
    }

    @Test
    void jadxDeobfuscationAppendsRenameStateVersion() {
        String enabled = CacheKeys.optionsHash(DecompilerTypeEnum.JADX,
                Map.of("deobfuscationOn", "true"));
        assertTrue(enabled.endsWith("_" + JadxProjectRenameSynchronizer.CACHE_STATE_VERSION));

        String disabled = CacheKeys.optionsHash(DecompilerTypeEnum.JADX,
                Map.of("deobfuscationOn", "false"));
        assertFalse(disabled.endsWith("_" + JadxProjectRenameSynchronizer.CACHE_STATE_VERSION));

        String upperCase = CacheKeys.optionsHash(DecompilerTypeEnum.JADX,
                Map.of("deobfuscationOn", "TRUE"));
        assertTrue(upperCase.endsWith("_" + JadxProjectRenameSynchronizer.CACHE_STATE_VERSION));
    }

    @Test
    void nullOptionsHashUsesEmptyOptions() {
        assertEquals(DecompilerOptions.hash(Map.of()),
                CacheKeys.optionsHash(DecompilerTypeEnum.CFR, null));
    }

    @Test
    void classFingerprintKnownVectorAndSentinels() {
        assertEquals("ba7816bf8f01cfea",
                CacheKeys.classFingerprint("abc".getBytes(StandardCharsets.UTF_8)));
        assertEquals("0", CacheKeys.classFingerprint(null));
        assertEquals("0", CacheKeys.classFingerprint(new byte[0]));
    }

    @Test
    void workspaceIdUsesAbsolutePathAndContentStamp() {
        Workspace ws = workspace("stamp-1");
        String id = CacheKeys.workspaceId(ws);
        assertEquals(ws.getSourceFile().getAbsolutePath() + "|stamp-1", id);
    }
}
