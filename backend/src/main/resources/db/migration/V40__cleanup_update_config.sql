-- V31: 清理 update_config 表 — 移除普通更新字段，保留热部署字段并重命名为主字段
DROP PROCEDURE IF EXISTS v31_cleanup_update_config;
DELIMITER $$
CREATE PROCEDURE v31_cleanup_update_config()
BEGIN
    -- 将 hot_deploy_enabled → enabled（如果旧 enabled 列存在则先删除）
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'update_config'
                 AND COLUMN_NAME = 'hot_deploy_enabled') THEN

        -- 用热部署字段的值覆盖普通更新字段
        UPDATE update_config
        SET enabled        = COALESCE(hot_deploy_enabled, 0),
            cron_expr      = COALESCE(hot_deploy_cron_expr, '0 0 2 ? * SUN'),
            last_run_at    = hot_deploy_last_run_at,
            last_run_status = COALESCE(hot_deploy_last_run_status, 'NEVER');

        -- 删除热部署专用列
        ALTER TABLE update_config
            DROP COLUMN hot_deploy_enabled,
            DROP COLUMN hot_deploy_cron_expr,
            DROP COLUMN hot_deploy_last_run_at,
            DROP COLUMN hot_deploy_last_run_status;
    END IF;

    -- 删除普通更新专用列（backup_before_update）
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'update_config'
                 AND COLUMN_NAME = 'backup_before_update') THEN
        ALTER TABLE update_config DROP COLUMN backup_before_update;
    END IF;
END$$
DELIMITER ;
CALL v31_cleanup_update_config();
DROP PROCEDURE IF EXISTS v31_cleanup_update_config;
