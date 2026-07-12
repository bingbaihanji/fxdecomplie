package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.util.io.AtomicFile;
import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * L3 磁盘持久化缓存将反编译结果序列化到 {@code <appDir>/cache/} 下
 * 超量时自动清理旧缓存
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class DiskCodeCache {

    private static final Logger log = LoggerFactory.getLogger(DiskCodeCache.class);

    private static final Path CACHE_ROOT = AppPaths.cacheDir();

    private static final long MAX_CACHE_SIZE_BYTES = 500L * 1024 * 1024; // 500 MB
    private static final int CACHE_SCHEMA_VERSION = 2;
    private static volatile boolean cleanupInProgress;

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
            if (!Files.exists(file)) {
                return null;
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            // 零字节文件(写入时崩溃/断电残留)视为缓存未命中,触发重新反编译
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            log.debug("加载磁盘代码缓存失败: {}", file, e);
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
        // 跳过 null/blank 源码,避免写入空文件后被 load() 当作缓存未命中
        if (sourceCode == null || sourceCode.isBlank()) {
            return;
        }
        try {
            Path file = cachePath(workspaceHash, internalName, engine, optionsHash);
            Files.createDirectories(file.getParent());
            // 惰性检查：写入前检查缓存大小，超限时触发后台清理
            checkAndCleanLazy();
            AtomicFile af = new AtomicFile(file.toFile());
            af.write(os -> {
                try {
                    os.write(sourceCode.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException | RuntimeException e) {
            log.debug("保存磁盘代码缓存失败: {}/{}", workspaceHash, internalName, e);
        }
    }

    private static void checkAndCleanLazy() {
        if (cleanupInProgress) {
            return;
        }
        try {
            if (!Files.exists(CACHE_ROOT)) {
                return;
            }
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
            if (totalSize > MAX_CACHE_SIZE_BYTES) {
                cleanupInProgress = true;
                cleanIfNeeded();
            }
        } catch (IOException e) {
            log.warn("磁盘缓存惰性检查失败", e);
        } finally {
            cleanupInProgress = false;
        }
    }

    /** 删除指定工作区和内部名称的所有引擎/选项缓存文件 */
    public static void invalidate(String workspaceHash, String internalName) {
        if (workspaceHash == null || internalName == null) {
            return;
        }
        String safeWorkspace = sanitizePathPart(workspaceHash);
        String safeName = sanitizePathPart(internalName);
        Path dir = CACHE_ROOT.resolve("v" + CACHE_SCHEMA_VERSION).resolve(safeWorkspace);
        if (!Files.exists(dir)) {
            return;
        }
        try (var engineDirs = Files.list(dir)) {
            engineDirs.filter(Files::isDirectory).forEach(engineDir -> {
                try (var files = Files.list(engineDir)) {
                    files.filter(f -> {
                        String fname = f.getFileName().toString();
                        return fname.startsWith(safeName + ".");
                    }).forEach(f -> {
                        try {
                            Files.deleteIfExists(f);
                        } catch (IOException ignored) {
                            // 删除单个缓存文件失败不阻塞其余操作
                        }
                    });
                } catch (IOException ignored) {
                    // 目录不可读时跳过
                }
            });
        } catch (IOException ignored) {
            // 工作区目录不可读时跳过
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
            if (!Files.exists(CACHE_ROOT)) {
                return;
            }
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
                log.debug("磁盘缓存大小: {} MB (阈值 {} MB), 无需清理",
                        totalSize / (1024 * 1024), MAX_CACHE_SIZE_BYTES / (1024 * 1024));
                return;
            }
            log.info("磁盘缓存超过阈值: {} MB > {} MB, 开始清理",
                    totalSize / (1024 * 1024), MAX_CACHE_SIZE_BYTES / (1024 * 1024));
            // 第二遍扫描:收集、按时间排序,删除最旧的直至低于目标大小
            long targetSize = (long) (MAX_CACHE_SIZE_BYTES * 0.7);
            List<Path> files;
            try (var stream = Files.walk(CACHE_ROOT)) {
                files = stream.filter(Files::isRegularFile)
                        .sorted(Comparator.comparingLong(p -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (IOException e) {
                                return Long.MAX_VALUE;
                            }
                        }))
                        .toList();
            }
            int deleted = 0;
            for (Path f : files) {
                if (totalSize <= targetSize) {
                    break;
                }
                try {
                    long sz = Files.size(f);
                    Files.deleteIfExists(f);
                    totalSize -= sz;
                    deleted++;
                } catch (IOException ignored) {
                }
            }
            log.info("磁盘缓存清理完成: 删除 {} 个文件, 剩余 {} MB",
                    deleted, totalSize / (1024 * 1024));
        } catch (IOException e) {
            log.warn("磁盘缓存清理失败", e);
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
        return ByteUtils.sha256Hex(optionsHash).substring(0, 16);
    }
}
