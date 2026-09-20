-- 小芽文章知识点：按文章内容、实际阅读版本和推荐层级跨学生复用。
CREATE TABLE IF NOT EXISTS article_learning_insights (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    text_id BIGINT NOT NULL,
    content_version VARCHAR(24) NOT NULL,
    target_level VARCHAR(20) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    points_json TEXT NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_article_insight_cache (text_id, content_version, target_level, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 个人投递和已读状态单独保存，不进入全站通知公告。
CREATE TABLE IF NOT EXISTS article_insight_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    text_id BIGINT NOT NULL,
    insight_id BIGINT NOT NULL,
    read_time DATETIME DEFAULT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_article_insight_delivery (user_id, insight_id),
    KEY idx_article_insight_unread (user_id, read_time, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
