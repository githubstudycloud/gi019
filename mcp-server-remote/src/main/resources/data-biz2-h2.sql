-- ============================================================
-- 业务库2（biz2db）种子数据：支付网关
-- ============================================================

-- 基线用例（支付网关 project_id=3）
INSERT INTO t_baseline_case (project_id, baseline_name, case_code, case_name, case_type, priority, module_name, precondition, steps, expected_result, status, created_by)
VALUES (3, 'v1.0基线', 'PAY-001', '支付宝支付-正常支付', '功能测试', 'P0', '支付模块',
        '用户已登录，订单已创建，支付宝账号有余额',
        '1.进入支付页\n2.选择支付宝\n3.确认支付\n4.输入密码',
        '支付成功，订单状态变为已支付，通知商户', 'active', 'tester04');

INSERT INTO t_baseline_case (project_id, baseline_name, case_code, case_name, case_type, priority, module_name, precondition, steps, expected_result, status, created_by)
VALUES (3, 'v1.0基线', 'PAY-002', '微信支付-正常支付', '功能测试', 'P0', '支付模块',
        '用户已登录，订单已创建，微信钱包有余额',
        '1.进入支付页\n2.选择微信支付\n3.唤起微信\n4.确认支付',
        '支付成功，订单状态变为已支付', 'active', 'tester04');

INSERT INTO t_baseline_case (project_id, baseline_name, case_code, case_name, case_type, priority, module_name, precondition, steps, expected_result, status, created_by)
VALUES (3, 'v1.0基线', 'PAY-003', '支付超时-自动关单', '异常测试', 'P1', '支付模块',
        '订单已创建，未支付',
        '1.创建订单\n2.进入支付页\n3.等待15分钟不操作',
        '订单自动关闭，支付链接失效，库存释放', 'active', 'tester05');

INSERT INTO t_baseline_case (project_id, baseline_name, case_code, case_name, case_type, priority, module_name, precondition, steps, expected_result, status, created_by)
VALUES (3, 'v1.0基线', 'PAY-004', '重复支付拦截', '安全测试', 'P0', '支付模块',
        '订单已支付成功',
        '1.重复调用支付接口\n2.提交相同订单号',
        '返回幂等错误，不重复扣款', 'active', 'tester05');

-- 执行用例（支付网关 v1.0 第1轮）
INSERT INTO t_execution_case (project_id, baseline_id, case_code, case_name, execute_round, executor, execute_time, actual_result, execute_status, remark)
VALUES (3, 4, 'PAY-001', '支付宝支付-正常支付', '第1轮', 'tester04',
        '2026-03-03 10:00:00', '支付成功，商户通知正常', 'pass', NULL);

INSERT INTO t_execution_case (project_id, baseline_id, case_code, case_name, execute_round, executor, execute_time, actual_result, execute_status, remark)
VALUES (3, 4, 'PAY-002', '微信支付-正常支付', '第1轮', 'tester04',
        '2026-03-03 11:00:00', '支付成功', 'pass', NULL);

INSERT INTO t_execution_case (project_id, baseline_id, case_code, case_name, execute_round, executor, execute_time, actual_result, execute_status, bug_id, remark)
VALUES (3, 4, 'PAY-003', '支付超时-自动关单', '第1轮', 'tester05',
        '2026-03-03 14:00:00', '订单未自动关闭，仍可支付', 'fail', 'BUG-003', '定时任务未触发');

INSERT INTO t_execution_case (project_id, baseline_id, case_code, case_name, execute_round, executor, execute_time, actual_result, execute_status, remark)
VALUES (3, 4, 'PAY-004', '重复支付拦截', '第1轮', 'tester05',
        '2026-03-03 15:00:00', '幂等校验正常，未重复扣款', 'pass', NULL);
