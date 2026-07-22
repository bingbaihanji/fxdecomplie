package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.DecompilerProject;
import com.bingbaihanji.utils.json.JSONUtils;

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

    private ProjectFileManager() {
        throw new AssertionError("工具类不可实例化");
    }

    /** 将项目配置序列化为 JSON 并写入指定路径 */
    public static void save(Path path, DecompilerProject project) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, JSONUtils.toPrettyJson(project));
    }

    /**
     * 从指定路径读取并反序列化项目配置
     *
     * @throws IOException 文件读取失败或 JSON 解析失败时抛出
     */
    public static DecompilerProject load(Path path) throws IOException {
        try {
            DecompilerProject project = JSONUtils.fromJson(Files.readString(path), DecompilerProject.class);
            if (project == null) {
                throw new IOException("项目文件为空");
            }
            return project;
        } catch (RuntimeException e) {
            throw new IOException("无效的项目文件: " + path, e);
        }
    }
}
