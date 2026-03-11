-- ============================================================
-- 业务库1（biz1db）种子数据：电商 + 用户中心
-- ============================================================

-- 基线用例（电商平台项目 project_id=1）
INSERT INTO t_baseline_case (project_id, baseline_name, case_code, case_name, case_type, priority, module_name, precondition, steps, expected_result, status, created_by)
VALUES (1, 'v1.0基线', 'EC-001', '用户登录-正常流程', '功能测试', 'P0', '登录模块',
        '用户已注册，账号状态正常',
        '1.打开登录页\n2.输入正确用户名密码\n3.点击登录',
        '登录成功，跳转首页，显示用户昵称', 'active', 'tester01');

INSERT INTO t_baseline_case (project_id, baseline_name, case_code, case_name, case_type, priority, module_name, precondition, steps, expected_result, status, created_by)
VALUES (1, 'v1.0基线', 'EC-002', '用户登录-密码错误', '异常测试', 'P1', '登录模块',
        '用户已注册',
        '1.打开登录页\n2.输入正确用户名+错误密码\n3.点击登录',
        '提示密码错误，连续5次锁定账号', 'active', 'tester01');

INSERT INTO t_baseline_case (project_id, baseline_name, case_code, case_name, case_type, priority, module_name, precondition, steps, expected_result, status, created_by)
VALUES (1, 'v1.0基线', 'EC-003', '商品搜索-关键字搜索', '功能测试', 'P0', '搜索模块',
        '商品数据已录入',
        '1.在搜索框输入"手机"\n2.点击搜索',
        '返回包含"手机"的商品列表，按相关性排序', 'active', 'tester02');

INSERT INTO t_baseline_case (project_id, baseline_name, case_code, case_name, case_type, priority, module_name, precondition, steps, expected_result, status, created_by)
VALUES (1, 'v2.0基线', 'EC-101', '购物车-添加商品', '功能测试', 'P0', '购物车模块',
        '用户已登录，商品有库存',
        '1.进入商品详情页\n2.点击加入购物车',
        '购物车数量+1，提示添加成功', 'active', 'tester02');

INSERT INTO t_baseline_case (project_id, baseline_name, case_code, case_name, case_type, priority, module_name, precondition, steps, expected_result, status, created_by)
VALUES (1, 'v2.0基线', 'EC-102', '订单-提交订单', '功能测试', 'P0', '订单模块',
        '购物车有商品，用户有收货地址',
        '1.进入购物车\n2.选择商品\n3.点击结算\n4.确认地址\n5.提交订单',
        '订单创建成功，库存扣减，跳转支付页', 'active', 'tester03');

-- 基线用例（用户中心 project_id=2）
INSERT INTO t_baseline_case (project_id, baseline_name, case_code, case_name, case_type, priority, module_name, precondition, steps, expected_result, status, created_by)
VALUES (2, 'v1.0基线', 'UC-001', '用户注册-手机号注册', '功能测试', 'P0', '注册模块',
        '手机号未被注册',
        '1.打开注册页\n2.输入手机号\n3.获取验证码\n4.填写密码\n5.提交',
        '注册成功，自动登录，跳转首页', 'active', 'tester01');

INSERT INTO t_baseline_case (project_id, baseline_name, case_code, case_name, case_type, priority, module_name, precondition, steps, expected_result, status, created_by)
VALUES (2, 'v1.0基线', 'UC-002', '用户信息修改', '功能测试', 'P1', '个人中心',
        '用户已登录',
        '1.进入个人中心\n2.编辑昵称\n3.保存',
        '昵称修改成功，实时刷新显示', 'active', 'tester02');

-- 执行用例（电商平台 v1.0 第1轮执行）
INSERT INTO t_execution_case (project_id, baseline_id, case_code, case_name, execute_round, executor, execute_time, actual_result, execute_status, remark)
VALUES (1, 1, 'EC-001', '用户登录-正常流程', '第1轮', 'tester01',
        '2026-03-01 10:00:00', '登录成功，跳转首页正常', 'pass', NULL);

INSERT INTO t_execution_case (project_id, baseline_id, case_code, case_name, execute_round, executor, execute_time, actual_result, execute_status, bug_id, remark)
VALUES (1, 1, 'EC-002', '用户登录-密码错误', '第1轮', 'tester01',
        '2026-03-01 10:30:00', '提示密码错误但第5次未锁定账号', 'fail', 'BUG-001', '锁定逻辑有问题');

INSERT INTO t_execution_case (project_id, baseline_id, case_code, case_name, execute_round, executor, execute_time, actual_result, execute_status, remark)
VALUES (1, 1, 'EC-003', '商品搜索-关键字搜索', '第1轮', 'tester02',
        '2026-03-01 14:00:00', '返回结果正常，排序正确', 'pass', NULL);

-- 执行用例（v1.0 第2轮回归）
INSERT INTO t_execution_case (project_id, baseline_id, case_code, case_name, execute_round, executor, execute_time, actual_result, execute_status, remark)
VALUES (1, 1, 'EC-002', '用户登录-密码错误', '回归', 'tester01',
        '2026-03-05 09:00:00', '修复后验证通过，第5次正常锁定', 'pass', '回归通过');

-- 执行用例（v2.0 第1轮）
INSERT INTO t_execution_case (project_id, baseline_id, case_code, case_name, execute_round, executor, execute_time, actual_result, execute_status, remark)
VALUES (1, 2, 'EC-101', '购物车-添加商品', '第1轮', 'tester02',
        '2026-03-07 10:00:00', '添加成功，数量正常', 'pass', NULL);

INSERT INTO t_execution_case (project_id, baseline_id, case_code, case_name, execute_round, executor, execute_time, actual_result, execute_status, bug_id, remark)
VALUES (1, 2, 'EC-102', '订单-提交订单', '第1轮', 'tester03',
        '2026-03-07 14:00:00', '库存扣减异常，超卖', 'fail', 'BUG-002', '并发扣库存Bug');
