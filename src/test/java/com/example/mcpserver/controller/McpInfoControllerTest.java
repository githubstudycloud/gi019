package com.example.mcpserver.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "mcp.transport.sse.enabled=false"
})
@AutoConfigureMockMvc
class McpInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getInfo_returnsServerInfo() throws Exception {
        mockMvc.perform(get("/api/mcp/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server.name").value("spring-boot-mcp-server"))
                .andExpect(jsonPath("$.server.version").value("1.0.0"));
    }

    @Test
    void getInfo_returnsTools() throws Exception {
        mockMvc.perform(get("/api/mcp/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools").isArray())
                .andExpect(jsonPath("$.tools.length()").value(4));
    }

    @Test
    void getInfo_returnsResources() throws Exception {
        mockMvc.perform(get("/api/mcp/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources").isArray())
                .andExpect(jsonPath("$.resources.length()").value(3));
    }

    @Test
    void getInfo_returnsPrompts() throws Exception {
        mockMvc.perform(get("/api/mcp/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompts").isArray())
                .andExpect(jsonPath("$.prompts.length()").value(3));
    }
}
