package com.bingbaihanji.fxdecomplie.mcp.transport;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 无鉴权健康检查，只返回服务名、版本和状态。
 *
 * @author bingbaihanji
 */
public final class McpHealthServlet extends HttpServlet {

    private final String version;

    public McpHealthServlet(String version) {
        this.version = version;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"name\":\"fxdecompiler\",\"version\":\""
                + version + "\",\"status\":\"ok\"}");
    }
}
