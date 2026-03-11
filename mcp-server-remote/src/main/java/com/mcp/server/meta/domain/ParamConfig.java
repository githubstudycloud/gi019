package com.mcp.server.meta.domain;

/**
 * 工具参数配置 —— 对应 mcp_param_config。
 */
public class ParamConfig {

    private Long id;
    private Long toolId;
    private String paramName;     // 参数名（对外暴露给 AI）
    private String paramLabel;    // 中文标签
    private String description;
    private String paramType;     // string / integer / boolean / enum
    private boolean required = false;
    private String defaultValue;
    private String enumValues;    // JSON 数组字符串
    private int sortOrder = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }

    public String getParamName() { return paramName; }
    public void setParamName(String paramName) { this.paramName = paramName; }

    public String getParamLabel() { return paramLabel; }
    public void setParamLabel(String paramLabel) { this.paramLabel = paramLabel; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getParamType() { return paramType; }
    public void setParamType(String paramType) { this.paramType = paramType; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getEnumValues() { return enumValues; }
    public void setEnumValues(String enumValues) { this.enumValues = enumValues; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
