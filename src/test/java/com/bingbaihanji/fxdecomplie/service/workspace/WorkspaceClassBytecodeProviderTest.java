package com.bingbaihanji.fxdecomplie.service.workspace;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.ClassDiscoverer;
import com.bingbaihanji.fxdecomplie.service.FileTreeBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkspaceClassBytecodeProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesClassBytesFromWorkspaceTree() {
        byte[] bytes = {1, 2, 3, 4};
        FileTreeNode node = new FileTreeNode("Foo.class", "com/example/Foo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        node.setByteLoader(() -> bytes);
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new FileTreeModel(node));
        Workspace workspace = new Workspace("demo", tempDir.toFile(), root, false);
        WorkspaceClassBytecodeProvider provider = new WorkspaceClassBytecodeProvider(workspace);

        assertArrayEquals(bytes, provider.getClassBytes("com/example/Foo"));
        assertNotNull(node.getCachedBytes(), "provider 应把字节缓存到节点");
    }

    @Test
    void returnsNullForMissingClassAndRemembersMiss() throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("workspace"));
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        Workspace workspace = new Workspace("demo", sourceDir.toFile(), root, false);
        WorkspaceClassBytecodeProvider provider = new WorkspaceClassBytecodeProvider(workspace);

        assertNull(provider.getClassBytes("com/example/Missing"));

        // 首次未命中后再把类文件放到磁盘上；提供器应记住未命中
        Files.createDirectories(sourceDir.resolve("com/example"));
        Files.write(sourceDir.resolve("com/example/Missing.class"), new byte[]{1, 2, 3});
        assertNull(provider.getClassBytes("com/example/Missing"));

        // 新提供器应能解析到该文件，证明磁盘上确实可读
        WorkspaceClassBytecodeProvider fresh = new WorkspaceClassBytecodeProvider(workspace);
        assertArrayEquals(new byte[]{1, 2, 3}, fresh.getClassBytes("com/example/Missing"));
    }

    @Test
    void resolvesClassBytesFromArchiveWorkspace() throws Exception {
        byte[] bytes = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 1, 2, 3};
        Path jar = tempDir.resolve("lib.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("com/example/Foo.class"));
            out.write(bytes);
            out.closeEntry();
        }
        List<ClassDiscoverer.ClassEntry> entries = ClassDiscoverer.discover(jar.toFile());
        FileTreeModel root = FileTreeBuilder.build("lib.jar", entries);
        Workspace workspace = new Workspace("lib.jar", jar.toFile(), root, true);
        WorkspaceClassBytecodeProvider provider = new WorkspaceClassBytecodeProvider(workspace);

        try {
            assertArrayEquals(bytes, provider.getClassBytes("com/example/Foo"));
            assertNull(provider.getClassBytes("com/example/Missing"));
        } finally {
            // 释放发现阶段保留的 JarFile,否则 Windows 下 @TempDir 无法删除 lib.jar
            workspace.close();
        }
    }

    @Test
    void resolvesClassByParsedInternalNameWhenPathDiffers() throws Exception {
        byte[] bytes = compileClass("com.example", "Versioned");
        FileTreeNode node = new FileTreeNode("Versioned.class",
                "META-INF/versions/9/com/example/Versioned.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        node.setByteLoader(() -> bytes);
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new FileTreeModel(node));
        WorkspaceIndex index = WorkspaceIndex.build(root);
        Workspace workspace = new Workspace("lib.jar",
                tempDir.resolve("lib.jar").toFile(), root, true, index);
        WorkspaceClassBytecodeProvider provider = new WorkspaceClassBytecodeProvider(workspace);

        assertArrayEquals(bytes, provider.getClassBytes("com/example/Versioned"));
    }

    private byte[] compileClass(String packageName, String className) throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("src"));
        Path outputDir = Files.createDirectories(tempDir.resolve("classes"));
        Path sourceFile = sourceDir.resolve(className + ".java");
        Files.writeString(sourceFile, """
                package %s;
                public class %s {}
                """.formatted(packageName, className), StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests require a JDK compiler");
        int exit = compiler.run(null, null, null, "--release", "17",
                "-d", outputDir.toString(), sourceFile.toString());
        assertEquals(0, exit, "test class should compile");
        return Files.readAllBytes(outputDir.resolve(packageName.replace('.', '/'))
                .resolve(className + ".class"));
    }
}
