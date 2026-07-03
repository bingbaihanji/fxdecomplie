package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.service.ProjectFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectFileManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsProject() throws Exception {
        Path file = tempDir.resolve("demo.fxdproj");
        DecompilerProject project = new DecompilerProject(
                1, "VINEFLOWER", List.of("demo.jar"), "demo.jar", "out");

        ProjectFileManager.save(file, project);
        DecompilerProject loaded = ProjectFileManager.load(file);

        assertEquals("VINEFLOWER", loaded.engine());
        assertEquals(List.of("demo.jar"), loaded.inputPaths());
    }

    @Test
    void invalidProjectJsonThrowsIOException() throws Exception {
        Path file = tempDir.resolve("broken.fxdproj");
        Files.writeString(file, "{bad json");

        assertThrows(IOException.class, () -> ProjectFileManager.load(file));
    }
}
