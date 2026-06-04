-- V30: update_config 表新增零停机热部署调度字段
DROP PROCEDURE IF EXISTS v30_add_hot_deploy_fields;
DELIMITER $$
CREATE PROCEDURE v30_add_hot_deploy_fields()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'update_config'
                     AND COLUMN_NAME = 'hot_deploy_enabled') THEN
        ALTER TABLE update_config
            ADD COLUMN hot_deploy_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用定时零停机热部署',
            ADD COLUMN hot_deploy_cron_expr VARCHAR(100) NULL COMMENT '热部署 cron 表达式',
            ADD COLUMN hot_deploy_last_run_at DATETIME NULL COMMENT '最近一次热部署执行时间',
            ADD COLUMN hot_deploy_last_run_status VARCHAR(20) NULL DEFAULT 'NEVER' COMMENT '最近一次热部署状态';
    END IF;
END$$
DELIMITER ;
CALL v30_add_hot_deploy_fields();
DROP PROCEDURE IF EXISTS v30_add_hot_deploy_fields;
