package com.bingbaihanji.fxdecomplie.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
}
