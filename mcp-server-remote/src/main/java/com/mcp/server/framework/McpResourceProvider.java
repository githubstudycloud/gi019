package com.mcp.server.framework;

import java.util.List;
import java.util.Map;

/**
 * 业务资源提供者接口 —— 实现此接口提供 MCP Resources。
 */
public interface McpResourceProvider {

    List<ResourceDefinition> getResourceDefinitions();

    Object readResource(String uri);

    default boolean supportsResource(String uri) {
        return getResourceDefinitions().stream().anyMatch(d -> d.uri().equals(uri));
    }

    record ResourceDefinition(
            String uri,
            String name,
            String description,
            String mimeType
    ) {}
}
