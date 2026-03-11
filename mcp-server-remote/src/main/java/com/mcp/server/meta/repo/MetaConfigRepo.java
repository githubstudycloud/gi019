package com.mcp.server.meta.repo;

import com.mcp.server.meta.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.*;

/**
 * Meta-Config 仓库 —— 所有元数据的 CRUD 操作。
 * 使用 primary JdbcTemplate（主数据源存放 meta 配置表）。
 */
@Repository
public class MetaConfigRepo {

    private static final Logger log = LoggerFactory.getLogger(MetaConfigRepo.class);

    private final JdbcTemplate jdbc;

    public MetaConfigRepo(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ================================================================
    // DsConfig
    // ================================================================

    public List<DsConfig> findAllDatasources(boolean enabledOnly) {
        String sql = "SELECT * FROM mcp_datasource" + (enabledOnly ? " WHERE enabled=TRUE" : "") + " ORDER BY id";
        return jdbc.query(sql, DS_MAPPER);
    }

    public Optional<DsConfig> findDatasourceByKey(String dsKey) {
        List<DsConfig> list = jdbc.query(
                "SELECT * FROM mcp_datasource WHERE ds_key=?", DS_MAPPER, dsKey);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void saveDatasource(DsConfig ds) {
        if (ds.getId() == null) {
            jdbc.update(
                "INSERT INTO mcp_datasource (ds_key,ds_name,ds_type,url,username,password,driver_class,pool_size,extra_props,enabled,remark) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                ds.getDsKey(), ds.getDsName(), ds.getDsType(), ds.getUrl(),
                ds.getUsername(), ds.getPassword(), ds.getDriverClass(),
                ds.getPoolSize(), ds.getExtraProps(), ds.isEnabled(), ds.getRemark());
        } else {
            jdbc.update(
                "UPDATE mcp_datasource SET ds_name=?,ds_type=?,url=?,username=?,password=?,driver_class=?," +
                "pool_size=?,extra_props=?,enabled=?,remark=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                ds.getDsName(), ds.getDsType(), ds.getUrl(), ds.getUsername(), ds.getPassword(),
                ds.getDriverClass(), ds.getPoolSize(), ds.getExtraProps(), ds.isEnabled(),
                ds.getRemark(), ds.getId());
        }
    }

    public void toggleDatasource(Long id, boolean enabled) {
        jdbc.update("UPDATE mcp_datasource SET enabled=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", enabled, id);
    }

    // ================================================================
    // TableMeta
    // ================================================================

    public List<TableMeta> findTablesByDsId(Long dsId) {
        return jdbc.query(
                "SELECT * FROM mcp_table_meta WHERE ds_id=? ORDER BY table_name", TABLE_MAPPER, dsId);
    }

    public List<TableMeta> findActiveTables(Long dsId) {
        return jdbc.query(
                "SELECT * FROM mcp_table_meta WHERE ds_id=? AND is_excluded=FALSE ORDER BY table_name",
                TABLE_MAPPER, dsId);
    }

    public void upsertTableMeta(TableMeta t) {
        int rows = jdbc.update(
            "UPDATE mcp_table_meta SET table_alias=?,description=?,is_excluded=?,is_large_table=?,row_estimate=?," +
            "last_stats_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE ds_id=? AND table_name=?",
            t.getTableAlias(), t.getDescription(), t.isExcluded(), t.isLargeTable(),
            t.getRowEstimate(), t.getDsId(), t.getTableName());
        if (rows == 0) {
            jdbc.update(
                "INSERT INTO mcp_table_meta (ds_id,schema_name,table_name,table_alias,description,is_excluded,is_large_table,row_estimate,last_stats_at) " +
                "VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                t.getDsId(), t.getSchemaName(), t.getTableName(), t.getTableAlias(),
                t.getDescription(), t.isExcluded(), t.isLargeTable(), t.getRowEstimate());
        }
    }

    public void setTableExcluded(Long tableId, boolean excluded) {
        jdbc.update("UPDATE mcp_table_meta SET is_excluded=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", excluded, tableId);
    }

    public void updateTableStats(Long dsId, String tableName, long rowCount) {
        jdbc.update(
            "UPDATE mcp_table_meta SET row_estimate=?,is_large_table=?,last_stats_at=CURRENT_TIMESTAMP WHERE ds_id=? AND table_name=?",
            rowCount, rowCount > 100000, dsId, tableName);
    }

    // ================================================================
    // QueryView
    // ================================================================

    public List<QueryView> findAllViews(boolean enabledOnly) {
        String sql = "SELECT * FROM mcp_query_view" + (enabledOnly ? " WHERE enabled=TRUE" : "") + " ORDER BY id";
        return jdbc.query(sql, VIEW_MAPPER);
    }

    public Optional<QueryView> findViewById(Long id) {
        List<QueryView> list = jdbc.query("SELECT * FROM mcp_query_view WHERE id=?", VIEW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<QueryView> findViewByKey(String viewKey) {
        List<QueryView> list = jdbc.query("SELECT * FROM mcp_query_view WHERE view_key=?", VIEW_MAPPER, viewKey);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void saveView(QueryView v) {
        if (v.getId() == null) {
            jdbc.update(
                "INSERT INTO mcp_query_view (view_key,view_name,description,primary_ds_key,sql_template,count_sql,result_columns,max_rows,enabled) " +
                "VALUES (?,?,?,?,?,?,?,?,?)",
                v.getViewKey(), v.getViewName(), v.getDescription(), v.getPrimaryDsKey(),
                v.getSqlTemplate(), v.getCountSql(), v.getResultColumns(), v.getMaxRows(), v.isEnabled());
        } else {
            jdbc.update(
                "UPDATE mcp_query_view SET view_name=?,description=?,primary_ds_key=?,sql_template=?,count_sql=?," +
                "result_columns=?,max_rows=?,enabled=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                v.getViewName(), v.getDescription(), v.getPrimaryDsKey(), v.getSqlTemplate(),
                v.getCountSql(), v.getResultColumns(), v.getMaxRows(), v.isEnabled(), v.getId());
        }
    }

    // ================================================================
    // ToolConfig + ParamConfig
    // ================================================================

    public List<ToolConfig> findAllTools(boolean enabledOnly) {
        String sql = "SELECT * FROM mcp_tool_config" + (enabledOnly ? " WHERE enabled=TRUE" : "") + " ORDER BY sort_order, id";
        List<ToolConfig> tools = jdbc.query(sql, TOOL_MAPPER);
        // 批量加载参数
        for (ToolConfig tool : tools) {
            tool.setParams(findParamsByToolId(tool.getId()));
        }
        return tools;
    }

    public List<ParamConfig> findParamsByToolId(Long toolId) {
        return jdbc.query(
                "SELECT * FROM mcp_param_config WHERE tool_id=? ORDER BY sort_order",
                PARAM_MAPPER, toolId);
    }

    public void saveTool(ToolConfig t) {
        if (t.getId() == null) {
            jdbc.update(
                "INSERT INTO mcp_tool_config (tool_key,tool_name,description,tool_type,query_view_id,enabled,sort_order) " +
                "VALUES (?,?,?,?,?,?,?)",
                t.getToolKey(), t.getToolName(), t.getDescription(), t.getToolType(),
                t.getQueryViewId(), t.isEnabled(), t.getSortOrder());
        } else {
            jdbc.update(
                "UPDATE mcp_tool_config SET tool_name=?,description=?,tool_type=?,query_view_id=?," +
                "enabled=?,sort_order=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                t.getToolName(), t.getDescription(), t.getToolType(), t.getQueryViewId(),
                t.isEnabled(), t.getSortOrder(), t.getId());
        }
    }

    public void saveParam(ParamConfig p) {
        if (p.getId() == null) {
            jdbc.update(
                "INSERT INTO mcp_param_config (tool_id,param_name,param_label,description,param_type,is_required,default_value,enum_values,sort_order) " +
                "VALUES (?,?,?,?,?,?,?,?,?)",
                p.getToolId(), p.getParamName(), p.getParamLabel(), p.getDescription(),
                p.getParamType(), p.isRequired(), p.getDefaultValue(), p.getEnumValues(), p.getSortOrder());
        } else {
            jdbc.update(
                "UPDATE mcp_param_config SET param_label=?,description=?,param_type=?,is_required=?," +
                "default_value=?,enum_values=?,sort_order=? WHERE id=?",
                p.getParamLabel(), p.getDescription(), p.getParamType(), p.isRequired(),
                p.getDefaultValue(), p.getEnumValues(), p.getSortOrder(), p.getId());
        }
    }

    public void deleteTool(Long id) {
        jdbc.update("DELETE FROM mcp_param_config WHERE tool_id=?", id);
        jdbc.update("DELETE FROM mcp_tool_config WHERE id=?", id);
    }

    // ================================================================
    // PerfRule
    // ================================================================

    public List<PerfRule> findPerfRules(Long dsId) {
        // 全局规则 + 该数据源专属规则
        return jdbc.query(
            "SELECT * FROM mcp_perf_rule WHERE enabled=TRUE AND (ds_id IS NULL OR ds_id=?) ORDER BY ds_id NULLS LAST",
            PERF_MAPPER, dsId);
    }

    // ================================================================
    // RowMappers
    // ================================================================

    private static final RowMapper<DsConfig> DS_MAPPER = (rs, i) -> {
        DsConfig d = new DsConfig();
        d.setId(rs.getLong("id"));
        d.setDsKey(rs.getString("ds_key"));
        d.setDsName(rs.getString("ds_name"));
        d.setDsType(rs.getString("ds_type"));
        d.setUrl(rs.getString("url"));
        d.setUsername(rs.getString("username"));
        d.setPassword(rs.getString("password"));
        d.setDriverClass(rs.getString("driver_class"));
        d.setPoolSize(rs.getInt("pool_size"));
        d.setExtraProps(rs.getString("extra_props"));
        d.setEnabled(rs.getBoolean("enabled"));
        d.setRemark(rs.getString("remark"));
        return d;
    };

    private static final RowMapper<TableMeta> TABLE_MAPPER = (rs, i) -> {
        TableMeta t = new TableMeta();
        t.setId(rs.getLong("id"));
        t.setDsId(rs.getLong("ds_id"));
        t.setSchemaName(rs.getString("schema_name"));
        t.setTableName(rs.getString("table_name"));
        t.setTableAlias(rs.getString("table_alias"));
        t.setDescription(rs.getString("description"));
        t.setExcluded(rs.getBoolean("is_excluded"));
        t.setLargeTable(rs.getBoolean("is_large_table"));
        long rowEst = rs.getLong("row_estimate");
        t.setRowEstimate(rs.wasNull() ? null : rowEst);
        return t;
    };

    private static final RowMapper<QueryView> VIEW_MAPPER = (rs, i) -> {
        QueryView v = new QueryView();
        v.setId(rs.getLong("id"));
        v.setViewKey(rs.getString("view_key"));
        v.setViewName(rs.getString("view_name"));
        v.setDescription(rs.getString("description"));
        v.setPrimaryDsKey(rs.getString("primary_ds_key"));
        v.setSqlTemplate(rs.getString("sql_template"));
        v.setCountSql(rs.getString("count_sql"));
        v.setResultColumns(rs.getString("result_columns"));
        v.setMaxRows(rs.getInt("max_rows"));
        v.setEnabled(rs.getBoolean("enabled"));
        return v;
    };

    private static final RowMapper<ToolConfig> TOOL_MAPPER = (rs, i) -> {
        ToolConfig t = new ToolConfig();
        t.setId(rs.getLong("id"));
        t.setToolKey(rs.getString("tool_key"));
        t.setToolName(rs.getString("tool_name"));
        t.setDescription(rs.getString("description"));
        t.setToolType(rs.getString("tool_type"));
        long viewId = rs.getLong("query_view_id");
        t.setQueryViewId(rs.wasNull() ? null : viewId);
        t.setEnabled(rs.getBoolean("enabled"));
        t.setSortOrder(rs.getInt("sort_order"));
        return t;
    };

    private static final RowMapper<ParamConfig> PARAM_MAPPER = (rs, i) -> {
        ParamConfig p = new ParamConfig();
        p.setId(rs.getLong("id"));
        p.setToolId(rs.getLong("tool_id"));
        p.setParamName(rs.getString("param_name"));
        p.setParamLabel(rs.getString("param_label"));
        p.setDescription(rs.getString("description"));
        p.setParamType(rs.getString("param_type"));
        p.setRequired(rs.getBoolean("is_required"));
        p.setDefaultValue(rs.getString("default_value"));
        p.setEnumValues(rs.getString("enum_values"));
        p.setSortOrder(rs.getInt("sort_order"));
        return p;
    };

    private static final RowMapper<PerfRule> PERF_MAPPER = (rs, i) -> {
        PerfRule r = new PerfRule();
        r.setId(rs.getLong("id"));
        r.setRuleName(rs.getString("rule_name"));
        long dsId = rs.getLong("ds_id");
        r.setDsId(rs.wasNull() ? null : dsId);
        r.setTablePattern(rs.getString("table_pattern"));
        r.setMaxScanRows(rs.getLong("max_scan_rows"));
        r.setMaxResultRows(rs.getInt("max_result_rows"));
        r.setTimeoutSeconds(rs.getInt("timeout_seconds"));
        r.setAction(rs.getString("action"));
        return r;
    };

    // ================================================================
    // 统计查询（用于 Admin UI 概览）
    // ================================================================

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("datasourceCount",     jdbc.queryForObject("SELECT COUNT(*) FROM mcp_datasource WHERE enabled=TRUE", Long.class));
        stats.put("tableCount",          jdbc.queryForObject("SELECT COUNT(*) FROM mcp_table_meta WHERE is_excluded=FALSE", Long.class));
        stats.put("dynamicToolCount",    jdbc.queryForObject("SELECT COUNT(*) FROM mcp_tool_config WHERE enabled=TRUE", Long.class));
        stats.put("viewCount",           jdbc.queryForObject("SELECT COUNT(*) FROM mcp_query_view WHERE enabled=TRUE", Long.class));
        // 注：staticToolCount 由 McpRegistry 统计，此处仅反映动态配置工具数量
        return stats;
    }
}
