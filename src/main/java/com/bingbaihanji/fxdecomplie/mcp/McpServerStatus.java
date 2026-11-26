package com.bingbaihanji.fxdecomplie.mcp;

/**
 * MCP 服务运行状态快照.
 *
 * @param running  是否运行
 * @param bindHost 监听地址
 * @param port     监听端口
 * @param message  状态说明或错误信息
 * @author bingbaihanji
 */
public record McpServerStatus(boolean running, String bindHost, int port, String message) {

    public static McpServerStatus stopped(String bindHost, int port, String message) {
        return new McpServerStatus(false, bindHost, port, message);
    }

    public static McpServerStatus running(String bindHost, int port) {
        return new McpServerStatus(true, bindHost, port, "running");
    }
}
