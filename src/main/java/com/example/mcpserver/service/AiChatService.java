package com.example.mcpserver.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * AI Chat Service - demonstrates Spring AI integration.
 *
 * Uses Spring AI ChatClient to interact with AI models (OpenAI, etc.).
 * The MCP tools/resources/prompts can be consumed by AI clients connecting
 * to this server via any supported transport.
 */
@Service
public class AiChatService {

    private final ChatClient chatClient;

    public AiChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("You are a helpful assistant with access to MCP tools.")
                .build();
    }

    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }

    public String chatWithContext(String userMessage, String systemContext) {
        return chatClient.prompt()
                .system(systemContext)
                .user(userMessage)
                .call()
                .content();
    }
}
