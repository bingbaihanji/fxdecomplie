package com.bingbaihanji.fxdecomplie.mcp.tool;

import java.util.Map;

/**
 * MCP 工具错误,返回给模型时只包含可读信息,不包含 Java 堆栈.
 *
 * @author bingbaihanji
 */
public record McpToolError(String code, String message, Map<String, Object> details) {

    public McpToolError {
        code = code == null ? "INTERNAL_ERROR" : code;
        message = message == null ? "" : message;
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
