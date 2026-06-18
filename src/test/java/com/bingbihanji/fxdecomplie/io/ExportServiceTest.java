package com.bingbihanji.fxdecomplie.io;

import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbihanji.fxdecomplie.model.ExportConfig;
import com.bingbihanji.fxdecomplie.model.ExportResult;
import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import com.bingbihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbihanji.fxdecomplie.service.ExportService;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsResourcesToDirectoryWhenEnabled() throws Exception {
        TreeItem<FileTreeNode> root = root(resource("config/app.properties", "enabled=true"));
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
        TreeItem<FileTreeNode> root = root(resource("config/app.properties", "enabled=true"));

        ExportResult result = ExportService.exportAll(root, config(tempDir.resolve("out"),
                ExportConfig.Format.DIR, ExportConfig.ConflictPolicy.OVERWRITE, false), WorkspaceIndex.build(root), null);

        assertEquals(0, result.totalFiles());
        assertEquals(0, result.successCount());
        assertFalse(Files.exists(tempDir.resolve("out/config/app.properties")));
    }

    @Test
    void renamesDirectoryConflicts() throws Exception {
        TreeItem<FileTreeNode> root = root(
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
        TreeItem<FileTreeNode> root = root(resource("config/app.properties", "enabled=true"));
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
        TreeItem<FileTreeNode> root = root(resource("../evil.properties", "bad"));
        Path outputDir = tempDir.resolve("out");

        ExportResult result = ExportService.exportAll(root, config(outputDir,
                ExportConfig.Format.DIR, ExportConfig.ConflictPolicy.OVERWRITE, true), WorkspaceIndex.build(root), null);

        assertEquals(1, result.totalFiles());
        assertEquals(0, result.successCount());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("unsafe"));
        assertFalse(Files.exists(tempDir.resolve("evil.properties")));
    }

    private static ExportConfig config(Path outputPath, ExportConfig.Format format,
                                       ExportConfig.ConflictPolicy conflictPolicy,
                                       boolean exportResources) {
        return new ExportConfig(outputPath, DecompilerTypeEnum.VINEFLOWER, format,
                conflictPolicy, exportResources);
    }

    @SafeVarargs
    private static TreeItem<FileTreeNode> root(TreeItem<FileTreeNode>... children) {
        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().addAll(children);
        return root;
    }

    private static TreeItem<FileTreeNode> resource(String path, String content) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        FileTreeNode node = new FileTreeNode(name, path, FileTreeNode.NodeTypeEnum.RESOURCE);
        node.setCachedBytes(content.getBytes(StandardCharsets.UTF_8));
        return new TreeItem<>(node);
    }
}
