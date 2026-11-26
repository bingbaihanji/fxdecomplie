package com.bingbaihanji.fxdecomplie.mcp.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具参数读取与校验辅助方法.
 *
 * @author bingbaihanji
 */
public final class McpArgs {

    private McpArgs() {
        throw new AssertionError("utility class");
    }

    public static String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new McpToolException("INVALID_ARGUMENT", key + " is required", Map.of());
        }
        return value.toString().trim();
    }

    public static String optionalString(Map<String, Object> args, String key, String fallback) {
        Object value = args.get(key);
        return value == null || value.toString().isBlank()
                ? fallback : value.toString().trim();
    }

    public static int intValue(Map<String, Object> args, String key, int fallback,
                               int min, int max) {
        Object value = args.get(key);
        int parsed = fallback;
        if (value != null) {
            try {
                parsed = Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException e) {
                throw new McpToolException("INVALID_ARGUMENT",
                        key + " must be an integer", Map.of("value", value.toString()));
            }
        }
        if (parsed < min || parsed > max) {
            throw new McpToolException("INVALID_ARGUMENT",
                    key + " must be between " + min + " and " + max,
                    Map.of("value", parsed));
        }
        return parsed;
    }

    public static boolean booleanValue(Map<String, Object> args, String key, boolean fallback) {
        Object value = args.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> stringMap(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    public static List<String> stringList(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(item.toString());
            }
        }
        return List.copyOf(result);
    }

}
