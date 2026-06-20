package com.bingbaihanji.fxdecomplie.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取与写入 .fxdproj 项目文件
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class ProjectFileManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectFileManager() {
        throw new AssertionError("工具类不可实例化");
    }

    public static void save(Path path, DecompilerProject project) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, GSON.toJson(project));
    }

    public static DecompilerProject load(Path path) throws IOException {
        try {
            DecompilerProject project = GSON.fromJson(Files.readString(path), DecompilerProject.class);
            if (project == null) {
                throw new IOException("项目文件为空");
            }
            return project;
        } catch (JsonParseException e) {
            throw new IOException("无效的项目文件: " + path, e);
        }
    }
}
