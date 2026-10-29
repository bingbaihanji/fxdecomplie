package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheScope;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheStore;
import com.bingbaihanji.fxdecomplie.util.io.AtomicFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L3 磁盘持久化缓存 — 按 工作区/引擎/包 分层存放反编译源码,
 * 写入后交由 {@link DiskQuotaGuard} 在后台按 mtime LRU 淘汰,并累计命中/未命中统计.
 *
 * <p>结构见 {@link CacheLayout};实现 {@link CodeCacheStore} 以支持注入和测试替换.</p>
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class DiskCodeCache implements CodeCacheStore {

    private static final Logger log = LoggerFactory.getLogger(DiskCodeCache.class);

    /** 统计快照中的分类名 */
    private static final String STATS_NAME = "decompile-l3";

    private final Path decompileRoot;
    private final DiskQuotaGuard quotaGuard;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public DiskCodeCache(Path decompileRoot, long quotaBytes) {
        this(decompileRoot, DiskQuotaGuard.background(decompileRoot, quotaBytes,
                BackgroundTasks.PoolType.IO, "DiskQuota-check"));
    }

    DiskCodeCache(Path decompileRoot, DiskQuotaGuard quotaGuard) {
        this.decompileRoot = Objects.requireNonNull(decompileRoot, "decompileRoot");
        this.quotaGuard = Objects.requireNonNull(quotaGuard, "quotaGuard");
    }

    // ─── CodeCacheStore 实现 ───

    @Override
    public Optional<String> load(CodeCacheKey key) {
        String content = loadFile(CacheLayout.decompilePath(decompileRoot, key));
        if (content == null) {
            misses.incrementAndGet();
            return Optional.empty();
        }
        hits.incrementAndGet();
        return Optional.of(content);
    }

    @Override
    public void save(CodeCacheKey key, String source) {
        if (saveFile(CacheLayout.decompilePath(decompileRoot, key), source)) {
            quotaGuard.onWrite();
        }
    }

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
        switch (scope) {
            case CLASS -> invalidateClass(key);
            case WORKSPACE -> deleteTree(
                    decompileRoot.resolve(CacheLayout.safeSegment(key.workspaceId())));
            case ALL -> clearAll();
        }
    }

    // ─── 诊断与管理 ───

    /** 反编译缓存根目录(供诊断展示) */
    public Path root() {
        return decompileRoot;
    }

    /** 清空反编译缓存子树(不影响缓存根目录下的姊妹缓存),并重置配额守卫节流状态 */
    public void clearAll() {
        deleteTree(decompileRoot);
        quotaGuard.reset();
    }

    /** 当前 L3 统计快照:文件数、字节数、命中与未命中次数 */
    public CacheStats stats() {
        return quotaGuard.stats(STATS_NAME, hits.get(), misses.get());
    }

    // ─── 内部实现 ───

    /** 读取缓存文件；不存在、内容为空或 IO 异常均视为未命中 */
    private String loadFile(Path file) {
        String content;
        try {
            if (!Files.exists(file)) {
                return null;
            }
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("加载磁盘代码缓存失败: {}", file, e);
            return null;
        }
        if (content.isEmpty()) {
            return null;
        }
        // 读取即视为使用,刷新 mtime 以便配额守卫按 LRU 近似淘汰;
        // 刷新失败不影响已成功读取的内容
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException e) {
            log.debug("刷新磁盘缓存访问时间失败: {}", file, e);
        }
        return content;
    }

    /** 原子写入缓存文件;null/空白源码或写入失败返回 false */
    private boolean saveFile(Path file, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return false;
        }
        try {
            Files.createDirectories(file.getParent());
            AtomicFile af = new AtomicFile(file.toFile());
            af.write(os -> {
                try {
                    os.write(sourceCode.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            return true;
        } catch (IOException | RuntimeException e) {
            log.debug("保存磁盘代码缓存失败: {}", file, e);
            return false;
        }
    }

    /** 按类失效:删除该工作区下所有引擎目录中该类的全部指纹/选项/键版本变体 */
    private void invalidateClass(CodeCacheKey key) {
        Path workspaceDir = decompileRoot.resolve(CacheLayout.safeSegment(key.workspaceId()));
        if (!Files.exists(workspaceDir)) {
            return;
        }
        Path packageDir = CacheLayout.packagePath(CacheLayout.packageOf(key.internalName()));
        String prefix = CacheLayout.filePrefix(key);
        try (var engineDirs = Files.list(workspaceDir)) {
            engineDirs.filter(Files::isDirectory).forEach(engineDir -> {
                Path classDir = engineDir.resolve(packageDir);
                if (!Files.exists(classDir)) {
                    return;
                }
                try (var files = Files.list(classDir)) {
                    files.filter(f -> f.getFileName().toString().startsWith(prefix))
                            .forEach(f -> {
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

    /** 删除目录树(不跟随链接),单条失败忽略 */
    private void deleteTree(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var files = Files.walk(dir).sorted(Comparator.reverseOrder())) {
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
}
