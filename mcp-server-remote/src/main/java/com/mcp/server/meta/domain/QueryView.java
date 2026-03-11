package com.mcp.server.meta.domain;

/**
 * 查询视图/模板 —— 对应 mcp_query_view。
 * 封装单表或多表查询为可复用的 SQL 模板。
 * 参数占位：#{paramName}
 * 条件块：#{if paramName} ... #{/if}（参数不为空时才拼入）
 */
public class QueryView {

    private Long id;
    private String viewKey;       // 唯一key
    private String viewName;
    private String description;
    private String primaryDsKey;  // 主数据源，null 则由调用方传入
    private String sqlTemplate;   // SQL 模板
    private String countSql;      // 专用 COUNT SQL（可空，空则自动包装）
    private String resultColumns; // JSON: 列别名/可见性配置
    private int maxRows = 500;
    private boolean enabled = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getViewKey() { return viewKey; }
    public void setViewKey(String viewKey) { this.viewKey = viewKey; }

    public String getViewName() { return viewName; }
    public void setViewName(String viewName) { this.viewName = viewName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPrimaryDsKey() { return primaryDsKey; }
    public void setPrimaryDsKey(String primaryDsKey) { this.primaryDsKey = primaryDsKey; }

    public String getSqlTemplate() { return sqlTemplate; }
    public void setSqlTemplate(String sqlTemplate) { this.sqlTemplate = sqlTemplate; }

    public String getCountSql() { return countSql; }
    public void setCountSql(String countSql) { this.countSql = countSql; }

    public String getResultColumns() { return resultColumns; }
    public void setResultColumns(String resultColumns) { this.resultColumns = resultColumns; }

    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int maxRows) { this.maxRows = maxRows; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
