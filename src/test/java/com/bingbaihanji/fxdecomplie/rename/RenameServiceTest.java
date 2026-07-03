package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenameServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void appliesClassMethodFieldAndParameterRenames() {
        String workspaceHash = "rename-test";
        RenameService.setRootDir(tempDir);
        RenameService.save(workspaceHash,
                new RenameEntry("class", "com/example/Foo", "Foo", "User", ""));
        RenameService.save(workspaceHash,
                new RenameEntry("method", "com/example/Foo", "run", "execute", "(I)V"));
        RenameService.save(workspaceHash,
                new RenameEntry("field", "com/example/Foo", "value", "count", "I"));
        RenameService.save(workspaceHash,
                new RenameEntry("param", "com/example/Foo", "input", "amount", "run(I)V"));

        String source = """
                package com.example;
                public class Foo {
                    private int value;
                    public void run(int input) {
                        this.value = input;
                    }
                    public void other(int input) {
                        run(input);
                    }
                }
                """;

        String renamed = RenameService.applyRenames(source, workspaceHash, "com/example/Foo");

        assertTrue(renamed.contains("class User"));
        assertTrue(renamed.contains("private int count"));
        assertTrue(renamed.contains("void execute(int amount)"));
        assertTrue(renamed.contains("this.count = amount"));
        assertTrue(renamed.contains("execute(input)"));
        assertTrue(renamed.contains("void other(int input)"));
    }

    @Test
    void renamedJavaPathUsesClassAlias() {
        String workspaceHash = "path-test";
        RenameService.setRootDir(tempDir);
        RenameService.save(workspaceHash,
                new RenameEntry("class", "com/example/Foo", "Foo", "User", ""));

        assertEquals("com/example/User.java",
                RenameService.renamedJavaPath("com/example/Foo.class", workspaceHash));
    }

    @Test
    void resolveTargetUsesBytecodeMetadataForMembers() throws Exception {
        byte[] bytes = compileClass("com.example", "Foo", """
                package com.example;
                public class Foo {
                    private int value;
                    public void run(int input) {
                        this.value = input;
                    }
                }
                """);
        WorkspaceIndex index = index(bytes);
        String source = """
                package com.example;
                public class Foo {
                    private int value;
                    public void run(int input) {
                        this.value = input;
                    }
                }
                """;

        RenameService.RenameTarget method = RenameService.resolveTarget(
                        source, source.indexOf("run(int"), "com/example/Foo", bytes, index)
                .orElseThrow();
        RenameService.RenameTarget field = RenameService.resolveTarget(
                        source, source.indexOf("value;"), "com/example/Foo", bytes, index)
                .orElseThrow();
        RenameService.RenameTarget param = RenameService.resolveTarget(
                        source, source.indexOf("input)"), "com/example/Foo", bytes, index)
                .orElseThrow();

        assertEquals("method", method.entry().type());
        assertEquals("(I)V", method.entry().desc());
        assertEquals("field", field.entry().type());
        assertEquals("I", field.entry().desc());
        assertEquals("param", param.entry().type());
        assertEquals("run(I)V", param.entry().desc());
    }

    @Test
    void resolveTargetMapsVisibleAliasBackToOriginalEntry() {
        String workspaceHash = "alias-test";
        RenameService.setRootDir(tempDir);
        RenameService.save(workspaceHash,
                new RenameEntry("class", "com/example/Foo", "Foo", "User", ""));

        String source = "package com.example; public class User {}";
        RenameService.RenameTarget target = RenameService.resolveTarget(
                        source, source.indexOf("User"), "com/example/Foo",
                        null, WorkspaceIndex.EMPTY, workspaceHash)
                .orElseThrow();

        assertEquals("class", target.entry().type());
        assertEquals("Foo", target.entry().oldName());
        assertEquals("User", target.currentName());
    }

    @Test
    void parameterRenameStillMatchesAfterMethodAlias() {
        String workspaceHash = "method-param-alias-test";
        RenameService.setRootDir(tempDir);
        RenameService.save(workspaceHash,
                new RenameEntry("method", "com/example/Foo", "run", "execute", "(I)V"));

        String source = """
                package com.example;
                public class Foo {
                    public void execute(int input) {
                        System.out.println(input);
                    }
                }
                """;
        RenameService.RenameTarget target = RenameService.resolveTarget(
                        source, source.indexOf("input)"), "com/example/Foo",
                        null, WorkspaceIndex.EMPTY, workspaceHash)
                .orElseThrow();

        assertEquals("param", target.entry().type());
        assertEquals("run(I)V", target.entry().desc());

        String renamed = RenameService.applySingleRename(source,
                new RenameEntry("param", "com/example/Foo", "input", "amount", "run(I)V"),
                "com/example/Foo", workspaceHash);
        assertTrue(renamed.contains("execute(int amount)"));
        assertTrue(renamed.contains("println(amount)"));
    }

    @Test
    void validNamesRejectJavaKeywords() {
        assertFalse(RenameService.isValidName("class"));
        assertFalse(RenameService.isValidName("1abc"));
        assertTrue(RenameService.isValidName("renamedValue"));
    }

    private WorkspaceIndex index(byte[] bytes) {
        FileTreeNode node = new FileTreeNode("Foo.class", "com/example/Foo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        node.setCachedBytes(bytes);
        TreeItem<FileTreeNode> root = new TreeItem<>(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new TreeItem<>(node));
        return WorkspaceIndex.build(root);
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
