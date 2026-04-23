-- V15: 统一身份认证 (SSO) 集成

-- SSO 用户绑定表
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

-- SSO 登录日志表
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

-- SSO 配置表
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

-- 扩展 users 表字段
ALTER TABLE users
ADD COLUMN sso_bound TINYINT(1) DEFAULT 0 COMMENT '是否通过SSO绑定: 0=否, 1=是',
ADD COLUMN sso_provider VARCHAR(50) COMMENT 'SSO提供商',
ADD COLUMN sso_subject VARCHAR(255) COMMENT 'SSO唯一标识',
ADD INDEX idx_sso_bound (sso_bound),
ADD INDEX idx_sso_subject (sso_provider, sso_subject);

-- 插入默认 SSO 配置
INSERT INTO sso_config (config_key, config_value, config_type, description) VALUES
('sso.enabled', 'false', 'SYSTEM', 'SSO功能总开关'),
('sso.protocol', 'CAS', 'SYSTEM', 'SSO协议类型: CAS 或 OAUTH2'),
('sso.auto_approval.enabled', 'true', 'SYSTEM', '是否启用自动审批'),
('sso.role_mapping.ou=管理员', 'ADMIN', 'ROLE_MAPPING', '管理员角色映射'),
('sso.role_mapping.ou=运维', 'OPS', 'ROLE_MAPPING', '运维角色映射'),
('sso.role_mapping.ou=教师', 'TEACHER', 'ROLE_MAPPING', '教师角色映射'),
('sso.role_mapping.ou=学生', 'STUDENT', 'ROLE_MAPPING', '学生角色映射');
