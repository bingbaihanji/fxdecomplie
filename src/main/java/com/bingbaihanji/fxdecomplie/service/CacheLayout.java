package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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

    /** 单个路径段最大长度（留出扩展名余量） */
    private static final int MAX_SEGMENT_LENGTH = 120;

    /** 超长段截断后追加的摘要长度 */
    private static final int SEGMENT_DIGEST_LENGTH = 16;

    /** 超长段保留的原始前缀长度 */
    private static final int SEGMENT_PREFIX_LENGTH = MAX_SEGMENT_LENGTH - SEGMENT_DIGEST_LENGTH - 1;

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
        String fileName = filePrefix(key)
                + safeSegment(key.classFingerprint()) + "."
                + optionsId(key.optionsHash()) + "."
                + key.keyVersion() + ".java";
        return classDirectory(decompileRoot, key).resolve(fileName);
    }

    /** 从内部名提取包名（无斜杠或 null 返回空串） */
    public static String packageOf(String internalName) {
        int slash = internalName == null ? -1 : internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash);
    }

    /** 从内部名提取简单类名（无斜杠时返回原值，null 返回 null） */
    public static String simpleNameOf(String internalName) {
        int slash = internalName == null ? -1 : internalName.lastIndexOf('/');
        return slash < 0 ? internalName : internalName.substring(slash + 1);
    }

    /** 类级缓存目录：工作区/引擎/包路径 */
    public static Path classDirectory(Path decompileRoot, CodeCacheKey key) {
        return decompileRoot.resolve(safeSegment(key.workspaceId()))
                .resolve(key.engine().name().toLowerCase(Locale.ROOT))
                .resolve(packagePath(packageOf(key.internalName())));
    }

    /** 文件名前缀：安全化后的简单类名加分隔点 */
    public static String filePrefix(CodeCacheKey key) {
        return safeSegment(simpleNameOf(key.internalName())) + ".";
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

    /**
     * 安全化单个路径段：拒绝分隔符、控制字符、盘符、尾部空格/点、Windows 保留设备名与超长段.
     */
    public static String safeSegment(String value) {
        if (value == null) {
            return "_";
        }
        String cleaned = stripTrailingDotsAndSpaces(sanitize(value));
        if (cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return "_";
        }
        if (isWindowsReservedName(cleaned)) {
            cleaned = "_" + cleaned;
        }
        if (cleaned.length() > MAX_SEGMENT_LENGTH) {
            cleaned = cleaned.substring(0, SEGMENT_PREFIX_LENGTH) + "~"
                    + ByteUtils.sha256Hex(cleaned).substring(0, SEGMENT_DIGEST_LENGTH);
        }
        return cleaned;
    }

    /** 将控制字符与 Windows 非法字符替换为下划线 */
    private static String sanitize(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '/' || c == '\\' || c == ':' || c == '*'
                    || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 去除尾部的空格/空白与点号（反复剥离，避免去点后再次出现尾部空格） */
    private static String stripTrailingDotsAndSpaces(String value) {
        int end = value.length();
        while (end > 0) {
            char c = value.charAt(end - 1);
            if (c == '.' || Character.isWhitespace(c)) {
                end--;
            } else {
                break;
            }
        }
        return value.substring(0, end);
    }

    /** Windows 保留设备名（大小写不敏感，扩展名前的基名同样受限） */
    private static boolean isWindowsReservedName(String value) {
        int dot = value.indexOf('.');
        String base = dot < 0 ? value : value.substring(0, dot);
        String upper = base.toUpperCase(Locale.ROOT);
        if ("CON".equals(upper) || "PRN".equals(upper) || "AUX".equals(upper) || "NUL".equals(upper)) {
            return true;
        }
        return upper.length() == 4
                && (upper.startsWith("COM") || upper.startsWith("LPT"))
                && upper.charAt(3) >= '1' && upper.charAt(3) <= '9';
    }

    /** 选项哈希已由调用方安全化生成，此处仅去重默认值与非法字符 */
    private static String optionsId(String optionsHash) {
        if (optionsHash == null || optionsHash.isBlank() || "default".equals(optionsHash)) {
            return "default";
        }
        return safeSegment(optionsHash);
    }

    /**
     * 删除目录树：不跟随符号链接/联接点，单条失败只记录不中断，异常不外抛.
     */
    public static void deleteTree(Path dir) {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        CleanupCounter counter = new CleanupCounter();
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                    if (attrs.isSymbolicLink() || attrs.isOther()) {
                        delete(directory, counter);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    delete(file, counter);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    counter.failed++;
                    log.debug("访问缓存路径失败: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exc) {
                    if (exc != null) {
                        counter.failed++;
                        log.debug("遍历缓存目录失败: {}", directory, exc);
                        return FileVisitResult.CONTINUE;
                    }
                    delete(directory, counter);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            counter.failed++;
            log.warn("清理缓存目录失败: {}", dir, e);
            return;
        }
        if (counter.failed > 0) {
            log.warn("清理缓存目录完成: {}, 已删除 {} 项, 失败 {} 项",
                    dir, counter.deleted, counter.failed);
        } else {
            log.info("已清理缓存目录: {}, 已删除 {} 项", dir, counter.deleted);
        }
    }

    /** 删除单个路径（不跟随链接），失败计入失败数 */
    private static void delete(Path path, CleanupCounter counter) {
        try {
            if (Files.deleteIfExists(path)) {
                counter.deleted++;
            }
        } catch (IOException e) {
            counter.failed++;
            log.debug("删除缓存路径失败: {}", path, e);
        }
    }

    /** 清理计数 */
    private static final class CleanupCounter {

        private int deleted;

        private int failed;
    }
}
