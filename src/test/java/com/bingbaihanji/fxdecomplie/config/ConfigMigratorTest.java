package com.bingbaihanji.fxdecomplie.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigMigrator")
class ConfigMigratorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("备份配置文件")
    void shouldBackupConfigFile(@TempDir Path tmpDir) throws Exception {
        Path configFile = tmpDir.resolve("config.json");
        Files.writeString(configFile, "{\"schemaVersion\":1}");

        Path backup = ConfigMigrator.backup(configFile);

        assertTrue(Files.exists(backup), "备份文件应存在");
        assertTrue(backup.getFileName().toString().endsWith(".json.bak"),
                "备份文件应以 .json.bak 结尾");
    }

    @Test
    @DisplayName("迁移 schemaVersion 从 1 到 2")
    void shouldMigrateSchemaVersion() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("language", "zh-CN");

        var result = ConfigMigrator.migrate(root, 1, 2);

        assertEquals(2, result.get("schemaVersion").asInt(),
                "schemaVersion 应更新到 2");
        assertEquals("zh-CN", result.get("language").asText(),
                "其他字段应保持不变");
    }

    @Test
    @DisplayName("迁移跳过已是最新版本")
    void shouldSkipWhenAlreadyLatest() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", 2);

        var result = ConfigMigrator.migrate(root, 2, 2);

        assertEquals(2, result.get("schemaVersion").asInt());
    }
}
