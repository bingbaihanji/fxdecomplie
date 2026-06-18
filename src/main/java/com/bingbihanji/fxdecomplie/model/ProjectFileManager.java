package com.bingbihanji.fxdecomplie.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes .fxdproj project files.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class ProjectFileManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectFileManager() {
        throw new AssertionError("utility class");
    }

    public static void save(Path path, DecompilerProject project) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, GSON.toJson(project));
    }

    public static DecompilerProject load(Path path) throws IOException {
        return GSON.fromJson(Files.readString(path), DecompilerProject.class);
    }
}
