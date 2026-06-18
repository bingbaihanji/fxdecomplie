package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * L3 磁盘持久化缓存。将反编译结果序列化到 ~/.fxdecompiler/cache/ 下。
 * 应用版本升级时自动清理旧缓存。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class DiskCodeCache {

    private static final Path CACHE_ROOT = Path.of(
            System.getProperty("user.home"), ".fxdecompiler", "cache");

    private DiskCodeCache() {
        throw new AssertionError("utility class");
    }

    public static Path cacheRoot() {
        return CACHE_ROOT;
    }

    public static String load(String workspaceHash, String internalName, DecompilerTypeEnum engine) {
        Path file = cachePath(workspaceHash, internalName, engine);
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static void save(String workspaceHash, String internalName,
                            DecompilerTypeEnum engine, String sourceCode) {
        try {
            Path file = cachePath(workspaceHash, internalName, engine);
            Files.createDirectories(file.getParent());
            Files.writeString(file, sourceCode, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static void cleanAll() {
        try {
            if (Files.exists(CACHE_ROOT)) {
                try (var files = Files.walk(CACHE_ROOT).sorted(Comparator.reverseOrder())) {
                    files.forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static Path cachePath(String workspaceHash, String internalName, DecompilerTypeEnum engine) {
        return CACHE_ROOT.resolve(workspaceHash)
                .resolve(internalName.replace('/', '_').replace('\\', '_')
                        + "." + engine.name().toLowerCase() + ".java");
    }
}
