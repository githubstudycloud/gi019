-- ============================================================
-- Meta-Config 元数据库 Schema
-- 存储所有动态配置：数据源、表元数据、字段、查询视图、工具、参数、性能规则
-- 使用主数据源（primary datasource）存储，无需额外数据库
-- ============================================================

-- 数据源配置表
CREATE TABLE IF NOT EXISTS mcp_datasource (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    ds_key          VARCHAR(100) NOT NULL UNIQUE COMMENT '数据源唯一标识',
    ds_name         VARCHAR(200) NOT NULL COMMENT '显示名称',
    ds_type         VARCHAR(50)  NOT NULL DEFAULT 'mysql' COMMENT 'mysql/postgresql/h2',
    url             VARCHAR(500) COMMENT 'JDBC URL',
    username        VARCHAR(200) COMMENT '用户名',
    password        VARCHAR(500) COMMENT '密码（生产环境应加密）',
    driver_class    VARCHAR(200) COMMENT '驱动类名（可自动推断）',
    pool_size       INT          DEFAULT 5 COMMENT '连接池大小',
    extra_props     VARCHAR(1000) COMMENT '额外JDBC属性（JSON格式）',
    enabled         BOOLEAN      DEFAULT TRUE COMMENT '是否启用',
    remark          VARCHAR(500) COMMENT '备注',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- 表元数据注册表
CREATE TABLE IF NOT EXISTS mcp_table_meta (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    ds_id           BIGINT       NOT NULL COMMENT '所属数据源',
    schema_name     VARCHAR(100) COMMENT '数据库/Schema名',
    table_name      VARCHAR(200) NOT NULL COMMENT '物理表名',
    table_alias     VARCHAR(200) COMMENT '业务别名',
    description     VARCHAR(500) COMMENT '表说明',
    is_excluded     BOOLEAN      DEFAULT FALSE COMMENT '是否排除统计（万张表情况下标记不统计）',
    is_large_table  BOOLEAN      DEFAULT FALSE COMMENT '是否大表（需要性能保护）',
    row_estimate    BIGINT COMMENT '估算行数',
    last_stats_at   TIMESTAMP COMMENT '最后统计时间',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (ds_id, table_name)
);

-- 字段元数据配置表
CREATE TABLE IF NOT EXISTS mcp_field_meta (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    table_id        BIGINT       NOT NULL COMMENT '所属表',
    col_name        VARCHAR(200) NOT NULL COMMENT '物理列名',
    field_alias     VARCHAR(200) COMMENT '逻辑别名（camelCase，对外暴露）',
    field_label     VARCHAR(200) COMMENT '显示标签',
    description     VARCHAR(500) COMMENT '字段说明',
    data_type       VARCHAR(100) COMMENT '数据类型',
    is_visible      BOOLEAN      DEFAULT TRUE COMMENT '是否在结果中返回',
    is_searchable   BOOLEAN      DEFAULT FALSE COMMENT '是否允许搜索过滤',
    is_pk           BOOLEAN      DEFAULT FALSE COMMENT '是否主键',
    sort_order      INT          DEFAULT 0 COMMENT '排序',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (table_id, col_name)
);

-- 查询视图/模板表（单表或多表查询封装）
CREATE TABLE IF NOT EXISTS mcp_query_view (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    view_key        VARCHAR(100) NOT NULL UNIQUE COMMENT '视图唯一key',
    view_name       VARCHAR(200) NOT NULL COMMENT '视图名称',
    description     VARCHAR(500) COMMENT '说明',
    primary_ds_key  VARCHAR(100) COMMENT '主数据源key（不填则由工具参数决定）',
    sql_template    TEXT         NOT NULL COMMENT 'SQL模板，参数用 #{paramName} 占位',
    count_sql       TEXT COMMENT '独立COUNT SQL（为空则自动包装）',
    result_columns  VARCHAR(2000) COMMENT '返回列定义（JSON: [{col,alias,label,visible}]）',
    max_rows        INT          DEFAULT 500 COMMENT '最大返回行数',
    enabled         BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- MCP 工具配置表
CREATE TABLE IF NOT EXISTS mcp_tool_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tool_key        VARCHAR(200) NOT NULL UNIQUE COMMENT '工具唯一key（即tool name）',
    tool_name       VARCHAR(200) NOT NULL COMMENT '工具显示名',
    description     TEXT         NOT NULL COMMENT 'MCP工具描述（AI用来理解工具用途）',
    tool_type       VARCHAR(50)  DEFAULT 'query' COMMENT 'query/count/detail/guided',
    query_view_id   BIGINT COMMENT '关联的查询视图',
    enabled         BOOLEAN      DEFAULT TRUE,
    sort_order      INT          DEFAULT 0,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- 工具参数配置表
CREATE TABLE IF NOT EXISTS mcp_param_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tool_id         BIGINT       NOT NULL COMMENT '所属工具',
    param_name      VARCHAR(200) NOT NULL COMMENT '参数名（对外暴露给AI）',
    param_label     VARCHAR(200) COMMENT '参数标签',
    description     VARCHAR(500) COMMENT '参数说明',
    param_type      VARCHAR(50)  DEFAULT 'string' COMMENT 'string/integer/boolean/enum',
    is_required     BOOLEAN      DEFAULT FALSE COMMENT '是否必填',
    default_value   VARCHAR(500) COMMENT '默认值',
    enum_values     VARCHAR(1000) COMMENT '可选值列表（JSON数组）',
    sort_order      INT          DEFAULT 0,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- 性能保护规则表
CREATE TABLE IF NOT EXISTS mcp_perf_rule (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_name       VARCHAR(200) NOT NULL COMMENT '规则名称',
    ds_id           BIGINT COMMENT '适用数据源（NULL=全局）',
    table_pattern   VARCHAR(200) COMMENT '表名模式（支持%通配，NULL=所有表）',
    max_scan_rows   BIGINT       DEFAULT 1000000 COMMENT '最大扫描行数',
    max_result_rows INT          DEFAULT 1000 COMMENT '最大结果行数',
    timeout_seconds INT          DEFAULT 30 COMMENT '查询超时秒数',
    action          VARCHAR(20)  DEFAULT 'warn' COMMENT 'warn=警告继续/block=拒绝/sample=采样',
    enabled         BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 公共库新增表（第二业务：执行/基线查询）
-- ============================================================

-- 项目-业务数据库映射表（公共库）
CREATE TABLE IF NOT EXISTS t_biz_db_mapping (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id      BIGINT       NOT NULL COMMENT '项目ID',
    ds_key          VARCHAR(100) NOT NULL COMMENT '对应mcp_datasource.ds_key',
    biz_name        VARCHAR(200) COMMENT '业务库名称',
    enabled         BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- 用例版本关系表（公共库）
CREATE TABLE IF NOT EXISTS t_case_version (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id      BIGINT       NOT NULL COMMENT '项目ID',
    version_name    VARCHAR(100) NOT NULL COMMENT '版本名',
    baseline_id     BIGINT COMMENT '关联基线ID',
    remark          VARCHAR(500),
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
