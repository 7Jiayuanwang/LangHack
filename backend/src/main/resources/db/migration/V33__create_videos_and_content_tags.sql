-- 视频内容标签表
CREATE TABLE IF NOT EXISTS `content_tags` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL COMMENT '标签名称',
    `color` VARCHAR(20) DEFAULT NULL COMMENT '标签颜色（Hex）',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容标签';

-- 视频表
CREATE TABLE IF NOT EXISTS `videos` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(255) NOT NULL COMMENT '视频标题',
    `description` TEXT DEFAULT NULL COMMENT '视频描述',
    `media_id` BIGINT DEFAULT NULL COMMENT '关联 media 表（本地存储）',
    `cover_media_id` BIGINT DEFAULT NULL COMMENT '封面 media ID',
    `video_url` VARCHAR(1024) DEFAULT NULL COMMENT '视频外链 URL',
    `cover_url` VARCHAR(1024) DEFAULT NULL COMMENT '封面外链 URL',
    `subtitle_content` LONGTEXT DEFAULT NULL COMMENT '英文字幕内容',
    `subtitle_content_zh` LONGTEXT DEFAULT NULL COMMENT '中文字幕内容',
    `user_id` BIGINT NOT NULL COMMENT '上传者用户 ID',
    `is_public` TINYINT DEFAULT 1 COMMENT '是否公开 0=私有 1=公开',
    `is_featured` TINYINT DEFAULT 0 COMMENT '是否精选推荐',
    `featured_priority` INT DEFAULT 0 COMMENT '精选排序优先级',
    `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签（逗号分隔）',
    `view_count` INT DEFAULT 0 COMMENT '播放次数',
    `like_count` INT DEFAULT 0 COMMENT '点赞次数',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/approved/rejected',
    `associated_text_id` BIGINT DEFAULT NULL COMMENT '关联文章 ID',
    `transcription_status` VARCHAR(20) DEFAULT NULL COMMENT '转写状态',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_is_public` (`is_public`),
    KEY `idx_is_featured` (`is_featured`),
    KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频内容';
