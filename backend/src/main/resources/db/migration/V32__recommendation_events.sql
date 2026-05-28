-- V32: recommendation_events — 推荐事件追踪
CREATE TABLE IF NOT EXISTS recommendation_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    text_id BIGINT NOT NULL,
    event_type VARCHAR(20) NOT NULL COMMENT 'IMPRESSION/CLICK/DISMISS/BOOKMARK',
    source VARCHAR(30) DEFAULT NULL COMMENT 'item_cf/user_cf/fallback/persona',
    position INT DEFAULT NULL COMMENT 'Recommendation list position (0-based)',
    persona_tags VARCHAR(200) DEFAULT NULL COMMENT 'Active persona tag codes at event time, comma-separated',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_re_user (user_id),
    INDEX idx_re_text (text_id),
    INDEX idx_re_user_type (user_id, event_type),
    INDEX idx_re_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
