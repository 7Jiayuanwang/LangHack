-- ═══════════════════════════════════════════════════════════════
-- V13: 学校数据接入配置种子数据（system_config school_data 组）
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 数据源配置
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'source', 'api', '数据源类型: api 或 etl', 0),
('school_data', 'api_base_url', 'https://api.imau.edu.cn', 'API基础URL', 0),
('school_data', 'api_token', '', 'API认证Token', 1),
('school_data', 'api_connect_timeout', '5000', 'API连接超时(ms)', 0),
('school_data', 'api_read_timeout', '30000', 'API读取超时(ms)', 0),
('school_data', 'api_retry_attempts', '3', 'API重试次数', 0),
('school_data', 'etl_jdbc_url', '', 'ETL JDBC连接URL', 0),
('school_data', 'etl_username', '', 'ETL数据库用户名', 0),
('school_data', 'etl_password', '', 'ETL数据库密码', 1),
('school_data', 'etl_connection_timeout', '30000', 'ETL连接超时(ms)', 0),
('school_data', 'etl_query_timeout', '60000', 'ETL查询超时(ms)', 0);

-- ────────────────────────────────────────
-- 同步调度配置
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'sync_enabled', 'false', '是否启用同步调度', 0),
('school_data', 'sync_full_cron', '0 0 2 * * ?', '全量同步cron', 0),
('school_data', 'sync_incremental_cron', '0 0 * * * ?', '增量同步cron', 0),
('school_data', 'sync_validation_cron', '0 0 4 * * ?', '数据校验cron', 0),
('school_data', 'sync_batch_size', '500', '批量大小', 0),
('school_data', 'sync_timeout_minutes', '60', '同步超时(分钟)', 0);
