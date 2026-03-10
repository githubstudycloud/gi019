package com.mcp.server.framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP 注册表 —— 管理所有已注册的 Tool/Resource/Prompt Provider。
 * 通过 Spring 自动注入所有实现了对应接口的 Bean。
 */
@Component
public class McpRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpRegistry.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<McpToolProvider> toolProviders;
    private final List<McpResourceProvider> resourceProviders;
    private final List<McpPromptProvider> promptProviders;

    public McpRegistry(
            List<McpToolProvider> toolProviders,
            List<McpResourceProvider> resourceProviders,
            List<McpPromptProvider> promptProviders) {
        this.toolProviders = toolProviders != null ? toolProviders : List.of();
        this.resourceProviders = resourceProviders != null ? resourceProviders : List.of();
        this.promptProviders = promptProviders != null ? promptProviders : List.of();

        log.info("[MCP Registry] Registered {} tool providers, {} resource providers, {} prompt providers",
                this.toolProviders.size(), this.resourceProviders.size(), this.promptProviders.size());

        // 打印所有注册的工具
        for (McpToolProvider provider : this.toolProviders) {
            for (ToolDefinition tool : provider.getToolDefinitions()) {
                log.info("[MCP Registry] Tool registered: {}", tool.name());
            }
        }
    }

    // --- Tools ---

    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpToolProvider provider : toolProviders) {
            for (ToolDefinition def : provider.getToolDefinitions()) {
                tools.add(Map.of(
                        "name", def.name(),
                        "description", def.description(),
                        "inputSchema", def.inputSchema()
                ));
            }
        }
        return tools;
    }

    public Map<String, Object> callTool(String name, Map<String, Object> arguments) {
        for (McpToolProvider provider : toolProviders) {
            if (provider.supportsTool(name)) {
                try {
                    Object result = provider.callTool(name, arguments);
                    String text;
                    try {
                        text = objectMapper.writeValueAsString(result);
                    } catch (Exception e) {
                        text = String.valueOf(result);
                    }
                    return Map.of(
                            "content", List.of(Map.of("type", "text", "text", text)),
                            "isError", false
                    );
                } catch (Exception e) {
                    log.error("[MCP Registry] Tool call failed: {} - {}", name, e.getMessage(), e);
                    return Map.of(
                            "content", List.of(Map.of("type", "text", "text", "Error: " + e.getMessage())),
                            "isError", true
                    );
                }
            }
        }
        throw new IllegalArgumentException("Unknown tool: " + name);
    }

    // --- Resources ---

    public List<Map<String, Object>> listResources() {
        List<Map<String, Object>> resources = new ArrayList<>();
        for (McpResourceProvider provider : resourceProviders) {
            for (McpResourceProvider.ResourceDefinition def : provider.getResourceDefinitions()) {
                resources.add(Map.of(
                        "uri", def.uri(),
                        "name", def.name(),
                        "description", def.description(),
                        "mimeType", def.mimeType()
                ));
            }
        }
        return resources;
    }

    public Map<String, Object> readResource(String uri) {
        for (McpResourceProvider provider : resourceProviders) {
            if (provider.supportsResource(uri)) {
                try {
                    Object result = provider.readResource(uri);
                    String text;
                    try {
                        text = objectMapper.writeValueAsString(result);
                    } catch (Exception e) {
                        text = String.valueOf(result);
                    }
                    return Map.of(
                            "contents", List.of(Map.of(
                                    "uri", uri,
                                    "mimeType", "application/json",
                                    "text", text
                            ))
                    );
                } catch (Exception e) {
                    throw new RuntimeException("Resource read failed: " + e.getMessage(), e);
                }
            }
        }
        throw new IllegalArgumentException("Unknown resource: " + uri);
    }

    // --- Prompts ---

    public List<Map<String, Object>> listPrompts() {
        List<Map<String, Object>> prompts = new ArrayList<>();
        for (McpPromptProvider provider : promptProviders) {
            for (McpPromptProvider.PromptDefinition def : provider.getPromptDefinitions()) {
                Map<String, Object> prompt = new LinkedHashMap<>();
                prompt.put("name", def.name());
                prompt.put("description", def.description());
                if (def.arguments() != null) {
                    prompt.put("arguments", def.arguments());
                }
                prompts.add(prompt);
            }
        }
        return prompts;
    }

    public Object getPrompt(String name, Map<String, Object> arguments) {
        for (McpPromptProvider provider : promptProviders) {
            if (provider.supportsPrompt(name)) {
                return provider.getPrompt(name, arguments);
            }
        }
        throw new IllegalArgumentException("Unknown prompt: " + name);
    }
}
