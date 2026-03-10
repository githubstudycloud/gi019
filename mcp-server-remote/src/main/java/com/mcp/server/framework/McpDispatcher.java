package com.mcp.server.framework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP JSON-RPC 2.0 请求分发器。
 * 将 JSON-RPC method 分发到 Registry 对应的处理逻辑。
 */
@Component
public class McpDispatcher {

    private static final Logger log = LoggerFactory.getLogger(McpDispatcher.class);
    private static final String PROTOCOL_VERSION = "2025-03-26";

    private final McpRegistry registry;

    @Value("${mcp.server.name:mcp-remote-server}")
    private String serverName;

    @Value("${mcp.server.version:1.0.0}")
    private String serverVersion;

    public McpDispatcher(McpRegistry registry) {
        this.registry = registry;
    }

    /**
     * 分发 JSON-RPC 请求
     * @param method JSON-RPC method
     * @param params 参数
     * @return 结果（放入 result 字段）
     */
    @SuppressWarnings("unchecked")
    public Object dispatch(String method, Object params) {
        log.debug("[MCP] dispatch: method={}", method);

        return switch (method) {
            case "initialize" -> handleInitialize();
            case "ping" -> Map.of();

            case "tools/list" -> Map.of("tools", registry.listTools());
            case "tools/call" -> handleToolsCall(params);

            case "resources/list" -> Map.of("resources", registry.listResources());
            case "resources/read" -> handleResourcesRead(params);

            case "prompts/list" -> Map.of("prompts", registry.listPrompts());
            case "prompts/get" -> handlePromptsGet(params);

            default -> throw new UnsupportedOperationException("Unknown method: " + method);
        };
    }

    private Map<String, Object> handleInitialize() {
        return Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(
                        "tools", Map.of("listChanged", false),
                        "resources", Map.of("subscribe", false, "listChanged", false),
                        "prompts", Map.of("listChanged", false)
                ),
                "serverInfo", Map.of(
                        "name", serverName,
                        "version", serverVersion
                )
        );
    }

    @SuppressWarnings("unchecked")
    private Object handleToolsCall(Object params) {
        if (!(params instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Invalid params for tools/call");
        }
        String name = (String) map.get("name");
        Object argsObj = map.get("arguments");
        Map<String, Object> arguments = argsObj instanceof Map ? (Map<String, Object>) argsObj : Map.of();
        return registry.callTool(name, arguments);
    }

    @SuppressWarnings("unchecked")
    private Object handleResourcesRead(Object params) {
        if (!(params instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Invalid params for resources/read");
        }
        String uri = (String) map.get("uri");
        return registry.readResource(uri);
    }

    @SuppressWarnings("unchecked")
    private Object handlePromptsGet(Object params) {
        if (!(params instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Invalid params for prompts/get");
        }
        String name = (String) map.get("name");
        Object argsObj = map.get("arguments");
        Map<String, Object> arguments = argsObj instanceof Map ? (Map<String, Object>) argsObj : Map.of();
        return registry.getPrompt(name, arguments);
    }
}
