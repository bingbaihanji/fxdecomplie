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
