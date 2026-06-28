-- ═══════════════════════════════════════════════════════════════
-- V6: 文章收藏 + 功能开关 + 个性化开关
-- 整合自: 原 V12 (article_favorites + feature_toggle page toggles)
--         + V13 (system_config description 乱码修复 + admin toggle + teacher/ops toggle)
--         + V14 (user_tags + feature_override)
--         + V17 (page_word_recite toggle)
--         + V37 (learner profile feature toggles)
--         + V47 (feature toggle cleanup — delete orphans + add missing seeds)
-- ═══════════════════════════════════════════════════════════════

SET NAMES utf8mb4;

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
-- 2. 学生端功能开关 seed [原 V12 + V13 + V17]
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
('feature_toggle', 'page_user_texts', 'true', '学生端-我的上传页', 0),
-- V13: 学生端缺失 toggle（补齐全 15 页覆盖）
('feature_toggle', 'page_courses',           'false', '学生端-课程学习页（开发中）',   0),
('feature_toggle', 'page_scores',            'true',  '学生端-等级考试成绩页',         0),
('feature_toggle', 'page_course_selections', 'true',  '学生端-我的选课页',             0),
('feature_toggle', 'page_text_learning',     'true',  '学生端-课文学习子页',           0),
('feature_toggle', 'page_schedule',          'true',  '学生端-我的课表页',             0),
-- V13: 学生端补充页面 toggle
('feature_toggle', 'page_videos',            'true',  '学生端-视频学习页',             0),
('feature_toggle', 'page_video_player',      'true',  '学生端-视频播放器页',           0),
('feature_toggle', 'page_interactive_text',  'true',  '学生端-交互式课文页',           0),
('feature_toggle', 'page_vocabulary',        'true',  '学生端-词汇学习页',             0),
('feature_toggle', 'page_ai_qa',             'true',  '学生端-AI问答页',               0),
('feature_toggle', 'page_articles',          'true',  '学生端-文章阅读页',             0),
('feature_toggle', 'page_user_texts',        'true',  '学生端-我的课文页',             0),
-- V17: 补齐背单词页 toggle
('feature_toggle', 'page_word_recite',       'true',  '学生端-背单词页',               0),
	('feature_toggle', 'student.campaigns',      'true',  '学生端-活动页',                 0);

-- ────────────────────────────────────────
-- 3. 管理员功能开关 seed [原 V13]
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'admin.dashboard',    'true', '管理员-仪表盘',     0),
  ('feature_toggle', 'admin.courses',      'true', '管理员-课程管理',   0),
  ('feature_toggle', 'admin.classes',      'true', '管理员-班级管理',   0),
  ('feature_toggle', 'admin.users',        'true', '管理员-用户管理',   0),
  ('feature_toggle', 'admin.units',        'true', '管理员-单元管理',   0),
  ('feature_toggle', 'admin.words',        'true', '管理员-单词管理',   0),
  ('feature_toggle', 'admin.texts',        'true', '管理员-课文管理',   0),
  ('feature_toggle', 'admin.assignments',  'true', '管理员-作业管理',   0),
  ('feature_toggle', 'admin.sso_approval', 'true', '管理员-SSO 审批',   0),
  ('feature_toggle', 'admin.campaigns',   'true', '管理员-活动管理',   0);

-- ────────────────────────────────────────
-- 4. 教师端功能开关 seed [原 V13]
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_teacher_dashboard',   'true', '教师端-仪表盘',         0),
  ('feature_toggle', 'page_teacher_courses',     'true', '教师端-课程管理',       0),
  ('feature_toggle', 'page_teacher_my_courses',  'true', '教师端-我的课程',       0),
  ('feature_toggle', 'page_teacher_classes',     'true', '教师端-班级管理',       0),
  ('feature_toggle', 'page_teacher_assignments', 'true', '教师端-作业管理',       0);

-- ────────────────────────────────────────
-- 5. 管理员端补充页面 toggle [原 V13]
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_admin_videos',      'true', '管理员-视频管理',       0),
  ('feature_toggle', 'page_admin_backups',     'true', '管理员-备份管理',       0),
  ('feature_toggle', 'page_admin_monitoring',  'true', '管理员-系统监控',       0);

-- ────────────────────────────────────────
-- 6. 运维端页面 toggle [原 V13]
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_ops_monitoring',           'true', '运维端-系统监控',           0),
  ('feature_toggle', 'page_ops_ai_providers',         'true', '运维端-AI提供商管理',       0),
  ('feature_toggle', 'page_ops_feature_toggles',      'true', '运维端-功能开关管理',       0),
  ('feature_toggle', 'page_ops_school_data_config',   'true', '运维端-学校数据配置',       0),
  ('feature_toggle', 'page_ops_school_data_browser',  'true', '运维端-学校数据浏览器',     0),
  ('feature_toggle', 'page_ops_update',               'true', '运维端-系统更新',           0);

-- ────────────────────────────────────────
-- 7. AI 配置 + 行为开关 [原 V13]
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'student.lazy_course_selections', 'true', '学生端 Lazy 选课记录同步（陈旧度阈值 1 天）', 0),
  ('feature_toggle', 'teacher.school_data_enabled', 'true', '教师端学校数据功能（课表/选课/成绩）', 0);

-- ────────────────────────────────────────
-- 8. 学习者画像功能开关 [原 V37]
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'admin.persona_analytics',         'true', '管理员-画像分析',           0),
  ('feature_toggle', 'admin.class_cohort',              'true', '管理员-班级画像分析',       0),
  ('feature_toggle', 'student.learner_profile_enabled', 'true', '学生端-我的画像',           0),
  ('feature_toggle', 'admin.tag_management',            'true', '管理员-标签管理',           0);

-- ────────────────────────────────────────
-- 9. 修复 description 乱码 [原 V13]
--    确保所有 feature_toggle 条目的 description 字段被规范化
-- ────────────────────────────────────────
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
UPDATE `system_config` SET `description` = '学生端-背单词页'                     WHERE `config_group` = 'feature_toggle' AND `config_key` = 'page_word_recite' AND (`description` IS NULL OR `description` = '');

-- ────────────────────────────────────────
-- 10. 恢复 page_ai_qa 开关 + 清除禁用覆盖 [原 V13 Part 2]
-- ────────────────────────────────────────
UPDATE `system_config`
SET `config_value` = 'true'
WHERE `config_group` = 'feature_toggle'
  AND `config_key`   = 'page_ai_qa';

-- 清除所有对 page_ai_qa 的禁用覆盖（tag/user scope）
DELETE FROM `feature_override` WHERE `feature_key` = 'page_ai_qa';

-- ────────────────────────────────────────
-- 11. 删除孤立种子 — 无路由守卫或代码消费 [原 V47]
--     注意：这些键在前面步骤 seed 后必须被删除，以保证最终状态正确
-- ────────────────────────────────────────
DELETE FROM system_config WHERE config_group = 'feature_toggle' AND config_key IN (
  -- 学生端：路由无 featureKey 守卫（页面始终可达），或路由仅为 redirect
  'page_vocabulary',        -- 消费点改用 page_word_recite（Words 页统一入口）
  'page_videos',            -- /student/videos 路由无 featureKey，页面始终可达
  'page_video_player',      -- /student/video-player 路由无 featureKey
  'page_interactive_text',  -- 无对应路由（文章详情走 /article/:id，不受此 toggle）
  'page_text_learning',     -- 路由为纯 redirect，无实体页面
  -- 管理员端：实际在 /ops/ 路由下，不在 /admin/ 下
  'page_admin_backups',     -- 备份页在 /ops/backups，无 featureKey 守卫
  'page_admin_monitoring',  -- 监控页在 /ops/monitoring，无 featureKey 守卫
  -- 运维端：ops 路由均无 featureKey 守卫（ops.school_data_* 另有专用 key）
  'page_ops_monitoring',
  'page_ops_ai_providers',
  'page_ops_feature_toggles',
  'page_ops_school_data_config',   -- 实际 key 是 ops.school_data_config
  'page_ops_school_data_browser',  -- 实际 key 是 ops.school_data_browser
  'page_ops_update',
  -- 教师端：teacher 路由均无 featureKey 守卫（teacher.school_data_* 另有专用 key）
  'page_teacher_dashboard',
  'page_teacher_courses',
  'page_teacher_my_courses',
  'page_teacher_classes',
  'page_teacher_assignments',
  -- 行为开关：曾规划但无代码消费
  'student.lazy_data_sync',
  'student.lazy_course_selections'
);

-- ────────────────────────────────────────
-- 12. 补充缺失种子 — 业务代码已消费但未 seed [原 V47]
-- ────────────────────────────────────────
INSERT IGNORE INTO system_config (config_group, config_key, config_value, description, is_secret) VALUES
  -- UserVideoController + UserTexts.vue 消费：控制学生能否上传视频
  ('feature_toggle', 'student.video_upload_enabled', 'true', '学生端-视频上传功能（全局关闭后仅标签内用户可上传）', 0),
  -- TeacherDataController 消费：控制教师是否可访问学习者画像 API
  ('feature_toggle', 'teacher.learner_profile_enabled', 'true', '教师端-学生学习画像查看功能', 0);

-- ────────────────────────────────────────
-- 13. 个性化功能开关 — user_tags + feature_override [原 V14]
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
