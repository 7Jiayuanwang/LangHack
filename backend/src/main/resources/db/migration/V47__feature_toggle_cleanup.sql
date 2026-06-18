-- ═══════════════════════════════════════════════════════════════════════
-- V47: 功能开关清理 — 删除孤立种子 + 补充缺失种子
--
-- 删除的孤立种子：seeded 但没有任何路由守卫或业务代码消费，
--   在运维面板显示为无效控制项。
-- 补充的缺失种子：业务代码已消费但未 seed，isEnabled() 返回 undefined
--   等同于 true，无法通过运维面板关闭。
-- ═══════════════════════════════════════════════════════════════════════

-- ── 1. 删除孤立种子 ──────────────────────────────────────────────────────

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

-- ── 2. 补充缺失种子 ──────────────────────────────────────────────────────

INSERT IGNORE INTO system_config (config_group, config_key, config_value, description, is_secret) VALUES
  -- UserVideoController + UserTexts.vue 消费：控制学生能否上传视频
  ('feature_toggle', 'student.video_upload_enabled', 'true', '学生端-视频上传功能（全局关闭后仅标签内用户可上传）', 0),
  -- TeacherDataController 消费：控制教师是否可访问学习者画像 API
  ('feature_toggle', 'teacher.learner_profile_enabled', 'true', '教师端-学生学习画像查看功能', 0);
