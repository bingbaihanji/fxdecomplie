package com.bingbihanji.fxdecomplie.model;

import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceIndexTest {

    @Test
    void emptyTreeProducesEmptyIndex() {
        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        WorkspaceIndex index = WorkspaceIndex.build(root);
        assertNotNull(index);
        assertTrue(index.classes().isEmpty());
        assertTrue(index.resources().isEmpty());
        assertTrue(index.classPaths().isEmpty());
    }

    @Test
    void indexesClassNodes() {
        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));

        // Make a simple class bytes (hello world classfile header only - not valid, but enough for basic index test)
        byte[] dummyBytes = new byte[]{
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, // magic
                0, 0, 0, 61, // version
                0, 0 // constant pool count = 0
        };

        FileTreeNode clsNode = new FileTreeNode("Test.class", "com/example/Test.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        clsNode.setCachedBytes(dummyBytes);
        TreeItem<FileTreeNode> clsItem = new TreeItem<>(clsNode);
        root.getChildren().add(clsItem);

        WorkspaceIndex index = WorkspaceIndex.build(root);
        assertEquals(1, index.classes().size());
        // Malformed class bytes produce path-based fallback in index
        assertFalse(index.classPaths().isEmpty());
    }

    @Test
    void indexesResourceNodes() {
        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));

        FileTreeNode resNode = new FileTreeNode("test.xml", "test.xml",
                FileTreeNode.NodeTypeEnum.RESOURCE);
        resNode.setCachedBytes("<xml></xml>".getBytes());
        TreeItem<FileTreeNode> resItem = new TreeItem<>(resNode);
        root.getChildren().add(resItem);

        WorkspaceIndex index = WorkspaceIndex.build(root);
        assertEquals(1, index.resources().size());
    }

    @Test
    void getClassBytesReturnsNullForMissing() {
        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        WorkspaceIndex index = WorkspaceIndex.build(root);
        assertNull(index.getClassBytes("nonexistent"));
    }

    @Test
    void emptyIndexIsUsable() {
        WorkspaceIndex empty = WorkspaceIndex.EMPTY;
        assertNotNull(empty);
        assertTrue(empty.classes().isEmpty());
        assertTrue(empty.classPaths().isEmpty());
    }
}
