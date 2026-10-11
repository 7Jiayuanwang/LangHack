-- 英语免修申请：学生提交后，向其任课教师投递个人待审核通知。

CREATE TABLE IF NOT EXISTS english_exemption_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '申请学生 users.id',
    application_reason VARCHAR(500) NOT NULL COMMENT '申请说明',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_exemption_student_created (student_id, created_at),
    INDEX idx_exemption_status_created (status, created_at),
    CONSTRAINT fk_exemption_application_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='英语免修申请';

DROP PROCEDURE IF EXISTS _v62_add_notifications_target_user;
CREATE PROCEDURE _v62_add_notifications_target_user()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notifications'
                     AND column_name = 'target_user_id') THEN
        ALTER TABLE notifications
            ADD COLUMN target_user_id BIGINT NULL COMMENT '指定接收人 users.id；NULL 表示按角色广播' AFTER target_role;
    END IF;
END;
CALL _v62_add_notifications_target_user();
DROP PROCEDURE IF EXISTS _v62_add_notifications_target_user;

DROP PROCEDURE IF EXISTS _v62_add_notifications_target_user_index;
CREATE PROCEDURE _v62_add_notifications_target_user_index()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notifications'
                     AND index_name = 'idx_notifications_target_user') THEN
        CREATE INDEX idx_notifications_target_user ON notifications (target_user_id, is_active, created_at);
    END IF;
END;
CALL _v62_add_notifications_target_user_index();
DROP PROCEDURE IF EXISTS _v62_add_notifications_target_user_index;
