package com.bingbaihanji.fxdecomplie.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 配置 schema 版本迁移 — 集中管理所有版本升级逻辑.
 *
 * <p>每次迁移:
 * <ol>
 *   <li>先备份原文件到同目录(带时间戳)</li>
 *   <li>执行迁移逻辑(空方法体表示无字段变化)</li>
 *   <li>迁移失败时回退到默认配置并保留错误日志</li>
 * </ol>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public final class ConfigMigrator {

    private static final Logger log = LoggerFactory.getLogger(ConfigMigrator.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigMigrator() {
        throw new AssertionError("utility class");
    }

    /**
     * 执行从 fromVersion 到 toVersion 的完整迁移链.
     *
     * @param root        配置 JSON 节点
     * @param fromVersion 当前 schema 版本
     * @param toVersion   目标 schema 版本
     * @return 迁移后的节点
     */
    public static JsonNode migrate(JsonNode root, int fromVersion, int toVersion) {
        JsonNode current = root;
        int v = fromVersion;
        while (v < toVersion) {
            current = applyMigration(current, v, v + 1);
            v++;
            log.info("配置迁移完成: {} → {}", v - 1, v);
        }
        return current;
    }

    private static JsonNode applyMigration(JsonNode root, int from, int to) {
        if (root instanceof ObjectNode obj) {
            obj.put("schemaVersion", to);
        }
        // schema 1→2: 预留 — 当前版本无字段变化
        log.debug("配置迁移步骤: {} → {} (no-op)", from, to);
        return root;
    }

    /**
     * 在迁移前备份配置文件.
     *
     * @param configFile 配置文件路径
     * @return 备份文件路径
     * @throws IOException 备份失败
     */
    public static Path backup(Path configFile) throws IOException {
        String ts = LocalDateTime.now().format(TS_FORMAT);
        Path backup = configFile.resolveSibling("config-" + ts + ".json.bak");
        Files.copy(configFile, backup);
        log.info("配置备份完成: {}", backup);
        return backup;
    }
}
