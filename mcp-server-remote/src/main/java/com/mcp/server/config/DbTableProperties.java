package com.mcp.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 可配置的数据库表名和字段名映射。
 * 通过 application.yml 中的 mcp.db 前缀配置。
 *
 * 使用方式:
 *   mcp.db.tables.project=t_project
 *   mcp.db.columns.project.name=name
 */
@Component
@ConfigurationProperties(prefix = "mcp.db")
public class DbTableProperties {

    /** 表名映射: 逻辑名 -> 实际表名 */
    private Map<String, String> tables = new HashMap<>();

    /** 字段名映射: 逻辑表名.逻辑字段名 -> 实际字段名 */
    private Map<String, Map<String, String>> columns = new HashMap<>();

    public Map<String, String> getTables() { return tables; }
    public void setTables(Map<String, String> tables) { this.tables = tables; }
    public Map<String, Map<String, String>> getColumns() { return columns; }
    public void setColumns(Map<String, Map<String, String>> columns) { this.columns = columns; }

    // --- 便捷方法 ---

    /** 获取实际表名，找不到返回默认值 */
    public String table(String logicalName, String defaultName) {
        return tables.getOrDefault(logicalName, defaultName);
    }

    /** 获取实际字段名，找不到返回默认值 */
    public String column(String tableName, String logicalColumn, String defaultColumn) {
        Map<String, String> cols = columns.get(tableName);
        if (cols == null) return defaultColumn;
        return cols.getOrDefault(logicalColumn, defaultColumn);
    }

    // --- 预定义的逻辑名常量（与业务表对应）---

    public String projectTable() {
        return table("project", "t_project");
    }

    public String versionTable() {
        return table("version", "t_project_version");
    }

    public String testcaseTable() {
        return table("testcase", "t_test_case");
    }

    // 项目表字段
    public String projectCol(String logical, String defaultCol) {
        return column("project", logical, defaultCol);
    }

    // 版本表字段
    public String versionCol(String logical, String defaultCol) {
        return column("version", logical, defaultCol);
    }

    // 用例表字段
    public String testcaseCol(String logical, String defaultCol) {
        return column("testcase", logical, defaultCol);
    }
}
