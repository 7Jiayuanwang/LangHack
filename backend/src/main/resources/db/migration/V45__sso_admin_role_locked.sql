-- V45: SSO 绑定新增管理员角色锁定字段，并补偿 admin_remark 列（历史未迁移）

DELIMITER $$

DROP PROCEDURE IF EXISTS _add_sso_binding_cols$$
CREATE PROCEDURE _add_sso_binding_cols()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'sso_user_bindings'
          AND COLUMN_NAME  = 'admin_remark'
    ) THEN
        ALTER TABLE sso_user_bindings
            ADD COLUMN admin_remark TEXT COMMENT '管理员备注：记录特殊情况（如用户被删除后重新登录）'
            AFTER sso_member_of;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'sso_user_bindings'
          AND COLUMN_NAME  = 'admin_role_locked'
    ) THEN
        ALTER TABLE sso_user_bindings
            ADD COLUMN admin_role_locked TINYINT(1) DEFAULT 0
                COMMENT '管理员角色锁定: 1=管理员手动审批指定角色，SSO/学校目录不得覆盖'
            AFTER admin_remark;
    END IF;
END$$

CALL _add_sso_binding_cols()$$
DROP PROCEDURE IF EXISTS _add_sso_binding_cols$$

DELIMITER ;
