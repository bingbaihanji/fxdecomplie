package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceIndexServiceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void useTempCacheDir() throws IOException {
        WorkspaceIndexCache.setCacheDirectoryForTesting(Files.createDirectories(
                tempDir.resolve("cache")));
    }

    @AfterEach
    void resetCacheDir() {
        WorkspaceIndexCache.setCacheDirectoryForTesting(null);
    }

    @Test
    void closedWorkspaceDoesNotPublishOrCacheIndex() throws Exception {
        Path source = tempDir.resolve("closed-test.jar");
        Files.writeString(source, "dummy");
        FileTreeNode node = new FileTreeNode("Foo.class", "com/example/Foo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        node.setByteLoader(() -> {
            LockSupport.parkNanos(300_000_000L);
            return new byte[]{1, 2, 3};
        });
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new FileTreeModel(node));
        Workspace workspace = new Workspace("closed-test", source.toFile(), root, false);
        String fingerprint = WorkspaceIndexCache.computeFingerprint(source);
        Path cacheFile = tempDir.resolve("cache").resolve(fingerprint + ".idx.gz");

        WorkspaceIndexService.ensureIndexingStarted(workspace);
        awaitTrue(workspace::isIndexBuildStarted, 5_000);

        workspace.close();

        awaitTrue(() -> !workspace.isIndexBuildStarted(), 5_000);
        assertFalse(workspace.isIndexReady());
        assertFalse(Files.exists(cacheFile));
    }

    @Test
    void closedWorkspaceDoesNotStartIndexing() throws Exception {
        Path source = tempDir.resolve("already-closed.jar");
        Files.writeString(source, "dummy");
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        Workspace workspace = new Workspace("already-closed", source.toFile(), root, false);
        workspace.close();

        WorkspaceIndexService.ensureIndexingStarted(workspace);

        assertFalse(workspace.isIndexBuildStarted());
        assertFalse(workspace.isIndexReady());
        String fingerprint = WorkspaceIndexCache.computeFingerprint(source);
        assertFalse(Files.exists(tempDir.resolve("cache").resolve(fingerprint + ".idx.gz")));
    }

    private static void awaitTrue(java.util.function.BooleanSupplier condition, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "condition not met within " + timeoutMillis + "ms");
    }
}
