-- V37: 学习者画像功能开关
INSERT IGNORE INTO system_config (config_group, config_key, config_value, description)
VALUES
  ('feature_toggle', 'admin.persona_analytics',         'true', '管理员-画像分析'),
  ('feature_toggle', 'admin.class_cohort',              'true', '管理员-班级画像分析'),
  ('feature_toggle', 'student.learner_profile_enabled', 'true', '学生端-我的画像');
