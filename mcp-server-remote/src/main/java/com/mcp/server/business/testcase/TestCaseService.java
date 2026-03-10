package com.mcp.server.business.testcase;

import com.mcp.server.config.DbTableProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 测试用例业务服务 —— 纯业务逻辑，与 MCP 框架无关。
 * 所有 SQL 中的表名和字段名都通过 DbTableProperties 动态获取。
 */
@Service
public class TestCaseService {

    private static final Logger log = LoggerFactory.getLogger(TestCaseService.class);

    private final JdbcTemplate jdbc;
    private final DbTableProperties db;

    public TestCaseService(JdbcTemplate jdbc, DbTableProperties db) {
        this.jdbc = jdbc;
        this.db = db;
    }

    /**
     * 搜索用例 —— 支持模糊匹配项目名和用例名，可选过滤版本和 URI
     */
    public List<Map<String, Object>> searchTestCase(String projectName, String caseName,
                                                      String versionName, String uri) {
        // 动态获取表名和字段名
        String pTable = db.projectTable();
        String tcTable = db.testcaseTable();
        String vTable = db.versionTable();

        String pId = db.projectCol("id", "id");
        String pName = db.projectCol("name", "name");

        String tcProjectId = db.testcaseCol("projectId", "project_id");
        String tcCaseName = db.testcaseCol("caseName", "case_name");
        String tcCaseType = db.testcaseCol("caseType", "case_type");
        String tcPriority = db.testcaseCol("priority", "priority");
        String tcPrecondition = db.testcaseCol("precondition", "precondition");
        String tcSteps = db.testcaseCol("steps", "steps");
        String tcExpectedResult = db.testcaseCol("expectedResult", "expected_result");
        String tcStatus = db.testcaseCol("status", "status");

        String vProjectId = db.versionCol("projectId", "project_id");
        String vVersionName = db.versionCol("versionName", "version_name");
        String vUri = db.versionCol("uri", "uri");

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        sql.append("SELECT tc.*, p.").append(pName).append(" AS project_name");

        // 如果需要版本信息，做 JOIN
        boolean needVersion = (versionName != null && !versionName.isBlank())
                || (uri != null && !uri.isBlank());

        if (needVersion) {
            sql.append(", v.").append(vVersionName).append(" AS version_name");
            sql.append(", v.").append(vUri).append(" AS version_uri");
        }

        sql.append(" FROM ").append(tcTable).append(" tc");
        sql.append(" JOIN ").append(pTable).append(" p ON tc.").append(tcProjectId).append(" = p.").append(pId);

        if (needVersion) {
            sql.append(" JOIN ").append(vTable).append(" v ON tc.").append(tcProjectId)
               .append(" = v.").append(vProjectId);
        }

        sql.append(" WHERE 1=1");

        // 模糊匹配项目名
        if (projectName != null && !projectName.isBlank()) {
            sql.append(" AND p.").append(pName).append(" LIKE ?");
            params.add("%" + projectName.trim() + "%");
        }

        // 模糊匹配用例名
        if (caseName != null && !caseName.isBlank()) {
            sql.append(" AND tc.").append(tcCaseName).append(" LIKE ?");
            params.add("%" + caseName.trim() + "%");
        }

        // 可选: 版本名过滤
        if (versionName != null && !versionName.isBlank()) {
            sql.append(" AND v.").append(vVersionName).append(" LIKE ?");
            params.add("%" + versionName.trim() + "%");
        }

        // 可选: URI 过滤
        if (uri != null && !uri.isBlank()) {
            sql.append(" AND v.").append(vUri).append(" LIKE ?");
            params.add("%" + uri.trim() + "%");
        }

        sql.append(" ORDER BY tc.").append(db.testcaseCol("id", "id")).append(" LIMIT 50");

        log.debug("[TestCase] SQL: {}, params: {}", sql, params);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 列出项目 —— 可模糊搜索
     */
    public List<Map<String, Object>> listProjects(String keyword) {
        String pTable = db.projectTable();
        String pName = db.projectCol("name", "name");
        String pDesc = db.projectCol("description", "description");

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        sql.append("SELECT p.*, (SELECT COUNT(*) FROM ").append(db.testcaseTable())
           .append(" tc WHERE tc.").append(db.testcaseCol("projectId", "project_id"))
           .append(" = p.").append(db.projectCol("id", "id"))
           .append(") AS case_count");
        sql.append(", (SELECT COUNT(*) FROM ").append(db.versionTable())
           .append(" v WHERE v.").append(db.versionCol("projectId", "project_id"))
           .append(" = p.").append(db.projectCol("id", "id"))
           .append(") AS version_count");
        sql.append(" FROM ").append(pTable).append(" p");

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" WHERE p.").append(pName).append(" LIKE ?");
            sql.append(" OR p.").append(pDesc).append(" LIKE ?");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }

        sql.append(" ORDER BY p.").append(db.projectCol("id", "id"));

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 获取项目详情 —— 包含版本列表和用例统计
     */
    public Map<String, Object> getProjectDetail(String projectName) {
        String pTable = db.projectTable();
        String pName = db.projectCol("name", "name");
        String pId = db.projectCol("id", "id");

        // 模糊查找项目
        List<Map<String, Object>> projects = jdbc.queryForList(
                "SELECT * FROM " + pTable + " WHERE " + pName + " LIKE ? LIMIT 1",
                "%" + projectName.trim() + "%"
        );

        if (projects.isEmpty()) {
            return Map.of("error", "项目未找到: " + projectName, "found", false);
        }

        Map<String, Object> project = projects.get(0);
        Object projectId = project.get(pId);

        // 查版本列表
        List<Map<String, Object>> versions = jdbc.queryForList(
                "SELECT * FROM " + db.versionTable() +
                " WHERE " + db.versionCol("projectId", "project_id") + " = ?" +
                " ORDER BY " + db.versionCol("id", "id"),
                projectId
        );

        // 查用例统计
        List<Map<String, Object>> caseStats = jdbc.queryForList(
                "SELECT " + db.testcaseCol("caseType", "case_type") + " AS case_type, " +
                db.testcaseCol("priority", "priority") + " AS priority, " +
                "COUNT(*) AS count FROM " + db.testcaseTable() +
                " WHERE " + db.testcaseCol("projectId", "project_id") + " = ?" +
                " GROUP BY " + db.testcaseCol("caseType", "case_type") + ", " +
                db.testcaseCol("priority", "priority"),
                projectId
        );

        long totalCases = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + db.testcaseTable() +
                " WHERE " + db.testcaseCol("projectId", "project_id") + " = ?",
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
}
