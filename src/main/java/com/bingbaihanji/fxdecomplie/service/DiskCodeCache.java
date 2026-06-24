package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * L3 磁盘持久化缓存将反编译结果序列化到 {@code <appDir>/cache/} 下
 * 超量时自动清理旧缓存
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class DiskCodeCache {

    private static final Logger logger = LoggerFactory.getLogger(DiskCodeCache.class);

    private static final Path CACHE_ROOT = AppConfig.appDir().resolve("cache");

    private static final long MAX_CACHE_SIZE_BYTES = 500L * 1024 * 1024; // 500 MB
    private static final int CACHE_SCHEMA_VERSION = 2;

    private DiskCodeCache() {
        throw new AssertionError("utility class");
    }

    public static Path cacheRoot() {
        return CACHE_ROOT;
    }

    public static String load(String workspaceHash, String internalName, DecompilerTypeEnum engine) {
        return load(workspaceHash, internalName, engine, "default");
    }

    public static String load(String workspaceHash, String internalName,
                              DecompilerTypeEnum engine, String optionsHash) {
        Path file = cachePath(workspaceHash, internalName, engine, optionsHash);
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            logger.debug("加载磁盘代码缓存失败: {}", file, e);
            return null;
        }
    }

    public static void save(String workspaceHash, String internalName,
                            DecompilerTypeEnum engine, String sourceCode) {
        save(workspaceHash, internalName, engine, "default", sourceCode);
    }

    public static void save(String workspaceHash, String internalName,
                            DecompilerTypeEnum engine, String optionsHash,
                            String sourceCode) {
        try {
            Path file = cachePath(workspaceHash, internalName, engine, optionsHash);
            Files.createDirectories(file.getParent());
            Files.writeString(file, sourceCode, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.debug("保存磁盘代码缓存失败: {}/{}", workspaceHash, internalName, e);
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

    /** 检查并在缓存超过 500MB 时清理最早的条目(应在启动时调用),使用两遍扫描避免收集所有路径到内存 */
    public static void cleanIfNeeded() {
        try {
            if (!Files.exists(CACHE_ROOT)) return;
            // 第一遍扫描:计算总大小,不收集所有路径
            long totalSize;
            try (var stream = Files.walk(CACHE_ROOT)) {
                totalSize = stream.filter(Files::isRegularFile)
                        .mapToLong(p -> {
                            try {
                                return Files.size(p);
                            } catch (IOException e) {
                                return 0L;
                            }
                        }).sum();
            }
            if (totalSize <= MAX_CACHE_SIZE_BYTES) {
                return;
            }
            // 第二遍扫描:收集、按时间排序,删除最旧的直至低于目标大小
            long targetSize = (long) (MAX_CACHE_SIZE_BYTES * 0.7);
            List<Path> files;
            try (var stream = Files.walk(CACHE_ROOT)) {
                files = stream.filter(Files::isRegularFile)
                        .sorted(Comparator.comparingLong(p -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (IOException e) {
                                return 0L;
                            }
                        }))
                        .collect(Collectors.toList());
            }
            for (Path f : files) {
                if (totalSize <= targetSize) {
                    break;
                }
                try {
                    long sz = Files.size(f);
                    Files.deleteIfExists(f);
                    totalSize -= sz;
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static Path cachePath(String workspaceHash, String internalName, DecompilerTypeEnum engine) {
        return cachePath(workspaceHash, internalName, engine, "default");
    }

    private static Path cachePath(String workspaceHash, String internalName,
                                  DecompilerTypeEnum engine, String optionsHash) {
        String safeWorkspace = sanitizePathPart(workspaceHash);
        String safeInternalName = sanitizePathPart(internalName);
        String safeEngine = engine.name().toLowerCase();
        return CACHE_ROOT.resolve("v" + CACHE_SCHEMA_VERSION)
                .resolve(safeWorkspace)
                .resolve(safeEngine)
                .resolve(safeInternalName + "." + optionsId(optionsHash) + ".java");
    }

    private static String sanitizePathPart(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        return value.replace('/', '_')
                .replace('\\', '_')
                .replace(':', '_')
                .replace('*', '_')
                .replace('?', '_')
                .replace('"', '_')
                .replace('<', '_')
                .replace('>', '_')
                .replace('|', '_');
    }

    private static String optionsId(String optionsHash) {
        if (optionsHash == null || optionsHash.isBlank() || "default".equals(optionsHash)) {
            return "default";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(optionsHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                id.append(String.format("%02x", hash[i]));
            }
            return id.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(optionsHash.hashCode());
        }
    }
}
