package com.bingbaihanji.fxdecomplie.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceIndexTest {

    @TempDir
    Path tempDir;

    private static byte[] withMajorVersion(byte[] bytes, int majorVersion) {
        byte[] copy = bytes.clone();
        copy[6] = (byte) ((majorVersion >>> 8) & 0xff);
        copy[7] = (byte) (majorVersion & 0xff);
        return copy;
    }

    @Test
    void emptyTreeProducesEmptyIndex() {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        WorkspaceIndex index = WorkspaceIndex.build(root);
        assertNotNull(index);
        assertTrue(index.classes().isEmpty());
        assertTrue(index.resources().isEmpty());
        assertTrue(index.classPaths().isEmpty());
    }

    @Test
    void indexesClassNodes() throws Exception {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));

        FileTreeNode clsNode = new FileTreeNode("Test.class", "com/example/Test.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        clsNode.setCachedBytes(compileClass("com.example", "Test", """
                                                                   package com.example;
                                                                   public class Test {
                                                                       private String value;
                                                                       public String getValue() { return value; }
                                                                   }
                                                                   """));
        FileTreeModel clsItem = new FileTreeModel(clsNode);
        root.getChildren().add(clsItem);

        WorkspaceIndex index = WorkspaceIndex.build(root);
        assertEquals(1, index.classes().size());
        assertEquals("com/example/Test", index.classes().getFirst().internalName());
        assertTrue(index.classes().getFirst().fields().stream()
                .anyMatch(field -> field.name().equals("value")));
        assertTrue(index.classes().getFirst().methods().stream()
                .anyMatch(method -> method.name().equals("getValue")));
        assertNotNull(index.getClassBytes("com/example/Test"));
    }

    @Test
    void cachesSubclassRelationshipsFromClassMetadata() throws Exception {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));

        FileTreeNode parent = new FileTreeNode("Parent.class", "com/example/Parent.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        parent.setCachedBytes(compileClass("com.example", "Parent", """
                                                                    package com.example;
                                                                    public class Parent {}
                                                                    """));
        FileTreeNode child = new FileTreeNode("Child.class", "com/example/Child.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        child.setCachedBytes(compileClass("com.example", "Child", """
                                                                  package com.example;
                                                                  public class Child extends Parent {}
                                                                  """));
        root.getChildren().add(new FileTreeModel(parent));
        root.getChildren().add(new FileTreeModel(child));

        WorkspaceIndex index = WorkspaceIndex.build(root);

        assertSame(index.subclassesByParent(), index.subclassesByParent());
        assertEquals(List.of("com/example/Child"),
                index.subclassesByParent().get("com/example/Parent"));
    }

    @Test
    void indexesJava25ClassFileWithoutAsmVersionFailure() throws Exception {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));

        FileTreeNode clsNode = new FileTreeNode("Test.class", "com/example/Test.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        clsNode.setCachedBytes(withMajorVersion(compileClass("com.example", "Test", """
                                                                                    package com.example;
                                                                                    public class Test {
                                                                                        public void run() {}
                                                                                    }
                                                                                    """), 69));
        root.getChildren().add(new FileTreeModel(clsNode));

        WorkspaceIndex index = WorkspaceIndex.build(root);
        assertEquals(1, index.classes().size());
        assertEquals("com/example/Test", index.classes().getFirst().internalName());
        assertTrue(index.classes().getFirst().methods().stream()
                .anyMatch(method -> method.name().equals("run")));
        assertNotNull(index.getClassBytes("com/example/Test"));
    }

    @Test
    void indexesResourceNodes() {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));

        FileTreeNode resNode = new FileTreeNode("test.xml", "test.xml",
                FileTreeNode.NodeTypeEnum.RESOURCE);
        resNode.setCachedBytes("<xml></xml>".getBytes());
        FileTreeModel resItem = new FileTreeModel(resNode);
        root.getChildren().add(resItem);

        WorkspaceIndex index = WorkspaceIndex.build(root);
        assertEquals(1, index.resources().size());
    }

    @Test
    void skipsLargeResourcesWithoutReadingBytes() {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        FileTreeNode resNode = new FileTreeNode("large.txt", "large.txt",
                FileTreeNode.NodeTypeEnum.RESOURCE);
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        resNode.setSize(6L * 1024 * 1024);
        resNode.setByteLoader(() -> {
            reads.incrementAndGet();
            return "large".getBytes(StandardCharsets.UTF_8);
        });
        root.getChildren().add(new FileTreeModel(resNode));

        WorkspaceIndex index = WorkspaceIndex.build(root);

        assertTrue(index.resources().isEmpty());
        assertEquals(0, reads.get());
    }

    @Test
    void getClassBytesReturnsNullForMissing() {
        FileTreeModel root = new FileTreeModel(
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

    @Test
    void indexReadsLazyClassBytesWithoutCachingNodeDuringBuild() throws Exception {
        byte[] bytes = compileClass("com.example", "Lazy", """
                                                           package com.example;
                                                           public class Lazy {
                                                               public int value() { return 1; }
                                                           }
                                                           """);
        FileTreeNode clsNode = new FileTreeNode("Lazy.class", "com/example/Lazy.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        clsNode.setByteLoader(() -> {
            reads.incrementAndGet();
            return bytes;
        });
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new FileTreeModel(clsNode));

        WorkspaceIndex index = WorkspaceIndex.build(root);

        assertEquals(1, reads.get());
        assertNull(clsNode.getCachedBytes());
        assertNotNull(index.getClassBytes("com/example/Lazy"));
        assertNotNull(clsNode.getCachedBytes());
    }

    @Test
    void parallelBuildPreservesNodeOrder() throws Exception {
        byte[] bytes = compileClass("com.example", "Shared", """
                package com.example;
                public class Shared {}
                """);
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        int count = 32;
        List<String> expected = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String path = "com/example/C" + i + ".class";
            FileTreeNode node = new FileTreeNode("C" + i + ".class", path,
                    FileTreeNode.NodeTypeEnum.CLASS_FILE);
            long delayMillis = count - i;
            node.setByteLoader(() -> {
                LockSupport.parkNanos(delayMillis * 1_000_000L);
                return bytes;
            });
            root.getChildren().add(new FileTreeModel(node));
            expected.add(path);
        }

        WorkspaceIndex index = WorkspaceIndex.build(root);

        assertEquals(count, index.classes().size());
        assertEquals(expected, index.classes().stream()
                .map(ClassIndexEntry::fullPath).toList());
    }

    @Test
    void progressListenerFailureFailsBuild() throws Exception {
        byte[] bytes = compileClass("com.example", "Shared", """
                package com.example;
                public class Shared {}
                """);
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        for (int i = 0; i < 4; i++) {
            FileTreeNode node = new FileTreeNode("C" + i + ".class",
                    "com/example/C" + i + ".class", FileTreeNode.NodeTypeEnum.CLASS_FILE);
            node.setCachedBytes(bytes);
            root.getChildren().add(new FileTreeModel(node));
        }

        assertThrows(IllegalStateException.class, () -> WorkspaceIndex.build(root, progress -> {
            throw new IllegalStateException("listener failure");
        }));
    }

    @Test
    void interruptedBuildDoesNotPublishPartialIndex() throws Exception {
        byte[] bytes = compileClass("com.example", "Shared", """
                package com.example;
                public class Shared {}
                """);
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        for (int i = 0; i < 8; i++) {
            FileTreeNode node = new FileTreeNode("C" + i + ".class",
                    "com/example/C" + i + ".class", FileTreeNode.NodeTypeEnum.CLASS_FILE);
            node.setCachedBytes(bytes);
            root.getChildren().add(new FileTreeModel(node));
        }

        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> WorkspaceIndex.build(root));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void excludesImageResourcesFromIndex() {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        FileTreeNode image = new FileTreeNode("logo.png", "static/logo.png",
                FileTreeNode.NodeTypeEnum.RESOURCE);
        image.setCachedBytes(new byte[]{1, 2, 3});
        FileTreeNode text = new FileTreeNode("app.properties", "app.properties",
                FileTreeNode.NodeTypeEnum.RESOURCE);
        text.setCachedBytes("server.port=8080".getBytes(StandardCharsets.UTF_8));
        root.getChildren().add(new FileTreeModel(image));
        root.getChildren().add(new FileTreeModel(text));

        WorkspaceIndex index = WorkspaceIndex.build(root);

        assertEquals(List.of("app.properties"), index.resources().stream()
                .map(ResourceIndexEntry::fullPath).toList());
    }

    private byte[] compileClass(String packageName, String className, String source) throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("src"));
        Path outputDir = Files.createDirectories(tempDir.resolve("classes"));
        Path sourceFile = sourceDir.resolve(className + ".java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests require a JDK compiler");
        int exit = compiler.run(null, null, null,
                "--release", "17",
                "-classpath", outputDir.toString(),
                "-d", outputDir.toString(),
                sourceFile.toString());
        assertEquals(0, exit, "test class should compile");

        Path classFile = outputDir.resolve(packageName.replace('.', '/')).resolve(className + ".class");
        return Files.readAllBytes(classFile);
    }
}
