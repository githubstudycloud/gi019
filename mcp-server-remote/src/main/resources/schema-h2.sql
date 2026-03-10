-- 项目表
CREATE TABLE IF NOT EXISTS t_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 版本/URI 表
CREATE TABLE IF NOT EXISTS t_project_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    version_name VARCHAR(100) NOT NULL,
    uri VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 用例表
CREATE TABLE IF NOT EXISTS t_test_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    case_name VARCHAR(200) NOT NULL,
    case_type VARCHAR(50),
    priority VARCHAR(20),
    precondition TEXT,
    steps TEXT,
    expected_result TEXT,
    status VARCHAR(20) DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
