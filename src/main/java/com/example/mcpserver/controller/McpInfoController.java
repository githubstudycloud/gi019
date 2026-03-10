package com.example.mcpserver.controller;

import com.example.mcpserver.mcp.McpToolProvider;
import com.example.mcpserver.mcp.McpResourceProvider;
import com.example.mcpserver.mcp.McpPromptProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Provides MCP server metadata via REST API.
 * Useful for debugging and discovery.
 */
@RestController
@RequestMapping("/api/mcp")
public class McpInfoController {

    @Value("${mcp.server.name:spring-boot-mcp-server}")
    private String serverName;

    @Value("${mcp.server.version:1.0.0}")
    private String serverVersion;

    @Value("${mcp.transport.sse.enabled:true}")
    private boolean sseEnabled;

    @Value("${mcp.transport.streamable-http.enabled:false}")
    private boolean streamableHttpEnabled;

    @Value("${mcp.transport.stdio.enabled:false}")
    private boolean stdioEnabled;

    private final McpToolProvider toolProvider;
    private final McpResourceProvider resourceProvider;
    private final McpPromptProvider promptProvider;

    public McpInfoController(McpToolProvider toolProvider,
                             McpResourceProvider resourceProvider,
                             McpPromptProvider promptProvider) {
        this.toolProvider = toolProvider;
        this.resourceProvider = resourceProvider;
        this.promptProvider = promptProvider;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        var transports = new ArrayList<Map<String, Object>>();
        if (sseEnabled) {
            transports.add(Map.of(
                    "type", "SSE",
                    "endpoint", "/sse",
                    "messageEndpoint", "/mcp/messages"));
        }
        if (streamableHttpEnabled) {
            transports.add(Map.of(
                    "type", "Streamable HTTP",
                    "endpoint", "/mcp/stream"));
        }
        if (stdioEnabled) {
            transports.add(Map.of(
                    "type", "Stdio",
                    "description", "Standard I/O transport for subprocess mode"));
        }

        return Map.of(
                "server", Map.of("name", serverName, "version", serverVersion),
                "transports", transports,
                "tools", toolProvider.getToolDefinitions(),
                "resources", resourceProvider.getResourceDefinitions(),
                "prompts", promptProvider.getPromptDefinitions()
        );
    }
}
