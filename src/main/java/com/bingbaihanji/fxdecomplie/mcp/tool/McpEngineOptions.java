package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.DecompilerOptions;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 合并应用配置中的引擎选项与 MCP 单次调用覆盖值.
 *
 * @author bingbaihanji
 */
public final class McpEngineOptions {

    private McpEngineOptions() {
        throw new AssertionError("utility class");
    }

    // 合并配置选项与单次调用覆盖值
    public static Map<String, String> merge(AppConfig config, DecompilerTypeEnum engine,
                                            Map<String, String> overrides) {
        Map<String, String> base = DecompilerOptions.forEngine(config, engine);
        if (overrides == null || overrides.isEmpty()) {
            return base;
        }
        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(overrides);
        return Map.copyOf(merged);
    }
}
