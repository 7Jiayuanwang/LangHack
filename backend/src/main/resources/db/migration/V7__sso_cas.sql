-- ═══════════════════════════════════════════════════════════════════════
-- V7: SSO / CAS 统一身份认证
-- 合并自: V8 (sso_integration) + V16 (enable_sso_auto_grant)
--
-- 历史说明:
--   - V8  引入 SSO 表 (sso_user_bindings / sso_login_logs / sso_config)
--         并为 users 增加 sso_bound / sso_provider / sso_subject 列
--   - V16 默认开启 sso.auto_grant_student / sso.auto_grant_teacher 开关
--         (V14 已 seed = false, V16 改 true)
--   - 2026-04-19 二次整合: V8 header 从 V15 改为 V8
--   - 2026-04-22 V14 seed false 后, V16 兜底开启
-- ═══════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════
-- Part 1: SSO 三大表
-- ═══════════════════════════════════════════════════════════════════════

-- 1.1 SSO 用户绑定表
CREATE TABLE IF NOT EXISTS sso_user_bindings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '本地用户ID',
    sso_provider VARCHAR(50) NOT NULL COMMENT 'SSO提供商: CAS, OAUTH2',
    sso_user_id VARCHAR(255) NOT NULL COMMENT 'SSO用户唯一标识',
    sso_username VARCHAR(255) NOT NULL COMMENT 'SSO用户名',
    sso_email VARCHAR(255) COMMENT 'SSO邮箱',
    sso_real_name VARCHAR(255) COMMENT 'SSO真实姓名',
    sso_member_of TEXT COMMENT 'SSO组织单位 (JSON数组)',
    auto_approved TINYINT(1) DEFAULT 0 COMMENT '是否自动审批: 0=待审批, 1=已审批',
    last_login_time DATETIME COMMENT '最后登录时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除: 0=正常, 1=已删除',
    UNIQUE KEY uk_sso_provider_user (sso_provider, sso_user_id, deleted),
    INDEX idx_user_id (user_id),
    INDEX idx_sso_username (sso_username),
    INDEX idx_auto_approved (auto_approved),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO用户绑定表';

-- 1.2 SSO 登录日志表
CREATE TABLE IF NOT EXISTS sso_login_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    sso_provider VARCHAR(50) NOT NULL COMMENT 'SSO提供商',
    sso_user_id VARCHAR(255) NOT NULL COMMENT 'SSO用户ID',
    sso_username VARCHAR(255) NOT NULL COMMENT 'SSO用户名',
    login_result VARCHAR(50) NOT NULL COMMENT '登录结果: SUCCESS, PENDING_APPROVAL, FAILED',
    failure_reason VARCHAR(500) COMMENT '失败原因',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    user_agent VARCHAR(500) COMMENT '用户代理',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_sso_user (sso_provider, sso_user_id),
    INDEX idx_login_time (created_time),
    INDEX idx_login_result (login_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO登录日志';

-- 1.3 SSO 配置表
CREATE TABLE IF NOT EXISTS sso_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    config_type VARCHAR(50) NOT NULL COMMENT '配置类型: SYSTEM, CAS, OAUTH2, ROLE_MAPPING',
    description VARCHAR(500) COMMENT '配置描述',
    is_encrypted TINYINT(1) DEFAULT 0 COMMENT '是否加密存储',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_config_key (config_key),
    INDEX idx_config_type (config_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO配置表';

-- ═══════════════════════════════════════════════════════════════════════
-- Part 2: users 表扩展 (sso_bound / sso_provider / sso_subject)
-- ═══════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS _v7_add_sso_bound;
CREATE PROCEDURE _v7_add_sso_bound()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sso_bound') THEN
        ALTER TABLE users ADD COLUMN sso_bound TINYINT(1) DEFAULT 0 COMMENT '是否通过SSO绑定: 0=否, 1=是' AFTER deleted;
    END IF;
END;
CALL _v7_add_sso_bound();
DROP PROCEDURE IF EXISTS _v7_add_sso_bound;

DROP PROCEDURE IF EXISTS _v7_add_sso_provider;
CREATE PROCEDURE _v7_add_sso_provider()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sso_provider') THEN
        ALTER TABLE users ADD COLUMN sso_provider VARCHAR(50) COMMENT 'SSO提供商' AFTER sso_bound;
    END IF;
END;
CALL _v7_add_sso_provider();
DROP PROCEDURE IF EXISTS _v7_add_sso_provider;

DROP PROCEDURE IF EXISTS _v7_add_sso_subject;
CREATE PROCEDURE _v7_add_sso_subject()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sso_subject') THEN
        ALTER TABLE users ADD COLUMN sso_subject VARCHAR(255) COMMENT 'SSO唯一标识' AFTER sso_provider;
    END IF;
END;
CALL _v7_add_sso_subject();
DROP PROCEDURE IF EXISTS _v7_add_sso_subject;

-- ═══════════════════════════════════════════════════════════════════════
-- Part 3: users SSO 索引
-- ═══════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS _v7_idx_sso_bound;
CREATE PROCEDURE _v7_idx_sso_bound()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_sso_bound') THEN
        CREATE INDEX idx_sso_bound ON users (sso_bound);
    END IF;
END;
CALL _v7_idx_sso_bound();
DROP PROCEDURE IF EXISTS _v7_idx_sso_bound;

DROP PROCEDURE IF EXISTS _v7_idx_sso_subject;
CREATE PROCEDURE _v7_idx_sso_subject()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_sso_subject') THEN
        CREATE INDEX idx_sso_subject ON users (sso_provider, sso_subject);
    END IF;
END;
CALL _v7_idx_sso_subject();
DROP PROCEDURE IF EXISTS _v7_idx_sso_subject;

-- ═══════════════════════════════════════════════════════════════════════
-- Part 4: 默认 SSO 配置 (含 role_mapping)
-- ═══════════════════════════════════════════════════════════════════════

INSERT IGNORE INTO sso_config (config_key, config_value, config_type, description) VALUES
('sso.enabled', 'false', 'SYSTEM', 'SSO功能总开关'),
('sso.protocol', 'CAS', 'SYSTEM', 'SSO协议类型: CAS 或 OAUTH2'),
('sso.auto_approval.enabled', 'true', 'SYSTEM', '是否启用自动审批'),
('sso.role_mapping.ou=管理员', 'ADMIN', 'ROLE_MAPPING', '管理员角色映射'),
('sso.role_mapping.ou=运维', 'OPS', 'ROLE_MAPPING', '运维角色映射'),
('sso.role_mapping.ou=教师', 'TEACHER', 'ROLE_MAPPING', '教师角色映射'),
('sso.role_mapping.ou=学生', 'STUDENT', 'ROLE_MAPPING', '学生角色映射'),
('sso.role_mapping.ou=People', 'STUDENT', 'ROLE_MAPPING', 'CAS containerId 为 People 的用户映射为 STUDENT 角色');

-- ═══════════════════════════════════════════════════════════════════════
-- Part 5: 启用 SSO 自动授权功能开关 (原 V16)
-- SSO 学号匹配 school_student 自动授权 STUDENT
-- SSO 工号匹配 school_teacher 自动授权 TEACHER
-- ═══════════════════════════════════════════════════════════════════════

-- V14 已 seed sso.auto_grant_* = false, 强制更新为 true
UPDATE `system_config`
SET `config_value` = 'true'
WHERE `config_group` = 'feature_toggle'
  AND `config_key` IN ('sso.auto_grant_student', 'sso.auto_grant_teacher');

-- 若记录不存在则插入 (幂等)
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'sso.auto_grant_student', 'true', 'SSO 学号匹配 school_student 自动授权 STUDENT', 0),
('feature_toggle', 'sso.auto_grant_teacher', 'true', 'SSO 工号匹配 school_teacher 自动授权 TEACHER', 0);