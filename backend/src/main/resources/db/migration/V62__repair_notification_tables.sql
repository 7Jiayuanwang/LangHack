-- Ensure notification tables exist and support personal notifications.
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    target_role VARCHAR(20) NOT NULL DEFAULT 'ALL',
    target_user_id BIGINT NULL COMMENT 'users.id; NULL means role broadcast',
    created_by BIGINT NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_target_role (target_role),
    INDEX idx_notifications_target_user (target_user_id, is_active, created_at),
    INDEX idx_is_active (is_active),
    INDEX idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='notifications';

CREATE TABLE IF NOT EXISTS notification_reads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_notification_user (notification_id, user_id),
    INDEX idx_user_id (user_id),
    CONSTRAINT fk_notification_reads_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='notification reads';

DROP PROCEDURE IF EXISTS _v62_add_notifications_target_user;
CREATE PROCEDURE _v62_add_notifications_target_user()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'notifications' AND column_name = 'target_user_id') THEN
        ALTER TABLE notifications ADD COLUMN target_user_id BIGINT NULL AFTER target_role;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'notifications' AND index_name = 'idx_notifications_target_user') THEN
        CREATE INDEX idx_notifications_target_user ON notifications (target_user_id, is_active, created_at);
    END IF;
END;
CALL _v62_add_notifications_target_user();
DROP PROCEDURE IF EXISTS _v62_add_notifications_target_user;
