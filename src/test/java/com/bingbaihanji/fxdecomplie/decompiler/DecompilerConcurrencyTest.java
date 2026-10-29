package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.service.DecompilerRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecompilerConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void decompilesClassesConcurrently() throws Exception {
        assertEquals(Math.max(2, Math.min(8,
                        Runtime.getRuntime().availableProcessors() / 2)),
                DecompilerRunner.decompilerThreads());
        int count = 8;
        List<byte[]> classes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            classes.add(compileClass("Concurrent" + i));
        }
        int threads = Math.max(2, DecompilerRunner.decompilerThreads());
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                byte[] bytes = classes.get(i);
                String path = "com/example/Concurrent" + i + ".class";
                futures.add(executor.submit(() -> DecompilerRunner.decompileWithTimeout(
                        path, bytes, DecompilerTypeEnum.VINEFLOWER, DecompilerContext.EMPTY)));
            }
            for (int i = 0; i < count; i++) {
                String source = futures.get(i).get(60, TimeUnit.SECONDS);
                assertNotNull(source);
                assertTrue(source.contains("class Concurrent" + i), source);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void decompilesJadxClassesConcurrentlyWithoutCorruption() throws Exception {
        int count = 2;
        List<byte[]> classes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            classes.add(compileClass("JadxConcurrent" + i));
        }
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                byte[] bytes = classes.get(i);
                String path = "com/example/JadxConcurrent" + i + ".class";
                futures.add(executor.submit(() -> DecompilerRunner.decompileWithTimeout(
                        path, bytes, DecompilerTypeEnum.JADX, DecompilerContext.EMPTY)));
            }
            for (int i = 0; i < count; i++) {
                String source = futures.get(i).get(60, TimeUnit.SECONDS);
                assertNotNull(source);
                assertTrue(source.contains("class JadxConcurrent" + i), source);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private byte[] compileClass(String className) throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("src"));
        Path outputDir = Files.createDirectories(tempDir.resolve("classes"));
        Path sourceFile = sourceDir.resolve(className + ".java");
        Files.writeString(sourceFile,
                "package com.example; public class " + className + " {}",
                StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests require a JDK compiler");
        int exit = compiler.run(null, null, null, "--release", "17",
                "-d", outputDir.toString(), sourceFile.toString());
        assertEquals(0, exit, "test class should compile");
        return Files.readAllBytes(outputDir.resolve("com/example/" + className + ".class"));
    }
}
