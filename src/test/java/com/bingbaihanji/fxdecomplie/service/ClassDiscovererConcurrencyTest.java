package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClassDiscovererConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void indexesEveryClassFromJarWithParallelReads() throws Exception {
        byte[] classBytes = compileClass();
        Path jar = tempDir.resolve("many-classes.jar");
        int classCount = 64;
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (int i = 0; i < classCount; i++) {
                out.putNextEntry(new JarEntry("com/example/C" + i + ".class"));
                out.write(classBytes);
                out.closeEntry();
            }
            out.putNextEntry(new JarEntry("application.properties"));
            out.write("server.port=8080".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        List<ClassDiscoverer.ClassEntry> entries = ClassDiscoverer.discover(jar.toFile());
        FileTreeModel root = FileTreeBuilder.build("many-classes.jar", entries);
        Workspace workspace = new Workspace("many-classes.jar", jar.toFile(), root, true);
        try {
            WorkspaceIndex index = WorkspaceIndex.build(root);
            assertEquals(classCount, index.classes().size());
            assertEquals(1, index.resources().size());
        } finally {
            workspace.close();
        }
    }

    private byte[] compileClass() throws IOException {
        Path sourceDir = Files.createDirectories(tempDir.resolve("src"));
        Path outputDir = Files.createDirectories(tempDir.resolve("classes"));
        Path sourceFile = sourceDir.resolve("Shared.java");
        Files.writeString(sourceFile, """
                package com.example;
                public class Shared {
                    private int value;
                    public int getValue() { return value; }
                }
                """, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests require a JDK compiler");
        int exit = compiler.run(null, null, null,
                "--release", "17",
                "-d", outputDir.toString(),
                sourceFile.toString());
        assertEquals(0, exit, "test class should compile");
        return Files.readAllBytes(outputDir.resolve("com/example/Shared.class"));
    }
}
