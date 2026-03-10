package com.mcp.remote.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP Streamable HTTP Transport Controller
 *
 * Implements the MCP 2025-03-26 Streamable HTTP transport:
 * - POST /mcp : JSON-RPC endpoint (synchronous response)
 * - Accepts JSON-RPC requests, returns JSON-RPC responses
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);
    private static final String PROTOCOL_VERSION = "2025-03-26";

    private final McpToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpController(McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleJsonRpc(@RequestBody Map<String, Object> request) {
        String method = (String) request.get("method");
        Object id = request.get("id");
        Object params = request.get("params");

        log.info("MCP request: method={}, id={}", method, id);

        // Notification (no id) - just acknowledge
        if (id == null) {
            return ResponseEntity.accepted().build();
        }

        try {
            Object result = switch (method) {
                case "initialize" -> handleInitialize();
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolsCall(params);
                case "ping" -> Map.of();
                default -> throw new UnsupportedOperationException("Unknown method: " + method);
            };

            return ResponseEntity.ok(jsonRpcResult(id, result));
        } catch (Exception e) {
            log.error("MCP error: method={}, error={}", method, e.getMessage());
            return ResponseEntity.ok(jsonRpcError(id, -32000, e.getMessage()));
        }
    }

    private Map<String, Object> handleInitialize() {
        return Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(
                        "tools", Map.of("listChanged", false)
                ),
                "serverInfo", Map.of(
                        "name", "remote-mcp-server",
                        "version", "1.0.0"
                )
        );
    }

    private Map<String, Object> handleToolsList() {
        return Map.of("tools", toolRegistry.listTools());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Object params) {
        if (!(params instanceof Map<?, ?> paramsMap)) {
            throw new IllegalArgumentException("Invalid params");
        }

        String toolName = (String) paramsMap.get("name");
        Object argsObj = paramsMap.get("arguments");
        Map<String, Object> arguments = argsObj instanceof Map ? (Map<String, Object>) argsObj : Map.of();

        if (!toolRegistry.hasTool(toolName)) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        Object toolResult = toolRegistry.callTool(toolName, arguments);
        String textContent;
        try {
            textContent = objectMapper.writeValueAsString(toolResult);
        } catch (Exception e) {
            textContent = toolResult.toString();
        }

        return Map.of(
                "content", List.of(Map.of(
                        "type", "text",
                        "text", textContent
                )),
                "isError", false
        );
    }

    private Map<String, Object> jsonRpcResult(Object id, Object result) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", result
        );
    }

    private Map<String, Object> jsonRpcError(Object id, int code, String message) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "error", Map.of("code", code, "message", message)
        );
    }
}
