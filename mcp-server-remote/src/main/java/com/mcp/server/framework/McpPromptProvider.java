package com.mcp.server.framework;

import java.util.List;
import java.util.Map;

/**
 * 业务提示模板提供者接口 —— 实现此接口提供 MCP Prompts。
 */
public interface McpPromptProvider {

    List<PromptDefinition> getPromptDefinitions();

    Object getPrompt(String name, Map<String, Object> arguments);

    default boolean supportsPrompt(String name) {
        return getPromptDefinitions().stream().anyMatch(d -> d.name().equals(name));
    }

    record PromptDefinition(
            String name,
            String description,
            List<Map<String, Object>> arguments
    ) {}
}
