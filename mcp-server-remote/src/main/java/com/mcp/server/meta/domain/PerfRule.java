package com.mcp.server.meta.domain;

/**
 * 性能保护规则 —— 对应 mcp_perf_rule。
 * 防止查询把数据库打挂：扫描行数预检、结果行数限制、超时控制。
 */
public class PerfRule {

    private Long id;
    private String ruleName;
    private Long dsId;           // null = 全局规则
    private String tablePattern; // 表名通配符，null = 所有表
    private long maxScanRows = 500000L;
    private int maxResultRows = 500;
    private int timeoutSeconds = 30;
    private String action = "warn"; // warn=警告后继续 / block=拒绝 / sample=自动采样

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public Long getDsId() { return dsId; }
    public void setDsId(Long dsId) { this.dsId = dsId; }

    public String getTablePattern() { return tablePattern; }
    public void setTablePattern(String tablePattern) { this.tablePattern = tablePattern; }

    public long getMaxScanRows() { return maxScanRows; }
    public void setMaxScanRows(long maxScanRows) { this.maxScanRows = maxScanRows; }

    public int getMaxResultRows() { return maxResultRows; }
    public void setMaxResultRows(int maxResultRows) { this.maxResultRows = maxResultRows; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    /** 根据表名判断此规则是否适用 */
    public boolean matchesTable(String tableName) {
        if (tablePattern == null || tablePattern.isBlank()) return true;
        String regex = tablePattern.replace("%", ".*").replace("?", ".");
        return tableName.matches(regex);
    }
}
