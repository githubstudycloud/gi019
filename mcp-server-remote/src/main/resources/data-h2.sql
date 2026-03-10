-- 测试数据

-- 项目
INSERT INTO t_project (id, name, description) VALUES (1, '电商平台', '电商平台核心系统');
INSERT INTO t_project (id, name, description) VALUES (2, '用户中心', '统一用户认证和管理系统');
INSERT INTO t_project (id, name, description) VALUES (3, '支付网关', '多渠道支付集成网关');

-- 版本
INSERT INTO t_project_version (id, project_id, version_name, uri) VALUES (1, 1, 'v1.0.0', '/api/v1/shop');
INSERT INTO t_project_version (id, project_id, version_name, uri) VALUES (2, 1, 'v2.0.0', '/api/v2/shop');
INSERT INTO t_project_version (id, project_id, version_name, uri) VALUES (3, 2, 'v1.0.0', '/api/v1/user');
INSERT INTO t_project_version (id, project_id, version_name, uri) VALUES (4, 2, 'v1.1.0', '/api/v1.1/user');
INSERT INTO t_project_version (id, project_id, version_name, uri) VALUES (5, 3, 'v1.0.0', '/api/v1/pay');

-- 用例
INSERT INTO t_test_case (id, project_id, case_name, case_type, priority, precondition, steps, expected_result, status) VALUES
(1, 1, '用户登录-正常流程', '功能测试', 'P0', '用户已注册', '1. 输入用户名密码\n2. 点击登录', '登录成功，跳转首页', 'active');
INSERT INTO t_test_case (id, project_id, case_name, case_type, priority, precondition, steps, expected_result, status) VALUES
(2, 1, '用户登录-密码错误', '功能测试', 'P0', '用户已注册', '1. 输入用户名\n2. 输入错误密码\n3. 点击登录', '提示密码错误', 'active');
INSERT INTO t_test_case (id, project_id, case_name, case_type, priority, precondition, steps, expected_result, status) VALUES
(3, 1, '商品搜索-关键词匹配', '功能测试', 'P1', '商品库有数据', '1. 在搜索框输入关键词\n2. 点击搜索', '展示匹配的商品列表', 'active');
INSERT INTO t_test_case (id, project_id, case_name, case_type, priority, precondition, steps, expected_result, status) VALUES
(4, 1, '下单流程-正常购买', '功能测试', 'P0', '商品有库存，用户已登录', '1. 选择商品\n2. 加入购物车\n3. 结算\n4. 支付', '订单创建成功', 'active');
INSERT INTO t_test_case (id, project_id, case_name, case_type, priority, precondition, steps, expected_result, status) VALUES
(5, 1, '下单流程-库存不足', '异常测试', 'P1', '商品库存为0', '1. 选择商品\n2. 点击购买', '提示库存不足', 'active');
INSERT INTO t_test_case (id, project_id, case_name, case_type, priority, precondition, steps, expected_result, status) VALUES
(6, 2, '用户注册-手机号注册', '功能测试', 'P0', '手机号未注册', '1. 输入手机号\n2. 获取验证码\n3. 输入验证码\n4. 设置密码', '注册成功', 'active');
INSERT INTO t_test_case (id, project_id, case_name, case_type, priority, precondition, steps, expected_result, status) VALUES
(7, 2, '用户注册-重复手机号', '异常测试', 'P1', '手机号已注册', '1. 输入已注册手机号\n2. 获取验证码', '提示手机号已注册', 'active');
INSERT INTO t_test_case (id, project_id, case_name, case_type, priority, precondition, steps, expected_result, status) VALUES
(8, 2, '修改密码', '功能测试', 'P1', '用户已登录', '1. 进入设置\n2. 点击修改密码\n3. 输入旧密码和新密码\n4. 确认', '密码修改成功', 'active');
INSERT INTO t_test_case (id, project_id, case_name, case_type, priority, precondition, steps, expected_result, status) VALUES
(9, 3, '微信支付-正常支付', '功能测试', 'P0', '订单已创建', '1. 选择微信支付\n2. 确认支付\n3. 完成支付', '支付成功，订单状态更新', 'active');
INSERT INTO t_test_case (id, project_id, case_name, case_type, priority, precondition, steps, expected_result, status) VALUES
(10, 3, '支付超时处理', '异常测试', 'P1', '订单已创建', '1. 选择支付\n2. 等待超过30分钟', '订单自动取消', 'active');
