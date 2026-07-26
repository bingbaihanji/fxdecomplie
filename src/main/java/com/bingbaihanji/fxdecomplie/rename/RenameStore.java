package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.util.io.AtomicFile;
import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;
import com.bingbaihanji.utils.json.JSONUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 重命名数据持久化 — 负责文件路径解析、JSON 序列化和原子写入。
 *
 * <p>从 {@link RenameService} 中提取出的纯 I/O 层，不包含缓存和业务逻辑。</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
final class RenameStore {

    private static final Logger log = LoggerFactory.getLogger(RenameStore.class);
    private static final ObjectMapper MAPPER = createMapper();
    private static final TypeReference<List<RenameEntry>> LIST_TYPE = new TypeReference<>() {};
    private static volatile Path rootDir;

    private RenameStore() {
        throw new AssertionError("utility class");
    }

    // ─── ObjectMapper ───

    static ObjectMapper mapper() {
        return MAPPER;
    }

    private static ObjectMapper createMapper() {
        return JSONUtils.getPrettyMapper().copy()
                .registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                        .addSerializer(RenameEntry.class,
                                new RenameService.RenameEntrySerializer())
                        .addDeserializer(RenameEntry.class,
                                new RenameService.RenameEntryDeserializer()));
    }

    // ─── 根目录管理 ───

    static Path getRootDir() {
        return rootDir != null ? rootDir : AppPaths.renamesDir();
    }

    static void setRootDir(Path dir) {
        rootDir = dir;
    }

    // ─── 路径解析 ───

    static Path resolveFile(String workspaceHash) {
        return getRootDir().resolve(workspaceHashDigest(workspaceHash) + ".json");
    }

    static Path resolveLegacyFile(String workspaceHash) {
        String safe = nullToEmpty(workspaceHash).replaceAll("[\\\\/:*?\"<>|]", "_");
        return getRootDir().resolve(safe + ".json");
    }

    private static String workspaceHashDigest(String workspaceHash) {
        return ByteUtils.sha256Hex(nullToEmpty(workspaceHash)).substring(0, 32);
    }

    // ─── 读取 ───

    /**
     * 从磁盘读取原始重命名条目列表（不包含缓存和标准化）。
     */
    static List<RenameEntry> readRaw(String workspaceHash) {
        Path file = resolveFile(workspaceHash);
        if (!Files.isRegularFile(file)) {
            Path legacyFile = resolveLegacyFile(workspaceHash);
            if (!Files.isRegularFile(legacyFile)) {
                return new ArrayList<>();
            }
            file = legacyFile;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<RenameEntry> list = MAPPER.readValue(json, LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (IOException | RuntimeException e) {
            log.debug("读取重命名文件失败: {}", file, e);
            return new ArrayList<>();
        }
    }

    // ─── 写入 ───

    /**
     * 原子写入重命名条目列表到磁盘。
     * 写入前自动创建快照备份。
     */
    static boolean writeFile(String workspaceHash, List<RenameEntry> list) {
        Path file = resolveFile(workspaceHash);
        try {
            Files.createDirectories(file.getParent());
            snapshotRenameFile(file);
            AtomicFile af = new AtomicFile(file.toFile());
            af.write(os -> {
                try {
                    os.write(MAPPER.writeValueAsString(list).getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            return true;
        } catch (IOException | RuntimeException e) {
            log.error("保存重命名失败", e);
            return false;
        }
    }

    private static void snapshotRenameFile(Path file) {
        if (!Files.isRegularFile(file)) return;
        Path backup = file.resolveSibling(
                file.getFileName() + ".bak." + System.currentTimeMillis());
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.debug("创建重命名快照失败: {}", backup, e);
        }
    }

    // ─── 清理 ───

    /** 删除指定工作区的重命名文件 */
    static boolean deleteFile(String workspaceHash) {
        Path file = resolveFile(workspaceHash);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.debug("删除重命名文件失败: {}", file, e);
            return false;
        }
    }

    /** 恢复最近的备份 */
    static Path findLatestBackup(String workspaceHash) {
        Path file = resolveFile(workspaceHash);
        Path parent = file.getParent();
        if (parent == null || !Files.isDirectory(parent)) return null;
        String prefix = file.getFileName() + ".bak.";
        try (var stream = Files.list(parent)) {
            return stream
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .max(java.util.Comparator.comparing(
                            path -> path.getFileName().toString()))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // ─── 工具 ───

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
