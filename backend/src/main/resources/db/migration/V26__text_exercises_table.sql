-- V26: 补建 text_exercises 表（课文习题）
-- 该表在历史数据库中存在，但从未纳入 Flyway 迁移脚本

CREATE TABLE IF NOT EXISTS `text_exercises` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `text_id` BIGINT NOT NULL COMMENT '课文ID',
    `question` TEXT NOT NULL COMMENT '题目内容',
    `exercise_type` ENUM('MULTIPLE_CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'SHORT_ANSWER') DEFAULT 'MULTIPLE_CHOICE' COMMENT '习题类型',
    `order_num` INT DEFAULT 1 COMMENT '显示顺序',
    `created_by` BIGINT NOT NULL COMMENT '创建者ID',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (`text_id`) REFERENCES `texts`(`id`) ON DELETE CASCADE,
    INDEX `idx_text_id` (`text_id`),
    INDEX `idx_exercise_type` (`exercise_type`),
    INDEX `idx_order_num` (`order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课文习题表';
