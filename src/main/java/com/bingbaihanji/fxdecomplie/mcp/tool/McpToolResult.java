package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.utils.json.JSONUtils;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 工具统一返回信封。
 *
 * @author bingbaihanji
 */
public record McpToolResult(boolean ok, Object data, McpToolError error) {

    public static McpToolResult ok(Object data) {
        return new McpToolResult(true, data, null);
    }

    public static McpToolResult error(String code, String message,
                                      Map<String, Object> details) {
        return new McpToolResult(false, null, new McpToolError(code, message, details));
    }

    public McpSchema.CallToolResult toCallToolResult() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", ok);
        if (ok) {
            payload.put("data", data);
        } else {
            payload.put("error", Map.of(
                    "code", error.code(),
                    "message", error.message(),
                    "details", error.details()));
        }
        String text = JSONUtils.toPrettyJson(payload);
        return McpSchema.CallToolResult.builder()
                .addTextContent(text)
                .structuredContent(payload)
                .isError(!ok)
                .build();
    }
}
