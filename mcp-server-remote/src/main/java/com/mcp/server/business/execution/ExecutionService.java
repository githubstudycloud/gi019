package com.mcp.server.business.execution;

import com.mcp.server.meta.datasource.DynamicDsManager;
import com.mcp.server.meta.engine.QueryEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 执行用例业务服务 —— 第二个 MCP 业务的核心逻辑。
 *
 * 业务模型：
 *   公共库：t_project / t_biz_db_mapping / t_case_version
 *   业务库（按项目路由）：t_baseline_case / t_execution_case
 *
 * 引导式查询流程：
 *   1. list_projects_with_biz  → 查项目+所属业务库（公共库）
 *   2. list_baselines          → 按项目查基线（业务库）
 *   3. query_baseline_cases    → 查基线用例（业务库）
 *   4. query_execution_results → 查执行结果（业务库）
 *   5. compare_baseline_exec   → 基线 vs 执行对比（业务库）
 *   6. execution_stats         → 执行统计汇总（业务库）
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final JdbcTemplate commonJdbc;   // 主数据源（公共库）
    private final DynamicDsManager dsManager;
    private final QueryEngine queryEngine;

    public ExecutionService(JdbcTemplate jdbc, DynamicDsManager dsManager, QueryEngine queryEngine) {
        this.commonJdbc = jdbc;
        this.dsManager = dsManager;
        this.queryEngine = queryEngine;
    }

    // ================================================================
    // Step 1: 查询项目及其业务库（引导入口）
    // ================================================================

    public Map<String, Object> listProjectsWithBiz(String keyword, Integer page, Integer limit) {
        int safePage  = page  == null || page  < 1 ? 1  : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        int offset    = (safePage - 1) * safeLimit;

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (p.name LIKE ? OR p.description LIKE ?)");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }

        long total = commonJdbc.queryForObject(
                "SELECT COUNT(*) FROM t_project p" + where, Long.class, params.toArray());

        String sql = "SELECT p.id, p.name AS projectName, p.description," +
                "  m.ds_key AS dsKey, m.biz_name AS bizName, m.enabled AS bizEnabled" +
                " FROM t_project p" +
                " LEFT JOIN t_biz_db_mapping m ON p.id = m.project_id" +
                where + " ORDER BY p.id LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(safeLimit);
        dataParams.add(offset);

        List<Map<String, Object>> items = commonJdbc.queryForList(sql, dataParams.toArray());

        Map<String, Object> result = pagedResult(total, safePage, safeLimit, items);
        result.put("_guide", "下一步：使用 execution_list_baselines 传入 projectId 查询该项目的基线列表");
        return result;
    }

    // ================================================================
    // Step 2: 查询项目的基线列表
    // ================================================================

    public Map<String, Object> listBaselines(Long projectId, Integer page, Integer limit) {
        String dsKey = resolveBizDsKey(projectId);
        JdbcTemplate jdbc = dsManager.getTemplate(dsKey);

        int safePage  = page  == null || page  < 1 ? 1  : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        int offset    = (safePage - 1) * safeLimit;

        long total = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT baseline_name) FROM t_baseline_case WHERE project_id=?",
                Long.class, projectId);

        String sql = "SELECT baseline_name AS baselineName," +
                " COUNT(*) AS caseCount," +
                " SUM(CASE WHEN status='active' THEN 1 ELSE 0 END) AS activeCount," +
                " MAX(created_at) AS lastUpdated" +
                " FROM t_baseline_case WHERE project_id=?" +
                " GROUP BY baseline_name ORDER BY MAX(created_at) DESC LIMIT ? OFFSET ?";

        List<Map<String, Object>> items = jdbc.queryForList(sql, projectId, safeLimit, offset);

        // 获取各基线的执行情况
        for (Map<String, Object> baseline : items) {
            String baselineName = (String) baseline.get("baselineName");
            Map<String, Object> execSummary = getBaselineExecSummary(jdbc, projectId, baselineName);
            baseline.put("executionSummary", execSummary);
        }

        Map<String, Object> result = pagedResult(total, safePage, safeLimit, items);
        result.put("projectId", projectId);
        result.put("dsKey", dsKey);
        result.put("_guide", "下一步：使用 execution_query_baseline_cases 传入 projectId + baselineName 查看基线用例详情");
        return result;
    }

    // ================================================================
    // Step 3: 查询基线用例
    // ================================================================

    public Map<String, Object> queryBaselineCases(Long projectId, String baselineName,
                                                   String caseType, String priority,
                                                   String keyword, Integer page, Integer limit) {
        String dsKey = resolveBizDsKey(projectId);
        JdbcTemplate jdbc = dsManager.getTemplate(dsKey);

        int safePage  = page  == null || page  < 1 ? 1  : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        int offset    = (safePage - 1) * safeLimit;

        StringBuilder where = new StringBuilder(" WHERE project_id=?");
        List<Object> params = new ArrayList<>();
        params.add(projectId);

        if (baselineName != null && !baselineName.isBlank()) {
            where.append(" AND baseline_name=?");
            params.add(baselineName.trim());
        }
        if (caseType != null && !caseType.isBlank()) {
            where.append(" AND case_type=?");
            params.add(caseType.trim());
        }
        if (priority != null && !priority.isBlank()) {
            where.append(" AND priority=?");
            params.add(priority.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (case_name LIKE ? OR module_name LIKE ?)");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }

        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_baseline_case" + where, Long.class, params.toArray());

        String sql = "SELECT id, baseline_name AS baselineName, case_code AS caseCode," +
                " case_name AS caseName, case_type AS caseType, priority, module_name AS moduleName," +
                " precondition, steps, expected_result AS expectedResult, status" +
                " FROM t_baseline_case" + where +
                " ORDER BY priority, case_code LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(safeLimit);
        dataParams.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(sql, dataParams.toArray());

        Map<String, Object> result = pagedResult(total, safePage, safeLimit, items);
        result.put("projectId", projectId);
        result.put("_guide", "下一步：使用 execution_query_results 传入 projectId + baselineName 查看执行结果");
        return result;
    }

    // ================================================================
    // Step 4: 查询执行结果
    // ================================================================

    public Map<String, Object> queryExecutionResults(Long projectId, String baselineName,
                                                      String executeRound, String executeStatus,
                                                      String keyword, Integer page, Integer limit) {
        String dsKey = resolveBizDsKey(projectId);
        JdbcTemplate jdbc = dsManager.getTemplate(dsKey);

        int safePage  = page  == null || page  < 1 ? 1  : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        int offset    = (safePage - 1) * safeLimit;

        // 先通过 baseline_name 关联拿到 baseline_id
        StringBuilder where = new StringBuilder(" WHERE e.project_id=?");
        List<Object> params = new ArrayList<>();
        params.add(projectId);

        boolean needBaselineJoin = (baselineName != null && !baselineName.isBlank());
        String fromClause = " FROM t_execution_case e";
        if (needBaselineJoin) {
            fromClause += " JOIN t_baseline_case b ON e.baseline_id=b.id AND b.baseline_name=?";
            params.add(0, projectId); // 重新组装：e.project_id 在前
            // 重置 where，join 后再加条件
            where = new StringBuilder(" WHERE e.project_id=?");
            params.clear();
            params.add(projectId);
            params.add(baselineName.trim());
        }

        if (executeRound != null && !executeRound.isBlank()) {
            where.append(" AND e.execute_round=?");
            params.add(executeRound.trim());
        }
        if (executeStatus != null && !executeStatus.isBlank()) {
            where.append(" AND e.execute_status=?");
            params.add(executeStatus.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (e.case_name LIKE ? OR e.actual_result LIKE ?)");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }

        long total = jdbc.queryForObject(
                "SELECT COUNT(*)" + fromClause + where, Long.class, params.toArray());

        String sql = "SELECT e.id, e.case_code AS caseCode, e.case_name AS caseName," +
                " e.execute_round AS executeRound, e.executor, e.execute_time AS executeTime," +
                " e.actual_result AS actualResult, e.execute_status AS executeStatus," +
                " e.bug_id AS bugId, e.remark" +
                fromClause + where +
                " ORDER BY e.execute_time DESC, e.id LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(safeLimit);
        dataParams.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(sql, dataParams.toArray());

        Map<String, Object> result = pagedResult(total, safePage, safeLimit, items);
        result.put("projectId", projectId);
        result.put("_guide", "下一步：使用 execution_compare 传入 projectId + baselineName 查看基线与执行的对比分析");
        return result;
    }

    // ================================================================
    // Step 5: 基线 vs 执行对比
    // ================================================================

    public Map<String, Object> compareBaselineVsExecution(Long projectId, String baselineName,
                                                           String executeRound) {
        String dsKey = resolveBizDsKey(projectId);
        JdbcTemplate jdbc = dsManager.getTemplate(dsKey);

        // 基线用例集
        String baselineSql = "SELECT id, case_code, case_name, case_type, priority" +
                " FROM t_baseline_case WHERE project_id=? AND baseline_name=? AND status='active'" +
                " ORDER BY case_code";
        List<Map<String, Object>> baselineCases = jdbc.queryForList(baselineSql, projectId, baselineName);

        // 执行结果（该轮次）
        StringBuilder execWhere = new StringBuilder("WHERE e.project_id=?");
        List<Object> execParams = new ArrayList<>();
        execParams.add(projectId);

        execWhere.append(" AND b.baseline_name=?");
        execParams.add(baselineName);

        if (executeRound != null && !executeRound.isBlank()) {
            execWhere.append(" AND e.execute_round=?");
            execParams.add(executeRound.trim());
        }

        String execSql = "SELECT e.case_code AS caseCode, e.case_name AS caseName," +
                " e.execute_round AS executeRound, e.execute_status AS executeStatus," +
                " e.executor, e.execute_time AS executeTime, e.bug_id AS bugId" +
                " FROM t_execution_case e JOIN t_baseline_case b ON e.baseline_id=b.id" +
                " " + execWhere + " ORDER BY e.case_code, e.execute_time DESC";
        List<Map<String, Object>> execResults = jdbc.queryForList(execSql, execParams.toArray());

        // 建立执行结果 Map（code → latest）
        Map<String, Map<String, Object>> execMap = new LinkedHashMap<>();
        for (Map<String, Object> r : execResults) {
            // 兼容有无别名两种情况
            String code = r.containsKey("caseCode") ? (String) r.get("caseCode") : (String) r.get("case_code");
            execMap.putIfAbsent(code, r);
        }

        // 对比分析
        List<Map<String, Object>> compared = new ArrayList<>();
        int passCount = 0, failCount = 0, skipCount = 0, notExecCount = 0;

        for (Map<String, Object> bc : baselineCases) {
            String code = bc.containsKey("case_code") ? (String) bc.get("case_code") : (String) bc.get("caseCode");
            Map<String, Object> row = new LinkedHashMap<>(bc);
            Map<String, Object> execResult = execMap.get(code);

            if (execResult == null) {
                row.put("executeStatus", "未执行");
                row.put("compareResult", "missing");
                notExecCount++;
            } else {
                String status = (String) execResult.get("executeStatus");
                row.put("executeStatus", status);
                row.put("executor", execResult.get("executor"));
                row.put("executeTime", execResult.get("executeTime"));
                row.put("bugId", execResult.get("bugId"));
                row.put("compareResult", status);
                if ("pass".equals(status))    passCount++;
                else if ("fail".equals(status)) failCount++;
                else skipCount++;
            }
            compared.add(row);
        }

        // 汇总统计
        int total = baselineCases.size();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("pass", passCount);
        summary.put("fail", failCount);
        summary.put("skip", skipCount);
        summary.put("notExecuted", notExecCount);
        summary.put("passRate", total > 0 ? String.format("%.1f%%", passCount * 100.0 / total) : "N/A");
        summary.put("coverageRate", total > 0 ?
                String.format("%.1f%%", (total - notExecCount) * 100.0 / total) : "N/A");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("baselineName", baselineName);
        result.put("executeRound", executeRound);
        result.put("summary", summary);
        result.put("details", compared);
        if (failCount > 0) {
            result.put("_guide", "发现 " + failCount + " 个失败用例。可使用 execution_query_results 按 executeStatus=fail 过滤查看详情");
        }
        return result;
    }

    // ================================================================
    // Step 6: 执行统计
    // ================================================================

    public Map<String, Object> executionStats(Long projectId, String baselineName) {
        String dsKey = resolveBizDsKey(projectId);
        JdbcTemplate jdbc = dsManager.getTemplate(dsKey);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("baselineName", baselineName);

        // 基线用例统计
        List<Map<String, Object>> baselineStats = jdbc.queryForList(
                "SELECT case_type AS caseType, priority, COUNT(*) AS count" +
                " FROM t_baseline_case WHERE project_id=? AND baseline_name=? AND status='active'" +
                " GROUP BY case_type, priority ORDER BY priority",
                projectId, baselineName);
        result.put("baselineStats", baselineStats);

        // 执行状态统计（按轮次）
        List<Map<String, Object>> execStats = jdbc.queryForList(
                "SELECT e.execute_round AS executeRound, e.execute_status AS executeStatus, COUNT(*) AS count" +
                " FROM t_execution_case e JOIN t_baseline_case b ON e.baseline_id=b.id" +
                " WHERE e.project_id=? AND b.baseline_name=?" +
                " GROUP BY e.execute_round, e.execute_status ORDER BY e.execute_round",
                projectId, baselineName);
        result.put("execStats", execStats);

        // Bug 统计
        List<Map<String, Object>> bugStats = jdbc.queryForList(
                "SELECT e.bug_id AS bugId, e.case_code AS caseCode, e.case_name AS caseName," +
                " e.execute_round AS executeRound, e.executor, e.execute_time AS executeTime" +
                " FROM t_execution_case e JOIN t_baseline_case b ON e.baseline_id=b.id" +
                " WHERE e.project_id=? AND b.baseline_name=? AND e.execute_status='fail' AND e.bug_id IS NOT NULL" +
                " ORDER BY e.execute_time DESC",
                projectId, baselineName);
        result.put("bugList", bugStats);
        result.put("bugCount", bugStats.size());

        return result;
    }

    // ================================================================
    // 跨库查询：版本关系（公共库）
    // ================================================================

    public List<Map<String, Object>> listCaseVersions(Long projectId) {
        return commonJdbc.queryForList(
                "SELECT id, version_name AS versionName, baseline_id AS baselineId, remark" +
                " FROM t_case_version WHERE project_id=? ORDER BY id",
                projectId);
    }

    // ================================================================
    // 私有辅助
    // ================================================================

    /**
     * 根据 projectId 查询对应的业务库 dsKey（引导式查询的路由核心）。
     * 若项目未配置业务库则抛出友好错误，引导用户先查项目信息。
     */
    private String resolveBizDsKey(Long projectId) {
        List<Map<String, Object>> mapping = commonJdbc.queryForList(
                "SELECT ds_key FROM t_biz_db_mapping WHERE project_id=? AND enabled=TRUE LIMIT 1",
                projectId);
        if (mapping.isEmpty()) {
            throw new IllegalArgumentException(
                "项目 [" + projectId + "] 未配置业务库映射。请先调用 execution_list_projects 确认项目ID和对应的业务库。");
        }
        return (String) mapping.get(0).get("ds_key");
    }

    private Map<String, Object> getBaselineExecSummary(JdbcTemplate jdbc, Long projectId, String baselineName) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT e.execute_status, COUNT(*) AS cnt" +
                " FROM t_execution_case e JOIN t_baseline_case b ON e.baseline_id=b.id" +
                " WHERE e.project_id=? AND b.baseline_name=?" +
                " GROUP BY e.execute_status",
                projectId, baselineName);
        Map<String, Object> summary = new LinkedHashMap<>();
        long pass = 0, fail = 0, total = 0;
        for (Map<String, Object> r : rows) {
            long cnt = ((Number) r.get("cnt")).longValue();
            total += cnt;
            String status = (String) r.get("executeStatus") != null
                    ? (String) r.get("executeStatus")
                    : (String) r.get("execute_status");
            if ("pass".equalsIgnoreCase(status)) pass = cnt;
            if ("fail".equalsIgnoreCase(status)) fail = cnt;
        }
        summary.put("totalExec", total);
        summary.put("pass", pass);
        summary.put("fail", fail);
        summary.put("passRate", total > 0 ? String.format("%.1f%%", pass * 100.0 / total) : "N/A");
        return summary;
    }

    private Map<String, Object> pagedResult(long total, int page, int pageSize, List<Map<String, Object>> items) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", total);
        r.put("page", page);
        r.put("pageSize", pageSize);
        r.put("totalPages", (int) Math.ceil((double) total / pageSize));
        r.put("items", items);
        return r;
    }
}
