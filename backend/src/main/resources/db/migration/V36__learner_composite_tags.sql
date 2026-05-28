-- V36: learner_composite_tags — 可配置的组合标签
CREATE TABLE IF NOT EXISTS learner_composite_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    tag_type VARCHAR(20) NOT NULL COMMENT 'risk/ability/habit/preference/positive',
    priority INT DEFAULT 50,
    rule_definition JSON NOT NULL COMMENT '{"conditions":[...],"combiner":"AND"}',
    action_hint VARCHAR(200) DEFAULT NULL,
    enabled TINYINT(1) DEFAULT 1,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
