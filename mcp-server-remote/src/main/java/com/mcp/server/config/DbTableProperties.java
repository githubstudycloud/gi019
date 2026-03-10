package com.mcp.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 富字段配置 —— 每个字段包含：实际列名、中文显示名、语义描述、是否输出、是否可搜索。
 *
 * YAML 结构（mcp.db.tables）：
 *
 *   tables:
 *     testcase:
 *       name: t_test_case        # 实际表名
 *       label: 测试用例           # 中文名（文档/工具描述用）
 *       fields:
 *         caseName:              # 逻辑字段名（SQL 别名 + 代码引用）
 *           column: case_name    # 实际 DB 列名
 *           label: 用例名称       # 中文名
 *           description: ...     # 值域/含义说明
 *           visible: true        # 是否出现在查询结果（默认 true）
 *           searchable: true     # 是否支持 LIKE 搜索（默认 false）
 *
 * 扩展动态字段：只需在 YAML 中追加 fields 条目，无需改 Java 代码。
 * 场景适配：通过 Spring Profile 覆盖不同表名/字段名配置。
 */
@Component
@ConfigurationProperties(prefix = "mcp.db")
public class DbTableProperties {

    /** 表配置：逻辑表名 → TableConfig（按 YAML 定义顺序保留） */
    private Map<String, TableConfig> tables = new LinkedHashMap<>();

    public Map<String, TableConfig> getTables() { return tables; }
    public void setTables(Map<String, TableConfig> tables) { this.tables = tables; }

    // ── 表级便捷方法 ──────────────────────────────────────────

    public String tableName(String logical) {
        TableConfig t = tables.get(logical);
        return (t != null && t.getName() != null) ? t.getName() : logical;
    }

    public TableConfig table(String logical) {
        return tables.get(logical);
    }

    // ── 字段级便捷方法 ────────────────────────────────────────

    /** 返回指定逻辑字段的配置，找不到返回 null */
    public FieldConfig field(String tableLogical, String fieldLogical) {
        TableConfig t = tables.get(tableLogical);
        if (t == null || t.getFields() == null) return null;
        return t.getFields().get(fieldLogical);
    }

    /** 返回实际列名，找不到时回退到 fallback（保持旧接口兼容） */
    public String col(String tableLogical, String fieldLogical, String fallback) {
        FieldConfig f = field(tableLogical, fieldLogical);
        return (f != null && f.getColumn() != null) ? f.getColumn() : fallback;
    }

    /**
     * 返回所有 visible=true 的字段（有序，按 YAML 顺序）。
     * 用于动态构建 SELECT 子句：key=逻辑名（SQL 别名），value=字段配置。
     */
    public Map<String, FieldConfig> visibleFields(String tableLogical) {
        TableConfig t = tables.get(tableLogical);
        if (t == null || t.getFields() == null) return Map.of();
        Map<String, FieldConfig> result = new LinkedHashMap<>();
        t.getFields().forEach((logical, cfg) -> {
            if (cfg.isVisible()) result.put(logical, cfg);
        });
        return result;
    }

    // ── 旧接口兼容（避免 TestCaseService 大面积改动）────────────

    public String projectTable()   { return tableName("project"); }
    public String versionTable()   { return tableName("version"); }
    public String testcaseTable()  { return tableName("testcase"); }

    public String projectCol(String logical, String fallback)  { return col("project",  logical, fallback); }
    public String versionCol(String logical, String fallback)  { return col("version",  logical, fallback); }
    public String testcaseCol(String logical, String fallback) { return col("testcase", logical, fallback); }

    // ── 内部数据类 ────────────────────────────────────────────

    public static class TableConfig {
        /** 实际 DB 表名，必填 */
        private String name;
        /** 中文名，文档和工具描述用 */
        private String label;
        /** 字段定义，按 YAML 顺序保留（LinkedHashMap） */
        private Map<String, FieldConfig> fields = new LinkedHashMap<>();

        public String getName()  { return name; }
        public String getLabel() { return label; }
        public Map<String, FieldConfig> getFields() { return fields; }

        public void setName(String name)   { this.name = name; }
        public void setLabel(String label) { this.label = label; }
        public void setFields(Map<String, FieldConfig> fields) { this.fields = fields; }
    }

    public static class FieldConfig {
        /** 实际 DB 列名，必填 */
        private String column;
        /** 中文显示名，工具描述用；不设则用逻辑名 */
        private String label;
        /** 字段含义/值域说明，写入工具描述帮助 AI 理解 */
        private String description;
        /** 是否出现在 SELECT 结果中，默认 true */
        private boolean visible = true;
        /** 是否可用作 WHERE LIKE 搜索条件，默认 false */
        private boolean searchable = false;

        public String  getColumn()      { return column; }
        public String  getLabel()       { return label; }
        public String  getDescription() { return description; }
        public boolean isVisible()      { return visible; }
        public boolean isSearchable()   { return searchable; }

        public void setColumn(String column)           { this.column = column; }
        public void setLabel(String label)             { this.label = label; }
        public void setDescription(String description) { this.description = description; }
        public void setVisible(boolean visible)        { this.visible = visible; }
        public void setSearchable(boolean searchable)  { this.searchable = searchable; }
    }
}
