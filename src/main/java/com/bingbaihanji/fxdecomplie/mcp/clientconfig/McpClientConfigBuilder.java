package com.bingbaihanji.fxdecomplie.mcp.clientconfig;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.utils.json.JSONUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生成 Claude Code / Cursor 可用的 mcpServers JSON。
 *
 * @author bingbaihanji
 */
public final class McpClientConfigBuilder {

    private McpClientConfigBuilder() {
        throw new AssertionError("utility class");
    }

    public static String buildJson(AppConfig config, boolean legacySse) {
        var mcp = config.mcp();
        String host = mcp.advertisedHost();
        int port = mcp.port();
        String url = legacySse
                ? "http://" + host + ":" + port + "/sse"
                : "http://" + host + ":" + port + "/mcp";

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", legacySse ? "sse" : "http");
        server.put("url", url);
        server.put("headers", Map.of(
                "Authorization", "Bearer " + mcp.authToken()));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mcpServers", Map.of("fxdecompiler", server));
        return JSONUtils.toPrettyJson(root);
    }
}
