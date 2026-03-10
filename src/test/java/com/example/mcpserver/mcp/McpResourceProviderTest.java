package com.example.mcpserver.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpResourceProviderTest {

    private McpResourceProvider resourceProvider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        resourceProvider = new McpResourceProvider();
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(resourceProvider, "appName", "test-app");
    }

    @Test
    void getResourceSpecifications_returnsThreeResources() {
        var specs = resourceProvider.getResourceSpecifications();
        assertEquals(3, specs.size());
        assertEquals("config://app/info", specs.get(0).resource().uri());
        assertEquals("config://app/health", specs.get(1).resource().uri());
        assertEquals("data://knowledge/topics", specs.get(2).resource().uri());
    }

    @Test
    void getResourceDefinitions_returnsCorrectStructure() {
        var defs = resourceProvider.getResourceDefinitions();
        assertEquals(3, defs.size());
        assertEquals("config://app/info", defs.get(0).get("uri"));
        assertEquals("application/json", defs.get(0).get("mimeType"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readResource_appInfo_returnsAppMetadata() throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "config://app/info");

        var result = resourceProvider.readResource(params);
        var contents = (List<Map<String, Object>>) result.get("contents");
        assertEquals(1, contents.size());
        assertEquals("config://app/info", contents.get(0).get("uri"));
        assertEquals("application/json", contents.get(0).get("mimeType"));

        String text = (String) contents.get(0).get("text");
        var parsed = objectMapper.readTree(text);
        assertEquals("test-app", parsed.get("name").asText());
        assertEquals("1.0.0", parsed.get("version").asText());
    }

    @Test
    @SuppressWarnings("unchecked")
    void readResource_health_returnsHealthInfo() throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "config://app/health");

        var result = resourceProvider.readResource(params);
        var contents = (List<Map<String, Object>>) result.get("contents");
        String text = (String) contents.get(0).get("text");
        var parsed = objectMapper.readTree(text);
        assertEquals("UP", parsed.get("status").asText());
        assertTrue(parsed.has("memory"));
        assertTrue(parsed.has("timestamp"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readResource_knowledgeTopics_returnsTopics() throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "data://knowledge/topics");

        var result = resourceProvider.readResource(params);
        var contents = (List<Map<String, Object>>) result.get("contents");
        String text = (String) contents.get(0).get("text");
        var parsed = objectMapper.readTree(text);
        assertTrue(parsed.has("topics"));
        assertEquals(3, parsed.get("topics").size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void readResource_unknownUri_returnsNotFound() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "unknown://resource");

        var result = resourceProvider.readResource(params);
        var contents = (List<Map<String, Object>>) result.get("contents");
        String text = (String) contents.get(0).get("text");
        assertTrue(text.contains("Resource not found"));
    }
}
