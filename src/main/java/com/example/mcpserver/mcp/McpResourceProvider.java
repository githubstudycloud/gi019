package com.example.mcpserver.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class McpResourceProvider {

    @Value("${spring.application.name:mcp-server-demo}")
    private String appName;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SyncResourceSpecification> getResourceSpecifications() {
        return List.of(
                new SyncResourceSpecification(
                        new Resource("config://app/info", "Application Info",
                                "Application configuration and metadata",
                                "application/json", null),
                        (exchange, request) -> handleAppInfo()
                ),
                new SyncResourceSpecification(
                        new Resource("config://app/health", "Health Status",
                                "Current application health status",
                                "application/json", null),
                        (exchange, request) -> handleHealthStatus()
                ),
                new SyncResourceSpecification(
                        new Resource("data://knowledge/topics", "Knowledge Topics",
                                "List of available knowledge base topics",
                                "application/json", null),
                        (exchange, request) -> handleKnowledgeTopics()
                )
        );
    }

    public List<Map<String, Object>> getResourceDefinitions() {
        return List.of(
                Map.of("uri", "config://app/info",
                        "name", "Application Info",
                        "description", "Application configuration and metadata",
                        "mimeType", "application/json"),
                Map.of("uri", "config://app/health",
                        "name", "Health Status",
                        "description", "Current application health status",
                        "mimeType", "application/json"),
                Map.of("uri", "data://knowledge/topics",
                        "name", "Knowledge Topics",
                        "description", "List of available knowledge base topics",
                        "mimeType", "application/json")
        );
    }

    public Map<String, Object> readResource(JsonNode params) {
        String uri = params.has("uri") ? params.get("uri").asText() : "";
        ReadResourceResult result = switch (uri) {
            case "config://app/info" -> handleAppInfo();
            case "config://app/health" -> handleHealthStatus();
            case "data://knowledge/topics" -> handleKnowledgeTopics();
            default -> new ReadResourceResult(List.of(
                    new TextResourceContents(uri, "text/plain", "Resource not found: " + uri)));
        };

        return Map.of("contents", result.contents().stream()
                .map(c -> {
                    var tc = (TextResourceContents) c;
                    var map = new LinkedHashMap<String, Object>();
                    map.put("uri", tc.uri());
                    map.put("mimeType", tc.mimeType());
                    map.put("text", tc.text());
                    return map;
                }).toList());
    }

    private ReadResourceResult handleAppInfo() {
        var info = Map.of(
                "name", appName,
                "version", "1.0.0",
                "framework", "Spring Boot 3.4.x + Spring AI",
                "transports", List.of("SSE", "Streamable HTTP", "Stdio"),
                "java", System.getProperty("java.version"),
                "os", System.getProperty("os.name") + " " + System.getProperty("os.version")
        );
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(info);
            return new ReadResourceResult(List.of(
                    new TextResourceContents("config://app/info", "application/json", json)));
        } catch (Exception e) {
            return new ReadResourceResult(List.of(
                    new TextResourceContents("config://app/info", "text/plain", "Error: " + e.getMessage())));
        }
    }

    private ReadResourceResult handleHealthStatus() {
        var health = Map.of(
                "status", "UP",
                "timestamp", java.time.Instant.now().toString(),
                "uptime_seconds", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                "memory", Map.of(
                        "max_mb", Runtime.getRuntime().maxMemory() / (1024 * 1024),
                        "used_mb", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024),
                        "free_mb", Runtime.getRuntime().freeMemory() / (1024 * 1024)
                )
        );
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(health);
            return new ReadResourceResult(List.of(
                    new TextResourceContents("config://app/health", "application/json", json)));
        } catch (Exception e) {
            return new ReadResourceResult(List.of(
                    new TextResourceContents("config://app/health", "text/plain", "Error: " + e.getMessage())));
        }
    }

    private ReadResourceResult handleKnowledgeTopics() {
        var topics = List.of(
                Map.of("id", "spring-ai", "name", "Spring AI", "description", "Spring AI framework integration"),
                Map.of("id", "mcp", "name", "MCP Protocol", "description", "Model Context Protocol specification"),
                Map.of("id", "transports", "name", "MCP Transports", "description", "Transport protocols: stdio, SSE, HTTP")
        );
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("topics", topics));
            return new ReadResourceResult(List.of(
                    new TextResourceContents("data://knowledge/topics", "application/json", json)));
        } catch (Exception e) {
            return new ReadResourceResult(List.of(
                    new TextResourceContents("data://knowledge/topics", "text/plain", "Error")));
        }
    }
}
