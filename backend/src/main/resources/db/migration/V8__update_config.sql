-- V8: self-update configuration table
CREATE TABLE IF NOT EXISTS update_config (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    cron_expr            VARCHAR(64) NOT NULL DEFAULT '0 0 3 * * ?' COMMENT 'Spring cron expression',
    enabled              TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether scheduled self-update is enabled',
    git_branch           VARCHAR(64) NOT NULL DEFAULT 'master' COMMENT 'tracked git branch',
    backup_before_update TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'whether to back up the database before update',
    last_run_at          DATETIME DEFAULT NULL COMMENT 'last execution time',
    last_run_status      VARCHAR(16) DEFAULT 'NEVER' COMMENT 'NEVER/RUNNING/SUCCESS/FAILED'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='self-update configuration';

INSERT IGNORE INTO update_config (id, cron_expr, enabled, git_branch, backup_before_update, last_run_status)
VALUES (1, '0 0 3 * * ?', 0, 'master', 1, 'NEVER');
