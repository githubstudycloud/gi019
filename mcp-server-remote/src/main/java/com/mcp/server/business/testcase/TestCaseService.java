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
 *
 * SELECT 子句由 DbTableProperties.visibleFields() 动态构建：
 *   - 新增字段：在 application.yml 中追加 fields 条目即可，无需改代码
 *   - 场景适配：Spring Profile 覆盖表名/字段名，代码不变
 *   - 结果键名：固定使用 YAML 中的逻辑字段名（camelCase），不受 DB 方言影响
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
     * 搜索用例：模糊匹配项目名和用例名，可选版本/URI 过滤，分页返回。
     * 返回结构：{total, page, pageSize, totalPages, items: [...]}
     */
    public Map<String, Object> searchTestCase(String projectName, String caseName,
                                               String versionName, String uri,
                                               Integer page, Integer limit) {
        int safePage  = (page == null || page < 1) ? 1 : page;
        int safeLimit = clampLimit(limit);
        int offset    = (safePage - 1) * safeLimit;

        String pTable      = db.projectTable();
        String tcTable     = db.testcaseTable();
        String vTable      = db.versionTable();
        String pId         = db.projectCol("id", "id");
        String pName       = db.projectCol("name", "name");
        String tcProjectId = db.testcaseCol("projectId", "project_id");
        String tcCaseName  = db.testcaseCol("caseName", "case_name");
        String vProjectId  = db.versionCol("projectId", "project_id");
        String vVersionName = db.versionCol("versionName", "version_name");
        String vUri        = db.versionCol("uri", "uri");

        boolean needVersion = (versionName != null && !versionName.isBlank())
                           || (uri != null && !uri.isBlank());

        // ── SELECT 子句：由 YAML visible 字段驱动 ───────────────────────────
        String selectCols = buildTestcaseSelect(pName, needVersion, vVersionName, vUri);

        // ── FROM + JOIN ──────────────────────────────────────────────────────
        StringBuilder from = new StringBuilder();
        from.append(" FROM ").append(tcTable).append(" tc");
        from.append(" JOIN ").append(pTable).append(" p ON tc.").append(tcProjectId)
            .append(" = p.").append(pId);
        if (needVersion) {
            from.append(" JOIN ").append(vTable).append(" v ON tc.").append(tcProjectId)
                .append(" = v.").append(vProjectId);
        }

        // ── WHERE 条件 ───────────────────────────────────────────────────────
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

        // ── COUNT（不加载数据） ───────────────────────────────────────────────
        long total = jdbc.queryForObject(
                "SELECT COUNT(*)" + from + where, Long.class, whereParams.toArray());

        // ── 数据查询（分页） ──────────────────────────────────────────────────
        String orderBy  = " ORDER BY tc." + db.testcaseCol("id", "id");
        String dataSql  = "SELECT " + selectCols + from + where + orderBy + " LIMIT ? OFFSET ?";

        List<Object> dataParams = new ArrayList<>(whereParams);
        dataParams.add(safeLimit);
        dataParams.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(dataSql, dataParams.toArray());

        log.debug("[TestCase] search: total={}, page={}, pageSize={}", total, safePage, safeLimit);

        return pagedResult(total, safePage, safeLimit, items);
    }

    /**
     * 仅统计用例数量，不加载数据——百万级数据瞬间响应。
     */
    public Map<String, Object> countTestCases(String projectName, String caseName,
                                               String versionName, String uri) {
        String pTable      = db.projectTable();
        String tcTable     = db.testcaseTable();
        String vTable      = db.versionTable();
        String pId         = db.projectCol("id", "id");
        String pName       = db.projectCol("name", "name");
        String tcProjectId = db.testcaseCol("projectId", "project_id");
        String tcCaseName  = db.testcaseCol("caseName", "case_name");
        String vProjectId  = db.versionCol("projectId", "project_id");
        String vVersionName = db.versionCol("versionName", "version_name");
        String vUri        = db.versionCol("uri", "uri");

        boolean needVersion = (versionName != null && !versionName.isBlank())
                           || (uri != null && !uri.isBlank());

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(tcTable).append(" tc");
        sql.append(" JOIN ").append(pTable).append(" p ON tc.").append(tcProjectId)
           .append(" = p.").append(pId);
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
        if (projectName != null && !projectName.isBlank()) result.put("projectName", projectName);
        if (caseName    != null && !caseName.isBlank())    result.put("caseName",    caseName);
        if (versionName != null && !versionName.isBlank()) result.put("versionName", versionName);
        if (uri         != null && !uri.isBlank())         result.put("uri",         uri);
        return result;
    }

    /**
     * 列出项目，分页返回。
     */
    public Map<String, Object> listProjects(String keyword, Integer page, Integer limit) {
        int safePage  = (page == null || page < 1) ? 1 : page;
        int safeLimit = clampLimit(limit);
        int offset    = (safePage - 1) * safeLimit;

        String pTable = db.projectTable();
        String pId    = db.projectCol("id", "id");
        String pName  = db.projectCol("name", "name");
        String pDesc  = db.projectCol("description", "description");

        StringBuilder where  = new StringBuilder();
        List<Object>  params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            where.append(" WHERE p.").append(pName).append(" LIKE ?");
            where.append(" OR p.").append(pDesc).append(" LIKE ?");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }

        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + pTable + " p" + where, Long.class, params.toArray());

        // SELECT：project 的可见字段 + 聚合统计
        String projectSelect = buildProjectSelect(pId);
        String dataSql = "SELECT " + projectSelect
                + " FROM " + pTable + " p"
                + where
                + " ORDER BY p." + pId
                + " LIMIT ? OFFSET ?";

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(safeLimit);
        dataParams.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(dataSql, dataParams.toArray());

        return pagedResult(total, safePage, safeLimit, items);
    }

    /**
     * 获取项目详情：版本列表 + 用例统计。
     */
    public Map<String, Object> getProjectDetail(String projectName) {
        String pTable = db.projectTable();
        String pName  = db.projectCol("name", "name");
        String pId    = db.projectCol("id", "id");

        // 查项目（用显式 SELECT）
        String projectSelect = buildProjectSelectPlain();
        List<Map<String, Object>> projects = jdbc.queryForList(
                "SELECT " + projectSelect + " FROM " + pTable + " p"
                + " WHERE p." + pName + " LIKE ? LIMIT 1",
                "%" + projectName.trim() + "%"
        );

        if (projects.isEmpty()) {
            return Map.of("found", false, "error", "Project not found: " + projectName);
        }

        Map<String, Object> project = projects.get(0);
        // getProjectDetail 查询用 pId 作为 map key，pId 可能是 "id"
        Object projectId = project.get(pId);
        if (projectId == null) {
            // 若用了逻辑名别名 "id"，尝试逻辑名
            projectId = project.get("id");
        }

        // 查版本（显式 SELECT）
        String vSelect = buildVersionSelect();
        List<Map<String, Object>> versions = jdbc.queryForList(
                "SELECT " + vSelect + " FROM " + db.versionTable()
                + " WHERE " + db.versionCol("projectId", "project_id") + " = ?"
                + " ORDER BY " + db.versionCol("id", "id"),
                projectId
        );

        // 用例统计（聚合，不用 visible 字段）
        List<Map<String, Object>> caseStats = jdbc.queryForList(
                "SELECT " + db.testcaseCol("caseType", "case_type") + " AS caseType, "
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

    // ── 私有辅助方法 ─────────────────────────────────────────────────────────

    /**
     * 构建 testcase 表的 SELECT 子句（tc.col AS logicalName 格式）。
     * 字段列表由 YAML visible 配置决定，新增字段无需改代码。
     */
    private String buildTestcaseSelect(String pNameCol,
                                        boolean needVersion,
                                        String vVersionNameCol,
                                        String vUriCol) {
        Map<String, DbTableProperties.FieldConfig> fields = db.visibleFields("testcase");
        List<String> parts = new ArrayList<>();

        if (fields.isEmpty()) {
            // 无配置时退化为 tc.*（兼容旧配置）
            parts.add("tc.*");
        } else {
            fields.forEach((logical, cfg) ->
                    parts.add("tc." + cfg.getColumn() + " AS " + logical));
        }

        parts.add("p." + pNameCol + " AS projectName");
        if (needVersion) {
            parts.add("v." + vVersionNameCol + " AS versionName");
            parts.add("v." + vUriCol + " AS versionUri");
        }
        return String.join(", ", parts);
    }

    /** 构建 project 表的 SELECT 子句（含聚合 case_count / version_count）。 */
    private String buildProjectSelect(String pId) {
        Map<String, DbTableProperties.FieldConfig> fields = db.visibleFields("project");
        List<String> parts = new ArrayList<>();

        if (fields.isEmpty()) {
            parts.add("p.*");
        } else {
            fields.forEach((logical, cfg) ->
                    parts.add("p." + cfg.getColumn() + " AS " + logical));
        }

        String tcTable = db.testcaseTable();
        String tcProjId = db.testcaseCol("projectId", "project_id");
        String vTable  = db.versionTable();
        String vProjId = db.versionCol("projectId", "project_id");

        parts.add("(SELECT COUNT(*) FROM " + tcTable + " tc WHERE tc." + tcProjId
                + " = p." + pId + ") AS caseCount");
        parts.add("(SELECT COUNT(*) FROM " + vTable + " v WHERE v." + vProjId
                + " = p." + pId + ") AS versionCount");
        return String.join(", ", parts);
    }

    /** 构建不带聚合的 project SELECT（供 getProjectDetail 用）。 */
    private String buildProjectSelectPlain() {
        Map<String, DbTableProperties.FieldConfig> fields = db.visibleFields("project");
        if (fields.isEmpty()) return "p.*";
        List<String> parts = new ArrayList<>();
        // getProjectDetail 需要 id 字段来关联查询，强制包含（即使 visible=false）
        String pIdCol = db.projectCol("id", "id");
        parts.add("p." + pIdCol + " AS id");
        fields.forEach((logical, cfg) -> {
            if (!logical.equals("id")) {  // 避免重复
                parts.add("p." + cfg.getColumn() + " AS " + logical);
            }
        });
        return String.join(", ", parts);
    }

    /** 构建 version 表的 SELECT 子句。 */
    private String buildVersionSelect() {
        Map<String, DbTableProperties.FieldConfig> fields = db.visibleFields("version");
        if (fields.isEmpty()) return "*";
        List<String> parts = new ArrayList<>();
        fields.forEach((logical, cfg) ->
                parts.add(cfg.getColumn() + " AS " + logical));
        return String.join(", ", parts);
    }

    private Map<String, Object> pagedResult(long total, int page, int pageSize,
                                             List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalPages", (int) Math.ceil((double) total / pageSize));
        result.put("items", items);
        return result;
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit < 1) return defaultPageSize;
        return Math.min(limit, maxPageSize);
    }
}
