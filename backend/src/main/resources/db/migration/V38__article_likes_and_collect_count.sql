-- 文章点赞表
CREATE TABLE IF NOT EXISTS `article_likes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '点赞用户ID',
    `text_id` BIGINT NOT NULL COMMENT '文章ID',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_text` (`user_id`, `text_id`),
    KEY `idx_text_id` (`text_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章点赞记录';

-- texts 表添加 collect_count 字段
ALTER TABLE `texts` ADD COLUMN `collect_count` INT DEFAULT 0 COMMENT '收藏数';
