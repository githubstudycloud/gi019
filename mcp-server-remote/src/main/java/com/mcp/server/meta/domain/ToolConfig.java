package com.mcp.server.meta.domain;

import java.util.List;

/**
 * MCP 工具配置 —— 对应 mcp_tool_config + mcp_param_config。
 * 从 DB 动态加载，无需重启即可新增/修改工具。
 */
public class ToolConfig {

    private Long id;
    private String toolKey;       // tool name（MCP 协议对外暴露的名称）
    private String toolName;      // 显示名
    private String description;   // AI 理解工具用途的描述
    private String toolType;      // query / count / detail / guided
    private Long queryViewId;
    private boolean enabled = true;
    private int sortOrder = 0;

    private List<ParamConfig> params; // 关联参数（嵌套加载）

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToolKey() { return toolKey; }
    public void setToolKey(String toolKey) { this.toolKey = toolKey; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getToolType() { return toolType; }
    public void setToolType(String toolType) { this.toolType = toolType; }

    public Long getQueryViewId() { return queryViewId; }
    public void setQueryViewId(Long queryViewId) { this.queryViewId = queryViewId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public List<ParamConfig> getParams() { return params; }
    public void setParams(List<ParamConfig> params) { this.params = params; }
}
