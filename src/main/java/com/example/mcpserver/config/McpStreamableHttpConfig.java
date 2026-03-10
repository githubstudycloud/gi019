package com.example.mcpserver.config;

import com.example.mcpserver.mcp.McpToolProvider;
import com.example.mcpserver.mcp.McpResourceProvider;
import com.example.mcpserver.mcp.McpPromptProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

/**
 * Streamable HTTP transport configuration.
 * Implements MCP JSON-RPC over HTTP for bidirectional streaming.
 *
 * Endpoint: POST /mcp/stream
 */
@RestController
@RequestMapping("/mcp/stream")
@ConditionalOnProperty(name = "mcp.transport.streamable-http.enabled", havingValue = "true")
public class McpStreamableHttpConfig {

    @Value("${mcp.server.name:spring-boot-mcp-server}")
    private String serverName;

    @Value("${mcp.server.version:1.0.0}")
    private String serverVersion;

    private final McpToolProvider toolProvider;
    private final McpResourceProvider resourceProvider;
    private final McpPromptProvider promptProvider;
    private final ObjectMapper objectMapper;

    public McpStreamableHttpConfig(McpToolProvider toolProvider,
                                    McpResourceProvider resourceProvider,
                                    McpPromptProvider promptProvider,
                                    ObjectMapper objectMapper) {
        this.toolProvider = toolProvider;
        this.resourceProvider = resourceProvider;
        this.promptProvider = promptProvider;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handlePost(@RequestBody String body) throws Exception {
        var jsonNode = objectMapper.readTree(body);
        String method = jsonNode.has("method") ? jsonNode.get("method").asText() : null;
        var id = jsonNode.has("id") ? jsonNode.get("id") : null;

        Object result = dispatch(method, jsonNode);

        var response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        if (id != null) response.put("id", id);
        response.put("result", result);
        return response;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void handleGet(HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader("Mcp-Session-Id", UUID.randomUUID().toString());
        var writer = response.getWriter();
        writer.write("event: endpoint\ndata: /mcp/stream\n\n");
        writer.flush();
    }

    @DeleteMapping
    public void handleDelete(HttpServletResponse response) {
        response.setStatus(200);
    }

    private Object dispatch(String method, JsonNode request) {
        if (method == null) return Map.of();
        return switch (method) {
            case "initialize" -> Map.of(
                    "protocolVersion", "2025-03-26",
                    "capabilities", Map.of(
                            "tools", Map.of("listChanged", true),
                            "resources", Map.of("subscribe", true, "listChanged", true),
                            "prompts", Map.of("listChanged", true),
                            "logging", Map.of()),
                    "serverInfo", Map.of("name", serverName, "version", serverVersion));
            case "tools/list" -> Map.of("tools", toolProvider.getToolDefinitions());
            case "tools/call" -> toolProvider.callTool(request.get("params"));
            case "resources/list" -> Map.of("resources", resourceProvider.getResourceDefinitions());
            case "resources/read" -> resourceProvider.readResource(request.get("params"));
            case "prompts/list" -> Map.of("prompts", promptProvider.getPromptDefinitions());
            case "prompts/get" -> promptProvider.getPrompt(request.get("params"));
            case "ping" -> Map.of();
            default -> Map.of();
        };
    }
}
