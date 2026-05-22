-- V25: 补建 exercise_options 表（AI 出题选项存储）
-- 该表在历史备份中存在，但从未纳入 Flyway 迁移脚本

CREATE TABLE IF NOT EXISTS `exercise_options` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `exercise_id` BIGINT NOT NULL COMMENT '习题ID',
  `option_content` VARCHAR(500) NOT NULL COMMENT '选项内容',
  `option_key` VARCHAR(10) NOT NULL COMMENT '选项标识(A, B, C, D等)',
  `is_correct` TINYINT(1) DEFAULT '0' COMMENT '是否正确答案',
  `order_num` INT DEFAULT '1' COMMENT '显示顺序',
  `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exercise_option` (`exercise_id`, `option_key`),
  KEY `idx_exercise_id` (`exercise_id`),
  CONSTRAINT `exercise_options_ibfk_1` FOREIGN KEY (`exercise_id`) REFERENCES `text_exercises` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='习题选项表';
