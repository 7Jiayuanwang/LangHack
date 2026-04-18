-- ═══════════════════════════════════════════════════════════════
-- V18: 学校数据功能开关
-- ═══════════════════════════════════════════════════════════════

-- 运维端：学校数据浏览和配置功能开关（默认关闭，仅运维和管理员可见）
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES
('feature_toggle', 'ops.school_data_browser', 'false', '运维端学校数据浏览功能（行政班、课程、教学班等）', 0),
('feature_toggle', 'ops.school_data_config', 'false', '运维端学校数据源配置功能（API/ETL切换、连接测试）', 0);

-- 学生端：课表功能开关（默认关闭，需要学校数据源配置完成后启用）
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES
('feature_toggle', 'page_schedule', 'false', '学生端课表查看功能（从教务系统同步课表数据）', 0);
