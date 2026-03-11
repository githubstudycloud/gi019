package com.mcp.server.meta.domain;

/**
 * 数据源配置 —— 对应 mcp_datasource 表。
 * 支持 mysql / postgresql / h2 等 JDBC 类型数据源。
 */
public class DsConfig {

    private Long id;
    private String dsKey;       // 唯一标识，如 "biz1" "common"
    private String dsName;      // 显示名称
    private String dsType;      // mysql / postgresql / h2
    private String url;
    private String username;
    private String password;
    private String driverClass;
    private int poolSize = 5;
    private String extraProps;  // JSON 格式额外属性
    private boolean enabled = true;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDsKey() { return dsKey; }
    public void setDsKey(String dsKey) { this.dsKey = dsKey; }

    public String getDsName() { return dsName; }
    public void setDsName(String dsName) { this.dsName = dsName; }

    public String getDsType() { return dsType; }
    public void setDsType(String dsType) { this.dsType = dsType; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDriverClass() { return driverClass; }
    public void setDriverClass(String driverClass) { this.driverClass = driverClass; }

    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }

    public String getExtraProps() { return extraProps; }
    public void setExtraProps(String extraProps) { this.extraProps = extraProps; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    /** 自动推断驱动类名 */
    public String resolvedDriverClass() {
        if (driverClass != null && !driverClass.isBlank()) return driverClass;
        if (url == null) return null;
        if (url.startsWith("jdbc:mysql"))      return "com.mysql.cj.jdbc.Driver";
        if (url.startsWith("jdbc:h2"))         return "org.h2.Driver";
        if (url.startsWith("jdbc:postgresql")) return "org.postgresql.Driver";
        return null;
    }
}
