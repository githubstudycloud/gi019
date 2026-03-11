package com.mcp.server.business.execution;

import com.mcp.server.framework.McpToolProvider;
import com.mcp.server.framework.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 第二个 MCP 业务 —— 执行用例查询工具组。
 *
 * 实现引导式查询流程：
 *   Step1: execution_list_projects      → 查项目+业务库映射（引导入口）
 *   Step2: execution_list_baselines     → 查项目的基线列表
 *   Step3: execution_query_baseline     → 查基线用例详情
 *   Step4: execution_query_results      → 查执行结果
 *   Step5: execution_compare            → 基线 vs 执行对比
 *   Step6: execution_stats              → 执行统计汇总
 *   Extra: execution_list_versions      → 查用例版本关系（公共库）
 */
@Component
public class ExecutionToolProvider implements McpToolProvider {

    private final ExecutionService service;

    public ExecutionToolProvider(ExecutionService service) {
        this.service = service;
    }

    @Override
    public List<ToolDefinition> getToolDefinitions() {
        return List.of(
            // ── Step 1 ──────────────────────────────────────────────
            new ToolDefinition(
                "execution_list_projects",
                "【第一步】列出所有项目及其关联的业务库信息。" +
                "返回 projectId、projectName、dsKey（业务库标识）、bizName。" +
                "这是执行用例查询的入口，后续步骤需要 projectId。",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "keyword", strProp("项目名称或描述的模糊搜索关键字，不填返回全部"),
                        "page",    intProp("页码，默认1"),
                        "limit",   intProp("每页数量，默认20，最大100")
                    )
                )
            ),
            // ── Step 2 ──────────────────────────────────────────────
            new ToolDefinition(
                "execution_list_baselines",
                "【第二步】列出指定项目的所有基线，包含用例数量和执行概况。" +
                "需要先调用 execution_list_projects 获取 projectId。" +
                "返回 baselineName、caseCount、activeCount、executionSummary（通过率等）。",
                Map.of(
                    "type", "object",
                    "required", List.of("projectId"),
                    "properties", Map.of(
                        "projectId", intProp("项目ID（必填，来自 execution_list_projects 的结果）"),
                        "page",      intProp("页码，默认1"),
                        "limit",     intProp("每页数量，默认20")
                    )
                )
            ),
            // ── Step 3 ──────────────────────────────────────────────
            new ToolDefinition(
                "execution_query_baseline",
                "【第三步】查询指定基线的用例详情，可按用例类型、优先级、关键字过滤。" +
                "需要 projectId（来自Step1）和 baselineName（来自Step2）。" +
                "返回 caseCode、caseName、caseType、priority、steps、expectedResult 等。",
                Map.of(
                    "type", "object",
                    "required", List.of("projectId", "baselineName"),
                    "properties", Map.of(
                        "projectId",    intProp("项目ID（必填）"),
                        "baselineName", strProp("基线名称（必填，如：v1.0基线）"),
                        "caseType",     strProp("用例类型过滤：功能测试/异常测试/性能测试/安全测试"),
                        "priority",     strProp("优先级过滤：P0/P1/P2/P3"),
                        "keyword",      strProp("按用例名称或模块名模糊搜索"),
                        "page",         intProp("页码，默认1"),
                        "limit",        intProp("每页数量，默认20")
                    )
                )
            ),
            // ── Step 4 ──────────────────────────────────────────────
            new ToolDefinition(
                "execution_query_results",
                "【第四步】查询指定项目/基线的执行结果，可按执行轮次、通过状态、关键字过滤。" +
                "需要 projectId。baselineName 可选（不填查该项目全部执行记录）。" +
                "executeStatus 可选值：pass/fail/skip/blocked。",
                Map.of(
                    "type", "object",
                    "required", List.of("projectId"),
                    "properties", Map.of(
                        "projectId",     intProp("项目ID（必填）"),
                        "baselineName",  strProp("基线名称（可选，如：v1.0基线）"),
                        "executeRound",  strProp("执行轮次（可选，如：第1轮/第2轮/回归）"),
                        "executeStatus", strProp("执行状态过滤：pass/fail/skip/blocked"),
                        "keyword",       strProp("按用例名称或实际结果模糊搜索"),
                        "page",          intProp("页码，默认1"),
                        "limit",         intProp("每页数量，默认20")
                    )
                )
            ),
            // ── Step 5 ──────────────────────────────────────────────
            new ToolDefinition(
                "execution_compare",
                "【第五步】基线用例 vs 执行结果对比分析。" +
                "展示每条基线用例的执行状态，标记未执行、通过、失败的用例，计算通过率和覆盖率。" +
                "需要 projectId 和 baselineName，executeRound 可选（不填则取最新一轮）。",
                Map.of(
                    "type", "object",
                    "required", List.of("projectId", "baselineName"),
                    "properties", Map.of(
                        "projectId",    intProp("项目ID（必填）"),
                        "baselineName", strProp("基线名称（必填）"),
                        "executeRound", strProp("执行轮次（可选，不填则对比最新一轮）")
                    )
                )
            ),
            // ── Step 6 ──────────────────────────────────────────────
            new ToolDefinition(
                "execution_stats",
                "【第六步】执行情况统计汇总。" +
                "按用例类型/优先级统计基线用例分布，按轮次统计通过/失败情况，列出关联的 Bug 清单。" +
                "需要 projectId 和 baselineName。",
                Map.of(
                    "type", "object",
                    "required", List.of("projectId", "baselineName"),
                    "properties", Map.of(
                        "projectId",    intProp("项目ID（必填）"),
                        "baselineName", strProp("基线名称（必填）")
                    )
                )
            ),
            // ── Extra ────────────────────────────────────────────────
            new ToolDefinition(
                "execution_list_versions",
                "查询项目的用例版本关系（公共库）。返回版本名称、关联基线ID等信息。" +
                "用于了解项目的版本-基线对应关系。",
                Map.of(
                    "type", "object",
                    "required", List.of("projectId"),
                    "properties", Map.of(
                        "projectId", intProp("项目ID（必填）")
                    )
                )
            )
        );
    }

    @Override
    public Object callTool(String name, Map<String, Object> args) {
        return switch (name) {
            case "execution_list_projects"  -> service.listProjectsWithBiz(
                    str(args, "keyword"), intVal(args, "page"), intVal(args, "limit"));

            case "execution_list_baselines" -> service.listBaselines(
                    longVal(args, "projectId"), intVal(args, "page"), intVal(args, "limit"));

            case "execution_query_baseline" -> service.queryBaselineCases(
                    longVal(args, "projectId"), str(args, "baselineName"),
                    str(args, "caseType"), str(args, "priority"),
                    str(args, "keyword"), intVal(args, "page"), intVal(args, "limit"));

            case "execution_query_results"  -> service.queryExecutionResults(
                    longVal(args, "projectId"), str(args, "baselineName"),
                    str(args, "executeRound"), str(args, "executeStatus"),
                    str(args, "keyword"), intVal(args, "page"), intVal(args, "limit"));

            case "execution_compare"        -> service.compareBaselineVsExecution(
                    longVal(args, "projectId"), str(args, "baselineName"),
                    str(args, "executeRound"));

            case "execution_stats"          -> service.executionStats(
                    longVal(args, "projectId"), str(args, "baselineName"));

            case "execution_list_versions"  -> service.listCaseVersions(longVal(args, "projectId"));

            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }

    // ── 参数解析辅助 ─────────────────────────────────────────────────

    private String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return (v == null || v.toString().isBlank()) ? null : v.toString().trim();
    }

    private Integer intVal(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private Long longVal(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) throw new IllegalArgumentException("参数 [" + key + "] 为必填项");
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) {
            throw new IllegalArgumentException("参数 [" + key + "] 必须是数字，当前值: " + v);
        }
    }

    // ── JSON Schema 构建工具 ──────────────────────────────────────────

    private Map<String, Object> strProp(String desc) {
        return Map.of("type", "string", "description", desc);
    }

    private Map<String, Object> intProp(String desc) {
        return Map.of("type", "integer", "description", desc);
    }
}
