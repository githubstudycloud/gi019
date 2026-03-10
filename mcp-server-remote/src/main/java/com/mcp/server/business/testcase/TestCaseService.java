package com.mcp.server.business.testcase;

import com.mcp.server.config.DbTableProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 测试用例业务服务 —— 纯业务逻辑，与 MCP 框架无关。
 * 所有 SQL 中的表名和字段名都通过 DbTableProperties 动态获取。
 * 支持分页查询，适合百万级数据场景。
 */
@Service
public class TestCaseService {

    private static final Logger log = LoggerFactory.getLogger(TestCaseService.class);

    private final JdbcTemplate jdbc;
    private final DbTableProperties db;

    @Value("${mcp.search.default-page-size:20}")
    private int defaultPageSize;

    @Value("${mcp.search.max-page-size:100}")
    private int maxPageSize;

    public TestCaseService(JdbcTemplate jdbc, DbTableProperties db) {
        this.jdbc = jdbc;
        this.db = db;
    }

    /**
     * 搜索用例 —— 支持模糊匹配项目名和用例名，可选过滤版本和 URI，分页返回。
     * 返回：{total, page, pageSize, totalPages, items: [...]}
     */
    public Map<String, Object> searchTestCase(String projectName, String caseName,
                                               String versionName, String uri,
                                               Integer page, Integer limit) {
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeLimit = clampLimit(limit);
        int offset = (safePage - 1) * safeLimit;

        String pTable = db.projectTable();
        String tcTable = db.testcaseTable();
        String vTable = db.versionTable();

        String pId = db.projectCol("id", "id");
        String pName = db.projectCol("name", "name");
        String tcProjectId = db.testcaseCol("projectId", "project_id");
        String tcCaseName = db.testcaseCol("caseName", "case_name");
        String vProjectId = db.versionCol("projectId", "project_id");
        String vVersionName = db.versionCol("versionName", "version_name");
        String vUri = db.versionCol("uri", "uri");

        boolean needVersion = (versionName != null && !versionName.isBlank())
                || (uri != null && !uri.isBlank());

        // 构建 FROM + JOIN
        StringBuilder from = new StringBuilder();
        from.append(" FROM ").append(tcTable).append(" tc");
        from.append(" JOIN ").append(pTable).append(" p ON tc.").append(tcProjectId).append(" = p.").append(pId);
        if (needVersion) {
            from.append(" JOIN ").append(vTable).append(" v ON tc.").append(tcProjectId)
                .append(" = v.").append(vProjectId);
        }

        // 构建 WHERE 条件
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> whereParams = new ArrayList<>();

        if (projectName != null && !projectName.isBlank()) {
            where.append(" AND p.").append(pName).append(" LIKE ?");
            whereParams.add("%" + projectName.trim() + "%");
        }
        if (caseName != null && !caseName.isBlank()) {
            where.append(" AND tc.").append(tcCaseName).append(" LIKE ?");
            whereParams.add("%" + caseName.trim() + "%");
        }
        if (versionName != null && !versionName.isBlank()) {
            where.append(" AND v.").append(vVersionName).append(" LIKE ?");
            whereParams.add("%" + versionName.trim() + "%");
        }
        if (uri != null && !uri.isBlank()) {
            where.append(" AND v.").append(vUri).append(" LIKE ?");
            whereParams.add("%" + uri.trim() + "%");
        }

        // COUNT 查询（不加载数据，只统计总数）
        String countSql = "SELECT COUNT(*)" + from + where;
        long total = jdbc.queryForObject(countSql, Long.class, whereParams.toArray());

        // 数据查询（加分页）
        String selectCols = "tc.*, p." + pName + " AS project_name";
        if (needVersion) {
            selectCols += ", v." + vVersionName + " AS version_name, v." + vUri + " AS version_uri";
        }
        String orderBy = " ORDER BY tc." + db.testcaseCol("id", "id");
        String dataSql = "SELECT " + selectCols + from + where + orderBy + " LIMIT ? OFFSET ?";

        List<Object> dataParams = new ArrayList<>(whereParams);
        dataParams.add(safeLimit);
        dataParams.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(dataSql, dataParams.toArray());

        log.debug("[TestCase] search: total={}, page={}, pageSize={}", total, safePage, safeLimit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", safeLimit);
        result.put("totalPages", (int) Math.ceil((double) total / safeLimit));
        result.put("items", items);
        return result;
    }

    /**
     * 仅统计用例数量，不加载数据——适合"有多少条用例"类问题，百万级也瞬间响应。
     */
    public Map<String, Object> countTestCases(String projectName, String caseName,
                                               String versionName, String uri) {
        String pTable = db.projectTable();
        String tcTable = db.testcaseTable();
        String vTable = db.versionTable();

        String pId = db.projectCol("id", "id");
        String pName = db.projectCol("name", "name");
        String tcProjectId = db.testcaseCol("projectId", "project_id");
        String tcCaseName = db.testcaseCol("caseName", "case_name");
        String vProjectId = db.versionCol("projectId", "project_id");
        String vVersionName = db.versionCol("versionName", "version_name");
        String vUri = db.versionCol("uri", "uri");

        boolean needVersion = (versionName != null && !versionName.isBlank())
                || (uri != null && !uri.isBlank());

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(tcTable).append(" tc");
        sql.append(" JOIN ").append(pTable).append(" p ON tc.").append(tcProjectId).append(" = p.").append(pId);
        if (needVersion) {
            sql.append(" JOIN ").append(vTable).append(" v ON tc.").append(tcProjectId)
               .append(" = v.").append(vProjectId);
        }
        sql.append(" WHERE 1=1");

        List<Object> params = new ArrayList<>();
        if (projectName != null && !projectName.isBlank()) {
            sql.append(" AND p.").append(pName).append(" LIKE ?");
            params.add("%" + projectName.trim() + "%");
        }
        if (caseName != null && !caseName.isBlank()) {
            sql.append(" AND tc.").append(tcCaseName).append(" LIKE ?");
            params.add("%" + caseName.trim() + "%");
        }
        if (versionName != null && !versionName.isBlank()) {
            sql.append(" AND v.").append(vVersionName).append(" LIKE ?");
            params.add("%" + versionName.trim() + "%");
        }
        if (uri != null && !uri.isBlank()) {
            sql.append(" AND v.").append(vUri).append(" LIKE ?");
            params.add("%" + uri.trim() + "%");
        }

        long count = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);
        if (projectName != null) result.put("projectName", projectName);
        if (caseName != null) result.put("caseName", caseName);
        if (versionName != null) result.put("versionName", versionName);
        if (uri != null) result.put("uri", uri);
        return result;
    }

    /**
     * 列出项目 —— 可模糊搜索，分页返回。
     */
    public Map<String, Object> listProjects(String keyword, Integer page, Integer limit) {
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeLimit = clampLimit(limit);
        int offset = (safePage - 1) * safeLimit;

        String pTable = db.projectTable();
        String pName = db.projectCol("name", "name");
        String pDesc = db.projectCol("description", "description");
        String pId = db.projectCol("id", "id");

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            where.append(" WHERE p.").append(pName).append(" LIKE ?");
            where.append(" OR p.").append(pDesc).append(" LIKE ?");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }

        // COUNT
        String countSql = "SELECT COUNT(*) FROM " + pTable + " p" + where;
        long total = jdbc.queryForObject(countSql, Long.class, params.toArray());

        // DATA
        String dataSql = "SELECT p.*"
                + ", (SELECT COUNT(*) FROM " + db.testcaseTable() + " tc WHERE tc."
                + db.testcaseCol("projectId", "project_id") + " = p." + pId + ") AS case_count"
                + ", (SELECT COUNT(*) FROM " + db.versionTable() + " v WHERE v."
                + db.versionCol("projectId", "project_id") + " = p." + pId + ") AS version_count"
                + " FROM " + pTable + " p"
                + where
                + " ORDER BY p." + pId
                + " LIMIT ? OFFSET ?";

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(safeLimit);
        dataParams.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(dataSql, dataParams.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", safeLimit);
        result.put("totalPages", (int) Math.ceil((double) total / safeLimit));
        result.put("items", items);
        return result;
    }

    /**
     * 获取项目详情 —— 包含版本列表和用例统计。
     */
    public Map<String, Object> getProjectDetail(String projectName) {
        String pTable = db.projectTable();
        String pName = db.projectCol("name", "name");
        String pId = db.projectCol("id", "id");

        List<Map<String, Object>> projects = jdbc.queryForList(
                "SELECT * FROM " + pTable + " WHERE " + pName + " LIKE ? LIMIT 1",
                "%" + projectName.trim() + "%"
        );

        if (projects.isEmpty()) {
            return Map.of("found", false, "error", "项目未找到: " + projectName);
        }

        Map<String, Object> project = projects.get(0);
        Object projectId = project.get(pId);

        List<Map<String, Object>> versions = jdbc.queryForList(
                "SELECT * FROM " + db.versionTable()
                + " WHERE " + db.versionCol("projectId", "project_id") + " = ?"
                + " ORDER BY " + db.versionCol("id", "id"),
                projectId
        );

        List<Map<String, Object>> caseStats = jdbc.queryForList(
                "SELECT " + db.testcaseCol("caseType", "case_type") + " AS case_type, "
                + db.testcaseCol("priority", "priority") + " AS priority, "
                + "COUNT(*) AS count FROM " + db.testcaseTable()
                + " WHERE " + db.testcaseCol("projectId", "project_id") + " = ?"
                + " GROUP BY " + db.testcaseCol("caseType", "case_type") + ", "
                + db.testcaseCol("priority", "priority"),
                projectId
        );

        long totalCases = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + db.testcaseTable()
                + " WHERE " + db.testcaseCol("projectId", "project_id") + " = ?",
                Long.class, projectId
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("project", project);
        result.put("versions", versions);
        result.put("totalCases", totalCases);
        result.put("caseStats", caseStats);
        return result;
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit < 1) return defaultPageSize;
        return Math.min(limit, maxPageSize);
    }
}
