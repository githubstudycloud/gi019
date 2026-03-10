package com.example.mcpserver.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class McpToolProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SyncToolSpecification> getToolSpecifications() {
        return List.of(
                new SyncToolSpecification(
                        new Tool("get_current_time", "Get the current date and time",
                                new JsonSchema("object",
                                        Map.of("timezone", Map.of("type", "string",
                                                "description", "Timezone (e.g., Asia/Shanghai, UTC)")),
                                        null, null)),
                        (exchange, args) -> handleGetCurrentTime(args)
                ),
                new SyncToolSpecification(
                        new Tool("calculate", "Perform basic arithmetic calculation",
                                new JsonSchema("object",
                                        Map.of("expression", Map.of("type", "string",
                                                "description", "Math expression (e.g., '2 + 3 * 4')")),
                                        List.of("expression"), null)),
                        (exchange, args) -> handleCalculate(args)
                ),
                new SyncToolSpecification(
                        new Tool("search_knowledge", "Search the internal knowledge base",
                                new JsonSchema("object",
                                        Map.of("query", Map.of("type", "string", "description", "Search query"),
                                                "limit", Map.of("type", "integer", "description", "Max results (default: 5)")),
                                        List.of("query"), null)),
                        (exchange, args) -> handleSearchKnowledge(args)
                ),
                new SyncToolSpecification(
                        new Tool("generate_uuid", "Generate a random UUID",
                                new JsonSchema("object", Map.of(), null, null)),
                        (exchange, args) -> handleGenerateUuid()
                )
        );
    }

    public List<Map<String, Object>> getToolDefinitions() {
        return List.of(
                Map.of("name", "get_current_time",
                        "description", "Get the current date and time",
                        "inputSchema", Map.of("type", "object",
                                "properties", Map.of("timezone", Map.of("type", "string")))),
                Map.of("name", "calculate",
                        "description", "Perform basic arithmetic calculation",
                        "inputSchema", Map.of("type", "object",
                                "properties", Map.of("expression", Map.of("type", "string")),
                                "required", List.of("expression"))),
                Map.of("name", "search_knowledge",
                        "description", "Search the internal knowledge base",
                        "inputSchema", Map.of("type", "object",
                                "properties", Map.of("query", Map.of("type", "string"),
                                        "limit", Map.of("type", "integer")),
                                "required", List.of("query"))),
                Map.of("name", "generate_uuid",
                        "description", "Generate a random UUID",
                        "inputSchema", Map.of("type", "object", "properties", Map.of()))
        );
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> callTool(JsonNode params) {
        String name = params.has("name") ? params.get("name").asText() : "";
        Map<String, Object> arguments = params.has("arguments")
                ? objectMapper.convertValue(params.get("arguments"), Map.class) : Map.of();

        CallToolResult result = switch (name) {
            case "get_current_time" -> handleGetCurrentTime(arguments);
            case "calculate" -> handleCalculate(arguments);
            case "search_knowledge" -> handleSearchKnowledge(arguments);
            case "generate_uuid" -> handleGenerateUuid();
            default -> new CallToolResult(List.of(new TextContent("Unknown tool: " + name)), true);
        };

        return Map.of("content", result.content().stream()
                .map(c -> Map.of("type", "text", "text", ((TextContent) c).text()))
                .toList());
    }

    private CallToolResult handleGetCurrentTime(Map<String, Object> args) {
        String timezone = String.valueOf(args.getOrDefault("timezone", ""));
        ZoneId zoneId;
        try {
            zoneId = timezone.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(timezone);
        } catch (Exception e) {
            zoneId = ZoneId.systemDefault();
        }
        String time = ZonedDateTime.now(zoneId)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"));
        return new CallToolResult(List.of(new TextContent("Current time: " + time)), false);
    }

    private CallToolResult handleCalculate(Map<String, Object> args) {
        String expression = String.valueOf(args.getOrDefault("expression", ""));
        try {
            double result = evaluateExpression(expression);
            return new CallToolResult(List.of(new TextContent(expression + " = " + result)), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    private CallToolResult handleSearchKnowledge(Map<String, Object> args) {
        String query = String.valueOf(args.getOrDefault("query", ""));
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 5;

        var knowledgeBase = List.of(
                Map.of("title", "Spring Boot AI Integration",
                        "content", "Spring AI provides a unified API for AI model integration with Spring Boot."),
                Map.of("title", "MCP Protocol",
                        "content", "Model Context Protocol enables AI models to interact with external tools and data sources."),
                Map.of("title", "MCP Transports",
                        "content", "MCP supports stdio, SSE, and Streamable HTTP transport protocols.")
        );

        var filtered = knowledgeBase.stream()
                .filter(r -> r.get("title").toLowerCase().contains(query.toLowerCase())
                        || r.get("content").toLowerCase().contains(query.toLowerCase()))
                .limit(limit)
                .toList();

        StringBuilder sb = new StringBuilder("Search results for '" + query + "':\n\n");
        if (filtered.isEmpty()) {
            sb.append("No results found.");
        } else {
            for (int i = 0; i < filtered.size(); i++) {
                sb.append(i + 1).append(". ").append(filtered.get(i).get("title")).append("\n");
                sb.append("   ").append(filtered.get(i).get("content")).append("\n\n");
            }
        }
        return new CallToolResult(List.of(new TextContent(sb.toString())), false);
    }

    private CallToolResult handleGenerateUuid() {
        return new CallToolResult(List.of(new TextContent(UUID.randomUUID().toString())), false);
    }

    private double evaluateExpression(String expr) {
        expr = expr.replaceAll("\\s+", "");
        return parseExpression(expr, new int[]{0});
    }

    private double parseExpression(String expr, int[] pos) {
        double result = parseTerm(expr, pos);
        while (pos[0] < expr.length() && (expr.charAt(pos[0]) == '+' || expr.charAt(pos[0]) == '-')) {
            char op = expr.charAt(pos[0]++);
            double term = parseTerm(expr, pos);
            result = op == '+' ? result + term : result - term;
        }
        return result;
    }

    private double parseTerm(String expr, int[] pos) {
        double result = parseFactor(expr, pos);
        while (pos[0] < expr.length() && (expr.charAt(pos[0]) == '*' || expr.charAt(pos[0]) == '/')) {
            char op = expr.charAt(pos[0]++);
            double factor = parseFactor(expr, pos);
            result = op == '*' ? result * factor : result / factor;
        }
        return result;
    }

    private double parseFactor(String expr, int[] pos) {
        if (pos[0] < expr.length() && expr.charAt(pos[0]) == '(') {
            pos[0]++;
            double result = parseExpression(expr, pos);
            if (pos[0] < expr.length() && expr.charAt(pos[0]) == ')') pos[0]++;
            return result;
        }
        int start = pos[0];
        if (pos[0] < expr.length() && (expr.charAt(pos[0]) == '-' || expr.charAt(pos[0]) == '+')) pos[0]++;
        while (pos[0] < expr.length() && (Character.isDigit(expr.charAt(pos[0])) || expr.charAt(pos[0]) == '.'))
            pos[0]++;
        return Double.parseDouble(expr.substring(start, pos[0]));
    }
}
