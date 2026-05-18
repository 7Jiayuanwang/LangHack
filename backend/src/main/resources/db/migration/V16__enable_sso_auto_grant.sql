-- V16: 默认开启 SSO 自动授权功能开关
-- SSO 学号匹配 school_student 自动授权 STUDENT
-- SSO 工号匹配 school_teacher 自动授权 TEACHER

UPDATE `system_config`
SET `config_value` = 'true'
WHERE `config_group` = 'feature_toggle'
  AND `config_key` IN ('sso.auto_grant_student', 'sso.auto_grant_teacher');

-- 若记录不存在则插入（幂等）
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'sso.auto_grant_student', 'true', 'SSO 学号匹配 school_student 自动授权 STUDENT', 0),
('feature_toggle', 'sso.auto_grant_teacher', 'true', 'SSO 工号匹配 school_teacher 自动授权 TEACHER', 0);
