package com.bingbihanji.fxdecomplie.usage;

import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import com.bingbihanji.fxdecomplie.model.UsageResult;
import com.bingbihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbihanji.fxdecomplie.service.UsageSearchService;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageSearchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void findsClassMethodAndFieldUsagesFromBytecode() throws Exception {
        Path sourceDir = tempDir.resolve("src/com/example");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("Target.java"), """
                package com.example;
                public class Target {
                    public static int value;
                    public void call() {}
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(sourceDir.resolve("Caller.java"), """
                package com.example;
                public class Caller {
                    public void run() {
                        Target.value = 1;
                        new Target().call();
                    }
                }
                """, StandardCharsets.UTF_8);

        int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17",
                "-d", tempDir.resolve("classes").toString(),
                sourceDir.resolve("Target.java").toString(),
                sourceDir.resolve("Caller.java").toString());
        assertTrue(exit == 0, "test classes should compile");

        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(classNode("com/example/Target.class",
                tempDir.resolve("classes/com/example/Target.class")));
        root.getChildren().add(classNode("com/example/Caller.class",
                tempDir.resolve("classes/com/example/Caller.class")));
        WorkspaceIndex index = WorkspaceIndex.build(root);

        List<UsageResult> classResults = UsageSearchService.findUsages(index, "com.example.Target");
        assertTrue(classResults.stream().anyMatch(r ->
                r.sourcePath().equals("com/example/Caller.class")
                        && (r.type() == UsageResult.UsageType.CLASS_REFERENCE
                        || r.type() == UsageResult.UsageType.METHOD_CALL
                        || r.type() == UsageResult.UsageType.FIELD_ACCESS)));

        List<UsageResult> methodResults = UsageSearchService.findUsages(index, "com.example.Target#call");
        assertTrue(methodResults.stream().anyMatch(r ->
                r.sourcePath().equals("com/example/Caller.class")
                        && r.type() == UsageResult.UsageType.METHOD_CALL));

        List<UsageResult> fieldResults = UsageSearchService.findUsages(index, "com.example.Target#value");
        assertTrue(fieldResults.stream().anyMatch(r ->
                r.sourcePath().equals("com/example/Caller.class")
                        && r.type() == UsageResult.UsageType.FIELD_ACCESS));
    }

    private static TreeItem<FileTreeNode> classNode(String path, Path classFile) throws Exception {
        String name = path.substring(path.lastIndexOf('/') + 1);
        FileTreeNode node = new FileTreeNode(name, path, FileTreeNode.NodeTypeEnum.CLASS_FILE);
        node.setCachedBytes(Files.readAllBytes(classFile));
        return new TreeItem<>(node);
    }
}
