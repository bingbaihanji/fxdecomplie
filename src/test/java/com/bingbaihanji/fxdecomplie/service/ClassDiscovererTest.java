package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ClassDiscovererTest {

    @Test
    void discoversClassFilesInDirectory(@TempDir Path tmp) throws IOException {
        // Create a simple directory structure
        Path comDir = Files.createDirectories(tmp.resolve("com").resolve("example"));
        Files.writeString(comDir.resolve("Test.class"), "dummy class content");

        List<ClassDiscoverer.ClassEntry> entries = ClassDiscoverer.discover(tmp.toFile());
        assertFalse(entries.isEmpty());
        assertTrue(entries.stream().anyMatch(
                e -> e.fullPath().contains("Test.class") && e.nodeType().name().contains("CLASS")));
    }

    @Test
    void detectsResourceFiles(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("config.properties"), "key=value");
        List<ClassDiscoverer.ClassEntry> entries = ClassDiscoverer.discover(tmp.toFile());
        assertTrue(entries.stream().anyMatch(
                e -> e.fullPath().contains("config.properties")));
    }

    @Test
    void emptyDirectoryReturnsEmpty(@TempDir Path tmp) throws IOException {
        List<ClassDiscoverer.ClassEntry> entries = ClassDiscoverer.discover(tmp.toFile());
        assertTrue(entries.isEmpty());
    }

    @Test
    void singleClassFileDetected(@TempDir Path tmp) throws IOException {
        Path classFile = tmp.resolve("Hello.class");
        Files.writeString(classFile, "dummy");

        List<ClassDiscoverer.ClassEntry> entries = ClassDiscoverer.discover(classFile.toFile());
        assertEquals(1, entries.size());
        assertEquals("Hello.class", entries.get(0).name());
    }

    @Test
    void binaryJarEntryCleanupDoesNotCloseReadableEntries(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("sample.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("native/data.bin"));
            out.write(new byte[]{1, 2, 3});
            out.closeEntry();
            out.putNextEntry(new JarEntry("com/example/Test.class"));
            out.write(new byte[]{4, 5, 6});
            out.closeEntry();
        }

        List<ClassDiscoverer.ClassEntry> entries = ClassDiscoverer.discover(jar.toFile());
        ClassDiscoverer.ClassEntry binary = entries.stream()
                .filter(e -> e.fullPath().equals("native/data.bin"))
                .findFirst()
                .orElseThrow();
        ClassDiscoverer.ClassEntry clazz = entries.stream()
                .filter(e -> e.fullPath().equals("com/example/Test.class"))
                .findFirst()
                .orElseThrow();

        assertNull(binary.cleanup(), "binary entries must not affect shared archive lifetime");
        assertArrayEquals(new byte[]{4, 5, 6}, clazz.byteLoader().load());
        clazz.cleanup().run();
    }

    @Test
    void discoversClassesInsideSpringBootNestedLibJar(@TempDir Path tmp) throws Exception {
        byte[] classBytes = compileClass(tmp, "ch.qos.logback.classic.boolex",
                "ExceptionMatchEvaluator", """
                        package ch.qos.logback.classic.boolex;
                        public class ExceptionMatchEvaluator {
                            public boolean matches(Throwable t) { return t != null; }
                        }
                        """);
        byte[] nestedJar = jarBytes("ch/qos/logback/classic/boolex/ExceptionMatchEvaluator.class",
                classBytes);
        Path bootJar = tmp.resolve("boot-0.0.3.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(bootJar))) {
            out.putNextEntry(new JarEntry("BOOT-INF/lib/logback-classic-1.5.18.jar"));
            out.write(nestedJar);
            out.closeEntry();
        }

        List<ClassDiscoverer.ClassEntry> entries = ClassDiscoverer.discover(bootJar.toFile());
        String nestedPath = "BOOT-INF/lib/logback-classic-1.5.18.jar:"
                + "ch/qos/logback/classic/boolex/ExceptionMatchEvaluator.class";
        ClassDiscoverer.ClassEntry clazz = entries.stream()
                .filter(e -> e.fullPath().equals(nestedPath))
                .findFirst()
                .orElseThrow();

        assertEquals(FileTreeNode.NodeTypeEnum.CLASS_FILE, clazz.nodeType());
        assertArrayEquals(classBytes, clazz.byteLoader().load());
        clazz.cleanup().run();
    }

    private static byte[] jarBytes(String entryName, byte[] bytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(buffer)) {
            out.putNextEntry(new JarEntry(entryName));
            out.write(bytes);
            out.closeEntry();
        }
        return buffer.toByteArray();
    }

    private static byte[] compileClass(Path tmp, String packageName, String className,
                                       String source) throws IOException {
        Path sourceDir = Files.createDirectories(tmp.resolve("src"));
        Path outputDir = Files.createDirectories(tmp.resolve("classes"));
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
