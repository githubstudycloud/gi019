package com.example.mcpserver.config;

import com.example.mcpserver.mcp.McpToolProvider;
import com.example.mcpserver.mcp.McpResourceProvider;
import com.example.mcpserver.mcp.McpPromptProvider;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSE transport configuration using Servlet-based transport provider.
 *
 * Endpoints:
 * - GET  /mcp/sse      - SSE event stream (client connects here)
 * - POST /mcp/messages  - Send JSON-RPC messages to server
 */
@Configuration
@ConditionalOnProperty(name = "mcp.transport.sse.enabled", havingValue = "true", matchIfMissing = true)
public class McpSseConfig {

    @Value("${mcp.server.name:spring-boot-mcp-server}")
    private String serverName;

    @Value("${mcp.server.version:1.0.0}")
    private String serverVersion;

    @Bean
    public HttpServletSseServerTransportProvider sseServerTransportProvider(ObjectMapper objectMapper) {
        return HttpServletSseServerTransportProvider.builder()
                .objectMapper(objectMapper)
                .baseUrl("")
                .sseEndpoint("/sse")
                .messageEndpoint("/mcp/messages")
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletSseServerTransportProvider> mcpSseServlet(
            HttpServletSseServerTransportProvider transport) {
        var registration = new ServletRegistrationBean<>(transport, "/sse", "/mcp/messages");
        registration.setName("mcpSseTransport");
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean
    public McpSyncServer sseMcpServer(HttpServletSseServerTransportProvider transport,
                                       McpToolProvider toolProvider,
                                       McpResourceProvider resourceProvider,
                                       McpPromptProvider promptProvider) {
        return McpServer.sync(transport)
                .serverInfo(serverName, serverVersion)
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .resources(true, true)
                        .prompts(true)
                        .logging()
                        .build())
                .tools(toolProvider.getToolSpecifications())
                .resources(resourceProvider.getResourceSpecifications())
                .prompts(promptProvider.getPromptSpecifications())
                .build();
    }
}
