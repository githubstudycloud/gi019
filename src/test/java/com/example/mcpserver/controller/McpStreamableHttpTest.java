package com.example.mcpserver.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "mcp.transport.sse.enabled=false",
        "mcp.transport.streamable-http.enabled=true"
})
@AutoConfigureMockMvc
class McpStreamableHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void initialize_returnsServerCapabilities() throws Exception {
        mockMvc.perform(post("/mcp/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result.serverInfo.name").value("spring-boot-mcp-server"))
                .andExpect(jsonPath("$.result.capabilities.tools.listChanged").value(true));
    }

    @Test
    void toolsList_returnsAllTools() throws Exception {
        mockMvc.perform(post("/mcp/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools.length()").value(4));
    }

    @Test
    void toolsCall_calculate_returnsResult() throws Exception {
        mockMvc.perform(post("/mcp/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"calculate","arguments":{"expression":"2+3"}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].text").value("2+3 = 5.0"));
    }

    @Test
    void toolsCall_generateUuid_returnsUuid() throws Exception {
        mockMvc.perform(post("/mcp/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"generate_uuid","arguments":{}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].type").value("text"))
                .andExpect(jsonPath("$.result.content[0].text").exists());
    }

    @Test
    void resourcesList_returnsAllResources() throws Exception {
        mockMvc.perform(post("/mcp/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":5,"method":"resources/list","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resources.length()").value(3));
    }

    @Test
    void resourcesRead_appInfo_returnsMetadata() throws Exception {
        mockMvc.perform(post("/mcp/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":6,"method":"resources/read","params":{"uri":"config://app/info"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.contents[0].uri").value("config://app/info"))
                .andExpect(jsonPath("$.result.contents[0].mimeType").value("application/json"));
    }

    @Test
    void promptsList_returnsAllPrompts() throws Exception {
        mockMvc.perform(post("/mcp/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":7,"method":"prompts/list","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.prompts.length()").value(3));
    }

    @Test
    void promptsGet_codeReview_returnsPromptMessage() throws Exception {
        mockMvc.perform(post("/mcp/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":8,"method":"prompts/get","params":{"name":"code_review","arguments":{"code":"int x = 1;"}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.description").value("Code review prompt"))
                .andExpect(jsonPath("$.result.messages[0].role").value("user"));
    }

    @Test
    void ping_returnsEmptyResult() throws Exception {
        mockMvc.perform(post("/mcp/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":9,"method":"ping","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").exists());
    }

    @Test
    void deleteSession_returns200() throws Exception {
        mockMvc.perform(delete("/mcp/stream"))
                .andExpect(status().isOk());
    }
}
