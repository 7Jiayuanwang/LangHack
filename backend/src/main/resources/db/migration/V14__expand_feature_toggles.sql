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
