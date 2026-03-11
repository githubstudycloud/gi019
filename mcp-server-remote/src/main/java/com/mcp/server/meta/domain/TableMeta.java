package com.mcp.server.meta.domain;

import java.time.LocalDateTime;

/**
 * 表元数据 —— 对应 mcp_table_meta。
 * 支持万张表场景：is_excluded 标记不统计的表，is_large_table 触发性能保护。
 */
public class TableMeta {

    private Long id;
    private Long dsId;
    private String schemaName;
    private String tableName;      // 物理表名
    private String tableAlias;     // 业务别名
    private String description;
    private boolean excluded = false;      // true=不在统计/搜索中显示
    private boolean largeTable = false;    // true=触发性能保护规则
    private Long rowEstimate;              // 估算行数（用于性能判断）
    private LocalDateTime lastStatsAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDsId() { return dsId; }
    public void setDsId(Long dsId) { this.dsId = dsId; }

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getTableAlias() { return tableAlias; }
    public void setTableAlias(String tableAlias) { this.tableAlias = tableAlias; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isExcluded() { return excluded; }
    public void setExcluded(boolean excluded) { this.excluded = excluded; }

    public boolean isLargeTable() { return largeTable; }
    public void setLargeTable(boolean largeTable) { this.largeTable = largeTable; }

    public Long getRowEstimate() { return rowEstimate; }
    public void setRowEstimate(Long rowEstimate) { this.rowEstimate = rowEstimate; }

    public LocalDateTime getLastStatsAt() { return lastStatsAt; }
    public void setLastStatsAt(LocalDateTime lastStatsAt) { this.lastStatsAt = lastStatsAt; }

    /** 显示名：优先使用业务别名 */
    public String displayName() {
        return (tableAlias != null && !tableAlias.isBlank()) ? tableAlias : tableName;
    }
}
