package com.bingbaihanji.fxdecomplie.io;

import com.bingbaihanji.fxdecomplie.decompiler.ClassBytecodeProvider;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.model.ExportResult;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.ExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

//import com.bingbaihanji.fxdecomplie.decompiler.BytecodeCache;

class ExportServiceTest {

    @TempDir
    Path tempDir;
//
//    @AfterEach
//    void clearGlobalBytecodeCache() {
//        BytecodeCache.clear();
//    }

    private static ExportConfig config(Path outputPath, ExportConfig.Format format,
                                       ExportConfig.ConflictPolicy conflictPolicy,
                                       boolean exportResources) {
        return new ExportConfig(outputPath, DecompilerTypeEnum.VINEFLOWER, format,
                conflictPolicy, exportResources);
    }

    @SafeVarargs
    private static FileTreeModel root(FileTreeModel... children) {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        java.util.Collections.addAll(root.getChildren(), children);
        return root;
    }

    private static FileTreeModel resource(String path, String content) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        FileTreeNode node = new FileTreeNode(name, path, FileTreeNode.NodeTypeEnum.RESOURCE);
        node.setCachedBytes(content.getBytes(StandardCharsets.UTF_8));
        return new FileTreeModel(node);
    }

    private static FileTreeNode classNode(String simpleName) {
        return new FileTreeNode(simpleName + ".class", "com/example/" + simpleName + ".class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
    }

    private static FileTreeNode resourceNode(String path, String content) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        FileTreeNode node = new FileTreeNode(name, path, FileTreeNode.NodeTypeEnum.RESOURCE);
        node.setCachedBytes(content.getBytes(StandardCharsets.UTF_8));
        return node;
    }

    private static void awaitFile(Path file, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(file)) {
                return;
            }
            Thread.sleep(20);
        }
    }

    @Test
    void exportsResourcesToDirectoryWhenEnabled() throws Exception {
        FileTreeModel root = root(resource("config/app.properties", "enabled=true"));
        Path outputDir = tempDir.resolve("out");

        ExportResult result = ExportService.exportAll(root, config(outputDir,
                ExportConfig.Format.DIR, ExportConfig.ConflictPolicy.OVERWRITE, true), WorkspaceIndex.build(root), null);

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.successCount());
        assertFalse(result.hasErrors());
        assertEquals("enabled=true", Files.readString(outputDir.resolve("config/app.properties")));
    }

    @Test
    void skipsResourcesWhenDisabled() throws Exception {
        FileTreeModel root = root(resource("config/app.properties", "enabled=true"));

        ExportResult result = ExportService.exportAll(root, config(tempDir.resolve("out"),
                ExportConfig.Format.DIR, ExportConfig.ConflictPolicy.OVERWRITE, false), WorkspaceIndex.build(root), null);

        assertEquals(0, result.totalFiles());
        assertEquals(0, result.successCount());
        assertFalse(Files.exists(tempDir.resolve("out/config/app.properties")));
    }

    @Test
    void renamesDirectoryConflicts() throws Exception {
        FileTreeModel root = root(
                resource("config/app.properties", "first"),
                resource("config/app.properties", "second"));
        Path outputDir = tempDir.resolve("out");

        ExportResult result = ExportService.exportAll(root, config(outputDir,
                ExportConfig.Format.DIR, ExportConfig.ConflictPolicy.RENAME, true), WorkspaceIndex.build(root), null);

        assertEquals(2, result.totalFiles());
        assertEquals(2, result.successCount());
        assertEquals("first", Files.readString(outputDir.resolve("config/app.properties")));
        assertEquals("second", Files.readString(outputDir.resolve("config/app-1.properties")));
    }

    @Test
    void exportsResourcesToZip() throws Exception {
        FileTreeModel root = root(resource("config/app.properties", "enabled=true"));
        Path zipPath = tempDir.resolve("out.zip");

        ExportResult result = ExportService.exportAll(root, config(zipPath,
                ExportConfig.Format.ZIP, ExportConfig.ConflictPolicy.OVERWRITE, true), WorkspaceIndex.build(root), null);

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.successCount());
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            assertNotNull(zip.getEntry("config/app.properties"));
        }
    }

    @Test
    void rejectsUnsafeRelativePaths() throws Exception {
        FileTreeModel root = root(resource("../evil.properties", "bad"));
        Path outputDir = tempDir.resolve("out");

        ExportResult result = ExportService.exportAll(root, config(outputDir,
                ExportConfig.Format.DIR, ExportConfig.ConflictPolicy.OVERWRITE, true), WorkspaceIndex.build(root), null);

        assertEquals(1, result.totalFiles());
        assertEquals(0, result.successCount());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("不安全"));
        assertFalse(Files.exists(tempDir.resolve("evil.properties")));
    }

//    @Test
//    void exportDoesNotReadClassBytesFromGlobalCache() throws Exception {
//        BytecodeCache.put("com/example/Missing", new byte[]{1, 2, 3});
//        FileTreeNode missingClass = new FileTreeNode("Missing.class", "com/example/Missing.class",
//                FileTreeNode.NodeTypeEnum.CLASS_FILE);
//        FileTreeModel root = root(new TreeItem<>(missingClass));
//
//        ExportResult result = ExportService.exportAll(root, config(tempDir.resolve("out"),
//                ExportConfig.Format.DIR, ExportConfig.ConflictPolicy.OVERWRITE, false),
//                WorkspaceIndex.build(root), null);
//
//        assertEquals(1, result.totalFiles());
//        assertEquals(0, result.successCount());
//        assertTrue(result.errors().getFirst().contains("未找到类字节码"));
//        assertFalse(Files.exists(tempDir.resolve("out/com/example/Missing.java")));
//    }

    @Test
    void exportHandlesSkipConflictPolicy() throws Exception {
        // Pre-create a file to simulate a conflict
        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(outputDir.resolve("config"));
        Files.writeString(outputDir.resolve("config/app.properties"), "original");

        FileTreeModel root = root(resource("config/app.properties", "new-content"));

        ExportResult result = ExportService.exportAll(root, config(outputDir,
                        ExportConfig.Format.DIR, ExportConfig.ConflictPolicy.SKIP, true),
                WorkspaceIndex.build(root), null);

        assertEquals(1, result.totalFiles());
        assertEquals(0, result.successCount());
        assertFalse(result.hasErrors());
        assertEquals("original", Files.readString(outputDir.resolve("config/app.properties")));
    }

    @Test
    void providerExportMatchesIndexExport() throws Exception {
        byte[] classBytes = compileClass("com.example", "Exported");

        // 索引路径：节点自带字节
        FileTreeNode indexNode = new FileTreeNode("Exported.class",
                "com/example/Exported.class", FileTreeNode.NodeTypeEnum.CLASS_FILE);
        indexNode.setCachedBytes(classBytes);
        FileTreeModel indexRoot = root(new FileTreeModel(indexNode));
        WorkspaceIndex index = WorkspaceIndex.build(indexRoot);

        // provider 路径：节点没有任何字节来源，必须由 provider 提供
        FileTreeNode providerNode = new FileTreeNode("Exported.class",
                "com/example/Exported.class", FileTreeNode.NodeTypeEnum.CLASS_FILE);
        java.util.concurrent.atomic.AtomicInteger lookups =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.Map<String, byte[]> classBytesByName =
                java.util.Map.of("com/example/Exported", classBytes);
        ClassBytecodeProvider provider = name -> {
            byte[] bytes = classBytesByName.get(name);
            if (bytes != null) {
                lookups.incrementAndGet();
            }
            return bytes;
        };

        Path indexOut = tempDir.resolve("index-out");
        Path providerOut = tempDir.resolve("provider-out");
        ExportResult indexResult = ExportService.exportAll(indexRoot, config(indexOut,
                ExportConfig.Format.DIR, ExportConfig.ConflictPolicy.OVERWRITE, false),
                index, null);
        ExportResult providerResult = ExportService.exportAll(List.of(providerNode),
                config(providerOut, ExportConfig.Format.DIR,
                        ExportConfig.ConflictPolicy.OVERWRITE, false),
                provider, null, null, null);

        assertEquals(1, indexResult.successCount());
        assertEquals(1, providerResult.successCount());
        assertTrue(lookups.get() > 0, "provider 应被调用以提供类字节");
        try (var files = Files.walk(indexOut)) {
            List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
            assertEquals(1, javaFiles.size());
            Path expectedFile = javaFiles.getFirst();
            Path actualFile = providerOut.resolve(indexOut.relativize(expectedFile));
            assertEquals(Files.readString(expectedFile), Files.readString(actualFile));
        }
    }

    @Test
    void incrementalWriteBeforeAllDecompiled() throws Exception {
        byte[] aBytes = compileClass("com.example", "IncrementalA");
        byte[] bBytes = compileClass("com.example", "IncrementalB");
        FileTreeNode a = classNode("IncrementalA");
        FileTreeNode b = classNode("IncrementalB");
        CountDownLatch releaseB = new CountDownLatch(1);
        ClassBytecodeProvider provider = name -> {
            if ("com/example/IncrementalA".equals(name)) {
                return aBytes;
            }
            if ("com/example/IncrementalB".equals(name)) {
                try {
                    releaseB.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                return bBytes;
            }
            return null;
        };

        Path out = tempDir.resolve("incremental-out");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread exportThread = new Thread(() -> {
            try {
                ExportService.exportAll(List.of(a, b), config(out,
                                ExportConfig.Format.DIR, ExportConfig.ConflictPolicy.OVERWRITE, false),
                        provider, null, null, null);
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "incremental-export-test");
        exportThread.start();

        Path aFile = out.resolve("com/example/IncrementalA.java");
        Path bFile = out.resolve("com/example/IncrementalB.java");
        try {
            awaitFile(aFile, 10_000);
            assertTrue(Files.exists(aFile), "A should be written before B finishes");
            assertFalse(Files.exists(bFile), "B should still be blocked");
        } finally {
            releaseB.countDown();
            exportThread.join(30_000);
        }

        assertNull(failure.get(), "export failed: " + failure.get());
        assertTrue(Files.exists(bFile));
    }

    @Test
    void zipEntriesFollowInputOrder() throws Exception {
        FileTreeNode first = resourceNode("z/last.txt", "last");
        FileTreeNode second = resourceNode("a/first.txt", "first");
        FileTreeNode third = resourceNode("m/mid.txt", "mid");
        Path zip = tempDir.resolve("order.zip");
        ClassBytecodeProvider provider = name -> null;

        ExportResult result = ExportService.exportAll(List.of(first, second, third),
                config(zip, ExportConfig.Format.ZIP,
                        ExportConfig.ConflictPolicy.OVERWRITE, true),
                provider, null, null, null);

        assertEquals(3, result.successCount());
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            var entries = zipFile.entries();
            var firstEntry = entries.nextElement();
            assertEquals("z/last.txt", firstEntry.getName());
            assertArrayEquals("last".getBytes(StandardCharsets.UTF_8),
                    zipFile.getInputStream(firstEntry).readAllBytes());
            var secondEntry = entries.nextElement();
            assertEquals("a/first.txt", secondEntry.getName());
            assertArrayEquals("first".getBytes(StandardCharsets.UTF_8),
                    zipFile.getInputStream(secondEntry).readAllBytes());
            var thirdEntry = entries.nextElement();
            assertEquals("m/mid.txt", thirdEntry.getName());
            assertArrayEquals("mid".getBytes(StandardCharsets.UTF_8),
                    zipFile.getInputStream(thirdEntry).readAllBytes());
            assertFalse(entries.hasMoreElements());
        }
    }

    @Test
    void zipSkipsUnsafePathAndKeepsWriting() throws Exception {
        FileTreeNode unsafe = resourceNode("../evil.properties", "bad");
        FileTreeNode ok = resourceNode("ok.txt", "ok");
        Path zip = tempDir.resolve("unsafe-continue.zip");
        ClassBytecodeProvider provider = name -> null;

        ExportResult result = ExportService.exportAll(List.of(unsafe, ok),
                config(zip, ExportConfig.Format.ZIP,
                        ExportConfig.ConflictPolicy.OVERWRITE, true),
                provider, null, null, null);

        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("不安全"));
        assertEquals(1, result.successCount());
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            var entries = zipFile.entries();
            var entry = entries.nextElement();
            assertEquals("ok.txt", entry.getName());
            assertEquals("ok", new String(zipFile.getInputStream(entry).readAllBytes(),
                    StandardCharsets.UTF_8));
            assertFalse(entries.hasMoreElements());
        }
    }

    @Test
    void zipEntriesFollowInputOrderBeyondWindow() throws Exception {
        int count = 12;
        List<FileTreeNode> nodes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String path = "p" + i + ".txt";
            names.add(path);
            nodes.add(resourceNode(path, "v" + i));
        }
        Path zip = tempDir.resolve("order-many.zip");
        ClassBytecodeProvider provider = name -> null;

        ExportResult result = ExportService.exportAll(nodes,
                config(zip, ExportConfig.Format.ZIP,
                        ExportConfig.ConflictPolicy.OVERWRITE, true),
                provider, null, null, null);

        assertEquals(count, result.successCount());
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            var entries = zipFile.entries();
            for (int i = 0; i < count; i++) {
                var entry = entries.nextElement();
                assertEquals(names.get(i), entry.getName());
                assertEquals("v" + i, new String(zipFile.getInputStream(entry).readAllBytes(),
                        StandardCharsets.UTF_8));
            }
            assertFalse(entries.hasMoreElements());
        }
    }

    @Test
    void cancellationStopsExportAndRecordsMarker() throws Exception {
        byte[] bytes = compileClass("com.example", "CancelA");
        FileTreeNode a = classNode("CancelA");
        FileTreeNode b = classNode("CancelB");
        java.util.concurrent.atomic.AtomicBoolean canceled =
                new java.util.concurrent.atomic.AtomicBoolean();
        ClassBytecodeProvider provider = name -> {
            if ("com/example/CancelA".equals(name)) {
                canceled.set(true);
                return bytes;
            }
            return null;
        };

        ExportResult result = ExportService.exportAll(List.of(a, b),
                config(tempDir.resolve("cancel-out"), ExportConfig.Format.DIR,
                        ExportConfig.ConflictPolicy.OVERWRITE, false),
                provider, null, null, canceled::get);

        assertTrue(result.errors().stream().anyMatch(e -> e.contains("导出已取消")),
                String.valueOf(result.errors()));
    }

    private byte[] compileClass(String packageName, String className) throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("src"));
        Path outputDir = Files.createDirectories(tempDir.resolve("classes"));
        Path sourceFile = sourceDir.resolve(className + ".java");
        Files.writeString(sourceFile, """
                package %s;
                public class %s {
                    private int value;
                    public int getValue() { return value; }
                }
                """.formatted(packageName, className), StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests require a JDK compiler");
        int exit = compiler.run(null, null, null,
                "--release", "17",
                "-d", outputDir.toString(),
                sourceFile.toString());
        assertEquals(0, exit, "test class should compile");
        return Files.readAllBytes(outputDir.resolve(packageName.replace('.', '/'))
                .resolve(className + ".class"));
    }
}
