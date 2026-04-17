-- ═══════════════════════════════════════════════════════════════
-- V7: 文章收藏 + 功能开关 + 个性化开关
-- 整合自: 原 V12 (article_favorites + feature_toggle 配置)
--         + V13 (修复 system_config description 乱码)
--         + V14 (user_tags + feature_override)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 文章收藏表 [原 V12]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS article_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    text_id BIGINT NOT NULL COMMENT '课文/文章ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_student_text (student_id, text_id),
    INDEX idx_student_id (student_id),
    INDEX idx_text_id (text_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章收藏表';

-- ────────────────────────────────────────
-- 2. 功能开关配置 [原 V12 + V13]
--    V13 的 UPDATE 语句紧跟 INSERT IGNORE，确保 description 正确
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'page_dashboard', 'true', '学生端-发现页', 0),
('feature_toggle', 'page_courses', 'false', '学生端-课程页（开发中）', 0),
('feature_toggle', 'page_classes', 'true', '学生端-班级/社区页', 0),
('feature_toggle', 'page_assignments', 'true', '学生端-任务页', 0),
('feature_toggle', 'page_articles', 'true', '学生端-文章页', 0),
('feature_toggle', 'page_vocabulary', 'true', '学生端-生词本页', 0),
('feature_toggle', 'page_my_study', 'true', '学生端-我的学习页', 0),
('feature_toggle', 'page_ai_video', 'false', '学生端-AI视频字幕页（未完善）', 0),
('feature_toggle', 'page_ai_qa', 'true', '学生端-AI问答页', 0),
('feature_toggle', 'page_user_texts', 'true', '学生端-我的上传页', 0);

-- 修复 description 乱码 [原 V13]
UPDATE `system_config` SET `description` = '学生端-发现页'              WHERE `config_key` = 'page_dashboard'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-课程页（开发中）'    WHERE `config_key` = 'page_courses'     AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-班级/社区页'         WHERE `config_key` = 'page_classes'     AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-任务页'              WHERE `config_key` = 'page_assignments' AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-文章页'              WHERE `config_key` = 'page_articles'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-生词本页'            WHERE `config_key` = 'page_vocabulary' AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-我的学习页'          WHERE `config_key` = 'page_my_study'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-AI视频字幕页（未完善）' WHERE `config_key` = 'page_ai_video'  AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-AI问答页'            WHERE `config_key` = 'page_ai_qa'      AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-我的上传页'          WHERE `config_key` = 'page_user_texts' AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '是否使用 OpenRouter 作为 AI 服务商' WHERE `config_key` = 'use_openrouter'     AND `config_group` = 'ai_config';
UPDATE `system_config` SET `description` = 'OpenRouter API Key'                   WHERE `config_key` = 'openrouter_api_key' AND `config_group` = 'ai_config';
UPDATE `system_config` SET `description` = 'OpenRouter 模型 ID'                  WHERE `config_key` = 'openrouter_model'   AND `config_group` = 'ai_config';

-- ────────────────────────────────────────
-- 3. 个性化功能开关 [原 V14]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_tags (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    tag_name     VARCHAR(64) NOT NULL,
    created_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_user_tag (user_id, tag_name),
    INDEX idx_tag_name (tag_name),
    INDEX idx_user_id  (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS feature_override (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    scope        VARCHAR(16)  NOT NULL COMMENT 'tag 或 user',
    scope_value  VARCHAR(128) NOT NULL COMMENT 'tag名称 或 userId字符串',
    feature_key  VARCHAR(128) NOT NULL,
    enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    description  VARCHAR(255) DEFAULT NULL,
    updated_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_scope_key (scope, scope_value, feature_key),
    INDEX idx_scope_value (scope, scope_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
