package com.bingbaihanji.fxdecomplie.mcp.tool;

import java.util.Map;

/**
 * 可映射为 MCP 错误信封的业务异常。
 *
 * @author bingbaihanji
 */
public final class McpToolException extends RuntimeException {

    private final String code;

    private final Map<String, Object> details;

    public McpToolException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code == null ? "INTERNAL_ERROR" : code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
