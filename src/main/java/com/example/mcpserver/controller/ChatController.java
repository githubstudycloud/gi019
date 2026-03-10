package com.example.mcpserver.controller;

import com.example.mcpserver.service.AiChatService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for AI chat - demonstrates Spring AI usage alongside MCP server.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AiChatService aiChatService;

    public ChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        String response = aiChatService.chat(message);
        return Map.of("response", response);
    }

    @PostMapping("/with-context")
    public Map<String, String> chatWithContext(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        String context = request.getOrDefault("context", "");
        String response = aiChatService.chatWithContext(message, context);
        return Map.of("response", response);
    }
}
