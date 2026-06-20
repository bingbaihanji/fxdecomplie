package com.bingbaihanji.fxdecomplie.model;

import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceTest {

    @TempDir
    Path tempDir;

    @Test
    void findNodeByPathDoesNotLoadClassBytes() {
        FileTreeNode clsNode = new FileTreeNode("Demo.class", "com/example/Demo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        AtomicInteger reads = new AtomicInteger();
        clsNode.setByteLoader(() -> {
            reads.incrementAndGet();
            return new byte[]{1, 2, 3};
        });

        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new TreeItem<>(clsNode));

        Workspace workspace = new Workspace("demo", tempDir.toFile(), root,
                false, WorkspaceIndex.EMPTY);

        assertSame(clsNode, workspace.findNodeByPath("com\\example\\Demo.class"));
        assertSame(clsNode, workspace.findNodeByPath("com/example/Demo.class"));
        assertNull(clsNode.getCachedBytes());
        assertEquals(0, reads.get());
    }

    @Test
    void defaultConstructorDoesNotBuildFullIndex() {
        FileTreeNode clsNode = new FileTreeNode("Demo.class", "com/example/Demo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        AtomicInteger reads = new AtomicInteger();
        clsNode.setByteLoader(() -> {
            reads.incrementAndGet();
            return new byte[]{1, 2, 3};
        });

        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new TreeItem<>(clsNode));

        Workspace workspace = new Workspace("demo", tempDir.toFile(), root, false);

        assertFalse(workspace.isIndexReady());
        assertFalse(workspace.isIndexBuildStarted());
        assertNull(clsNode.getCachedBytes());
        assertEquals(0, reads.get());
    }

    @Test
    void failedIndexBuildCanBeRetriedWithNewFuture() {
        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        Workspace workspace = new Workspace("demo", tempDir.toFile(), root, false);

        assertTrue(workspace.markIndexBuildStarted());
        var failedFuture = workspace.getIndexFuture();
        workspace.failIndex(new RuntimeException("boom"));

        assertTrue(failedFuture.isCompletedExceptionally());
        assertFalse(workspace.isIndexBuildStarted());
        assertTrue(workspace.markIndexBuildStarted());
        assertNotSame(failedFuture, workspace.getIndexFuture());

        workspace.setIndex(WorkspaceIndex.build(root));
        assertTrue(workspace.isIndexReady());
        assertFalse(workspace.isIndexBuildStarted());
    }
}
