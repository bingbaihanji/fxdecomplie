package com.bingbaihanji.fxdecomplie.mcp.tool;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * 工具定义:SDK 元数据 + 本地处理器.
 *
 * @author bingbaihanji
 */
public record McpToolDefinition(String name, String description,
                                Map<String, Object> inputSchema,
                                BiFunction<McpToolContext, Map<String, Object>, McpToolResult> handler) {

    public McpToolDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        inputSchema = inputSchema == null ? Map.of("type", "object") : Map.copyOf(inputSchema);
        Objects.requireNonNull(handler, "handler");
    }

    public McpSchema.Tool toSdkTool() {
        return McpSchema.Tool.builder(name, inputSchema)
                .description(description)
                .build();
    }
}
