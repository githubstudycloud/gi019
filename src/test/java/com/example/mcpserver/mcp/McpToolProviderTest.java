package com.example.mcpserver.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpToolProviderTest {

    private McpToolProvider toolProvider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        toolProvider = new McpToolProvider();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getToolSpecifications_returnsAllTools() {
        var specs = toolProvider.getToolSpecifications();
        assertEquals(4, specs.size());
        assertEquals("get_current_time", specs.get(0).tool().name());
        assertEquals("calculate", specs.get(1).tool().name());
        assertEquals("search_knowledge", specs.get(2).tool().name());
        assertEquals("generate_uuid", specs.get(3).tool().name());
    }

    @Test
    void getToolDefinitions_returnsAllTools() {
        var defs = toolProvider.getToolDefinitions();
        assertEquals(4, defs.size());
        assertTrue(defs.stream().anyMatch(t -> "get_current_time".equals(t.get("name"))));
    }

    @Test
    void callTool_getCurrentTime_returnsTime() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "get_current_time");
        params.set("arguments", objectMapper.createObjectNode().put("timezone", "UTC"));

        var result = toolProvider.callTool(params);
        assertNotNull(result.get("content"));
        var content = (java.util.List<?>) result.get("content");
        assertFalse(content.isEmpty());
        var firstContent = (Map<?, ?>) content.get(0);
        assertTrue(((String) firstContent.get("text")).startsWith("Current time:"));
    }

    @Test
    void callTool_getCurrentTime_defaultTimezone() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "get_current_time");
        params.set("arguments", objectMapper.createObjectNode());

        var result = toolProvider.callTool(params);
        var content = (java.util.List<?>) result.get("content");
        var text = (String) ((Map<?, ?>) content.get(0)).get("text");
        assertTrue(text.startsWith("Current time:"));
    }

    @Test
    void callTool_getCurrentTime_invalidTimezone_fallsBackToDefault() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "get_current_time");
        params.set("arguments", objectMapper.createObjectNode().put("timezone", "Invalid/Zone"));

        var result = toolProvider.callTool(params);
        var content = (java.util.List<?>) result.get("content");
        var text = (String) ((Map<?, ?>) content.get(0)).get("text");
        assertTrue(text.startsWith("Current time:"));
    }

    @Test
    void callTool_calculate_simpleAddition() {
        var result = callCalculate("2 + 3");
        assertTextContains(result, "= 5.0");
    }

    @Test
    void callTool_calculate_multiplication() {
        var result = callCalculate("3 * 4");
        assertTextContains(result, "= 12.0");
    }

    @Test
    void callTool_calculate_parentheses() {
        var result = callCalculate("(2 + 3) * 4");
        assertTextContains(result, "= 20.0");
    }

    @Test
    void callTool_calculate_complexExpression() {
        var result = callCalculate("10 / 2 + 3 * 4");
        assertTextContains(result, "= 17.0");
    }

    @Test
    void callTool_calculate_invalidExpression_returnsError() {
        var result = callCalculate("abc");
        var content = (java.util.List<?>) result.get("content");
        var text = (String) ((Map<?, ?>) content.get(0)).get("text");
        assertTrue(text.startsWith("Error:"));
    }

    @Test
    void callTool_searchKnowledge_matchesResults() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "search_knowledge");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "MCP");
        params.set("arguments", args);

        var result = toolProvider.callTool(params);
        var text = getFirstText(result);
        assertTrue(text.contains("MCP Protocol"));
        assertTrue(text.contains("MCP Transports"));
    }

    @Test
    void callTool_searchKnowledge_noResults() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "search_knowledge");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "nonexistent_topic_xyz");
        params.set("arguments", args);

        var result = toolProvider.callTool(params);
        assertTextContains(result, "No results found");
    }

    @Test
    void callTool_searchKnowledge_withLimit() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "search_knowledge");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "Spring");
        args.put("limit", 1);
        params.set("arguments", args);

        var result = toolProvider.callTool(params);
        var text = getFirstText(result);
        assertTrue(text.contains("1."));
        assertFalse(text.contains("2."));
    }

    @Test
    void callTool_generateUuid_returnsValidUuid() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "generate_uuid");
        params.set("arguments", objectMapper.createObjectNode());

        var result = toolProvider.callTool(params);
        var text = getFirstText(result);
        assertDoesNotThrow(() -> java.util.UUID.fromString(text));
    }

    @Test
    void callTool_unknownTool_returnsError() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "unknown_tool");
        params.set("arguments", objectMapper.createObjectNode());

        var result = toolProvider.callTool(params);
        assertTextContains(result, "Unknown tool");
    }

    // Helper methods
    private Map<String, Object> callCalculate(String expression) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "calculate");
        params.set("arguments", objectMapper.createObjectNode().put("expression", expression));
        return toolProvider.callTool(params);
    }

    @SuppressWarnings("unchecked")
    private String getFirstText(Map<String, Object> result) {
        var content = (java.util.List<Map<String, String>>) result.get("content");
        return content.get(0).get("text");
    }

    private void assertTextContains(Map<String, Object> result, String expected) {
        assertTrue(getFirstText(result).contains(expected),
                "Expected text to contain '" + expected + "' but was: " + getFirstText(result));
    }
}
