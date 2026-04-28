-- ═══════════════════════════════════════════════════════════════
-- V14: 功能开关扩展 + AI 配置增强
-- 整合自: V14 (expand_feature_toggles) + V16 (ai_qa_recovery_and_hiagent_config)
--       + V17 (add_page_word_recite_toggle) + V18 (ai_provider_protocol)
-- ═══════════════════════════════════════════════════════════════

-- V14: 扩展 feature_toggle 覆盖学生端全量页面 + 管理员模块分级
-- 背景：
--   1. 原 V7 只 seed 了 11 个学生页 toggle，page_scores / page_course_selections 在代码中引用
--      但 DB 未 seed，导致 toggle 失效（默认展示）
--   2. 新增 page_courses / page_text_learning 以支持学生端 Layout 全覆盖
--   3. 新增 9 个 admin.* toggle 支持管理员模块级粒度控制
-- 与 V7/V12 关系：V7 只覆盖部分学生页；V12 添加学校数据相关；V14 补齐剩余

USE english_learning;
SET NAMES utf8mb4;

-- ─────────────────────────────────────────────
-- 学生端 4 个缺失 toggle（补齐全 15 页覆盖）
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_courses',           'false', '学生端-课程学习页（开发中）',   0),
  ('feature_toggle', 'page_scores',            'true',  '学生端-等级考试成绩页',         0),
  ('feature_toggle', 'page_course_selections', 'true',  '学生端-我的选课页',             0),
  ('feature_toggle', 'page_text_learning',     'true',  '学生端-课文学习子页',           0),
  ('feature_toggle', 'page_schedule',          'true',  '学生端-我的课表页',             0);

-- ─────────────────────────────────────────────
-- 管理员 9 个模块 toggle（分级控制）
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'admin.dashboard',    'true', '管理员-仪表盘',     0),
  ('feature_toggle', 'admin.courses',      'true', '管理员-课程管理',   0),
  ('feature_toggle', 'admin.classes',      'true', '管理员-班级管理',   0),
  ('feature_toggle', 'admin.users',        'true', '管理员-用户管理',   0),
  ('feature_toggle', 'admin.units',        'true', '管理员-单元管理',   0),
  ('feature_toggle', 'admin.words',        'true', '管理员-单词管理',   0),
  ('feature_toggle', 'admin.texts',        'true', '管理员-课文管理',   0),
  ('feature_toggle', 'admin.assignments',  'true', '管理员-作业管理',   0),
  ('feature_toggle', 'admin.sso_approval', 'true', '管理员-SSO 审批',   0);

-- ─────────────────────────────────────────────
-- 学生端补充页面 toggle
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_videos',            'true',  '学生端-视频学习页',             0),
  ('feature_toggle', 'page_video_player',      'true',  '学生端-视频播放器页',           0),
  ('feature_toggle', 'page_interactive_text',  'true',  '学生端-交互式课文页',           0),
  ('feature_toggle', 'page_vocabulary',        'true',  '学生端-词汇学习页',             0),
  ('feature_toggle', 'page_ai_qa',             'true',  '学生端-AI问答页',               0),
  ('feature_toggle', 'page_articles',          'true',  '学生端-文章阅读页',             0),
  ('feature_toggle', 'page_user_texts',        'true',  '学生端-我的课文页',             0);

-- ─────────────────────────────────────────────
-- 教师端页面 toggle
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_teacher_dashboard',   'true', '教师端-仪表盘',         0),
  ('feature_toggle', 'page_teacher_courses',     'true', '教师端-课程管理',       0),
  ('feature_toggle', 'page_teacher_my_courses',  'true', '教师端-我的课程',       0),
  ('feature_toggle', 'page_teacher_classes',     'true', '教师端-班级管理',       0),
  ('feature_toggle', 'page_teacher_assignments', 'true', '教师端-作业管理',       0);

-- ─────────────────────────────────────────────
-- 管理员端补充页面 toggle
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_admin_videos',      'true', '管理员-视频管理',       0),
  ('feature_toggle', 'page_admin_backups',     'true', '管理员-备份管理',       0),
  ('feature_toggle', 'page_admin_monitoring',  'true', '管理员-系统监控',       0);

-- ─────────────────────────────────────────────
-- 运维端页面 toggle
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_ops_monitoring',           'true', '运维端-系统监控',           0),
  ('feature_toggle', 'page_ops_ai_providers',         'true', '运维端-AI提供商管理',       0),
  ('feature_toggle', 'page_ops_feature_toggles',      'true', '运维端-功能开关管理',       0),
  ('feature_toggle', 'page_ops_school_data_config',   'true', '运维端-学校数据配置',       0),
  ('feature_toggle', 'page_ops_school_data_browser',  'true', '运维端-学校数据浏览器',     0),
  ('feature_toggle', 'page_ops_update',               'true', '运维端-系统更新',           0);

-- ═══════════════════════════════════════════════════════════════
-- Part 2: AI 问答恢复 + HiAgent 凭证配置
-- ═══════════════════════════════════════════════════════════════

--      或 feature_override 里有对 page_ai_qa 的禁用规则 → 路由守卫拦截 → "进不去"
--   2. HiAgent 改写/问答凭证原本只通过 application.yml + 环境变量注入，
--      违反"所有配置前端可配"的运维诉求 → 把 4+1 个 HiAgent 凭证迁到 system_config.ai_config
--      后端 HiAgentServiceImpl 读值优先 DB，回退到 @Value（环境变量）
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 恢复 page_ai_qa 开关为 true（无论之前是何值）
--    并清除 feature_override 里的禁用覆盖
-- ────────────────────────────────────────
UPDATE `system_config`
SET `config_value` = 'true'
WHERE `config_group` = 'feature_toggle'
  AND `config_key`   = 'page_ai_qa';

-- 若历史未 seed，补 seed（INSERT IGNORE 保持幂等）
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES ('feature_toggle', 'page_ai_qa', 'true', '学生端-AI问答页', 0);

-- 清除所有对 page_ai_qa 的禁用覆盖（tag/user scope）
DELETE FROM `feature_override` WHERE `feature_key` = 'page_ai_qa';

-- ────────────────────────────────────────
-- 2. HiAgent 凭证 seed 到 ai_config group（is_secret=1 for keys）
--    值默认为空字符串，表示"尚未从前端配置，使用 application.yml 环境变量"
--    前端面板写入 DB 后，HiAgentServiceImpl 会优先读 DB 值
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('ai_config', 'hiagent_api_url',    '', 'HiAgent API Base URL（留空则使用环境变量 HIAGENT_API_URL）',         0),
  ('ai_config', 'hiagent_api_key',    '', 'HiAgent 改写应用 API Key（留空则使用环境变量 HIAGENT_API_KEY）',      1),
  ('ai_config', 'hiagent_app_id',     '', 'HiAgent 改写应用 App ID（留空则使用环境变量 HIAGENT_APP_ID）',        0),
  ('ai_config', 'hiagent_qa_api_key', '', 'HiAgent 问答应用 API Key（必须与改写凭证不同；留空则用 HIAGENT_QA_API_KEY）', 1),
  ('ai_config', 'hiagent_qa_app_id',  '', 'HiAgent 问答应用 App ID（必须与改写凭证不同；留空则用 HIAGENT_QA_APP_ID）',  0);

-- ═══════════════════════════════════════════════════════════════
-- Part 3: 补齐背单词页 toggle
-- ═══════════════════════════════════════════════════════════════

--   toggleKey = 'page_word_recite'，但该键从未 seed 到
--   system_config.feature_toggle，导致运维面板"全局开关"列表缺少
--   "背单词"条目 —— 开关与页面无法一一对应。
--
--   同时 router/index.js 中 /student/word-recite 路由守卫缺失
--   featureKey，本迁移配合前端补齐后，达成"开关关闭 → 导航隐藏 +
--   快捷入口隐藏 + 直接访问被拦截"三层联动。
-- ═══════════════════════════════════════════════════════════════

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES ('feature_toggle', 'page_word_recite', 'true', '学生端-背单词页', 0);

-- 若历史中存在但描述为空，统一规范化描述
UPDATE `system_config`
SET `description` = '学生端-背单词页'
WHERE `config_group` = 'feature_toggle'
  AND `config_key` = 'page_word_recite'
  AND (`description` IS NULL OR `description` = '');

-- ═══════════════════════════════════════════════════════════════
-- Part 4: AI 服务商协议类型字段
-- ═══════════════════════════════════════════════════════════════


ALTER TABLE `ai_provider`
    ADD COLUMN `protocol` VARCHAR(30) NOT NULL DEFAULT 'openai_compatible'
        AFTER `service`;

ALTER TABLE `ai_provider`
    MODIFY COLUMN `protocol` VARCHAR(30) NOT NULL DEFAULT 'openai_compatible'
        COMMENT '协议类型: openai_compatible | hiagent';
