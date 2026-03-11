-- ============================================================
-- 业务库 Schema（基线用例表 + 执行用例表）
-- 每个业务库都有这两张表
-- H2 测试模式下，biz1db / biz2db 各自初始化一份
-- ============================================================

CREATE TABLE IF NOT EXISTS t_baseline_case (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id      BIGINT       NOT NULL COMMENT '项目ID',
    baseline_name   VARCHAR(200) NOT NULL COMMENT '基线名称（如：v1.0基线）',
    case_code       VARCHAR(100) COMMENT '用例编号',
    case_name       VARCHAR(200) NOT NULL COMMENT '用例名称',
    case_type       VARCHAR(50)  COMMENT '用例类型（功能/性能/安全）',
    priority        VARCHAR(20)  COMMENT '优先级 P0~P3',
    module_name     VARCHAR(100) COMMENT '所属模块',
    precondition    TEXT         COMMENT '前置条件',
    steps           TEXT         COMMENT '操作步骤',
    expected_result TEXT         COMMENT '预期结果',
    status          VARCHAR(20)  DEFAULT 'active' COMMENT 'active/disabled/draft',
    created_by      VARCHAR(100) COMMENT '创建人',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_execution_case (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id      BIGINT       NOT NULL COMMENT '项目ID',
    baseline_id     BIGINT COMMENT '关联基线ID',
    case_code       VARCHAR(100) COMMENT '用例编号',
    case_name       VARCHAR(200) NOT NULL COMMENT '用例名称',
    execute_round   VARCHAR(50)  COMMENT '执行轮次（第1轮/第2轮/回归）',
    executor        VARCHAR(100) COMMENT '执行人',
    execute_time    TIMESTAMP    COMMENT '执行时间',
    actual_result   TEXT         COMMENT '实际结果',
    execute_status  VARCHAR(50)  COMMENT 'pass/fail/skip/blocked',
    bug_id          VARCHAR(100) COMMENT '关联缺陷ID',
    remark          TEXT         COMMENT '备注',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
