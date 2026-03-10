package com.example.mcpserver;

import com.example.mcpserver.mcp.McpToolProvider;
import com.example.mcpserver.mcp.McpResourceProvider;
import com.example.mcpserver.mcp.McpPromptProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "mcp.transport.sse.enabled=false"
})
class McpServerApplicationTests {

    @Autowired
    private McpToolProvider toolProvider;

    @Autowired
    private McpResourceProvider resourceProvider;

    @Autowired
    private McpPromptProvider promptProvider;

    @Test
    void contextLoads() {
        assertNotNull(toolProvider);
        assertNotNull(resourceProvider);
        assertNotNull(promptProvider);
    }

    @Test
    void toolDefinitionsAreRegistered() {
        var tools = toolProvider.getToolDefinitions();
        assertFalse(tools.isEmpty());
        assertEquals(4, tools.size());
        assertTrue(tools.stream().anyMatch(t -> "get_current_time".equals(t.get("name"))));
        assertTrue(tools.stream().anyMatch(t -> "calculate".equals(t.get("name"))));
        assertTrue(tools.stream().anyMatch(t -> "search_knowledge".equals(t.get("name"))));
        assertTrue(tools.stream().anyMatch(t -> "generate_uuid".equals(t.get("name"))));
    }

    @Test
    void resourceDefinitionsAreRegistered() {
        var resources = resourceProvider.getResourceDefinitions();
        assertEquals(3, resources.size());
    }

    @Test
    void promptDefinitionsAreRegistered() {
        var prompts = promptProvider.getPromptDefinitions();
        assertEquals(3, prompts.size());
    }
}
