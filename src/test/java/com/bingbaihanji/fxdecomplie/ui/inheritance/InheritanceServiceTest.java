package com.bingbaihanji.fxdecomplie.ui.inheritance;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InheritanceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsTreeForJava25ClassFile() throws Exception {
        Path sourceDir = tempDir.resolve("src/com/example");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("Parent.java"), """
                package com.example;
                public class Parent {}
                """, StandardCharsets.UTF_8);
        Files.writeString(sourceDir.resolve("Child.java"), """
                package com.example;
                public class Child extends Parent {}
                """, StandardCharsets.UTF_8);

        int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17",
                "-d", tempDir.resolve("classes").toString(),
                sourceDir.resolve("Parent.java").toString(),
                sourceDir.resolve("Child.java").toString());
        assertTrue(exit == 0, "test classes should compile");

        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(classNode("com/example/Parent.class",
                tempDir.resolve("classes/com/example/Parent.class")));
        root.getChildren().add(classNode("com/example/Child.class",
                tempDir.resolve("classes/com/example/Child.class")));

        WorkspaceIndex index = WorkspaceIndex.build(root);
        TreeItem<InheritanceNode> tree = InheritanceService.buildTree("com/example/Child.class", index);

        assertNotNull(tree);
        assertTrue(tree.getChildren().stream()
                .anyMatch(item -> "com/example/Parent".equals(item.getValue().className())));
    }

    private static TreeItem<FileTreeNode> classNode(String path, Path classFile) throws Exception {
        String name = path.substring(path.lastIndexOf('/') + 1);
        FileTreeNode node = new FileTreeNode(name, path, FileTreeNode.NodeTypeEnum.CLASS_FILE);
        node.setCachedBytes(withMajorVersion(Files.readAllBytes(classFile), 69));
        return new TreeItem<>(node);
    }

    private static byte[] withMajorVersion(byte[] bytes, int majorVersion) {
        byte[] copy = bytes.clone();
        copy[6] = (byte) ((majorVersion >>> 8) & 0xff);
        copy[7] = (byte) (majorVersion & 0xff);
        return copy;
    }
}
