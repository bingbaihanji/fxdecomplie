package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

/**
 * 缓存磁盘布局 — 反编译、索引、JADX 临时目录互不重叠.
 *
 * <pre>
 * cache/
 * ├── decompile/v1/&lt;workspaceId&gt;/&lt;engine&gt;/&lt;package&gt;/&lt;Simple&gt;.&lt;hash&gt;.&lt;options&gt;.&lt;ver&gt;.java
 * ├── index/v1/&lt;fingerprint&gt;.idx.gz
 * └── jadx/runtime/...   （不由缓存清理触碰）
 * </pre>
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
public final class CacheLayout {

    private static final Logger log = LoggerFactory.getLogger(CacheLayout.class);

    private static final String DECOMPILE_DIR = "decompile";
    private static final String INDEX_DIR = "index";
    private static final String VERSION = "v1";
    private static final String LEGACY_DECOMPILE_DIR = "v2";
    private static final String LEGACY_INDEX_DIR = "index-cache";

    private CacheLayout() {
        throw new AssertionError("utility class");
    }

    public static Path cacheRoot() {
        return AppPaths.cacheDir();
    }

    public static Path decompileDir() {
        return cacheRoot().resolve(DECOMPILE_DIR).resolve(VERSION);
    }

    public static Path indexDir() {
        return cacheRoot().resolve(INDEX_DIR).resolve(VERSION);
    }

    public static Path jadxDir() {
        return AppPaths.jadxRuntimeDir();
    }

    /** 启动时清理旧布局；不触碰 decompile/index/jadx 新布局 */
    public static void cleanupLegacy() {
        cleanupLegacy(cacheRoot());
    }

    /** 包级可见，供测试注入根目录 */
    static void cleanupLegacy(Path root) {
        deleteTree(root.resolve(LEGACY_DECOMPILE_DIR));
        deleteTree(root.resolve(LEGACY_INDEX_DIR));
    }

    /** 构造反编译缓存文件路径；内部名按包路径分目录，文件名保留指纹/选项/键版本 */
    public static Path decompilePath(Path decompileRoot, CodeCacheKey key) {
        String internalName = key.internalName();
        int slash = internalName.lastIndexOf('/');
        String packageName = slash < 0 ? "" : internalName.substring(0, slash);
        String simpleName = slash < 0 ? internalName : internalName.substring(slash + 1);
        Path directory = decompileRoot
                .resolve(safeSegment(key.workspaceId()))
                .resolve(key.engine().name().toLowerCase(Locale.ROOT))
                .resolve(packagePath(packageName));
        String fileName = safeSegment(simpleName) + "."
                + safeSegment(key.classFingerprint()) + "."
                + optionsId(key.optionsHash()) + "."
                + key.keyVersion() + ".java";
        return directory.resolve(fileName);
    }

    /** 包的相对路径（空包返回空路径） */
    public static Path packagePath(String packageName) {
        Path path = Path.of("");
        if (packageName == null || packageName.isBlank()) {
            return path;
        }
        for (String segment : packageName.split("/")) {
            if (!segment.isBlank()) {
                path = path.resolve(safeSegment(segment));
            }
        }
        return path;
    }

    /** 安全化单个路径段：拒绝分隔符、盘符、.. 与空段 */
    public static String safeSegment(String value) {
        if (value == null || value.isBlank() || ".".equals(value) || "..".equals(value)) {
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

    private static void deleteTree(Path dir) {
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (var files = Files.walk(dir).sorted(Comparator.reverseOrder())) {
                files.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
            log.info("已清理旧缓存布局: {}", dir);
        } catch (IOException e) {
            log.debug("清理旧缓存布局失败: {}", dir, e);
        }
    }
}
