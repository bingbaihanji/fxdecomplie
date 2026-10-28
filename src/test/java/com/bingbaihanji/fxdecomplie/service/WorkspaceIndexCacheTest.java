package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.MemberIndexEntry;
import com.bingbaihanji.fxdecomplie.model.ResourceIndexEntry;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceIndexCacheTest {

    @Test
    void computeFingerprintReturnsNonNullForExistingFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("test.jar");
        Files.writeString(file, "dummy content");
        String fingerprint = WorkspaceIndexCache.computeFingerprint(file);
        assertNotNull(fingerprint);
        assertFalse(fingerprint.isBlank());
        assertEquals(16, fingerprint.length());
    }

    @Test
    void computeFingerprintReturnsNullForNonExistentFile() {
        String fingerprint = WorkspaceIndexCache.computeFingerprint(
                Path.of("/nonexistent/file.jar"));
        assertNull(fingerprint);
    }

    @Test
    void computeFingerprintIsStableForSameFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("test.jar");
        Files.writeString(file, "same content");
        String fp1 = WorkspaceIndexCache.computeFingerprint(file);
        String fp2 = WorkspaceIndexCache.computeFingerprint(file);
        assertEquals(fp1, fp2);
    }

    @Test
    void computeFingerprintChangesWhenMtimeChanges(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("test.jar");
        Files.writeString(file, "same content");
        String fp1 = WorkspaceIndexCache.computeFingerprint(file);
        Files.setLastModifiedTime(file, FileTime.fromMillis(
                Files.getLastModifiedTime(file).toMillis() + 5_000L));
        String fp2 = WorkspaceIndexCache.computeFingerprint(file);
        assertNotEquals(fp1, fp2);
    }

    @Test
    void computeFingerprintChangesWhenSizeChanges(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("test.jar");
        Files.writeString(file, "a");
        String fp1 = WorkspaceIndexCache.computeFingerprint(file);
        Files.writeString(file, "a-longer-content");
        String fp2 = WorkspaceIndexCache.computeFingerprint(file);
        assertNotEquals(fp1, fp2);
    }

    @Test
    void saveAndLoadRoundTrip(@TempDir Path tmp) throws IOException {
        // Create a test index with some class entries
        MemberIndexEntry method = new MemberIndexEntry("com/example/Foo.class", "doStuff", "()V");
        MemberIndexEntry field = new MemberIndexEntry("com/example/Foo.class", "count", "I");
        ClassIndexEntry classEntry = new ClassIndexEntry(
                "com/example/Foo.class", "com/example/Foo", "Foo",
                null, List.of(method), List.of(field), "java/lang/Object", List.of());
        WorkspaceIndex index = WorkspaceIndex.buildFromCache(List.of(classEntry));

        // Save
        String fingerprint = "test-fingerprint-12345";
        WorkspaceIndexCache.save(fingerprint, index);

        // Load
        WorkspaceIndexCache.CachedIndex loaded = WorkspaceIndexCache.load(fingerprint);
        assertNotNull(loaded);
        WorkspaceIndex loadedIndex = loaded.toWorkspaceIndex(null);
        assertEquals(1, loadedIndex.classes().size());

        ClassIndexEntry loadedClass = loadedIndex.classes().get(0);
        assertEquals("com/example/Foo", loadedClass.internalName());
        assertEquals("Foo", loadedClass.simpleName());
        assertEquals("java/lang/Object", loadedClass.superName());
        assertEquals(1, loadedClass.methods().size());
        assertEquals("doStuff", loadedClass.methods().get(0).name());
        assertEquals(1, loadedClass.fields().size());
        assertEquals("count", loadedClass.fields().get(0).name());
    }

    @Test
    void loadReturnsNullForMissingFingerprint() {
        WorkspaceIndexCache.CachedIndex loaded = WorkspaceIndexCache.load("nonexistent-fingerprint");
        assertNull(loaded);
    }

    @Test
    void loadReturnsNullForNullFingerprint() {
        WorkspaceIndexCache.CachedIndex loaded = WorkspaceIndexCache.load(null);
        assertNull(loaded);
    }

    @Test
    void clearAllRemovesCacheFiles(@TempDir Path tmp) throws IOException {
        // Create a test index
        ClassIndexEntry classEntry = new ClassIndexEntry(
                "com/example/Bar.class", "com/example/Bar", "Bar",
                null, List.of(), List.of(), null, List.of());
        WorkspaceIndex index = WorkspaceIndex.buildFromCache(List.of(classEntry));

        // Save
        String fingerprint = "clear-test-fingerprint";
        WorkspaceIndexCache.save(fingerprint, index);

        // Verify it exists
        WorkspaceIndexCache.CachedIndex loaded = WorkspaceIndexCache.load(fingerprint);
        assertNotNull(loaded);

        // Clear
        WorkspaceIndexCache.clearAll();

        // Verify it's gone
        WorkspaceIndexCache.CachedIndex afterClear = WorkspaceIndexCache.load(fingerprint);
        assertNull(afterClear);
    }

    @Test
    void buildFromCacheCreatesValidIndex() {
        ClassIndexEntry entry = new ClassIndexEntry(
                "com/example/Test.class", "com/example/Test", "Test",
                null, List.of(), List.of(), "java/lang/Object", List.of("java/io/Serializable"));
        WorkspaceIndex index = WorkspaceIndex.buildFromCache(List.of(entry));

        assertNotNull(index);
        assertEquals(1, index.classes().size());
        assertEquals("com/example/Test", index.classes().get(0).internalName());
        assertNotNull(index.findClass("com/example/Test"));
        assertEquals("java/lang/Object", index.classes().get(0).superName());
        assertEquals(List.of("java/io/Serializable"), index.classes().get(0).interfaces());
    }

    @Test
    void buildPhaseFiltersNodesByPhase() {
        FileTreeNode node1 = new FileTreeNode("Main.class", "com/app/Main.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        node1.setPhase(1);
        node1.setCachedBytes(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        FileTreeNode node2 = new FileTreeNode("Lib.class", "com/lib/Lib.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        node2.setPhase(2);
        node2.setCachedBytes(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new FileTreeModel(node1));
        root.getChildren().add(new FileTreeModel(node2));

        WorkspaceIndex phase1 = WorkspaceIndex.buildPhase(root, 1, null);
        WorkspaceIndex phase2 = WorkspaceIndex.buildPhase(root, 2, null);

        assertEquals(1, phase1.classes().size());
        assertEquals("com/app/Main.class", phase1.classes().getFirst().fullPath());
        assertEquals(1, phase2.classes().size());
        assertEquals("com/lib/Lib.class", phase2.classes().getFirst().fullPath());
    }

    @Test
    void mergeCombinesIndexes() {
        ClassIndexEntry entry1 = new ClassIndexEntry(
                "com/a/A.class", "com/a/A", "A",
                null, List.of(), List.of(), null, List.of());
        ClassIndexEntry entry2 = new ClassIndexEntry(
                "com/b/B.class", "com/b/B", "B",
                null, List.of(), List.of(), null, List.of());

        WorkspaceIndex index1 = WorkspaceIndex.buildFromCache(List.of(entry1));
        WorkspaceIndex index2 = WorkspaceIndex.buildFromCache(List.of(entry2));

        WorkspaceIndex merged = WorkspaceIndex.merge(index1, index2);

        assertEquals(2, merged.classes().size());
        assertNotNull(merged.findClass("com/a/A"));
        assertNotNull(merged.findClass("com/b/B"));
    }

    @Test
    void cacheRestoreKeepsClassBytesReadableFromWorkspace() {
        byte[] classBytes = new byte[]{1, 2, 3, 4};
        FileTreeNode node = new FileTreeNode("Foo.class", "com/example/Foo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        node.setByteLoader(() -> classBytes);
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new FileTreeModel(node));
        Workspace workspace = new Workspace("demo", Path.of(".").toFile(), root, false);

        ClassIndexEntry cached = new ClassIndexEntry(
                "com/example/Foo.class", "com/example/Foo", "Foo",
                null, List.of(), List.of(), "java/lang/Object", List.of());
        WorkspaceIndexCache.CachedIndex cachedData = new WorkspaceIndexCache.CachedIndex();
        cachedData.classes = List.of(WorkspaceIndexCache.CachedClassEntry.from(cached));
        WorkspaceIndex restored = cachedData.toWorkspaceIndex(workspace);

        assertArrayEquals(classBytes, restored.getClassBytes("com/example/Foo"));
    }

    @Test
    void cacheRoundTripKeepsResourceEntries() {
        ClassIndexEntry classEntry = new ClassIndexEntry(
                "com/example/Foo.class", "com/example/Foo", "Foo",
                null, List.of(), List.of(), null, List.of());
        WorkspaceIndex index = WorkspaceIndex.merge(
                WorkspaceIndex.buildFromCache(List.of(classEntry)),
                WorkspaceIndex.buildFromCache(List.of()));
        WorkspaceIndex withResource = WorkspaceIndex.buildFromCache(
                index.classes(),
                List.of(new ResourceIndexEntry("application.properties",
                        "server.port=8080".getBytes(), true)));

        String fingerprint = "resource-cache-test";
        WorkspaceIndexCache.save(fingerprint, withResource);
        WorkspaceIndexCache.CachedIndex loaded = WorkspaceIndexCache.load(fingerprint);
        assertNotNull(loaded);

        WorkspaceIndex restored = loaded.toWorkspaceIndex(null);
        assertEquals(1, restored.resources().size());
        assertArrayEquals("server.port=8080".getBytes(),
                restored.resourceBytesByPath().get("application.properties"));
    }

    @Test
    void saveWritesGzipCacheFile() throws IOException {
        ClassIndexEntry entry = new ClassIndexEntry(
                "com/example/Gz.class", "com/example/Gz", "Gz",
                null, List.of(), List.of(), null, List.of());
        WorkspaceIndex index = WorkspaceIndex.buildFromCache(List.of(entry));
        String fingerprint = "gzip-format-test";
        Path cacheFile = AppPaths.cacheDir().resolve("index-cache")
                .resolve(fingerprint + ".idx.gz");
        try {
            WorkspaceIndexCache.save(fingerprint, index);
            assertTrue(Files.exists(cacheFile));
        } finally {
            Files.deleteIfExists(cacheFile);
        }
    }

    @Test
    void loadIgnoresLegacyJsonCache() throws IOException {
        String fingerprint = "legacy-json-test";
        Path cacheDir = AppPaths.cacheDir().resolve("index-cache");
        Files.createDirectories(cacheDir);
        Path legacy = cacheDir.resolve(fingerprint + ".idx.json");
        Path current = cacheDir.resolve(fingerprint + ".idx.gz");
        Files.writeString(legacy, """
                {"classes":[{"fullPath":"com/example/L.class","internalName":"com/example/L",
                "simpleName":"L","interfaces":[],"methods":[],"fields":[]}]}
                """);
        try {
            assertNull(WorkspaceIndexCache.load(fingerprint));
        } finally {
            Files.deleteIfExists(legacy);
            Files.deleteIfExists(current);
        }
    }

    @Test
    void loadReturnsNullForCorruptCacheFile() throws IOException {
        String fingerprint = "corrupt-cache-test";
        Path cacheDir = AppPaths.cacheDir().resolve("index-cache");
        Files.createDirectories(cacheDir);
        Path cacheFile = cacheDir.resolve(fingerprint + ".idx.gz");
        Files.writeString(cacheFile, "not a gzip file");
        try {
            assertNull(WorkspaceIndexCache.load(fingerprint));
        } finally {
            Files.deleteIfExists(cacheFile);
        }
    }

    @Test
    void loadRejectsUnsupportedFormatVersion() throws IOException {
        String fingerprint = "version-test";
        Path cacheDir = AppPaths.cacheDir().resolve("index-cache");
        Files.createDirectories(cacheDir);
        Path cacheFile = cacheDir.resolve(fingerprint + ".idx.gz");
        try (DataOutputStream out = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(cacheFile)))) {
            out.writeInt(0x46584944);
            out.writeInt(999);
        }
        try {
            assertNull(WorkspaceIndexCache.load(fingerprint));
        } finally {
            Files.deleteIfExists(cacheFile);
        }
    }

    @Test
    void clearAllRemovesLegacyAndCurrentCacheFiles() throws IOException {
        Path cacheDir = AppPaths.cacheDir().resolve("index-cache");
        Files.createDirectories(cacheDir);
        Path legacy = cacheDir.resolve("clear-both-test.idx.json");
        Path current = cacheDir.resolve("clear-both-test.idx.gz");
        Files.writeString(legacy, "{}");
        Files.writeString(current, "{}");

        WorkspaceIndexCache.clearAll();

        assertFalse(Files.exists(legacy));
        assertFalse(Files.exists(current));
    }

    @Test
    void cacheRestoreBackfillsFileTreeNode() {
        byte[] classBytes = {1, 2, 3, 4};
        FileTreeNode node = new FileTreeNode("Foo.class", "com/example/Foo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        node.setByteLoader(() -> classBytes);
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new FileTreeModel(node));
        Workspace workspace = new Workspace("demo", Path.of(".").toFile(), root, false);

        ClassIndexEntry entry = new ClassIndexEntry(
                "com/example/Foo.class", "com/example/Foo", "Foo",
                null, List.of(), List.of(), "java/lang/Object", List.of());
        WorkspaceIndexCache.CachedIndex cachedData = new WorkspaceIndexCache.CachedIndex();
        cachedData.classes = List.of(WorkspaceIndexCache.CachedClassEntry.from(entry));
        cachedData.resources = List.of();

        WorkspaceIndex restored = cachedData.toWorkspaceIndex(workspace);

        assertSame(node, restored.classes().getFirst().node());
        assertArrayEquals(classBytes, restored.getClassBytes("com/example/Foo"));
    }
}
