package com.mcp.server.framework;

import java.util.Map;

/**
 * MCP 工具定义
 *
 * @param name        工具名称（全局唯一）
 * @param description 工具描述（给 LLM 看的）
 * @param inputSchema JSON Schema 格式的参数定义
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema
) {}
