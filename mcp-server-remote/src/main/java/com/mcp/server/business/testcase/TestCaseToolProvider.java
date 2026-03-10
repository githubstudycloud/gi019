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
                        "搜索测试用例。支持通过项目名称（模糊）和用例名称（模糊）搜索，可选按版本名或URI过滤。" +
                        "返回匹配的用例列表，包含用例名、类型、优先级、前置条件、操作步骤、预期结果等完整信息。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "projectName", Map.of(
                                                "type", "string",
                                                "description", "项目名称（支持模糊匹配，如：电商、用户）"
                                        ),
                                        "caseName", Map.of(
                                                "type", "string",
                                                "description", "用例名称（支持模糊匹配，如：登录、支付）"
                                        ),
                                        "versionName", Map.of(
                                                "type", "string",
                                                "description", "版本名称（可选，支持模糊匹配，如：v1.0）"
                                        ),
                                        "uri", Map.of(
                                                "type", "string",
                                                "description", "对应URI（可选，支持模糊匹配，如：/api/v1）"
                                        )
                                ),
                                "required", List.of()
                        )
                ),
                new ToolDefinition(
                        "list_projects",
                        "列出所有项目，可选关键词模糊搜索。返回项目名、描述、用例数量、版本数量。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "keyword", Map.of(
                                                "type", "string",
                                                "description", "搜索关键词（可选，模糊匹配项目名和描述）"
                                        )
                                )
                        )
                ),
                new ToolDefinition(
                        "get_project_detail",
                        "获取项目详情，包含版本列表和用例统计。通过项目名称模糊匹配。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "projectName", Map.of(
                                                "type", "string",
                                                "description", "项目名称（支持模糊匹配）"
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
                    getString(arguments, "uri")
            );
            case "list_projects" -> testCaseService.listProjects(
                    getString(arguments, "keyword")
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
}
