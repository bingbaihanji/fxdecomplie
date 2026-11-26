package com.bingbaihanji.fxdecomplie.mcp.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具注册表;按名称分发,未知工具返回 INVALID_ARGUMENT.
 *
 * @author bingbaihanji
 */
public final class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private final Map<String, McpToolDefinition> tools = new LinkedHashMap<>();

    public McpToolRegistry(List<McpToolDefinition> definitions) {
        for (McpToolDefinition definition : definitions) {
            tools.put(definition.name(), definition);
        }
    }

    public List<McpToolDefinition> definitions() {
        return List.copyOf(tools.values());
    }

    public McpToolResult invoke(McpToolContext context, String name,
                                Map<String, Object> arguments) {
        McpToolDefinition tool = tools.get(name);
        if (tool == null) {
            return McpToolResult.error("INVALID_ARGUMENT", "Unknown tool: " + name, Map.of());
        }
        long start = System.currentTimeMillis();
        String workspaceId = arguments == null ? "" : String.valueOf(
                arguments.getOrDefault("workspaceId", ""));
        try {
            McpToolResult result = tool.handler().apply(context,
                    arguments == null ? Map.of() : arguments);
            log.info("[MCP-AUDIT] tool={}, workspace={}, ok={}, elapsedMs={}", name,
                    workspaceId, result.ok(), System.currentTimeMillis() - start);
            return result;
        } catch (McpToolException e) {
            log.info("[MCP-AUDIT] tool={}, workspace={}, ok=false, code={}, elapsedMs={}",
                    name, workspaceId, e.code(), System.currentTimeMillis() - start);
            return McpToolResult.error(e.code(), e.getMessage(), e.details());
        } catch (Exception e) {
            log.error("[MCP-AUDIT] tool={}, workspace={}, ok=false, elapsedMs={}", name,
                    workspaceId, System.currentTimeMillis() - start, e);
            return McpToolResult.error("INTERNAL_ERROR",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    Map.of());
        }
    }
}
