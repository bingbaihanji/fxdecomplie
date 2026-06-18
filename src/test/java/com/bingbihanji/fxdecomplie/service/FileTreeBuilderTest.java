package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

class FileTreeBuilderTest {

    @Test
    void buildsTreeWithPackages() {
        List<ClassDiscoverer.ClassEntry> entries = new ArrayList<>();
        entries.add(new ClassDiscoverer.ClassEntry("Foo.class", "com/example/Foo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE, new byte[]{1, 2, 3}));
        entries.add(new ClassDiscoverer.ClassEntry("Bar.class", "com/example/Bar.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE, new byte[]{4, 5, 6}));

        TreeItem<FileTreeNode> root = FileTreeBuilder.build("test.jar", entries);
        assertNotNull(root);
        assertEquals("test.jar", root.getValue().getName());
        // Should have one package child "com"
        assertEquals(1, root.getChildren().size());
        assertEquals("com", root.getChildren().get(0).getValue().getName());
    }

    @Test
    void packagesSortedBeforeFiles() {
        List<ClassDiscoverer.ClassEntry> entries = new ArrayList<>();
        entries.add(new ClassDiscoverer.ClassEntry("Zzz.class", "Zzz.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE, null));
        entries.add(new ClassDiscoverer.ClassEntry("Readme.md", "docs/Readme.md",
                FileTreeNode.NodeTypeEnum.RESOURCE, "hello".getBytes()));

        TreeItem<FileTreeNode> root = FileTreeBuilder.build("test", entries);
        // "docs" package should come before "Zzz.class"
        assertEquals("docs", root.getChildren().get(0).getValue().getName());
    }

    @Test
    void emptyEntriesProducesEmptyTree() {
        TreeItem<FileTreeNode> root = FileTreeBuilder.build("empty", List.of());
        assertNotNull(root);
        assertTrue(root.getChildren().isEmpty());
    }
}
