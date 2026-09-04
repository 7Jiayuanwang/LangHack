CREATE TABLE IF NOT EXISTS assignment_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL COMMENT '模板名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '模板说明',
    config_json TEXT NOT NULL COMMENT '作业配置 JSON，不包含班级和作业截止时间',
    created_by BIGINT NOT NULL COMMENT '创建教师用户 ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_assignment_templates_creator (created_by, deleted),
    INDEX idx_assignment_templates_updated (updated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师作业模板';
