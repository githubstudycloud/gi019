-- MySQL 5.7 建表脚本
-- 根据实际需要修改表名/字段名，然后在 application.yml 中配置 mcp.db.tables 和 mcp.db.columns

CREATE TABLE IF NOT EXISTS t_project (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL COMMENT '项目名称',
    description VARCHAR(500) COMMENT '项目描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

CREATE TABLE IF NOT EXISTS t_project_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT '关联项目ID',
    version_name VARCHAR(100) NOT NULL COMMENT '版本名称',
    uri VARCHAR(500) COMMENT '对应URI',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_version_name (version_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目版本/URI表';

CREATE TABLE IF NOT EXISTS t_test_case (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT '关联项目ID',
    case_name VARCHAR(200) NOT NULL COMMENT '用例名称',
    case_type VARCHAR(50) COMMENT '用例类型',
    priority VARCHAR(20) COMMENT '优先级 P0/P1/P2',
    precondition TEXT COMMENT '前置条件',
    steps TEXT COMMENT '操作步骤',
    expected_result TEXT COMMENT '预期结果',
    status VARCHAR(20) DEFAULT 'active' COMMENT '状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_case_name (case_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试用例表';
