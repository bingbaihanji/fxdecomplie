package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.mcp.job.McpJobManager;
import com.bingbaihanji.fxdecomplie.service.decompile.ClassSourceService;
import com.bingbaihanji.fxdecomplie.service.decompile.DecompileCacheService;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceSessionRegistry;

import java.util.Objects;

/**
 * 单次 MCP 工具调用的共享依赖。
 *
 * @author bingbaihanji
 */
public record McpToolContext(WorkspaceSessionRegistry sessions,
                             AppConfig config,
                             ClassSourceService classSource,
                             DecompileCacheService decompileCache,
                             McpJobManager jobs,
                             int toolCount,
                             String serverVersion) {

    public McpToolContext {
        Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(classSource, "classSource");
        Objects.requireNonNull(decompileCache, "decompileCache");
        Objects.requireNonNull(jobs, "jobs");
        toolCount = Math.max(0, toolCount);
        serverVersion = serverVersion == null ? "unknown" : serverVersion;
    }
}
