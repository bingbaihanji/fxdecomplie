package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileTreeBuilderTest {

    @Test
    void buildsTreeWithPackages() {
        List<ClassDiscoverer.ClassEntry> entries = new ArrayList<>();
        entries.add(new ClassDiscoverer.ClassEntry("Foo.class", "com/example/Foo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE, new byte[]{1, 2, 3}));
        entries.add(new ClassDiscoverer.ClassEntry("Bar.class", "com/example/Bar.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE, new byte[]{4, 5, 6}));

        FileTreeModel root = FileTreeBuilder.build("test.jar", entries);
        assertNotNull(root);
        assertEquals("test.jar", root.getValue().getName());
        // Should have one package child "com"
        assertEquals(1, root.getChildren().size());
        assertEquals("com", root.getChildren().get(0).getValue().getName());
        FileTreeNode fooNode = root.getChildren().get(0)
                .getChildren().get(0)
                .getChildren().stream()
                .filter(item -> item.getValue().getName().equals("Foo.class"))
                .findFirst()
                .orElseThrow()
                .getValue();
        assertArrayEquals(new byte[]{1, 2, 3}, fooNode.getCachedBytes());
    }

    @Test
    void packagesSortedBeforeFiles() {
        List<ClassDiscoverer.ClassEntry> entries = new ArrayList<>();
        entries.add(new ClassDiscoverer.ClassEntry("Zzz.class", "Zzz.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE, null));
        entries.add(new ClassDiscoverer.ClassEntry("Readme.md", "docs/Readme.md",
                FileTreeNode.NodeTypeEnum.RESOURCE, "hello".getBytes()));

        FileTreeModel root = FileTreeBuilder.build("test", entries);
        // "docs" package should come before "Zzz.class"
        assertEquals("docs", root.getChildren().get(0).getValue().getName());
    }

    @Test
    void emptyEntriesProducesEmptyTree() {
        FileTreeModel root = FileTreeBuilder.build("empty", List.of());
        assertNotNull(root);
        assertTrue(root.getChildren().isEmpty());
    }

    @Test
    void treatsNestedJarSourceSeparatorAsTreeLevel() {
        FileTreeModel root = FileTreeBuilder.build("boot.jar", List.of(
                new ClassDiscoverer.ClassEntry("ExceptionMatchEvaluator.class",
                        "BOOT-INF/lib/logback-classic-1.5.18.jar:"
                                + "ch/qos/logback/classic/boolex/ExceptionMatchEvaluator.class",
                        FileTreeNode.NodeTypeEnum.CLASS_FILE, new byte[]{1})));

        FileTreeModel bootInf = child(root, "BOOT-INF");
        FileTreeModel lib = child(bootInf, "lib");
        FileTreeModel logback = child(lib, "logback-classic-1.5.18.jar");
        FileTreeModel ch = child(logback, "ch");

        assertNotNull(ch);
        assertEquals("ExceptionMatchEvaluator.class",
                child(child(child(child(child(ch, "qos"), "logback"), "classic"), "boolex"),
                        "ExceptionMatchEvaluator.class").getValue().getName());
    }

    private static FileTreeModel child(FileTreeModel parent, String name) {
        return parent.getChildren().stream()
                .filter(item -> item.getValue().getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
