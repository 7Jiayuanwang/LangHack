-- 单元单词关联表（从 complete_init.sql 补录）
CREATE TABLE IF NOT EXISTS `unit_words` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `unit_id` BIGINT NOT NULL COMMENT '单元ID',
    `word_id` BIGINT NOT NULL COMMENT '单词ID',
    `order_num` INT DEFAULT 0 COMMENT '排序号',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_unit_id (`unit_id`),
    INDEX idx_word_id (`word_id`),
    UNIQUE KEY uk_unit_word (`unit_id`, `word_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单元单词关联表';
