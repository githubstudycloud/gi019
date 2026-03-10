package com.mcp.server.framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP HTTP 控制器 —— 实现 Streamable HTTP 和 SSE 两种传输协议。
 *
 * Streamable HTTP (主要):
 *   POST /mcp -> JSON-RPC 2.0 请求，同步 JSON-RPC 响应
 *
 * SSE (兼容旧客户端):
 *   GET  /sse          -> SSE 流，推送 endpoint 事件
 *   POST /mcp/messages -> JSON-RPC 请求，响应通过 SSE 推送
 */
@RestController
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final McpDispatcher dispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // SSE sessions: sessionId -> emitter
    private final Map<String, SseEmitter> sseSessions = new ConcurrentHashMap<>();

    public McpController(McpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    // ========================
    // Streamable HTTP Transport
    // ========================

    @PostMapping(value = "/mcp",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> handleStreamableHttp(@RequestBody Map<String, Object> request) {
        Object id = request.get("id");
        String method = (String) request.get("method");
        Object params = request.get("params");

        // Notification (no id) - acknowledge only
        if (id == null) {
            log.debug("[MCP] Notification: {}", method);
            return ResponseEntity.accepted().build();
        }

        try {
            Object result = dispatcher.dispatch(method, params);
            return ResponseEntity.ok(jsonRpcResult(id, result));
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.ok(jsonRpcError(id, -32601, "Method not found: " + method));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(jsonRpcError(id, -32602, e.getMessage()));
        } catch (Exception e) {
            log.error("[MCP] Error processing request: method={}", method, e);
            return ResponseEntity.ok(jsonRpcError(id, -32000, e.getMessage()));
        }
    }

    // ========================
    // SSE Transport (兼容)
    // ========================

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter handleSseConnect() {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 min timeout

        sseSessions.put(sessionId, emitter);

        emitter.onCompletion(() -> {
            sseSessions.remove(sessionId);
            log.debug("[MCP SSE] Session completed: {}", sessionId);
        });
        emitter.onTimeout(() -> {
            sseSessions.remove(sessionId);
            log.debug("[MCP SSE] Session timeout: {}", sessionId);
        });

        // 发送 endpoint 事件
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .id(sessionId)
                    .name("endpoint")
                    .data("/mcp/messages?sessionId=" + sessionId);
            emitter.send(event);
        } catch (IOException e) {
            log.error("[MCP SSE] Failed to send endpoint event", e);
        }

        log.info("[MCP SSE] New session: {}", sessionId);
        return emitter;
    }

    @PostMapping(value = "/mcp/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleSseMessage(
            @RequestParam("sessionId") String sessionId,
            @RequestBody Map<String, Object> request) {

        SseEmitter emitter = sseSessions.get(sessionId);
        if (emitter == null) {
            return ResponseEntity.badRequest().build();
        }

        Object id = request.get("id");
        String method = (String) request.get("method");
        Object params = request.get("params");

        // Notification - no response needed
        if (id == null) {
            return ResponseEntity.accepted().build();
        }

        try {
            Object result = dispatcher.dispatch(method, params);
            Map<String, Object> response = jsonRpcResult(id, result);

            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .id(UUID.randomUUID().toString())
                    .name("message")
                    .data(objectMapper.writeValueAsString(response));
            emitter.send(event);
        } catch (Exception e) {
            try {
                Map<String, Object> errorResponse = jsonRpcError(id, -32000, e.getMessage());
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name("message")
                        .data(objectMapper.writeValueAsString(errorResponse));
                emitter.send(event);
            } catch (IOException ex) {
                log.error("[MCP SSE] Failed to send error response", ex);
            }
        }

        return ResponseEntity.accepted().build();
    }

    // ========================
    // Health
    // ========================

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "mcp-remote-server",
                "activeSseSessions", sseSessions.size()
        );
    }

    // ========================
    // JSON-RPC helpers
    // ========================

    private Map<String, Object> jsonRpcResult(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> jsonRpcError(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }
}
