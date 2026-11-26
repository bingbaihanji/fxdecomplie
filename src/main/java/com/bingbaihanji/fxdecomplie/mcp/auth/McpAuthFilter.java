package com.bingbaihanji.fxdecomplie.mcp.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * MCP HTTP Bearer Token 鉴权.
 *
 * <p>/health 不需要鉴权;其余请求必须带 Authorization: Bearer &lt;token&gt;.
 * token 使用常量时间比较,不记录到日志.</p>
 *
 * @author bingbaihanji
 */
public final class McpAuthFilter implements Filter {

    private static final String HEALTH_PATH = "/health";

    private final String expectedToken;

    public McpAuthFilter(String expectedToken) {
        this.expectedToken = expectedToken == null ? "" : expectedToken;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        if (httpRequest.getRequestURI().endsWith(HEALTH_PATH)) {
            chain.doFilter(request, response);
            return;
        }
        String header = httpRequest.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                || expectedToken.isBlank()
                || !constantTimeEquals(expectedToken, header.substring(7).trim())) {
            httpResponse.setHeader("WWW-Authenticate", "Bearer realm=\"fxdecompiler\"");
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }
        chain.doFilter(request, response);
    }
}
