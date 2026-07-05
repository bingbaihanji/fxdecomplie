package com.bingbaihanji.fxdecomplie.model;

import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        WorkspaceIndex index = WorkspaceIndex.build(root);
        assertNotNull(index);
        assertTrue(index.classes().isEmpty());
        assertTrue(index.resources().isEmpty());
        assertTrue(index.classPaths().isEmpty());
    }

    @Test
    void indexesClassNodes() throws Exception {
        TreeItem<FileTreeNode> root = new TreeItem<>(
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
        TreeItem<FileTreeNode> clsItem = new TreeItem<>(clsNode);
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
    void indexesJava25ClassFileWithoutAsmVersionFailure() throws Exception {
        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));

        FileTreeNode clsNode = new FileTreeNode("Test.class", "com/example/Test.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        clsNode.setCachedBytes(withMajorVersion(compileClass("com.example", "Test", """
                package com.example;
                public class Test {
                    public void run() {}
                }
                """), 69));
        root.getChildren().add(new TreeItem<>(clsNode));

        WorkspaceIndex index = WorkspaceIndex.build(root);
        assertEquals(1, index.classes().size());
        assertEquals("com/example/Test", index.classes().getFirst().internalName());
        assertTrue(index.classes().getFirst().methods().stream()
                .anyMatch(method -> method.name().equals("run")));
        assertNotNull(index.getClassBytes("com/example/Test"));
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
    void skipsLargeResourcesWithoutReadingBytes() {
        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        FileTreeNode resNode = new FileTreeNode("large.txt", "large.txt",
                FileTreeNode.NodeTypeEnum.RESOURCE);
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        resNode.setSize(6L * 1024 * 1024);
        resNode.setByteLoader(() -> {
            reads.incrementAndGet();
            return "large".getBytes(StandardCharsets.UTF_8);
        });
        root.getChildren().add(new TreeItem<>(resNode));

        WorkspaceIndex index = WorkspaceIndex.build(root);

        assertTrue(index.resources().isEmpty());
        assertEquals(0, reads.get());
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
        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new TreeItem<>(clsNode));

        WorkspaceIndex index = WorkspaceIndex.build(root);

        assertEquals(1, reads.get());
        assertNull(clsNode.getCachedBytes());
        assertNotNull(index.getClassBytes("com/example/Lazy"));
        assertNotNull(clsNode.getCachedBytes());
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
                "-d", outputDir.toString(),
                sourceFile.toString());
        assertEquals(0, exit, "test class should compile");

        Path classFile = outputDir.resolve(packageName.replace('.', '/')).resolve(className + ".class");
        return Files.readAllBytes(classFile);
    }
}
