package com.example.mcpserver.config;

import com.example.mcpserver.mcp.McpToolProvider;
import com.example.mcpserver.mcp.McpResourceProvider;
import com.example.mcpserver.mcp.McpPromptProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Value("${mcp.server.name:spring-boot-mcp-server}")
    private String serverName;

    @Value("${mcp.server.version:1.0.0}")
    private String serverVersion;

    @Bean
    @ConditionalOnProperty(name = "mcp.transport.stdio.enabled", havingValue = "true")
    public McpSyncServer stdioMcpServer(McpToolProvider toolProvider,
                                         McpResourceProvider resourceProvider,
                                         McpPromptProvider promptProvider) {
        var transportProvider = new StdioServerTransportProvider(new ObjectMapper());

        return McpServer.sync(transportProvider)
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
