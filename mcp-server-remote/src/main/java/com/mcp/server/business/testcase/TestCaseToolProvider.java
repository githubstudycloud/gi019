package com.mcp.server.business.testcase;

import com.mcp.server.framework.McpToolProvider;
import com.mcp.server.framework.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 测试用例相关的 MCP 工具提供者。
 * 实现 McpToolProvider 接口，框架自动发现并注册。
 *
 * 业务逻辑委托给 TestCaseService，本类只做 MCP 工具定义和参数解析。
 */
@Component
public class TestCaseToolProvider implements McpToolProvider {

    private final TestCaseService testCaseService;

    public TestCaseToolProvider(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @Override
    public List<ToolDefinition> getToolDefinitions() {
        return List.of(

                new ToolDefinition(
                        "search_test_case",
                        "Search test cases with fuzzy matching on project name and case name. " +
                        "Supports optional filtering by version name or URI. " +
                        "Returns paginated results with total count. " +
                        "Use page and limit params to navigate large datasets (millions of records supported).",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "projectName", Map.of(
                                                "type", "string",
                                                "description", "Project name, fuzzy match (e.g. 'ecommerce' matches 'Ecommerce Platform')"
                                        ),
                                        "caseName", Map.of(
                                                "type", "string",
                                                "description", "Test case name, fuzzy match (e.g. 'login' matches 'User Login - Normal Flow')"
                                        ),
                                        "versionName", Map.of(
                                                "type", "string",
                                                "description", "Version name, optional fuzzy filter (e.g. 'v2' matches 'v2.0.0')"
                                        ),
                                        "uri", Map.of(
                                                "type", "string",
                                                "description", "API URI, optional fuzzy filter (e.g. '/api/v1')"
                                        ),
                                        "page", Map.of(
                                                "type", "integer",
                                                "description", "Page number, starts from 1 (default: 1)",
                                                "minimum", 1,
                                                "default", 1
                                        ),
                                        "limit", Map.of(
                                                "type", "integer",
                                                "description", "Items per page, 1-100 (default: 20)",
                                                "minimum", 1,
                                                "maximum", 100,
                                                "default", 20
                                        )
                                ),
                                "required", List.of()
                        )
                ),

                new ToolDefinition(
                        "count_test_cases",
                        "Count matching test cases without loading data. " +
                        "Extremely fast even with millions of records. " +
                        "Use this when you only need the count, not the actual case content.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "projectName", Map.of(
                                                "type", "string",
                                                "description", "Project name, fuzzy match"
                                        ),
                                        "caseName", Map.of(
                                                "type", "string",
                                                "description", "Case name, fuzzy match"
                                        ),
                                        "versionName", Map.of(
                                                "type", "string",
                                                "description", "Version name, optional fuzzy filter"
                                        ),
                                        "uri", Map.of(
                                                "type", "string",
                                                "description", "API URI, optional fuzzy filter"
                                        )
                                ),
                                "required", List.of()
                        )
                ),

                new ToolDefinition(
                        "list_projects",
                        "List all projects with case and version counts. Supports optional keyword search and pagination.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "keyword", Map.of(
                                                "type", "string",
                                                "description", "Search keyword, fuzzy match on project name and description"
                                        ),
                                        "page", Map.of(
                                                "type", "integer",
                                                "description", "Page number, starts from 1 (default: 1)",
                                                "minimum", 1,
                                                "default", 1
                                        ),
                                        "limit", Map.of(
                                                "type", "integer",
                                                "description", "Items per page, 1-100 (default: 20)",
                                                "minimum", 1,
                                                "maximum", 100,
                                                "default", 20
                                        )
                                )
                        )
                ),

                new ToolDefinition(
                        "get_project_detail",
                        "Get full project details including version list and test case statistics by type and priority.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "projectName", Map.of(
                                                "type", "string",
                                                "description", "Project name, fuzzy match"
                                        )
                                ),
                                "required", List.of("projectName")
                        )
                )
        );
    }

    @Override
    public Object callTool(String name, Map<String, Object> arguments) {
        return switch (name) {
            case "search_test_case" -> testCaseService.searchTestCase(
                    getString(arguments, "projectName"),
                    getString(arguments, "caseName"),
                    getString(arguments, "versionName"),
                    getString(arguments, "uri"),
                    getInt(arguments, "page"),
                    getInt(arguments, "limit")
            );
            case "count_test_cases" -> testCaseService.countTestCases(
                    getString(arguments, "projectName"),
                    getString(arguments, "caseName"),
                    getString(arguments, "versionName"),
                    getString(arguments, "uri")
            );
            case "list_projects" -> testCaseService.listProjects(
                    getString(arguments, "keyword"),
                    getInt(arguments, "page"),
                    getInt(arguments, "limit")
            );
            case "get_project_detail" -> testCaseService.getProjectDetail(
                    getString(arguments, "projectName")
            );
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }

    private String getString(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    private Integer getInt(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return null; }
    }
}
