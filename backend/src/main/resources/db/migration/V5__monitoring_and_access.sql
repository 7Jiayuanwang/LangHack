-- ═══════════════════════════════════════════════════════════════
-- V23: 访问监控 + AI调用统计 + 角色扩展 + 演示账号
-- 整合自: V23 (monitoring tables), V24 (OPS role), V25 (demo accounts)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 访问日志表（原始数据，保留7天）[原 V23]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS access_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT DEFAULT NULL COMMENT '用户ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '用户名',
    method VARCHAR(10) NOT NULL COMMENT 'HTTP方法',
    uri VARCHAR(500) NOT NULL COMMENT '请求URI',
    query_string VARCHAR(1000) DEFAULT NULL COMMENT '查询参数',
    status_code INT NOT NULL COMMENT 'HTTP状态码',
    response_time_ms BIGINT NOT NULL DEFAULT 0 COMMENT '响应时间(毫秒)',
    ip_address VARCHAR(45) DEFAULT NULL COMMENT 'IP地址',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_created_time (created_time),
    INDEX idx_username (username),
    INDEX idx_uri (uri(191)),
    INDEX idx_status_code (status_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访问日志表';

-- ────────────────────────────────────────
-- 2. 每小时聚合统计表（保留90天）[原 V23]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS access_stats_hourly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_hour DATETIME NOT NULL COMMENT '统计小时(整点)',
    total_requests INT NOT NULL DEFAULT 0 COMMENT '总请求数',
    unique_users INT NOT NULL DEFAULT 0 COMMENT '独立用户数',
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '平均响应时间(毫秒)',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '最大响应时间(毫秒)',
    error_count INT NOT NULL DEFAULT 0 COMMENT '错误数(4xx+5xx)',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_stat_hour (stat_hour)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每小时访问统计';

-- ────────────────────────────────────────
-- 3. 每日聚合统计表（长期保留）[原 V23]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS access_stats_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL COMMENT '统计日期',
    total_requests INT NOT NULL DEFAULT 0 COMMENT '总请求数',
    unique_users INT NOT NULL DEFAULT 0 COMMENT '独立用户数',
    peak_concurrent INT NOT NULL DEFAULT 0 COMMENT '峰值并发数',
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '平均响应时间(毫秒)',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '最大响应时间(毫秒)',
    error_count INT NOT NULL DEFAULT 0 COMMENT '错误数',
    top_pages TEXT DEFAULT NULL COMMENT '热门页面JSON',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日访问统计';

-- ────────────────────────────────────────
-- 4. AI/外部服务调用日志表（保留30天）[原 V23]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(50) NOT NULL COMMENT '服务名称: HIAGENT/OPENROUTER/TENCENT_TRANSLATE/TENCENT_ASR/BILIBILI',
    method_name VARCHAR(100) DEFAULT NULL COMMENT '调用方法名',
    success TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否成功',
    response_time_ms BIGINT NOT NULL DEFAULT 0 COMMENT '响应时间(毫秒)',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    input_tokens INT DEFAULT NULL COMMENT '输入token数',
    output_tokens INT DEFAULT NULL COMMENT '输出token数',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    triggered_by VARCHAR(100) DEFAULT NULL COMMENT '触发用户',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_service_name (service_name),
    INDEX idx_created_time (created_time),
    INDEX idx_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI调用日志表';

-- ────────────────────────────────────────
-- 5. AI调用日统计表（长期保留）[原 V23]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_call_stats_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL COMMENT '统计日期',
    service_name VARCHAR(50) NOT NULL COMMENT '服务名称',
    total_calls INT NOT NULL DEFAULT 0 COMMENT '总调用次数',
    success_calls INT NOT NULL DEFAULT 0 COMMENT '成功次数',
    failed_calls INT NOT NULL DEFAULT 0 COMMENT '失败次数',
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '平均响应时间',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '最大响应时间',
    total_input_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总输入token',
    total_output_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总输出token',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_date_service (stat_date, service_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI调用日统计';

-- ────────────────────────────────────────
-- 6. 用户角色扩展 — 新增 OPS 运维角色 [原 V24]
-- ────────────────────────────────────────
ALTER TABLE users MODIFY COLUMN role ENUM('USER','STUDENT','TEACHER','ADMIN','OPS') NOT NULL DEFAULT 'USER';

-- ────────────────────────────────────────
-- 7. 确保演示账号存在且可正常登录 [原 V25]
--    密码: admin123 (复用 admin 账号的 BCrypt hash)
-- ────────────────────────────────────────
-- 确保 teacher 账号存在
INSERT INTO users (username, password, role, real_name, status, deleted, created_time, updated_time)
SELECT 'teacher',
       (SELECT password FROM users WHERE username = 'admin' LIMIT 1),
       'TEACHER', '演示教师', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'teacher');

-- 确保 student 账号存在
INSERT INTO users (username, password, role, real_name, status, deleted, created_time, updated_time)
SELECT 'student',
       (SELECT password FROM users WHERE username = 'admin' LIMIT 1),
       'STUDENT', '演示学生', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'student');

-- 同步密码 + 确保 ACTIVE 状态 + 未被软删除
UPDATE users SET
    password = (SELECT pw FROM (SELECT password AS pw FROM users WHERE username = 'admin' LIMIT 1) tmp),
    status = 'ACTIVE',
    deleted = 0
WHERE username IN ('teacher', 'student');
