package com.bingbaihanji.fxdecomplie.mcp;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.mcp.auth.McpAuthFilter;
import com.bingbaihanji.fxdecomplie.mcp.tool.AnalysisTools;
import com.bingbaihanji.fxdecomplie.mcp.tool.ClassTools;
import com.bingbaihanji.fxdecomplie.mcp.tool.ComparisonTools;
import com.bingbaihanji.fxdecomplie.mcp.tool.DeobfuscationTools;
import com.bingbaihanji.fxdecomplie.mcp.tool.ExportTools;
import com.bingbaihanji.fxdecomplie.mcp.tool.FullTextSearchTools;
import com.bingbaihanji.fxdecomplie.mcp.tool.JobTools;
import com.bingbaihanji.fxdecomplie.mcp.tool.McpToolContext;
import com.bingbaihanji.fxdecomplie.mcp.tool.McpToolDefinition;
import com.bingbaihanji.fxdecomplie.mcp.tool.McpToolRegistry;
import com.bingbaihanji.fxdecomplie.mcp.tool.WorkspaceTools;
import com.bingbaihanji.fxdecomplie.mcp.transport.McpHealthServlet;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceSessionRegistry;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

/**
 * MCP 服务生命周期：配置校验、Jetty 绑定、Streamable HTTP / legacy SSE、
 * Bearer Token 鉴权、健康检查和工具注册。
 *
 * @author bingbaihanji
 */
public final class McpServerManager {

    private static final Logger log = LoggerFactory.getLogger(McpServerManager.class);

    private static final String SERVER_VERSION = "1.0-SNAPSHOT";

    private final AppConfig config;

    private final WorkspaceSessionRegistry sessions;

    private final Object lifecycleLock = new Object();

    private volatile Server jettyServer;

    private volatile List<McpSyncServer> mcpServers = List.of();

    private volatile McpServerStatus status;

    private volatile com.bingbaihanji.fxdecomplie.service.decompile.ClassSourceService classSource;

    private volatile com.bingbaihanji.fxdecomplie.service.decompile.DecompileCacheService decompileCache;

    private volatile com.bingbaihanji.fxdecomplie.mcp.job.McpJobManager jobs;

    private volatile String runningFingerprint = "";

    public McpServerManager(AppConfig config, WorkspaceSessionRegistry sessions) {
        this.config = config;
        this.sessions = sessions;
        this.status = McpServerStatus.stopped(config.mcp().bindHost(), config.mcp().port(),
                "stopped");
    }

    public McpServerStatus status() {
        return status;
    }

    /** 由应用装配层注入共享反编译缓存；MCP 不反向依赖 app 包。 */
    public void setDecompileCache(
            com.bingbaihanji.fxdecomplie.service.decompile.DecompileCacheService cache) {
        this.decompileCache = cache;
    }

    /** 配置变化后调用；相同配置不重启。 */
    public void applyConfig() {
        synchronized (lifecycleLock) {
            if (!config.mcp().enabled()) {
                stop();
                return;
            }
            ensureToken();
            if (jettyServer != null && configFingerprint().equals(runningFingerprint)) {
                return;
            }
            if (jettyServer != null || jobs != null) {
                stop();
            }
            start();
        }
    }

    /** 幂等启动；已运行且配置未变化则直接返回。 */
    public void startIfEnabled() {
        synchronized (lifecycleLock) {
            if (!config.mcp().enabled()) {
                return;
            }
            if (jettyServer != null && configFingerprint().equals(runningFingerprint)) {
                return;
            }
            ensureToken();
            if (jettyServer != null || jobs != null || !mcpServers.isEmpty()) {
                stop();
            }
            start();
        }
    }

    private String configFingerprint() {
        var mcp = config.mcp();
        return String.join("|",
                String.valueOf(mcp.enabled()), mcp.bindHost(), String.valueOf(mcp.port()),
                String.valueOf(mcp.streamableHttpEnabled()),
                String.valueOf(mcp.legacySseEnabled()), mcp.authToken(),
                mcp.advertisedHost());
    }

    private void ensureToken() {
        if (config.mcp().authToken() == null || config.mcp().authToken().isBlank()) {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            config.mcp().authToken("fxd_" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(bytes));
            config.save();
        }
    }

    private void start() {
        String host = config.mcp().bindHost();
        int port = config.mcp().port();
        Server server = null;
        List<McpSyncServer> servers = new ArrayList<>();
        try {
            if (!config.mcp().streamableHttpEnabled() && !config.mcp().legacySseEnabled()) {
                throw new IllegalStateException("至少启用一种 MCP 协议");
            }
            if (decompileCache == null) {
                throw new IllegalStateException("MCP decompile cache is not wired");
            }
            this.classSource = new com.bingbaihanji.fxdecomplie.service.decompile.ClassSourceService(
                    config, decompileCache);
            this.jobs = new com.bingbaihanji.fxdecomplie.mcp.job.McpJobManager();
            List<McpServerFeatures.SyncToolSpecification> specs = toolSpecifications();
            server = new Server();
            ServerConnector connector = new ServerConnector(server);
            connector.setHost(host);
            connector.setPort(port);
            connector.setIdleTimeout(0);
            server.addConnector(connector);
            server.setStopTimeout(5_000);

            ServletContextHandler context = new ServletContextHandler(
                    ServletContextHandler.NO_SESSIONS);
            context.setContextPath("/");
            FilterHolder auth = new FilterHolder(new McpAuthFilter(config.mcp().authToken()));
            auth.setAsyncSupported(true);
            context.addFilter(auth, "/*", EnumSet.of(DispatcherType.REQUEST));

            DefaultServerTransportSecurityValidator validator = securityValidator(host, port);

            if (config.mcp().streamableHttpEnabled()) {
                HttpServletStreamableServerTransportProvider provider =
                        HttpServletStreamableServerTransportProvider.builder()
                                .mcpEndpoint("/mcp")
                                .keepAliveInterval(Duration.ofSeconds(30))
                                .securityValidator(validator)
                                .build();
                ServletHolder holder = new ServletHolder(provider);
                holder.setAsyncSupported(true);
                context.addServlet(holder, "/mcp");
                servers.add(McpServer.sync(provider)
                        .serverInfo("fxdecompiler", SERVER_VERSION)
                        .instructions(instructions())
                        .tools(specs)
                        .build());
            }
            if (config.mcp().legacySseEnabled()) {
                HttpServletSseServerTransportProvider provider =
                        HttpServletSseServerTransportProvider.builder()
                                .messageEndpoint("/message")
                                .sseEndpoint("/sse")
                                .keepAliveInterval(Duration.ofSeconds(30))
                                .securityValidator(validator)
                                .build();
                ServletHolder sseHolder = new ServletHolder(provider);
                sseHolder.setAsyncSupported(true);
                context.addServlet(sseHolder, "/sse");
                ServletHolder messageHolder = new ServletHolder(provider);
                messageHolder.setAsyncSupported(true);
                context.addServlet(messageHolder, "/message");
                servers.add(McpServer.sync(provider)
                        .serverInfo("fxdecompiler", SERVER_VERSION)
                        .instructions(instructions())
                        .tools(specs)
                        .build());
            }

            context.addServlet(new ServletHolder(new McpHealthServlet(SERVER_VERSION)), "/health");
            server.setHandler(context);
            server.start();

            synchronized (lifecycleLock) {
                this.jettyServer = server;
                this.mcpServers = List.copyOf(servers);
                this.runningFingerprint = configFingerprint();
                this.status = McpServerStatus.running(host, port);
            }
            log.info("MCP 服务已启动: {}:{}, streamableHttp={}, legacySse={}", host, port,
                    config.mcp().streamableHttpEnabled(), config.mcp().legacySseEnabled());
        } catch (Throwable e) {
            var currentJobs = jobs;
            jobs = null;
            if (currentJobs != null) {
                currentJobs.close();
            }
            for (McpSyncServer mcpServer : servers) {
                try {
                    mcpServer.closeGracefully();
                } catch (Throwable ignored) {
                }
            }
            if (server != null) {
                try {
                    server.stop();
                } catch (Throwable ignored) {
                }
            }
            status = McpServerStatus.stopped(host, port,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            log.error("MCP 服务启动失败: {}:{}", host, port, e);
        }
    }

    private List<McpServerFeatures.SyncToolSpecification> toolSpecifications() {
        McpToolRegistry registry = new McpToolRegistry(
                concatenate(WorkspaceTools.definitions(), ClassTools.definitions(),
                        AnalysisTools.definitions(), ComparisonTools.definitions(),
                        DeobfuscationTools.definitions(), ExportTools.definitions(),
                        FullTextSearchTools.definitions(), JobTools.definitions()));
        McpToolContext context = new McpToolContext(sessions, config, classSource,
                decompileCache, jobs, registry.definitions().size(), SERVER_VERSION);
        return registry.definitions().stream()
                .map(definition -> new McpServerFeatures.SyncToolSpecification(
                        definition.toSdkTool(),
                        (exchange, request) -> registry.invoke(context, definition.name(),
                                request.arguments()).toCallToolResult()))
                .toList();
    }

    private DefaultServerTransportSecurityValidator securityValidator(String host, int port) {
        String advertised = config.mcp().advertisedHost();
        List<String> hosts = new ArrayList<>();
        hosts.add("127.0.0.1:" + port);
        hosts.add("localhost:" + port);
        if (!"0.0.0.0".equals(host)) {
            hosts.add(host + ":" + port);
        }
        hosts.add(advertised + ":" + port);
        List<String> origins = List.of(
                "http://127.0.0.1:" + port,
                "http://localhost:" + port,
                "http://" + advertised + ":" + port);
        return DefaultServerTransportSecurityValidator.builder()
                .allowedHosts(hosts)
                .allowedOrigins(origins)
                .build();
    }

    @SafeVarargs
    private static List<McpToolDefinition> concatenate(List<McpToolDefinition>... groups) {
        List<McpToolDefinition> result = new ArrayList<>();
        for (List<McpToolDefinition> group : groups) {
            result.addAll(group);
        }
        return List.copyOf(result);
    }

    private static String instructions() {
        return "FxDecompiler exposes Java bytecode analysis tools. "
                + "Start with list_workspaces or open_workspace. "
                + "Use get_server_info for capabilities. "
                + "Prefer metadata before full source; source tools are paginated. "
                + "Long export/comparison/search tasks return a job id.";
    }

    /** 优雅停止；可重复调用。 */
    public void stop() {
        List<McpSyncServer> servers;
        Server server;
        synchronized (lifecycleLock) {
            servers = mcpServers;
            server = jettyServer;
            mcpServers = List.of();
            jettyServer = null;
            runningFingerprint = "";
            status = McpServerStatus.stopped(config.mcp().bindHost(), config.mcp().port(),
                    "stopped");
        }
        var currentJobs = jobs;
        jobs = null;
        if (currentJobs != null) {
            currentJobs.close();
        }
        for (McpSyncServer mcpServer : servers) {
            try {
                mcpServer.closeGracefully();
            } catch (Exception e) {
                log.warn("关闭 MCP 会话失败", e);
            }
        }
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                log.warn("停止 Jetty 失败", e);
            }
        }
        log.info("MCP 服务已停止");
    }
}
