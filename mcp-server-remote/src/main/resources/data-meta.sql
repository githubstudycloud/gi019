-- ============================================================
-- Meta-Config 初始种子数据
-- H2 测试模式：biz1db / biz2db 为内存库
-- ============================================================

-- 数据源配置：公共库（就是 primary datasource，用 "common" 标识）
INSERT INTO mcp_datasource (ds_key, ds_name, ds_type, url, username, password, driver_class, pool_size, remark)
VALUES ('common', '公共库', 'h2',
        'jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE',
        'sa', '', 'org.h2.Driver', 3, '公共项目库，含项目/版本/用例版本关系');

-- 数据源配置：业务库1
INSERT INTO mcp_datasource (ds_key, ds_name, ds_type, url, username, password, driver_class, pool_size, remark)
VALUES ('biz1', '业务库-电商', 'h2',
        'jdbc:h2:mem:biz1db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE',
        'sa', '', 'org.h2.Driver', 5, '电商业务测试库');

-- 数据源配置：业务库2
INSERT INTO mcp_datasource (ds_key, ds_name, ds_type, url, username, password, driver_class, pool_size, remark)
VALUES ('biz2', '业务库-支付', 'h2',
        'jdbc:h2:mem:biz2db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE',
        'sa', '', 'org.h2.Driver', 5, '支付业务测试库');

-- 项目-业务库映射（project_id 对应 data-h2.sql 中插入的项目）
-- 项目1（电商平台）→ biz1，项目2（用户中心）→ biz1，项目3（支付网关）→ biz2
INSERT INTO t_biz_db_mapping (project_id, ds_key, biz_name) VALUES (1, 'biz1', '电商业务库');
INSERT INTO t_biz_db_mapping (project_id, ds_key, biz_name) VALUES (2, 'biz1', '电商业务库');
INSERT INTO t_biz_db_mapping (project_id, ds_key, biz_name) VALUES (3, 'biz2', '支付业务库');

-- 用例版本关系
INSERT INTO t_case_version (project_id, version_name, baseline_id, remark) VALUES (1, 'v1.0', 1, '电商v1.0基线');
INSERT INTO t_case_version (project_id, version_name, baseline_id, remark) VALUES (1, 'v2.0', 2, '电商v2.0基线');
INSERT INTO t_case_version (project_id, version_name, baseline_id, remark) VALUES (2, 'v1.0', 3, '用户中心v1.0基线');
INSERT INTO t_case_version (project_id, version_name, baseline_id, remark) VALUES (3, 'v1.0', 4, '支付网关v1.0基线');

-- ============================================================
-- 表元数据注册（关键业务表，不用全量扫描）
-- ============================================================

-- 公共库表元数据
INSERT INTO mcp_table_meta (ds_id, table_name, table_alias, description, is_excluded)
VALUES (1, 't_project', '项目表', '项目基本信息', FALSE);
INSERT INTO mcp_table_meta (ds_id, table_name, table_alias, description, is_excluded)
VALUES (1, 't_project_version', '版本表', '项目版本和URI关系', FALSE);
INSERT INTO mcp_table_meta (ds_id, table_name, table_alias, description, is_excluded)
VALUES (1, 't_test_case', '测试用例表', '项目测试用例', FALSE);
INSERT INTO mcp_table_meta (ds_id, table_name, table_alias, description, is_excluded)
VALUES (1, 't_biz_db_mapping', '业务库映射表', '项目对应业务库', FALSE);
INSERT INTO mcp_table_meta (ds_id, table_name, table_alias, description, is_excluded)
VALUES (1, 't_case_version', '用例版本关系表', '项目用例版本关联', FALSE);

-- 业务库1表元数据
INSERT INTO mcp_table_meta (ds_id, table_name, table_alias, description, is_excluded)
VALUES (2, 't_baseline_case', '基线用例表', '基线测试用例', FALSE);
INSERT INTO mcp_table_meta (ds_id, table_name, table_alias, description, is_excluded)
VALUES (2, 't_execution_case', '执行用例表', '用例执行记录', FALSE);

-- 业务库2表元数据
INSERT INTO mcp_table_meta (ds_id, table_name, table_alias, description, is_excluded)
VALUES (3, 't_baseline_case', '基线用例表', '支付基线测试用例', FALSE);
INSERT INTO mcp_table_meta (ds_id, table_name, table_alias, description, is_excluded)
VALUES (3, 't_execution_case', '执行用例表', '支付用例执行记录', FALSE);

-- ============================================================
-- 性能保护规则
-- ============================================================
INSERT INTO mcp_perf_rule (rule_name, max_scan_rows, max_result_rows, timeout_seconds, action)
VALUES ('全局默认规则', 500000, 500, 30, 'warn');

INSERT INTO mcp_perf_rule (rule_name, ds_id, table_pattern, max_scan_rows, max_result_rows, timeout_seconds, action)
VALUES ('执行用例大表保护', 2, 't_execution_case', 1000000, 200, 20, 'warn');

-- ============================================================
-- 动态工具配置（execution 业务的动态工具示例）
-- ============================================================

-- 查询视图：跨库查询项目及其业务库
INSERT INTO mcp_query_view (view_key, view_name, description, primary_ds_key,
    sql_template, max_rows)
VALUES ('project_biz_overview', '项目业务库概览',
    '查询项目及其关联的业务数据库信息',
    'common',
    'SELECT p.id, p.name AS projectName, p.description, m.ds_key AS dsKey, m.biz_name AS bizName, m.enabled AS bizEnabled
     FROM t_project p
     LEFT JOIN t_biz_db_mapping m ON p.id = m.project_id
     WHERE 1=1
     #{if keyword} AND (p.name LIKE #{keyword} OR p.description LIKE #{keyword}) #{/if}
     ORDER BY p.id
     LIMIT #{limit} OFFSET #{offset}',
    100);

-- 动态工具：项目概览查询
INSERT INTO mcp_tool_config (tool_key, tool_name, description, tool_type, query_view_id, sort_order)
VALUES ('query_project_overview', '查询项目及业务库',
    '列出所有项目及其关联的业务库信息，可按项目名关键字过滤。返回结果包含项目ID、名称和所属业务库，是查询基线/执行记录的第一步。',
    'query', 1, 10);

INSERT INTO mcp_param_config (tool_id, param_name, param_label, description, param_type, is_required, sort_order)
VALUES (1, 'keyword', '项目名关键字', '项目名称或描述的模糊搜索词，不填则返回全部', 'string', FALSE, 1);
INSERT INTO mcp_param_config (tool_id, param_name, param_label, description, param_type, is_required, default_value, sort_order)
VALUES (1, 'page', '页码', '从1开始', 'integer', FALSE, '1', 2);
INSERT INTO mcp_param_config (tool_id, param_name, param_label, description, param_type, is_required, default_value, sort_order)
VALUES (1, 'limit', '每页数量', '每页返回条数，最大100', 'integer', FALSE, '20', 3);
