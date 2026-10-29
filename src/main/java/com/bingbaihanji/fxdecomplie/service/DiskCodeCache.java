package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheScope;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheStore;
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
import java.util.Optional;

/**
 * L3 磁盘持久化缓存将反编译结果序列化到 {@code <appDir>/cache/} 下.
 * 实现 {@link CodeCacheStore} 以支持注入和测试替换.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class DiskCodeCache implements CodeCacheStore {

    private static final Logger log = LoggerFactory.getLogger(DiskCodeCache.class);

    private static final long MAX_CACHE_SIZE_BYTES = 500L * 1024 * 1024;

    private static final int CACHE_SCHEMA_VERSION = 2;

    private static volatile DiskCodeCache defaultInstance;

    private final Path cacheRoot;

    private volatile boolean cleanupInProgress;

    public DiskCodeCache(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    /** 获取默认实例(使用 AppPaths.cacheDir() 作为根目录) */
    public static DiskCodeCache getDefault() {
        if (defaultInstance == null) {
            synchronized (DiskCodeCache.class) {
                if (defaultInstance == null) {
                    defaultInstance = new DiskCodeCache(AppPaths.cacheDir());
                }
            }
        }
        return defaultInstance;
    }

    /** @deprecated 使用 {@link #getDefault()} 获取实例后调用 {@link #load(CodeCacheKey)} */
    @Deprecated
    public static Path cacheRoot() {
        return getDefault().cacheRoot;
    }

    // ─── CodeCacheStore 实现 ───

    public static String load(String workspaceHash, String internalName, DecompilerTypeEnum engine) {
        return getDefault().loadInternal(workspaceHash, internalName, engine, "default");
    }

    public static String load(String workspaceHash, String internalName,
                              DecompilerTypeEnum engine, String optionsHash) {
        return getDefault().loadInternal(workspaceHash, internalName, engine, optionsHash);
    }

    public static void save(String workspaceHash, String internalName,
                            DecompilerTypeEnum engine, String sourceCode) {
        getDefault().saveInternal(workspaceHash, internalName, engine, "default", sourceCode);
    }

    // ─── 向后兼容: 静态方法 ───

    public static void save(String workspaceHash, String internalName,
                            DecompilerTypeEnum engine, String optionsHash, String sourceCode) {
        getDefault().saveInternal(workspaceHash, internalName, engine, optionsHash, sourceCode);
    }

    public static void invalidate(String workspaceHash, String internalName) {
        getDefault().invalidateInternal(workspaceHash, internalName);
    }

    public static void cleanAll() {
        getDefault().cleanAllInternal();
    }

    public static void cleanIfNeeded() {
        getDefault().cleanIfNeededInternal();
    }

    private static String sanitizePathPart(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        return value.replace('/', '_').replace('\\', '_').replace(':', '_')
                .replace('*', '_').replace('?', '_').replace('"', '_')
                .replace('<', '_').replace('>', '_').replace('|', '_');
    }

    private static String optionsId(String optionsHash) {
        if (optionsHash == null || optionsHash.isBlank() || "default".equals(optionsHash)) {
            return "default";
        }
        return ByteUtils.sha256Hex(optionsHash).substring(0, 16);
    }

    public Path root() {
        return cacheRoot;
    }

    @Override
    public Optional<String> load(CodeCacheKey key) {
        return Optional.ofNullable(load(key.workspaceId(), key.internalName(),
                key.engine(), key.optionsHash()));
    }

    @Override
    public void save(CodeCacheKey key, String source) {
        save(key.workspaceId(), key.internalName(),
                key.engine(), key.optionsHash(), source);
    }

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
        switch (scope) {
            case CLASS -> invalidate(key.workspaceId(), key.internalName());
            case WORKSPACE -> {
                // 清理该工作区下的所有缓存文件
                String safeWs = sanitizePathPart(key.workspaceId());
                Path wsDir = cacheRoot.resolve("v" + CACHE_SCHEMA_VERSION).resolve(safeWs);
                try {
                    if (Files.exists(wsDir)) {
                        try (var files = Files.walk(wsDir).sorted(Comparator.reverseOrder())) {
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
            case ALL -> cleanAll();
        }
    }

    private String loadInternal(String workspaceHash, String internalName,
                                DecompilerTypeEnum engine, String optionsHash) {
        Path file = cachePath(workspaceHash, internalName, engine, optionsHash);
        try {
            if (!Files.exists(file)) {
                return null;
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            log.debug("加载磁盘代码缓存失败: {}", file, e);
            return null;
        }
    }

    private void saveInternal(String workspaceHash, String internalName,
                              DecompilerTypeEnum engine, String optionsHash, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return;
        }
        try {
            Path file = cachePath(workspaceHash, internalName, engine, optionsHash);
            Files.createDirectories(file.getParent());
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

    private void checkAndCleanLazy() {
        if (cleanupInProgress) {
            return;
        }
        try {
            if (!Files.exists(cacheRoot)) {
                return;
            }
            long totalSize;
            try (var stream = Files.walk(cacheRoot)) {
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
                var cleanupTask = BackgroundTasks.run(BackgroundTasks.PoolType.IO,
                        "DiskCache-Cleanup", () -> {
                            try {
                                cleanIfNeeded();
                            } finally {
                                cleanupInProgress = false;
                            }
                        });
                if (cleanupTask.isDone()) {
                    try {
                        cleanupTask.get();
                    } catch (Exception e) {
                        cleanupInProgress = false;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("磁盘缓存惰性检查失败", e);
        }
    }

    private void invalidateInternal(String workspaceHash, String internalName) {
        if (workspaceHash == null || internalName == null) {
            return;
        }
        String safeWorkspace = sanitizePathPart(workspaceHash);
        String safeName = sanitizePathPart(internalName);
        Path dir = cacheRoot.resolve("v" + CACHE_SCHEMA_VERSION).resolve(safeWorkspace);
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
                        }
                    });
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void cleanAllInternal() {
        try {
            if (Files.exists(cacheRoot)) {
                try (var files = Files.walk(cacheRoot).sorted(Comparator.reverseOrder())) {
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

    private void cleanIfNeededInternal() {
        try {
            if (!Files.exists(cacheRoot)) {
                return;
            }
            long totalSize;
            try (var stream = Files.walk(cacheRoot)) {
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
            long targetSize = (long) (MAX_CACHE_SIZE_BYTES * 0.7);
            List<Path> files;
            try (var stream = Files.walk(cacheRoot)) {
                files = stream.filter(Files::isRegularFile)
                        .sorted(Comparator.comparingLong(p -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (IOException e) {
                                return Long.MAX_VALUE;
                            }
                        })).toList();
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

    private Path cachePath(String workspaceHash, String internalName,
                           DecompilerTypeEnum engine, String optionsHash) {
        String safeWorkspace = sanitizePathPart(workspaceHash);
        String safeInternalName = sanitizePathPart(internalName);
        String safeEngine = engine.name().toLowerCase();
        return cacheRoot.resolve("v" + CACHE_SCHEMA_VERSION)
                .resolve(safeWorkspace).resolve(safeEngine)
                .resolve(safeInternalName + "." + optionsId(optionsHash) + ".java");
    }
}
