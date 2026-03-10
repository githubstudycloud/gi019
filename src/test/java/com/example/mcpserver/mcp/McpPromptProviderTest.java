package com.example.mcpserver.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpPromptProviderTest {

    private McpPromptProvider promptProvider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        promptProvider = new McpPromptProvider();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getPromptSpecifications_returnsThreePrompts() {
        var specs = promptProvider.getPromptSpecifications();
        assertEquals(3, specs.size());
        assertEquals("code_review", specs.get(0).prompt().name());
        assertEquals("explain_concept", specs.get(1).prompt().name());
        assertEquals("generate_test", specs.get(2).prompt().name());
    }

    @Test
    void getPromptDefinitions_returnsCorrectStructure() {
        var defs = promptProvider.getPromptDefinitions();
        assertEquals(3, defs.size());

        var codeReview = defs.get(0);
        assertEquals("code_review", codeReview.get("name"));
        @SuppressWarnings("unchecked")
        var args = (List<Map<String, Object>>) codeReview.get("arguments");
        assertEquals(2, args.size());
        assertEquals("code", args.get(0).get("name"));
        assertEquals(true, args.get(0).get("required"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPrompt_codeReview_returnsFormattedPrompt() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "code_review");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("code", "public void hello() {}");
        args.put("language", "java");
        params.set("arguments", args);

        var result = promptProvider.getPrompt(params);
        assertEquals("Code review prompt", result.get("description"));

        var messages = (List<Map<String, Object>>) result.get("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).get("role"));

        var content = (Map<String, String>) messages.get(0).get("content");
        assertTrue(content.get("text").contains("public void hello()"));
        assertTrue(content.get("text").contains("java"));
        assertTrue(content.get("text").contains("Code quality"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPrompt_explainConcept_returnsFormattedPrompt() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "explain_concept");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("concept", "Dependency Injection");
        args.put("level", "beginner");
        params.set("arguments", args);

        var result = promptProvider.getPrompt(params);
        assertEquals("Concept explanation prompt", result.get("description"));

        var messages = (List<Map<String, Object>>) result.get("messages");
        var content = (Map<String, String>) messages.get(0).get("content");
        assertTrue(content.get("text").contains("Dependency Injection"));
        assertTrue(content.get("text").contains("beginner"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPrompt_generateTest_returnsFormattedPrompt() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "generate_test");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("code", "public int add(int a, int b) { return a + b; }");
        args.put("framework", "testng");
        params.set("arguments", args);

        var result = promptProvider.getPrompt(params);
        var messages = (List<Map<String, Object>>) result.get("messages");
        var content = (Map<String, String>) messages.get(0).get("content");
        assertTrue(content.get("text").contains("testng"));
        assertTrue(content.get("text").contains("add(int a, int b)"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPrompt_unknownPrompt_returnsErrorMessage() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "unknown_prompt");
        params.set("arguments", objectMapper.createObjectNode());

        var result = promptProvider.getPrompt(params);
        assertEquals("Unknown prompt", result.get("description"));

        var messages = (List<Map<String, Object>>) result.get("messages");
        var content = (Map<String, String>) messages.get(0).get("content");
        assertTrue(content.get("text").contains("Unknown prompt"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPrompt_defaultValues_whenArgumentsMissing() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "code_review");
        params.set("arguments", objectMapper.createObjectNode());

        var result = promptProvider.getPrompt(params);
        var messages = (List<Map<String, Object>>) result.get("messages");
        var content = (Map<String, String>) messages.get(0).get("content");
        // Should use defaults
        assertTrue(content.get("text").contains("java")); // default language
    }
}
