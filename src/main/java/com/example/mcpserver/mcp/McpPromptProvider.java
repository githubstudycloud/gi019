package com.example.mcpserver.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class McpPromptProvider {

    public List<SyncPromptSpecification> getPromptSpecifications() {
        return List.of(
                new SyncPromptSpecification(
                        new Prompt("code_review",
                                "Review code for best practices and potential issues",
                                List.of(
                                        new PromptArgument("code", "The code to review", true),
                                        new PromptArgument("language", "Programming language", false)
                                )),
                        (exchange, request) -> handleCodeReview(request)
                ),
                new SyncPromptSpecification(
                        new Prompt("explain_concept",
                                "Explain a technical concept in simple terms",
                                List.of(
                                        new PromptArgument("concept", "The concept to explain", true),
                                        new PromptArgument("level", "Explanation level: beginner/intermediate/advanced", false)
                                )),
                        (exchange, request) -> handleExplainConcept(request)
                ),
                new SyncPromptSpecification(
                        new Prompt("generate_test",
                                "Generate unit tests for given code",
                                List.of(
                                        new PromptArgument("code", "The code to test", true),
                                        new PromptArgument("framework", "Test framework (junit5, testng, etc.)", false)
                                )),
                        (exchange, request) -> handleGenerateTest(request)
                )
        );
    }

    public List<Map<String, Object>> getPromptDefinitions() {
        return List.of(
                Map.of("name", "code_review",
                        "description", "Review code for best practices and potential issues",
                        "arguments", List.of(
                                Map.of("name", "code", "description", "The code to review", "required", true),
                                Map.of("name", "language", "description", "Programming language", "required", false))),
                Map.of("name", "explain_concept",
                        "description", "Explain a technical concept in simple terms",
                        "arguments", List.of(
                                Map.of("name", "concept", "description", "The concept to explain", "required", true),
                                Map.of("name", "level", "description", "Explanation level", "required", false))),
                Map.of("name", "generate_test",
                        "description", "Generate unit tests for given code",
                        "arguments", List.of(
                                Map.of("name", "code", "description", "The code to test", "required", true),
                                Map.of("name", "framework", "description", "Test framework", "required", false)))
        );
    }

    public Map<String, Object> getPrompt(JsonNode params) {
        String name = params.has("name") ? params.get("name").asText() : "";
        Map<String, Object> arguments = new HashMap<>();
        if (params.has("arguments")) {
            params.get("arguments").fields().forEachRemaining(
                    entry -> arguments.put(entry.getKey(), entry.getValue().asText()));
        }

        GetPromptRequest request = new GetPromptRequest(name, arguments);
        GetPromptResult result = switch (name) {
            case "code_review" -> handleCodeReview(request);
            case "explain_concept" -> handleExplainConcept(request);
            case "generate_test" -> handleGenerateTest(request);
            default -> new GetPromptResult("Unknown prompt", List.of(
                    new PromptMessage(Role.USER, new TextContent("Unknown prompt: " + name))));
        };

        return Map.of(
                "description", result.description(),
                "messages", result.messages().stream()
                        .map(m -> Map.of(
                                "role", m.role().toString().toLowerCase(),
                                "content", Map.of("type", "text",
                                        "text", ((TextContent) m.content()).text())))
                        .toList()
        );
    }

    private GetPromptResult handleCodeReview(GetPromptRequest request) {
        String code = String.valueOf(request.arguments().getOrDefault("code", "// no code provided"));
        String language = String.valueOf(request.arguments().getOrDefault("language", "java"));

        String prompt = """
                Please review the following %s code for:
                1. Code quality and best practices
                2. Potential bugs or security issues
                3. Performance improvements
                4. Readability and maintainability

                Code:
                ```%s
                %s
                ```

                Provide specific, actionable feedback.""".formatted(language, language, code);

        return new GetPromptResult("Code review prompt",
                List.of(new PromptMessage(Role.USER, new TextContent(prompt))));
    }

    private GetPromptResult handleExplainConcept(GetPromptRequest request) {
        String concept = String.valueOf(request.arguments().getOrDefault("concept", ""));
        String level = String.valueOf(request.arguments().getOrDefault("level", "intermediate"));

        String prompt = """
                Explain the concept of "%s" at a %s level.

                Include:
                - A clear definition
                - How it works
                - Real-world examples or analogies
                - Common use cases
                - Key takeaways""".formatted(concept, level);

        return new GetPromptResult("Concept explanation prompt",
                List.of(new PromptMessage(Role.USER, new TextContent(prompt))));
    }

    private GetPromptResult handleGenerateTest(GetPromptRequest request) {
        String code = String.valueOf(request.arguments().getOrDefault("code", ""));
        String framework = String.valueOf(request.arguments().getOrDefault("framework", "junit5"));

        String prompt = """
                Generate comprehensive unit tests for the following code using %s:

                ```java
                %s
                ```

                Requirements:
                - Test all public methods
                - Include edge cases and error scenarios
                - Use descriptive test method names
                - Add assertions with clear messages""".formatted(framework, code);

        return new GetPromptResult("Test generation prompt",
                List.of(new PromptMessage(Role.USER, new TextContent(prompt))));
    }
}
